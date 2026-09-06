package com.chillzone.homes.ui;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side sign text input that works through the normal vanilla sign flow.
 *
 * A temporary real sign is placed in a nearby pair of AIR blocks (with a
 * temporary barrier underneath it for support), then ServerPlayer#openTextEdit
 * opens the vanilla editor. Because the sign truly exists server-side, both
 * Java and Geyser/Floodgate can follow the normal sign-edit protocol.
 *
 * The temporary blocks are removed as soon as the player submits/cancels.
 */
public final class SignInputManager {
    private static final Map<UUID, PendingInput> PENDING = new HashMap<>();
    private static final List<ScheduledOpen> OPEN_QUEUE = new ArrayList<>();
    private static boolean initialized;

    private SignInputManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<ScheduledOpen> it = OPEN_QUEUE.iterator();
            while (it.hasNext()) {
                ScheduledOpen scheduled = it.next();
                scheduled.ticks--;
                if (scheduled.ticks <= 0) {
                    it.remove();
                    ServerPlayer player = server.getPlayerList().getPlayer(scheduled.playerId);
                    if (player != null && player.connection != null) {
                        openNow(player, scheduled.prompt, scheduled.callback);
                    }
                }
            }
        });
    }

    public static void open(ServerPlayer player, String prompt, Consumer<String> callback) {
        // Close the homes inventory first, then wait a few ticks before opening
        // the sign editor. This avoids the old container close dismissing the
        // next screen on Java or through Geyser.
        player.closeContainer();
        cancelPending(player);
        OPEN_QUEUE.removeIf(s -> s.playerId.equals(player.getUUID()));
        OPEN_QUEUE.add(new ScheduledOpen(player.getUUID(), prompt, callback, 4));
    }

    private static void openNow(ServerPlayer player, String prompt, Consumer<String> callback) {
        ServerLevel level = (ServerLevel) player.level();
        SignLocation location = findSafeLocation(player, level);

        if (location == null) {
            callback.accept("");
            return;
        }

        BlockPos supportPos = location.supportPos;
        BlockPos signPos = location.signPos;

        // A standing sign needs support. Both selected positions were verified
        // as AIR, so this does not overwrite player builds.
        level.setBlockAndUpdate(supportPos, Blocks.BARRIER.defaultBlockState());
        level.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());

        BlockEntity be = level.getBlockEntity(signPos);
        if (!(be instanceof SignBlockEntity sign)) {
            cleanup(level, signPos, supportPos);
            callback.accept("");
            return;
        }

        String heading = (prompt == null || prompt.isBlank()) ? "Type answer here" : prompt;
        Component[] lines = new Component[] {
            Component.literal(fitHeading(heading)),
            Component.literal("      ↓      "),
            Component.empty(),
            Component.empty()
        };

        sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), true);
        sign.setAllowedPlayerEditor(player.getUUID());
        sign.setChanged();

        // setChanged() only marks the block entity dirty for saving. Explicitly
        // send a block update so the client (and Geyser) receives the prompt
        // text before the editor opens instead of seeing a blank sign.
        BlockState currentState = level.getBlockState(signPos);
        level.sendBlockUpdated(signPos, currentState, currentState, 3);

        PENDING.put(player.getUUID(), new PendingInput(signPos, supportPos, callback));

        // Use Minecraft's normal sign-editor path rather than manually sending
        // an OpenSignEditor packet. Vanilla re-syncs the real SignBlockEntity
        // and opens the editor in the protocol shape Geyser expects.
        player.openTextEdit(sign, true);
    }

    private static SignLocation findSafeLocation(ServerPlayer player, ServerLevel level) {
        BlockPos base = player.blockPosition();

        // Keep the sign within vanilla's nearby-editor range. Prefer directly
        // above the player so the temporary support cannot obstruct movement.
        for (int dy = 3; dy <= 6; dy++) {
            for (int radius = 0; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                        BlockPos support = base.offset(dx, dy, dz);
                        BlockPos sign = support.above();
                        if (level.getBlockState(support).isAir() && level.getBlockState(sign).isAir()) {
                            return new SignLocation(sign, support);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String fitHeading(String heading) {
        String h = heading.strip();
        if (h.equalsIgnoreCase("Name this home")
            || h.equalsIgnoreCase("Rename home")
            || h.equalsIgnoreCase("Search home icons")) {
            return "Type answer here";
        }
        return h.length() > 24 ? h.substring(0, 24) : h;
    }

    /** Called from ServerGamePacketListenerImplMixin. Returns true when consumed. */
    public static boolean handleUpdate(ServerPlayer player, ServerboundSignUpdatePacket packet) {
        PendingInput pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.signPos.equals(packet.getPos())) return false;

        PENDING.remove(player.getUUID());
        cleanup((ServerLevel) player.level(), pending.signPos, pending.supportPos);

        String[] lines = packet.getLines();
        String answer = extractAnswer(lines);
        if (answer.length() > 32) answer = answer.substring(0, 32);

        pending.callback.accept(answer);
        return true;
    }


    /**
     * Accept text typed on either blank input row, and also tolerate clients
     * (notably protocol translators) that move the cursor or omit the prompt.
     * Instruction text is ignored, so Create/Rename/Search all share the same
     * reliable input path.
     */
    private static String extractAnswer(String[] lines) {
        if (lines == null || lines.length == 0) return "";

        // Prefer the intended lower input rows first.
        int[] preferred = new int[] {3, 2, 0, 1};
        for (int index : preferred) {
            if (index >= lines.length || lines[index] == null) continue;
            String value = lines[index].strip();
            if (value.isBlank()) continue;
            if (isInstruction(value)) continue;
            return value;
        }
        return "";
    }

    private static boolean isInstruction(String value) {
        String normalized = value.strip()
            .replace("↓", "")
            .replace("\u2193", "")
            .strip();
        if (normalized.isBlank()) return true;
        return normalized.equalsIgnoreCase("Type answer here")
            || normalized.equalsIgnoreCase("Name this home")
            || normalized.equalsIgnoreCase("Rename home")
            || normalized.equalsIgnoreCase("Search home icons");
    }

    private static void cancelPending(ServerPlayer player) {
        PendingInput old = PENDING.remove(player.getUUID());
        if (old != null) {
            cleanup((ServerLevel) player.level(), old.signPos, old.supportPos);
        }
    }

    private static void cleanup(ServerLevel level, BlockPos signPos, BlockPos supportPos) {
        BlockState signState = level.getBlockState(signPos);
        if (signState.is(Blocks.OAK_SIGN)) {
            level.setBlockAndUpdate(signPos, Blocks.AIR.defaultBlockState());
        }
        BlockState supportState = level.getBlockState(supportPos);
        if (supportState.is(Blocks.BARRIER)) {
            level.setBlockAndUpdate(supportPos, Blocks.AIR.defaultBlockState());
        }
    }

    private record PendingInput(BlockPos signPos, BlockPos supportPos, Consumer<String> callback) {}
    private record SignLocation(BlockPos signPos, BlockPos supportPos) {}

    private static final class ScheduledOpen {
        final UUID playerId;
        final String prompt;
        final Consumer<String> callback;
        int ticks;

        ScheduledOpen(UUID playerId, String prompt, Consumer<String> callback, int ticks) {
            this.playerId = playerId;
            this.prompt = prompt;
            this.callback = callback;
            this.ticks = ticks;
        }
    }
}
