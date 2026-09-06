package com.chillzone.homes.ui;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Native Bedrock text input using Floodgate/Cumulus forms. */
public final class BedrockInputManager {
    private static final List<PendingForm> QUEUE = new ArrayList<>();
    private static boolean initialized;

    private BedrockInputManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<PendingForm> it = QUEUE.iterator();
            while (it.hasNext()) {
                PendingForm pending = it.next();
                pending.ticks--;
                if (pending.ticks > 0) continue;
                it.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
                if (player != null && player.connection != null) {
                    sendNow(player, pending.title, pending.initial, pending.callback, pending.attempt);
                }
            }
        });
    }

    public static boolean isBedrock(ServerPlayer player) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api != null && api.isFloodgatePlayer(player.getUUID());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void open(ServerPlayer player, String title, String initial, Consumer<String> callback) {
        player.closeContainer();
        QUEUE.removeIf(p -> p.playerId.equals(player.getUUID()));
        // Give Geyser time to finish closing the chest/menu before a native form
        // is sent. This prevents the previous menu-close packet from instantly
        // dismissing the text form for some Bedrock players.
        QUEUE.add(new PendingForm(player.getUUID(), title, initial, callback, 5, 1));
    }

    private static void sendNow(ServerPlayer player, String title, String initial, Consumer<String> callback, int attempt) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null || !api.isFloodgatePlayer(player.getUUID())) {
                callback.accept("");
                return;
            }

            String formTitle = normalizeTitle(title);
            String defaultText = initial == null ? "" : initial;

            CustomForm.Builder form = CustomForm.builder()
                .title(formTitle)
                .label("Type your answer below")
                .input("Answer", "Type here...", defaultText)
                .validResultHandler(response -> {
                    Object value = response.next();
                    String answer = value == null ? "" : value.toString().strip();
                    if (answer.length() > 32) answer = answer.substring(0, 32);
                    String finalAnswer = answer;
                    player.level().getServer().execute(() -> callback.accept(finalAnswer));
                })
                .closedOrInvalidResultHandler(() ->
                    player.level().getServer().execute(() -> callback.accept(""))
                );

            boolean sent = api.sendForm(player.getUUID(), form);
            if (!sent) {
                if (attempt < 2) {
                    QUEUE.add(new PendingForm(player.getUUID(), title, initial, callback, 6, attempt + 1));
                } else {
                    callback.accept("");
                }
            }
        } catch (Throwable throwable) {
            if (attempt < 2) {
                QUEUE.add(new PendingForm(player.getUUID(), title, initial, callback, 6, attempt + 1));
            } else {
                callback.accept("");
            }
        }
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()
            || title.equalsIgnoreCase("Name this home")
            || title.equalsIgnoreCase("Rename home")
            || title.equalsIgnoreCase("Search home icons")
            || title.equalsIgnoreCase("Type answer here")) {
            return "Chill Zone Homes";
        }
        return title;
    }

    private static final class PendingForm {
        final UUID playerId;
        final String title;
        final String initial;
        final Consumer<String> callback;
        int ticks;
        final int attempt;

        PendingForm(UUID playerId, String title, String initial, Consumer<String> callback, int ticks, int attempt) {
            this.playerId = playerId;
            this.title = title;
            this.initial = initial;
            this.callback = callback;
            this.ticks = ticks;
            this.attempt = attempt;
        }
    }
}
