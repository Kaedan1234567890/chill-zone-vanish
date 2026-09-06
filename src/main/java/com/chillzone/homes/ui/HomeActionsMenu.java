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
import net.minecraft.world.item.Items;

public class HomeActionsMenu extends ChestMenu {
    private static final int ROWS = 3;
    private static final int TELEPORT = 10, ICON = 12, RENAME = 14, DELETE = 16, BACK = 22;
    private final ServerPlayer player;
    private final int index;

    private HomeActionsMenu(int id, Inventory inv, ServerPlayer p, int index) {
        super(MenuType.GENERIC_9x3, id, inv, new SimpleContainer(ROWS * 9), ROWS);
        this.player = p;
        this.index = index;
        build();
    }

    public static void open(ServerPlayer p, int index) {
        Home h = ChillZoneHomes.store().getHome(p.getUUID(), index);
        if (h == null) { HomeListMenu.open(p); return; }
        p.openMenu(new SimpleMenuProvider(
            (id, inv, x) -> new HomeActionsMenu(id, inv, p, index),
            Component.literal(h.name())
        ));
    }

    private void build() {
        ItemStack filler = Ui.button(Ui.item("gray_stained_glass_pane"), Component.empty());
        for (int i = 0; i < 27; i++) getContainer().setItem(i, filler.copy());
        getContainer().setItem(TELEPORT, Ui.button(Items.ENDER_PEARL,
            Ui.name("Teleport", ChatFormatting.AQUA, ChatFormatting.BOLD), Ui.lore("Go to this home.")));
        getContainer().setItem(ICON, Ui.button(Items.ITEM_FRAME,
            Ui.name("Change Icon", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), Ui.lore("Choose a block for this home's icon.")));
        getContainer().setItem(RENAME, Ui.button(Items.NAME_TAG,
            Ui.name("Rename", ChatFormatting.YELLOW, ChatFormatting.BOLD), Ui.lore("Rename this home for free.")));
        getContainer().setItem(DELETE, Ui.button(Items.BARRIER,
            Ui.name("Delete", ChatFormatting.RED, ChatFormatting.BOLD), Ui.lore("Permanently remove this home.")));
        getContainer().setItem(BACK, Ui.button(Items.ARROW, Ui.name("Back", ChatFormatting.WHITE)));
    }

    @Override public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        Home h = ChillZoneHomes.store().getHome(player.getUUID(), index);
        if (h == null) { HomeListMenu.open(player); return; }
        switch (slotId) {
            case TELEPORT -> HomeTeleport.teleport(player, h);
            case ICON -> IconPickerMenu.open(player, index, 0, "");
            case RENAME -> NameInputMenu.open(player, "", "Type answer here", name -> {
                if (name != null && !name.isBlank()) {
                    ChillZoneHomes.store().setHome(player.getUUID(), index, h.withName(name));
                } else {
                    player.sendSystemMessage(Component.literal("Rename cancelled.").withStyle(ChatFormatting.YELLOW));
                }
                HomeActionsMenu.open(player, index);
            });
            case DELETE -> {
                ChillZoneHomes.store().deleteHome(player.getUUID(), index);
                player.sendSystemMessage(Component.literal("Home deleted.").withStyle(ChatFormatting.RED));
                HomeListMenu.open(player);
            }
            case BACK -> HomeListMenu.open(player);
        }
    }

    @Override public ItemStack quickMoveStack(Player c, int s) { return ItemStack.EMPTY; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) { return false; }
    @Override public boolean stillValid(Player c) { return true; }
}
