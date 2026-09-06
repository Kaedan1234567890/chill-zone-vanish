package com.chillzone.homes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;

/**
 * Lightweight LuckPerms permission bridge without a hard compile/runtime dependency.
 * Console is always allowed. Player checks use LuckPerms cached permission data.
 */
public final class LuckPermsPermissions {
    public static final String HOME_PERMISSION = "chillzonehomes.command.home";
    public static final String LIMIT_PERMISSION = "chillzonehomes.command.limit";
    public static final String SHARDS_ADMIN_PERMISSION = "chillzonehomes.command.shardsadmin";

    private LuckPermsPermissions() {}

    public static boolean canUseHome(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return hasPermission(player, HOME_PERMISSION);
    }

    public static boolean canManageLimits(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true; // console / command blocks / server source
        }
        return hasPermission(player, LIMIT_PERMISSION);
    }

    public static boolean canManageShards(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return hasPermission(player, SHARDS_ADMIN_PERMISSION);
    }

    public static boolean hasPermission(ServerPlayer player, String permission) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object lp = provider.getMethod("get").invoke(null);
            Object userManager = lp.getClass().getMethod("getUserManager").invoke(lp);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                .invoke(userManager, player.getUUID());
            if (user == null) return false;

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Method checkPermission = permissionData.getClass().getMethod("checkPermission", String.class);
            Object tristate = checkPermission.invoke(permissionData, permission);
            Method asBoolean = tristate.getClass().getMethod("asBoolean");
            Object result = asBoolean.invoke(tristate);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            ChillZoneHomes.LOGGER.debug("LuckPerms permission lookup failed for {}", permission, e);
            return false;
        }
    }
}
