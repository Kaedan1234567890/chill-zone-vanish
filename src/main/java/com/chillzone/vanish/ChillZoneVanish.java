package com.chillzone.vanish;

import com.mojang.brigadier.Command;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class ChillZoneVanish implements ModInitializer {
    public static final String PERMISSION = "chillzonevanish.command.vanish";
    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();
    private static int ticks = 0;

    // Tracks whether a viewer was recently close enough for Minecraft to begin
    // tracking a vanished player. This lets us re-hide only when tracking is
    // likely to start instead of spamming remove packets every second.
    private static final Map<ViewerKey, Boolean> NEARBY = new ConcurrentHashMap<>();

    // Last sampled position for vanished players. A large jump is treated as a
    // teleport and causes one fresh hide packet to nearby viewers.
    private static final Map<UUID, Position> LAST_POSITION = new ConcurrentHashMap<>();

    // Compatibility bridge for Chill Zone Staff TP. When a vanished staff member
    // teleports to/from another player, delaying the hide packet by a few ticks
    // prevents the teleport and entity-removal packets from colliding on the client.
    private static final Map<ViewerKey, Integer> DELAYED_HIDE_TICKS = new ConcurrentHashMap<>();
    private static final int STAFF_TP_HIDE_DELAY_TICKS = 4;

    private static final double TRACKING_RADIUS_SQ = 192.0 * 192.0;
    private static final double TELEPORT_DISTANCE_SQ = 32.0 * 32.0;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("vanish")
                .requires(source -> source.getEntity() instanceof ServerPlayer p && hasPermission(p))
                .executes(ctx -> {
                    toggle(ctx.getSource().getPlayerOrException());
                    return Command.SINGLE_SUCCESS;
                }))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                ServerPlayer joining = handler.getPlayer();
                for (UUID id : VANISHED) {
                    ServerPlayer hidden = server.getPlayerList().getPlayer(id);
                    if (hidden != null && hidden != joining) hideFrom(hidden, joining);
                }
            })
        );

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            VANISHED.remove(id);
            LAST_POSITION.remove(id);
            NEARBY.keySet().removeIf(key -> key.hidden().equals(id) || key.viewer().equals(id));
            DELAYED_HIDE_TICKS.keySet().removeIf(key -> key.hidden().equals(id) || key.viewer().equals(id));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Process Staff TP compatibility every tick so the delayed hide happens
            // almost immediately after the teleport has settled.
            processDelayedHides(server);

            if (++ticks < 10) return;
            ticks = 0;

            for (UUID id : VANISHED) {
                ServerPlayer hidden = server.getPlayerList().getPlayer(id);
                if (hidden == null) continue;

                Position now = new Position(hidden.getX(), hidden.getY(), hidden.getZ());
                Position before = LAST_POSITION.put(id, now);
                boolean teleported = before != null && before.distanceSquared(now) >= TELEPORT_DISTANCE_SQ;

                for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                    if (viewer == hidden) continue;

                    ViewerKey key = new ViewerKey(id, viewer.getUUID());
                    boolean near = hidden.distanceToSqr(viewer) <= TRACKING_RADIUS_SQ;
                    boolean wasNear = NEARBY.getOrDefault(key, false);

                    // Re-hide once when a viewer enters tracking range or when the
                    // vanished player makes a large jump (for example /tpto).
                    // This avoids the old every-second packet spam that could
                    // disconnect clients after teleporting directly beside them.
                    if (near && (!wasNear || teleported) && !DELAYED_HIDE_TICKS.containsKey(key)) {
                        hideFrom(hidden, viewer);
                    }

                    NEARBY.put(key, near);
                }
            }
        });
    }


    /**
     * Called reflectively by Chill Zone Staff TP before /tpto or /tphere.
     * There is intentionally no hard mod dependency between the two projects.
     */
    public static void prepareStaffTeleport(ServerPlayer staff, ServerPlayer otherPlayer) {
        if (staff == null || otherPlayer == null) return;
        UUID staffId = staff.getUUID();
        if (!VANISHED.contains(staffId)) return;

        ViewerKey key = new ViewerKey(staffId, otherPlayer.getUUID());
        DELAYED_HIDE_TICKS.put(key, STAFF_TP_HIDE_DELAY_TICKS);
    }

    private static void processDelayedHides(MinecraftServer server) {
        if (DELAYED_HIDE_TICKS.isEmpty()) return;

        for (Map.Entry<ViewerKey, Integer> entry : new ArrayList<>(DELAYED_HIDE_TICKS.entrySet())) {
            ViewerKey key = entry.getKey();
            int remaining = entry.getValue() - 1;

            if (remaining > 0) {
                DELAYED_HIDE_TICKS.put(key, remaining);
                continue;
            }

            DELAYED_HIDE_TICKS.remove(key);

            if (!VANISHED.contains(key.hidden())) continue;
            ServerPlayer hidden = server.getPlayerList().getPlayer(key.hidden());
            ServerPlayer viewer = server.getPlayerList().getPlayer(key.viewer());
            if (hidden == null || viewer == null || hidden == viewer) continue;

            hideFrom(hidden, viewer);
            NEARBY.put(key, hidden.distanceToSqr(viewer) <= TRACKING_RADIUS_SQ);
            LAST_POSITION.put(hidden.getUUID(), new Position(hidden.getX(), hidden.getY(), hidden.getZ()));
        }
    }

    private static boolean hasPermission(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            return user != null &&
                user.getCachedData().getPermissionData().checkPermission(PERMISSION).asBoolean();
        } catch (IllegalStateException ignored) {
            // LuckPerms is a required dependency for this mod.
            // If it is unavailable for any reason, fail closed instead of exposing /vanish.
            return false;
        }
    }

    private static void toggle(ServerPlayer player) {
        if (VANISHED.contains(player.getUUID())) unvanish(player);
        else vanish(player);
    }

    private static void vanish(ServerPlayer player) {
        VANISHED.add(player.getUUID());
        LAST_POSITION.put(player.getUUID(), new Position(player.getX(), player.getY(), player.getZ()));
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != player) {
                hideFrom(player, viewer);
                NEARBY.put(new ViewerKey(player.getUUID(), viewer.getUUID()),
                    player.distanceToSqr(viewer) <= TRACKING_RADIUS_SQ);
            }
        }

        broadcastFake(server, player, false);
        player.sendSystemMessage(Component.literal("You are now vanished."));
    }

    private static void unvanish(ServerPlayer player) {
        VANISHED.remove(player.getUUID());
        LAST_POSITION.remove(player.getUUID());
        NEARBY.keySet().removeIf(key -> key.hidden().equals(player.getUUID()));
        DELAYED_HIDE_TICKS.keySet().removeIf(key -> key.hidden().equals(player.getUUID()));
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != player) showTo(player, viewer);
        }

        broadcastFake(server, player, true);
        player.sendSystemMessage(Component.literal("You are now visible."));
    }

    private static void hideFrom(ServerPlayer hidden, ServerPlayer viewer) {
        // Remove the player from TAB and from the world on the viewer's client.
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hidden.getUUID())));
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(hidden.getId()));

        // Minecraft's locator bar is a separate waypoint system. Removing the player
        // entity alone does not remove its locator dot, so explicitly untrack it too.
        viewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(hidden.getUUID()));
    }

    private static void showTo(ServerPlayer shown, ServerPlayer viewer) {
        viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(shown)));
        viewer.connection.send(new ClientboundAddEntityPacket(
            shown.getId(),
            shown.getUUID(),
            shown.getX(), shown.getY(), shown.getZ(),
            shown.getXRot(), shown.getYRot(),
            EntityTypes.PLAYER,
            0,
            shown.getDeltaMovement(),
            shown.getYHeadRot()
        ));

        // Re-send equipped items immediately after re-spawning the player entity.
        // This fixes clients (especially Bedrock through Geyser/ViaVersion) sometimes
        // knowing the armour is equipped while not rendering it until the next hit/update.
        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = shown.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                equipment.add(Pair.of(slot, stack.copy()));
            }
        }
        if (!equipment.isEmpty()) {
            viewer.connection.send(new ClientboundSetEquipmentPacket(shown.getId(), equipment));
        }
    }

    private record ViewerKey(UUID hidden, UUID viewer) {}

    private record Position(double x, double y, double z) {
        double distanceSquared(Position other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static void broadcastFake(MinecraftServer server, ServerPlayer player, boolean joined) {
        Component message = Component.empty()
            .append(prefixedName(player))
            .append(Component.literal(joined ? " joined the game" : " left the game"));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static Component prefixedName(ServerPlayer player) {
        String prefix = "";
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null && user.getCachedData().getMetaData().getPrefix() != null) {
                prefix = user.getCachedData().getMetaData().getPrefix();
            }
        } catch (IllegalStateException ignored) {}

        Component out = Component.empty();
        if (!prefix.isBlank()) {
            out = out.copy().append(LegacyText.parse(prefix));
            if (!prefix.endsWith(" ")) out = out.copy().append(Component.literal(" "));
        }
        return out.copy().append(player.getName());
    }
}
