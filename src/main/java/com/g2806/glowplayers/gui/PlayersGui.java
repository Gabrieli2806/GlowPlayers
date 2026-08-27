package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.GlowPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Paginated list of the online players. */
public class PlayersGui extends SimpleGui {
	private static final int PAGE_SIZE = 45;
	private static final int SLOT_PREVIOUS = 45;
	private static final int SLOT_BACK = 49;
	private static final int SLOT_NEXT = 53;

	private final int page;
	private List<ServerPlayer> shown = List.of();

	public PlayersGui(ServerPlayer player, int page) {
		super(player, 6);
		this.page = page;
	}

	@Override
	public Component getDisplayName() {
		return GuiItems.label("GlowPlayers · Players", ChatFormatting.DARK_AQUA);
	}

	@Override
	protected void draw() {
		GlowConfig config = GlowPlayers.configs().get();
		clearSlots();

		MinecraftServer server = player.level().getServer();
		List<ServerPlayer> online = server == null
			? List.of()
			: new ArrayList<>(server.getPlayerList().getPlayers());
		online = new ArrayList<>(online);
		online.sort(Comparator.comparing(target -> target.getScoreboardName().toLowerCase()));

		int from = Math.min(page * PAGE_SIZE, online.size());
		int to = Math.min(from + PAGE_SIZE, online.size());
		shown = online.subList(from, to);

		for (int i = 0; i < shown.size(); i++) {
			set(i, head(shown.get(i), config));
		}

		if (page > 0) {
			set(SLOT_PREVIOUS, GuiItems.of(Items.ARROW,
				GuiItems.label("Previous page", ChatFormatting.YELLOW)));
		}
		if (to < online.size()) {
			set(SLOT_NEXT, GuiItems.of(Items.ARROW,
				GuiItems.label("Next page", ChatFormatting.YELLOW)));
		}
		set(SLOT_BACK, GuiItems.back());
		fillEmpty(GuiItems.FILLER);
	}

	private ItemStack head(ServerPlayer target, GlowConfig config) {
		TeamColor color = GlowPlayers.manager().colorOf(target.getUUID());
		boolean excluded = config.excludedPlayers.contains(target.getStringUUID());
		boolean manual = config.playerColors.containsKey(target.getStringUUID());

		List<Component> lore = new ArrayList<>();
		lore.add(Component.empty()
			.append(GuiItems.label("Color: ", ChatFormatting.GRAY))
			.append(color == null
				? GuiItems.label("none", ChatFormatting.DARK_GRAY)
				: GuiItems.label(GlowPalette.displayName(color), color)));
		lore.add(Component.empty()
			.append(GuiItems.label("Source: ", ChatFormatting.GRAY))
			.append(GuiItems.label(manual ? "manual" : "automatic", ChatFormatting.WHITE)));
		if (excluded) {
			lore.add(GuiItems.label("Excluded from the mod", ChatFormatting.RED));
		}
		lore.add(Component.empty());
		lore.add(GuiItems.label("Click to edit", ChatFormatting.DARK_GRAY));

		ItemStack stack = GuiItems.of(Items.PLAYER_HEAD,
			color == null
				? GuiItems.label(target.getScoreboardName(), ChatFormatting.WHITE)
				: GuiItems.label(target.getScoreboardName(), color), lore);
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(target.getGameProfile()));
		return stack;
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
		if (slot == SLOT_NEXT) {
			new PlayersGui(player, page + 1).open();
			return;
		}
		if (slot < shown.size()) {
			new PlayerGui(player, shown.get(slot).getUUID(), page).open();
		}
	}
}
