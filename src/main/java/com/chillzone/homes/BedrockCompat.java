package com.chillzone.homes;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional Floodgate bridge. Chill Zone Homes does not require Floodgate at
 * compile time, but when Floodgate is installed this identifies Bedrock
 * players by UUID so the UI can use the simpler Bedrock-safe home flow.
 */
public final class BedrockCompat {
    private static boolean initialized;
    private static Object floodgateApi;
    private static Method isFloodgatePlayer;

    private BedrockCompat() {}

    public static boolean isBedrock(ServerPlayer player) {
        if (player == null) return false;
        initialize();
        if (floodgateApi == null || isFloodgatePlayer == null) return false;
        try {
            Object result = isFloodgatePlayer.invoke(floodgateApi, player.getUUID());
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ChillZoneHomes.LOGGER.debug("Floodgate player check failed for {}", player.getUUID(), e);
            return false;
        }
    }

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            floodgateApi = getInstance.invoke(null);
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            ChillZoneHomes.LOGGER.info("Floodgate detected — Bedrock-safe home UI enabled.");
        } catch (ReflectiveOperationException | LinkageError e) {
            floodgateApi = null;
            isFloodgatePlayer = null;
            ChillZoneHomes.LOGGER.info("Floodgate not detected — all players use the full Java home UI.");
        }
    }
}
