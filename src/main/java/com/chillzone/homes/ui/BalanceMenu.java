package com.chillzone.homes.ui;

import com.chillzone.homes.ChillZoneHomes;
import com.chillzone.homes.ShardSidebar;
import com.chillzone.homes.ShardStore;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;

/** Paginated Shard leaderboard opened by /baltop. */
public final class BalanceMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int[] PLAYER_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };
    private static final int PREVIOUS = 45;
    private static final int NEXT = 53;
    private static final int PAGE_INFO = 49;

    private final ServerPlayer viewer;
    private final int page;
    private final List<ShardStore.BalanceEntry> ranked;

    private BalanceMenu(int id, Inventory inv, ServerPlayer viewer, int requestedPage) {
        super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(ROWS * 9), ROWS);
        this.viewer = viewer;

        // Refresh names and effective play time for everyone currently online before ranking.
        for (ServerPlayer online : viewer.level().getServer().getPlayerList().getPlayers()) {
            ChillZoneHomes.shards().rememberPlayer(online.getUUID(), online.getScoreboardName());
            long raw = online.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            ChillZoneHomes.shards().rememberPlayTime(online.getUUID(), raw);
        }
        ChillZoneHomes.shards().save();

        this.ranked = ChillZoneHomes.shards().rankedBalances();
        int pageCount = Math.max(1, (ranked.size() + PLAYER_SLOTS.length - 1) / PLAYER_SLOTS.length);
        this.page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        refresh();
    }

    public static void open(ServerPlayer viewer) { open(viewer, 0); }

    public static void open(ServerPlayer viewer, int page) {
        viewer.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new BalanceMenu(id, inv, viewer, page),
            Component.literal("Chill Zone SMP Shard Baltop")
        ));
    }

    private void refresh() {
        ItemStack filler = Ui.button(Ui.item("gray_stained_glass_pane"), Component.empty());
        for (int i = 0; i < ROWS * 9; i++) getContainer().setItem(i, filler.copy());

        int start = page * PLAYER_SLOTS.length;
        for (int i = 0; i < PLAYER_SLOTS.length; i++) {
            int index = start + i;
            if (index >= ranked.size()) {
                getContainer().setItem(PLAYER_SLOTS[i], ItemStack.EMPTY);
                continue;
            }

            ShardStore.BalanceEntry entry = ranked.get(index);
            String name = entry.name();
            if (name == null || name.isBlank()) name = "Unknown Player";

            Component shardLore = Component.literal("Shards: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Integer.toString(entry.shards())).withStyle(ChatFormatting.LIGHT_PURPLE));
            Component timeLore = Component.literal("Time Played: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ShardSidebar.formatPlayTime(entry.playTicks())).withStyle(ChatFormatting.YELLOW));
            Component rankLore = Component.literal("Rank: #" + (index + 1)).withStyle(ChatFormatting.GRAY);

            ItemStack head = Ui.button(Ui.item("player_head"),
                Ui.name(name, ChatFormatting.AQUA, ChatFormatting.BOLD),
                shardLore,
                timeLore,
                rankLore);

            // Online players use their live profile/skin. Offline entries resolve from saved profile data when possible.
            ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(entry.uuid());
            if (online != null) {
                GameProfile profile = online.getGameProfile();
                head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            } else if (name != null && !name.isBlank() && !name.startsWith("Unknown-")) {
                head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
            } else {
                head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(entry.uuid()));
            }

            getContainer().setItem(PLAYER_SLOTS[i], head);
        }

        int pageCount = Math.max(1, (ranked.size() + PLAYER_SLOTS.length - 1) / PLAYER_SLOTS.length);
        getContainer().setItem(PAGE_INFO, Ui.button(Ui.item("book"),
            Ui.name("Page " + (page + 1) + " / " + pageCount, ChatFormatting.YELLOW, ChatFormatting.BOLD),
            Ui.lore(ranked.size() + " player" + (ranked.size() == 1 ? "" : "s") + " on the leaderboard.")));

        if (page > 0) {
            getContainer().setItem(PREVIOUS, Ui.button(Ui.item("arrow"),
                Ui.name("Previous Page", ChatFormatting.GREEN, ChatFormatting.BOLD),
                Ui.lore("Click to go back.")));
        }
        if (page + 1 < pageCount) {
            getContainer().setItem(NEXT, Ui.button(Ui.item("arrow"),
                Ui.name("Next Page", ChatFormatting.GREEN, ChatFormatting.BOLD),
                Ui.lore("Click to continue.")));
        }
    }

    @Override public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        int pageCount = Math.max(1, (ranked.size() + PLAYER_SLOTS.length - 1) / PLAYER_SLOTS.length);
        if (slotId == PREVIOUS && page > 0) {
            open(viewer, page - 1);
            return;
        }
        if (slotId == NEXT && page + 1 < pageCount) {
            open(viewer, page + 1);
        }
    }

    @Override public ItemStack quickMoveStack(Player clicker, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) { return false; }
    @Override public boolean stillValid(Player clicker) { return true; }
}
