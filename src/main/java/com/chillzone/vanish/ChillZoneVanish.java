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
    private static int ticks = 0;

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

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            VANISHED.remove(handler.getPlayer().getUUID())
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks < 20) return;
            ticks = 0;
            for (UUID id : VANISHED) {
                ServerPlayer hidden = server.getPlayerList().getPlayer(id);
                if (hidden == null) continue;
                for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                    if (viewer != hidden) hideFrom(hidden, viewer);
                }
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
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != player) hideFrom(player, viewer);
        }

        broadcastFake(server, player, false);
        player.sendSystemMessage(Component.literal("You are now vanished."));
    }

    private static void unvanish(ServerPlayer player) {
        VANISHED.remove(player.getUUID());
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != player) showTo(player, viewer);
        }

        broadcastFake(server, player, true);
        player.sendSystemMessage(Component.literal("You are now visible."));
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
