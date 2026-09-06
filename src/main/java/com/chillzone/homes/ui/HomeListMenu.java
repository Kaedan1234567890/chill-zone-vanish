package com.chillzone.homes.ui;

import com.chillzone.homes.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Exact 6x9 Chill Zone homes layout approved for 0.3.0. */
public class HomeListMenu extends ChestMenu {
    private static final int ROWS = 6;
    // Four inner rows, seven positions each: all 28 positions are usable homes.
    private static final int[] HOME_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };
    private static final int BOOK = 49;

    private final ServerPlayer player;

    private HomeListMenu(int id, Inventory inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(ROWS * 9), ROWS);
        this.player = player;
        refresh();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new HomeListMenu(id, inv, player),
            Component.literal("Chill Zone — Homes")
        ));
    }

    /** Kept for compatibility with 0.2.0 call sites. */
    public static void open(ServerPlayer player, int ignoredPage) { open(player); }

    private void refresh() {
        ItemStack filler = Ui.button(Ui.item("gray_stained_glass_pane"), Component.empty());
        for (int i = 0; i < ROWS * 9; i++) getContainer().setItem(i, filler.copy());

        int limit = HomeLimitResolver.resolve(player);
        int max = Math.min(28, ChillZoneHomes.config().maximumVisibleSlots);
        List<Home> homes = ChillZoneHomes.store().getHomes(player.getUUID());

        for (int i = 0; i < HOME_SLOTS.length; i++) {
            ItemStack button;
            if (i >= max) {
                button = lockedButton(i, limit);
            } else {
                Home home = i < homes.size() ? homes.get(i) : null;
                if (i >= limit) {
                    button = lockedButton(i, limit);
                } else if (home == null) {
                    button = Ui.button(Ui.item("red_bed"),
                        Ui.name("New Home", ChatFormatting.GREEN, ChatFormatting.BOLD),
                        Ui.lore("Click to create Home " + (i + 1) + "."),
                        Ui.lore("It will save your current location."));
                } else {
                    button = Ui.button(Ui.item(iconPath(home)),
                        Ui.name(home.name(), ChatFormatting.AQUA, ChatFormatting.BOLD),
                        Ui.lore(String.format(Locale.ROOT, "%.0f, %.0f, %.0f", home.x(), home.y(), home.z())),
                        Ui.lore(prettyDimension(home.dimension())),
                        Ui.lore("Click to manage this home."));
                }
            }
            getContainer().setItem(HOME_SLOTS[i], button);
        }

        getContainer().setItem(BOOK, Ui.button(Ui.item("book"),
            Ui.name("Homes (" + homes.size() + "/" + limit + ")", ChatFormatting.YELLOW, ChatFormatting.BOLD),
            Ui.lore("Your current home allowance.")));
    }

    private ItemStack lockedButton(int index, int limit) {
        int homeNumber = index + 1;
        if (homeNumber >= 4 && homeNumber <= 28 && index == limit) {
            int cost = ShardStore.costForHome(homeNumber);
            int balance = ChillZoneHomes.shards().shards(player.getUUID());
            return Ui.button(Ui.item("barrier"),
                Ui.name("Locked — Home " + homeNumber, ChatFormatting.RED, ChatFormatting.BOLD),
                Ui.lore("Cost: " + cost + " Shards"),
                Ui.lore("Your Shards: " + balance),
                Ui.lore("Click to permanently unlock."));
        }
        return Ui.button(Ui.item("barrier"),
            Ui.name("Locked", ChatFormatting.RED, ChatFormatting.BOLD),
            Ui.lore("Unlock the previous home slot first."),
            Ui.lore("Your limit: " + limit + " homes."));
    }

    private static String iconPath(Home home) {
        String icon = home.icon();
        if (icon == null || icon.isBlank()) return "red_bed";
        return icon.startsWith("minecraft:") ? icon.substring("minecraft:".length()) : icon;
    }

    private static String prettyDimension(String d) {
        String p = d.contains(":") ? d.substring(d.indexOf(':') + 1) : d;
        return switch (p) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "The Nether";
            case "the_end" -> "The End";
            default -> p;
        };
    }

    @Override public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        int index = -1;
        for (int i = 0; i < HOME_SLOTS.length; i++) {
            if (slotId == HOME_SLOTS[i]) { index = i; break; }
        }
        if (index < 0) return;

        int max = Math.min(28, ChillZoneHomes.config().maximumVisibleSlots);
        int limit = HomeLimitResolver.resolve(player);
        if (index >= max) {
            player.sendSystemMessage(Component.literal("That home slot is locked.").withStyle(ChatFormatting.RED));
            return;
        }
        if (index >= limit) {
            int homeNumber = index + 1;
            if (index == limit && homeNumber >= 4 && homeNumber <= 28) {
                int cost = ShardStore.costForHome(homeNumber);
                int balance = ChillZoneHomes.shards().shards(player.getUUID());
                if (balance < cost) {
                    player.sendSystemMessage(Component.literal("You need " + cost + " Shards to unlock Home " + homeNumber + ". You have " + balance + ".").withStyle(ChatFormatting.RED));
                    return;
                }
                if (ChillZoneHomes.shards().purchaseNextHome(player.getUUID(), homeNumber)) {
                    player.sendSystemMessage(Component.literal("Home " + homeNumber + " unlocked for " + cost + " Shards!").withStyle(ChatFormatting.GREEN));
                    ShardSidebar.update(player, ChillZoneHomes.shards().shards(player.getUUID()));
                    refresh();
                }
                return;
            }
            player.sendSystemMessage(Component.literal("Unlock the previous home slot first.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        List<Home> homes = ChillZoneHomes.store().getHomes(player.getUUID());
        if (index < homes.size()) {
            HomeActionsMenu.open(player, index);
            return;
        }
        if (index != homes.size()) {
            player.sendSystemMessage(Component.literal("Create earlier home slots first.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        final int homeIndex = index;
        NameInputMenu.open(player, "", "Type answer here", name -> {
            if (name == null || name.isBlank()) {
                player.sendSystemMessage(Component.literal("Home creation cancelled.").withStyle(ChatFormatting.YELLOW));
                open(player);
                return;
            }
            Home h = new Home(name,
                player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), "red_bed");
            ChillZoneHomes.store().addHome(player.getUUID(), h);
            player.sendSystemMessage(Component.literal("Home \"" + name + "\" set.").withStyle(ChatFormatting.GREEN));
            open(player);
        });
    }

    @Override public ItemStack quickMoveStack(Player clicker, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) { return false; }
    @Override public boolean stillValid(Player clicker) { return true; }
}
