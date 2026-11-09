package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.ColorMode;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.GlowPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paginated list of everyone the mod knows about: online players, plus the offline members of
 * the fixed group and the excluded players.
 */
public class PlayersGui extends SimpleGui {
	private static final int FIRST_SLOT = 9;
	private static final int PAGE_SIZE = 36;
	private static final int SLOT_HELP = 4;
	private static final int SLOT_PREVIOUS = 45;
	private static final int SLOT_BACK = 49;
	private static final int SLOT_NEXT = 53;

	/** One row of the list. */
	record Entry(UUID uuid, String name, boolean online) {
	}

	private final int page;
	private List<Entry> shown = List.of();

	public PlayersGui(ServerPlayer player, int page) {
		super(player, 6);
		this.page = page;
	}

	@Override
	public Component getDisplayName() {
		return GuiItems.label("GlowPlayers · Players", ChatFormatting.DARK_AQUA);
	}

	static List<Entry> entries(MinecraftServer server, GlowConfig config) {
		Map<UUID, Entry> entries = new LinkedHashMap<>();
		if (server != null) {
			for (ServerPlayer online : server.getPlayerList().getPlayers()) {
				entries.put(online.getUUID(), new Entry(online.getUUID(), online.getScoreboardName(), true));
			}
		}
		if (config.mode() == ColorMode.FIXED) {
			config.members.forEach((key, member) -> addOffline(entries, key, member.name));
		}
		config.excludedPlayers.forEach((key, name) -> addOffline(entries, key, name));

		List<Entry> list = new ArrayList<>(entries.values());
		list.sort(Comparator.comparing((Entry entry) -> !entry.online())
			.thenComparing(entry -> entry.name().toLowerCase()));
		return list;
	}

	private static void addOffline(Map<UUID, Entry> entries, String key, String name) {
		try {
			UUID uuid = UUID.fromString(key);
			entries.putIfAbsent(uuid, new Entry(uuid, name == null ? "" : name, false));
		} catch (IllegalArgumentException ignored) {
			// A hand-edited key that is not a UUID: nothing to show.
		}
	}

	/** Color the entry has right now, or the one reserved for it in the fixed group. */
	static TeamColor colorOf(Entry entry, GlowConfig config) {
		TeamColor color = GlowPlayers.manager().colorOf(entry.uuid());
		if (color == null && config.mode() == ColorMode.FIXED) {
			GlowConfig.Member member = config.members.get(entry.uuid().toString());
			color = member == null ? null : GlowPalette.byName(member.color);
		}
		return color;
	}

	static String displayName(Entry entry) {
		return entry.name().isEmpty() ? entry.uuid().toString().substring(0, 8) + "…" : entry.name();
	}

	@Override
	protected void draw() {
		GlowConfig config = GlowPlayers.configs().get();
		clearSlots();

		List<Entry> all = entries(server(), config);
		int from = Math.min(page * PAGE_SIZE, all.size());
		int to = Math.min(from + PAGE_SIZE, all.size());
		shown = all.subList(from, to);

		set(SLOT_HELP, GuiItems.of(Items.BOOK,
			GuiItems.label("Players", ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE),
			GuiItems.label(config.mode() == ColorMode.FIXED
				? "Online players first, then offline group members."
				: "Online players, in join order slots.", ChatFormatting.GRAY),
			GuiItems.hint("Click a head to edit that player"),
			Component.empty(),
			GuiItems.field("Page", (page + 1) + " / " + Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE))));

		for (int i = 0; i < shown.size(); i++) {
			set(FIRST_SLOT + i, head(shown.get(i), config));
		}

		if (page > 0) {
			set(SLOT_PREVIOUS, GuiItems.of(Items.ARROW, GuiItems.label("Previous page", ChatFormatting.YELLOW)));
		}
		if (to < all.size()) {
			set(SLOT_NEXT, GuiItems.of(Items.ARROW, GuiItems.label("Next page", ChatFormatting.YELLOW)));
		}
		set(SLOT_BACK, GuiItems.back());
		frame();
	}

	private ItemStack head(Entry entry, GlowConfig config) {
		TeamColor color = colorOf(entry, config);
		boolean excluded = config.excludedPlayers.containsKey(entry.uuid().toString());
		boolean member = config.members.containsKey(entry.uuid().toString());
		String conflict = GlowPlayers.manager().conflictOf(entry.uuid());

		List<Component> lore = new ArrayList<>();
		lore.add(GuiItems.field("Status", entry.online()
			? GuiItems.label("Online", ChatFormatting.GREEN)
			: GuiItems.label("Offline", ChatFormatting.DARK_GRAY)));
		lore.add(GuiItems.field("Color", color == null
			? GuiItems.label("none", ChatFormatting.DARK_GRAY)
			: GuiItems.label(GlowPalette.displayName(color), color)));
		if (config.mode() == ColorMode.FIXED) {
			lore.add(GuiItems.field("In group", member ? GuiItems.label("yes", ChatFormatting.GREEN)
				: GuiItems.label("no", ChatFormatting.RED)));
		}
		if (excluded) {
			lore.add(GuiItems.label("Excluded from the mod", ChatFormatting.RED));
		}
		if (conflict != null) {
			lore.add(GuiItems.label("On foreign team \"" + conflict + "\"", ChatFormatting.YELLOW));
		}
		lore.add(Component.empty());
		lore.add(GuiItems.hint("Click to edit"));

		String name = displayName(entry);
		return GuiItems.head(entry.uuid(), entry.name(),
			color == null || excluded
				? GuiItems.label(name, ChatFormatting.WHITE)
				: GuiItems.label(name, color), lore);
	}

	@Override
	protected void onClick(int slot, ContainerInput input, int button) {
		if (slot == SLOT_BACK) {
			new MainGui(player).open();
			return;
		}
		if (slot == SLOT_PREVIOUS && page > 0) {
			new PlayersGui(player, page - 1).open();
			return;
		}
		if (slot == SLOT_NEXT && shown.size() == PAGE_SIZE) {
			new PlayersGui(player, page + 1).open();
			return;
		}
		int index = slot - FIRST_SLOT;
		if (index >= 0 && index < shown.size()) {
			new PlayerGui(player, shown.get(index), page).open();
		}
	}
}
