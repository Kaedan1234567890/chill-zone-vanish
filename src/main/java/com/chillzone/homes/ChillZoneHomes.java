package com.chillzone.homes;

import com.chillzone.homes.ui.SignInputManager;
import com.chillzone.homes.ui.BedrockInputManager;

import com.chillzone.homes.ui.HomeListMenu;
import com.chillzone.homes.ui.BalanceMenu;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public final class ChillZoneHomes implements ModInitializer {
    public static final String MOD_ID = "chillzonehomes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static HomeStore store;
    private static Config config;
    private static ShardStore shards;
    private static long ticks = 0;

    public static HomeStore store() { return store; }
    public static Config config() { return config; }
    public static ShardStore shards() { return shards; }

    @Override public void onInitialize() {
        SignInputManager.init();
        BedrockInputManager.init();
        config = Config.load();
        shards = ShardStore.load();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> store = HomeStore.load(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (store != null) store.save();
            if (shards != null) shards.save();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                shards.rememberPlayer(handler.player.getUUID(), handler.player.getScoreboardName());
                ShardSidebar.update(handler.player, shards.shards(handler.player.getUUID()));
            }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            long raw = handler.player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            shards.rememberPlayTime(handler.player.getUUID(), raw);
            shards.save();
            ShardSidebar.forget(handler.player.getUUID());
        });

        // Refresh the sidebar once per minute. Award one Shard every full five minutes online, including AFK time.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks % 1200L != 0L) return;

            boolean awardShard = ticks % 6000L == 0L;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (awardShard) shards.addShard(player.getUUID());
                ShardSidebar.update(player, shards.shards(player.getUUID()));
            }
            shards.save();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("home")
                .requires(LuckPermsPermissions::canUseHome)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HomeListMenu.open(player);
                    return 1;
                })
                .then(Commands.argument("homeName", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        List<Home> homes = store().getHomes(player.getUUID()).stream()
                            .sorted(Comparator.comparing(Home::name, String.CASE_INSENSITIVE_ORDER))
                            .toList();
                        String remaining = builder.getRemainingLowerCase();
                        for (Home home : homes) {
                            if (home.name().toLowerCase().startsWith(remaining)) builder.suggest(home.name());
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String requested = StringArgumentType.getString(ctx, "homeName").strip();
                        Home home = store().findHomeByName(player.getUUID(), requested);
                        if (home == null) {
                            player.sendSystemMessage(Component.literal("Home not found: " + requested).withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        return HomeTeleport.teleport(player, home) ? 1 : 0;
                    }))
            );


            dispatcher.register(Commands.literal("shards")
                .requires(LuckPermsPermissions::canManageShards)
                .then(Commands.literal("give")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                int balance = shards().addShards(target.getUUID(), amount);
                                ShardSidebar.update(target, balance);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Gave " + amount + " Shards to " + target.getScoreboardName() + ". New balance: " + balance
                                ).withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }))))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                int balance = shards().setShards(target.getUUID(), amount);
                                ShardSidebar.update(target, balance);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Set " + target.getScoreboardName() + "'s Shards to " + balance + "."
                                ).withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }))))
                .then(Commands.literal("take")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                int balance = shards().takeShards(target.getUUID(), amount);
                                ShardSidebar.update(target, balance);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Took " + amount + " Shards from " + target.getScoreboardName() + ". New balance: " + balance
                                ).withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }))))
                .then(Commands.literal("balance")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            int balance = shards().shards(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                target.getScoreboardName() + " has " + balance + " Shards."
                            ).withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        })))
            );

            // Admin play-time controls. Uses the same admin permission as /shards.
            // The amount is followed by a unit: minutes, hours, or days.
            dispatcher.register(Commands.literal("playtime")
                .requires(LuckPermsPermissions::canManageShards)
                .then(Commands.literal("give")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .then(Commands.argument("unit", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("minutes");
                                    builder.suggest("hours");
                                    builder.suggest("days");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    String unit = StringArgumentType.getString(ctx, "unit");
                                    long ticksToAdd = playTimeAmountToTicks(amount, unit);
                                    if (ticksToAdd < 0L) {
                                        ctx.getSource().sendFailure(Component.literal("Use minutes, hours, or days.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    long raw = target.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
                                    long value = shards().addPlayTicks(target.getUUID(), raw, ticksToAdd);
                                    ShardSidebar.update(target, shards().shards(target.getUUID()));
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Added " + amount + " " + unit + " to " + target.getScoreboardName() + "'s play time. New time: " + ShardSidebar.formatPlayTime(value)
                                    ).withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .then(Commands.argument("unit", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("minutes");
                                    builder.suggest("hours");
                                    builder.suggest("days");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    String unit = StringArgumentType.getString(ctx, "unit");
                                    long desiredTicks = playTimeAmountToTicks(amount, unit);
                                    if (desiredTicks < 0L) {
                                        ctx.getSource().sendFailure(Component.literal("Use minutes, hours, or days.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    long raw = target.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
                                    long value = shards().setPlayTicks(target.getUUID(), raw, desiredTicks);
                                    ShardSidebar.update(target, shards().shards(target.getUUID()));
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Set " + target.getScoreboardName() + "'s play time to " + ShardSidebar.formatPlayTime(value) + "."
                                    ).withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))))
                .then(Commands.literal("take")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .then(Commands.argument("unit", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("minutes");
                                    builder.suggest("hours");
                                    builder.suggest("days");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    String unit = StringArgumentType.getString(ctx, "unit");
                                    long ticksToTake = playTimeAmountToTicks(amount, unit);
                                    if (ticksToTake < 0L) {
                                        ctx.getSource().sendFailure(Component.literal("Use minutes, hours, or days.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    long raw = target.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
                                    long value = shards().takePlayTicks(target.getUUID(), raw, ticksToTake);
                                    ShardSidebar.update(target, shards().shards(target.getUUID()));
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Removed " + amount + " " + unit + " from " + target.getScoreboardName() + "'s play time. New time: " + ShardSidebar.formatPlayTime(value)
                                    ).withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))))
                .then(Commands.literal("balance")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            long raw = target.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
                            long value = shards().rememberPlayTime(target.getUUID(), raw);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                target.getScoreboardName() + " has " + ShardSidebar.formatPlayTime(value) + " of play time."
                            ).withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        })))
            );

            // Player-to-player Shard payments. /pay is a short alias of /shardpay.
            var shardPayCommand = Commands.literal("shardpay")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                            shards().rememberPlayer(sender.getUUID(), sender.getScoreboardName());
                            shards().rememberPlayer(target.getUUID(), target.getScoreboardName());

                            if (sender.getUUID().equals(target.getUUID())) {
                                sender.sendSystemMessage(Component.literal("You cannot pay Shards to yourself.").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            int before = shards().shards(sender.getUUID());
                            if (before < amount) {
                                sender.sendSystemMessage(Component.literal(
                                    "You do not have enough Shards. Balance: " + before
                                ).withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            if (!shards().transferShards(sender.getUUID(), target.getUUID(), amount)) {
                                sender.sendSystemMessage(Component.literal("Shard payment failed.").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            int senderBalance = shards().shards(sender.getUUID());
                            int targetBalance = shards().shards(target.getUUID());
                            ShardSidebar.update(sender, senderBalance);
                            ShardSidebar.update(target, targetBalance);

                            sender.sendSystemMessage(Component.literal(
                                "Paid " + amount + " Shards to " + target.getScoreboardName() + ". Balance: " + senderBalance
                            ).withStyle(ChatFormatting.GREEN));
                            target.sendSystemMessage(Component.literal(
                                "You received " + amount + " Shards from " + sender.getScoreboardName() + ". Balance: " + targetBalance
                            ).withStyle(ChatFormatting.AQUA));
                            return 1;
                        })));
            dispatcher.register(shardPayCommand);

            dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                            shards().rememberPlayer(sender.getUUID(), sender.getScoreboardName());
                            shards().rememberPlayer(target.getUUID(), target.getScoreboardName());

                            if (sender.getUUID().equals(target.getUUID())) {
                                sender.sendSystemMessage(Component.literal("You cannot pay Shards to yourself.").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            int before = shards().shards(sender.getUUID());
                            if (before < amount) {
                                sender.sendSystemMessage(Component.literal(
                                    "You do not have enough Shards. Balance: " + before
                                ).withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            if (!shards().transferShards(sender.getUUID(), target.getUUID(), amount)) {
                                sender.sendSystemMessage(Component.literal("Shard payment failed.").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            int senderBalance = shards().shards(sender.getUUID());
                            int targetBalance = shards().shards(target.getUUID());
                            ShardSidebar.update(sender, senderBalance);
                            ShardSidebar.update(target, targetBalance);

                            sender.sendSystemMessage(Component.literal(
                                "Paid " + amount + " Shards to " + target.getScoreboardName() + ". Balance: " + senderBalance
                            ).withStyle(ChatFormatting.GREEN));
                            target.sendSystemMessage(Component.literal(
                                "You received " + amount + " Shards from " + sender.getScoreboardName() + ". Balance: " + targetBalance
                            ).withStyle(ChatFormatting.AQUA));
                            return 1;
                        }))));

            // /bal privately reports your own Shard balance. /bal <name> reports another player's balance.
            // Online names are suggested automatically, while typed offline names are resolved from saved shard data.
            dispatcher.register(Commands.literal("bal")
                .executes(ctx -> {
                    ServerPlayer viewer = ctx.getSource().getPlayerOrException();
                    shards().rememberPlayer(viewer.getUUID(), viewer.getScoreboardName());
                    int balance = shards().shards(viewer.getUUID());
                    viewer.sendSystemMessage(Component.literal(
                        "You have " + balance + " Shards."
                    ).withStyle(ChatFormatting.AQUA));
                    return 1;
                })
                .then(Commands.argument("playerName", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        String remaining = builder.getRemainingLowerCase();
                        for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                            String name = online.getScoreboardName();
                            if (name.toLowerCase().startsWith(remaining)) builder.suggest(name);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        String requested = StringArgumentType.getString(ctx, "playerName").strip();

                        // Prefer an online exact match so brand-new players work immediately.
                        ServerPlayer onlineMatch = null;
                        for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                            if (online.getScoreboardName().equalsIgnoreCase(requested)) {
                                onlineMatch = online;
                                break;
                            }
                        }

                        if (onlineMatch != null) {
                            shards().rememberPlayer(onlineMatch.getUUID(), onlineMatch.getScoreboardName());
                            int balance = shards().shards(onlineMatch.getUUID());
                            String name = onlineMatch.getScoreboardName();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                name + " has " + balance + " Shards."
                            ).withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        }

                        ShardStore.BalanceEntry saved = shards().findByName(requested);
                        if (saved == null) {
                            ctx.getSource().sendFailure(Component.literal(
                                "No saved Shard balance was found for " + requested + "."
                            ).withStyle(ChatFormatting.RED));
                            return 0;
                        }

                        ctx.getSource().sendSuccess(() -> Component.literal(
                            saved.name() + " has " + saved.shards() + " Shards."
                        ).withStyle(ChatFormatting.AQUA), false);
                        return 1;
                    }))
            );

            // /baltop opens the paginated Shard leaderboard GUI.
            dispatcher.register(Commands.literal("baltop")
                .executes(ctx -> {
                    ServerPlayer viewer = ctx.getSource().getPlayerOrException();
                    shards().rememberPlayer(viewer.getUUID(), viewer.getScoreboardName());
                    BalanceMenu.open(viewer);
                    return 1;
                })
            );

            dispatcher.register(Commands.literal("homes")
                .requires(LuckPermsPermissions::canManageLimits)
                .then(Commands.literal("limit")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 28))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                String key = config().luckPermsMetaKey;
                                String command = "lp user " + target.getScoreboardName() + " meta set " + key + " " + amount;
                                ctx.getSource().getServer().getCommands().performPrefixedCommand(
                                    ctx.getSource().getServer().createCommandSourceStack(), command
                                );
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Set " + target.getScoreboardName() + "'s home limit to " + amount + "."
                                ).withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }))
                        .then(Commands.literal("reset")
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String key = config().luckPermsMetaKey;
                                String command = "lp user " + target.getScoreboardName() + " meta unset " + key;
                                ctx.getSource().getServer().getCommands().performPrefixedCommand(
                                    ctx.getSource().getServer().createCommandSourceStack(), command
                                );
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Reset " + target.getScoreboardName() + "'s home limit to the default."
                                ).withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }))))
            );
        });

        LOGGER.info("Chill Zone Homes initialized — /home is ready.");
    }
    private static long playTimeAmountToTicks(int amount, String unit) {
        long multiplier;
        if (unit == null) return -1L;
        switch (unit.toLowerCase()) {
            case "minute", "minutes", "m" -> multiplier = 1200L;
            case "hour", "hours", "h" -> multiplier = 72000L;
            case "day", "days", "d" -> multiplier = 1728000L;
            default -> { return -1L; }
        }
        try {
            return Math.multiplyExact((long) amount, multiplier);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

}
