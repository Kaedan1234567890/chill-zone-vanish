package com.chillzone.homes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Set;

public final class HomeTeleport {
    private HomeTeleport() {}

    public static boolean teleport(ServerPlayer player, Home home) {
        if (!ChillZoneHomes.config().allowCrossDimensionTeleport
            && !player.level().dimension().identifier().toString().equals(home.dimension())) {
            player.sendSystemMessage(Component.literal("Cross-dimension home teleport is disabled.").withStyle(ChatFormatting.RED));
            return false;
        }

        ServerLevel level = player.level().getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, Identifier.parse(home.dimension()))
        );
        if (level == null) {
            player.sendSystemMessage(Component.literal("That dimension no longer exists.").withStyle(ChatFormatting.RED));
            return false;
        }

        player.closeContainer();
        player.teleportTo(level, home.x(), home.y(), home.z(), Set.of(), home.yaw(), home.pitch(), false);
        level.playSound(null, BlockPos.containing(home.x(), home.y(), home.z()),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        player.sendOverlayMessage(Component.literal("Welcome to " + home.name() + ".").withStyle(ChatFormatting.AQUA));
        return true;
    }
}
