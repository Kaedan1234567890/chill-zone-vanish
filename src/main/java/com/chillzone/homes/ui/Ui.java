package com.chillzone.homes.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;
import java.util.Arrays;
import java.util.List;

final class Ui {
    private Ui() {}
    static Item item(String path) { return BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:" + path)); }
    static ItemStack button(ItemLike item, Component name, Component... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_NAME, name);
        if (lore.length > 0) stack.set(DataComponents.LORE, new ItemLore(Arrays.asList(lore), List.of()));
        return stack;
    }
    static Component name(String text, ChatFormatting... styles) { return Component.literal(text).withStyle(styles); }
    static Component lore(String text) { return Component.literal(text).withStyle(ChatFormatting.GRAY); }
}
