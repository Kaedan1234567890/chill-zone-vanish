package com.chillzone.homes.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/**
 * Cross-play text entry.
 *
 * Java uses the vanilla anvil rename field, which is reliable for every Java
 * player regardless of OP status, spawn protection, or nearby world blocks.
 * Bedrock/Floodgate uses a native Cumulus text form.
 */
public class NameInputMenu extends AnvilMenu {
    private final Consumer<String> onConfirm;
    private String currentName;
    private boolean confirmed;

    private NameInputMenu(int syncId, Inventory inv, String initial, Consumer<String> callback) {
        super(syncId, inv, ContainerLevelAccess.NULL);
        this.currentName = initial == null ? "" : initial;
        this.onConfirm = callback;
        ItemStack input = new ItemStack(Items.PAPER);
        input.set(DataComponents.CUSTOM_NAME, Component.literal(this.currentName));
        this.inputSlots.setItem(0, input);
        createResult();
    }

    public static void open(ServerPlayer player, String initial, Consumer<String> callback) {
        open(player, initial, "Name this home", callback);
    }

    public static void open(ServerPlayer player, String initial, String title, Consumer<String> callback) {
        if (BedrockInputManager.isBedrock(player)) {
            BedrockInputManager.open(player, title, initial, callback);
            return;
        }

        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new NameInputMenu(id, inv, initial, callback),
            Component.literal(title == null || title.isBlank() ? "Chill Zone Homes" : title)
        ));
    }

    @Override public boolean setItemName(String name) {
        String cleaned = name == null ? "" : name.strip();
        currentName = cleaned.substring(0, Math.min(32, cleaned.length()));
        createResult();
        return true;
    }

    @Override public void createResult() {
        ItemStack result = new ItemStack(Items.NAME_TAG);
        String shown = currentName.isBlank() ? "Save" : "Save \"" + currentName + "\"";
        result.set(DataComponents.ITEM_NAME, Component.literal(shown).withStyle(ChatFormatting.GREEN));
        this.resultSlots.setItem(0, result);
        broadcastChanges();
    }

    @Override protected boolean mayPickup(Player clicker, boolean hasStack) { return true; }
    @Override public int getCost() { return 0; }

    @Override protected void onTake(Player clicker, ItemStack stack) {
        setCarried(ItemStack.EMPTY);
        if (!confirmed) {
            confirmed = true;
            onConfirm.accept(currentName.strip());
        }
    }

    @Override public boolean stillValid(Player clicker) { return true; }
    @Override public void removed(Player clicker) { }
}
