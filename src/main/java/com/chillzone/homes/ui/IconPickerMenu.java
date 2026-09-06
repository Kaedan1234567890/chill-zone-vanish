package com.chillzone.homes.ui;

import com.chillzone.homes.ChillZoneHomes;
import com.chillzone.homes.Home;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 6x9 searchable, paged Minecraft item/block icon picker. */
public class IconPickerMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45; // first five rows
    private static final int BACK = 45;
    private static final int PREVIOUS = 47;
    private static final int SEARCH = 49;
    private static final int CLEAR = 50;
    private static final int NEXT = 53;

    private final ServerPlayer player;
    private final int homeIndex;
    private final int page;
    private final String query;
    private final List<IconChoice> choices;

    private record IconChoice(Item item, String path, String display) {}

    private IconPickerMenu(int id, Inventory inv, ServerPlayer player, int homeIndex, int page, String query) {
        super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(ROWS * 9), ROWS);
        this.player = player;
        this.homeIndex = homeIndex;
        this.query = query == null ? "" : query.strip();
        this.choices = collectChoices(this.query);
        int maxPage = choices.isEmpty() ? 0 : (choices.size() - 1) / PAGE_SIZE;
        this.page = Math.max(0, Math.min(page, maxPage));
        build();
    }

    public static void open(ServerPlayer player, int homeIndex, int page, String query) {
        Home home = ChillZoneHomes.store().getHome(player.getUUID(), homeIndex);
        if (home == null) { HomeListMenu.open(player); return; }
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new IconPickerMenu(id, inv, player, homeIndex, page, query),
            Component.literal("Choose Home Icon")
        ));
    }

    private static List<IconChoice> collectChoices(String query) {
        String needle = normalize(query);
        List<IconChoice> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !"minecraft".equals(id.getNamespace())) continue;
            String path = id.getPath();
            if (path.equals("air")) continue;
            String display = pretty(path);
            if (!needle.isEmpty() && !normalize(path).contains(needle) && !normalize(display).contains(needle)) continue;
            result.add(new IconChoice(item, path, display));
        }
        result.sort(Comparator.comparing(IconChoice::display, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void build() {
        ItemStack filler = Ui.button(Ui.item("gray_stained_glass_pane"), Component.empty());
        for (int i = 0; i < ROWS * 9; i++) getContainer().setItem(i, filler.copy());

        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= choices.size()) break;
            IconChoice choice = choices.get(index);
            getContainer().setItem(slot, Ui.button(choice.item(),
                Ui.name(choice.display(), ChatFormatting.AQUA),
                Ui.lore("Click to use this block as the icon.")));
        }

        getContainer().setItem(BACK, Ui.button(Items.ARROW, Ui.name("Back", ChatFormatting.WHITE)));
        if (page > 0) getContainer().setItem(PREVIOUS,
            Ui.button(Items.SPECTRAL_ARROW, Ui.name("Previous Page", ChatFormatting.YELLOW)));

        String searchLabel = query.isBlank() ? "Search Icons" : "Search: " + query;
        getContainer().setItem(SEARCH, Ui.button(Items.OAK_SIGN,
            Ui.name(searchLabel, ChatFormatting.GREEN, ChatFormatting.BOLD),
            Ui.lore("Click and type an item or block name."),
            Ui.lore("Example: book, stone, cherry, copper")));

        if (!query.isBlank()) {
            getContainer().setItem(CLEAR, Ui.button(Items.BARRIER,
                Ui.name("Clear Search", ChatFormatting.RED), Ui.lore("Show every icon again.")));
        }

        if ((page + 1) * PAGE_SIZE < choices.size()) getContainer().setItem(NEXT,
            Ui.button(Items.SPECTRAL_ARROW, Ui.name("Next Page", ChatFormatting.GREEN)));
    }

    @Override public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        if (slotId == BACK) { HomeActionsMenu.open(player, homeIndex); return; }
        if (slotId == PREVIOUS && page > 0) { open(player, homeIndex, page - 1, query); return; }
        if (slotId == NEXT && (page + 1) * PAGE_SIZE < choices.size()) { open(player, homeIndex, page + 1, query); return; }
        if (slotId == CLEAR && !query.isBlank()) { open(player, homeIndex, 0, ""); return; }
        if (slotId == SEARCH) {
            NameInputMenu.open(player, "", "Type answer here", text -> open(player, homeIndex, 0, text == null ? "" : text));
            return;
        }
        if (slotId < 0 || slotId >= PAGE_SIZE) return;

        int choiceIndex = page * PAGE_SIZE + slotId;
        if (choiceIndex >= choices.size()) return;
        Home home = ChillZoneHomes.store().getHome(player.getUUID(), homeIndex);
        if (home == null) { HomeListMenu.open(player); return; }
        IconChoice choice = choices.get(choiceIndex);
        ChillZoneHomes.store().setHome(player.getUUID(), homeIndex, home.withIcon(choice.path()));
        player.sendSystemMessage(Component.literal("Home icon changed to " + choice.display() + ".")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        HomeActionsMenu.open(player, homeIndex);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace("_", " ").strip();
    }

    private static String pretty(String path) {
        String[] words = path.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    @Override public ItemStack quickMoveStack(Player c, int s) { return ItemStack.EMPTY; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) { return false; }
    @Override public boolean stillValid(Player c) { return true; }
}
