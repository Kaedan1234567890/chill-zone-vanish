package com.chillzone.homes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Per-player packet sidebar: player name title, Shards, then total play time. */
public final class ShardSidebar {
    private static final Scoreboard DUMMY = new Scoreboard();
    private static final Set<UUID> REGISTERED = new HashSet<>();
    private ShardSidebar() {}

    public static void forget(UUID id) { REGISTERED.remove(id); }

    public static void update(ServerPlayer player, int shards) {
        String objectiveName = "czs_" + player.getUUID().toString().replace("-", "").substring(0, 12);
        Objective objective = new Objective(
            DUMMY,
            objectiveName,
            ObjectiveCriteria.DUMMY,
            Component.literal(player.getScoreboardName()),
            ObjectiveCriteria.RenderType.INTEGER,
            false,
            BlankFormat.INSTANCE
        );

        if (REGISTERED.add(player.getUUID())) {
            player.connection.send(new ClientboundSetObjectivePacket(objective, 0));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        }

        Component shardLine = Component.literal("Shards: ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(Integer.toString(shards)).withStyle(ChatFormatting.LIGHT_PURPLE));

        long rawPlayTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        long playTicks = ChillZoneHomes.shards().rememberPlayTime(player.getUUID(), rawPlayTicks);
        Component timeLine = Component.literal("Playtime: ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(formatPlayTime(playTicks)).withStyle(ChatFormatting.YELLOW));

        // Higher score renders above the lower score, so Playtime appears directly under Shards.
        player.connection.send(new ClientboundSetScorePacket(
            "cz_shards",
            objectiveName,
            2,
            Optional.of(shardLine),
            Optional.of(BlankFormat.INSTANCE)
        ));
        player.connection.send(new ClientboundSetScorePacket(
            "cz_time",
            objectiveName,
            1,
            Optional.of(timeLine),
            Optional.of(BlankFormat.INSTANCE)
        ));
    }

    /**
     * Play time is intentionally shown only in minutes, hours, and days.
     * Days keep accumulating forever (for example 30d, 75d) and never convert to months.
     */
    public static String formatPlayTime(long ticks) {
        long totalMinutes = Math.max(0L, ticks / 1200L);
        if (totalMinutes < 60L) {
            return totalMinutes + "m";
        }

        long totalHours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (totalHours < 24L) {
            return minutes == 0L ? totalHours + "h" : totalHours + "h " + minutes + "m";
        }

        long days = totalHours / 24L;
        long hours = totalHours % 24L;
        return hours == 0L ? days + "d" : days + "d " + hours + "h";
    }
}
