package com.chillzone.vanish;

import com.mojang.brigadier.Command;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChillZoneVanish implements ModInitializer {
    public static final String PERMISSION = "chillzonevanish.command.vanish";
    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();
    // Unvanish is deliberately delayed for a few ticks. If /vanish is toggled
    // rapidly, the pending reveal is cancelled before any ADD_PLAYER/entity
    // packet is sent, preventing a one-frame TAB flash.
    private static final ConcurrentHashMap<UUID, Integer> PENDING_REVEAL = new ConcurrentHashMap<>();
    private static final int REVEAL_DELAY_TICKS = 10;

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
            PENDING_REVEAL.remove(id);
        });

        // Re-assert vanish every server tick. This closes the brief TAB/entity
        // reappearance window caused by other mods or player-info refreshes.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Hidden wins over every refresh: remove TAB + tracked entity every tick.
            for (UUID id : VANISHED) {
                ServerPlayer hidden = server.getPlayerList().getPlayer(id);
                if (hidden == null) continue;
                for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                    if (viewer != hidden) hideFrom(hidden, viewer);
                }
            }

            // Reveal only after the state has remained stable for the full delay.
            // A rapid second /vanish cancels this before showTo() can run.
            PENDING_REVEAL.replaceAll((id, ticks) -> ticks - 1);
            for (var entry : PENDING_REVEAL.entrySet()) {
                if (entry.getValue() > 0) continue;
                UUID id = entry.getKey();
                if (!PENDING_REVEAL.remove(id, entry.getValue())) continue;
                if (!VANISHED.remove(id)) continue;
                ServerPlayer shown = server.getPlayerList().getPlayer(id);
                if (shown == null) continue;
                for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                    if (viewer != shown) showTo(shown, viewer);
                }
                broadcastFake(server, shown, true);
                shown.sendSystemMessage(Component.literal("You are now visible."));
            }
        });
    }

    private static boolean hasPermission(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            return user != null &&
                user.getCachedData().getPermissionData().checkPermission(PERMISSION).asBoolean();
        } catch (IllegalStateException ignored) {
            // Fail closed if LuckPerms is unavailable; avoids relying on removed 26.2 permission APIs.
            return false;
        }
    }

    private static void toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        if (PENDING_REVEAL.remove(id) != null) {
            // Rapid vanish -> unvanish -> vanish: never reveal in between.
            VANISHED.add(id);
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                    if (viewer != player) hideFrom(player, viewer);
                }
            }
            player.sendSystemMessage(Component.literal("Reveal cancelled. You remain vanished."));
        } else if (VANISHED.contains(id)) {
            unvanish(player);
        } else {
            vanish(player);
        }
    }

    private static void vanish(ServerPlayer player) {
        PENDING_REVEAL.remove(player.getUUID());
        VANISHED.add(player.getUUID());
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != player) hideFrom(player, viewer);
        }

        broadcastFake(server, player, false);
        player.sendSystemMessage(Component.literal("You are now vanished."));
    }

    private static void unvanish(ServerPlayer player) {
        // Keep VANISHED set until the delayed reveal actually fires. This is the
        // key to preventing TAB/entity flashes during rapid toggling.
        PENDING_REVEAL.put(player.getUUID(), REVEAL_DELAY_TICKS);
        player.sendSystemMessage(Component.literal("Unvanishing..."));
    }

    private static void hideFrom(ServerPlayer hidden, ServerPlayer viewer) {
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hidden.getUUID())));
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(hidden.getId()));
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
