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
import java.util.List;
import java.util.UUID;

/** Per-player screen: force a color, hand it back to the automatic mode, or exclude the player. */
public class PlayerGui extends SimpleGui {
	private static final int[] COLOR_SLOTS = {
		19, 20, 21, 22, 23, 24, 25, 26,
		28, 29, 30, 31, 32, 33, 34, 35
	};

	private static final int SLOT_HEAD = 4;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_AUTOMATIC = 48;
	private static final int SLOT_EXCLUDE = 50;
	private static final int SLOT_CLOSE = 53;

	private final UUID targetId;
	private final int returnPage;

	public PlayerGui(ServerPlayer player, UUID targetId, int returnPage) {
		super(player, 6);
		this.targetId = targetId;
		this.returnPage = returnPage;
	}

	@Override
	public Component getDisplayName() {
		ServerPlayer target = target();
		String name = target == null ? "Player" : target.getScoreboardName();
		return GuiItems.label("GlowPlayers · " + name, ChatFormatting.DARK_AQUA);
	}

	private ServerPlayer target() {
		MinecraftServer server = player.level().getServer();
		return server == null ? null : server.getPlayerList().getPlayer(targetId);
	}

	@Override
	protected void draw() {
		GlowConfig config = GlowPlayers.configs().get();
		clearSlots();

		ServerPlayer target = target();
		String key = targetId.toString();
		String manualName = config.playerColors.get(key);
		TeamColor manual = GlowPalette.byName(manualName);
		TeamColor current = GlowPlayers.manager().colorOf(targetId);
		boolean excluded = config.excludedPlayers.contains(key);

		ItemStack head = GuiItems.of(Items.PLAYER_HEAD,
			current == null
				? GuiItems.label(target == null ? key : target.getScoreboardName(), ChatFormatting.WHITE)
				: GuiItems.label(target == null ? key : target.getScoreboardName(), current),
			Component.empty()
				.append(GuiItems.label("Color: ", ChatFormatting.GRAY))
				.append(current == null
					? GuiItems.label("none", ChatFormatting.DARK_GRAY)
					: GuiItems.label(GlowPalette.displayName(current), current)),
			Component.empty()
				.append(GuiItems.label("Source: ", ChatFormatting.GRAY))
				.append(GuiItems.label(manual != null ? "manual" : "automatic", ChatFormatting.WHITE)));
		if (target != null) {
			head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(target.getGameProfile()));
		}
		set(SLOT_HEAD, head);

		for (int i = 0; i < GlowPalette.ALL_COLORS.size(); i++) {
			TeamColor color = GlowPalette.ALL_COLORS.get(i);
			boolean inPalette = config.palette.contains(color.getSerializedName());
			boolean selected = color == manual;

			List<Component> lore = new ArrayList<>();
			if (selected) {
				lore.add(GuiItems.label("Selected", ChatFormatting.GREEN));
			}
			if (!inPalette) {
				lore.add(GuiItems.label("Not in the palette", ChatFormatting.DARK_GRAY));
			}
			lore.add(GuiItems.label("Click to force this color", ChatFormatting.DARK_GRAY));

			set(COLOR_SLOTS[i], GuiItems.of(GlowPalette.icon(color),
				GuiItems.label((selected ? "> " : "") + GlowPalette.displayName(color), color), lore));
		}

		set(SLOT_AUTOMATIC, GuiItems.of(Items.COMPASS,
			GuiItems.label("Automatic color", ChatFormatting.AQUA),
			GuiItems.label("Drop the manual color and use the mode", ChatFormatting.GRAY)));
		set(SLOT_EXCLUDE, GuiItems.toggle("Excluded", excluded,
			"Skip this player entirely"));
		set(SLOT_BACK, GuiItems.back());
		set(SLOT_CLOSE, GuiItems.close());
		fillEmpty(GuiItems.FILLER);
	}

	@Override
	protected void onClick(int slot, ContainerInput input, int button) {
		GlowConfig config = GlowPlayers.configs().get();
		String key = targetId.toString();

		if (slot == SLOT_BACK) {
			new PlayersGui(player, returnPage).open();
			return;
		}
		if (slot == SLOT_CLOSE) {
			player.closeContainer();
			return;
		}
		if (slot == SLOT_AUTOMATIC) {
			config.playerColors.remove(key);
			config.assignedColors.remove(key);
			GlowPlayers.applyAndSave(player.level().getServer());
			refresh();
			return;
		}
		if (slot == SLOT_EXCLUDE) {
			if (!config.excludedPlayers.remove(key)) {
				config.excludedPlayers.add(key);
			}
			GlowPlayers.applyAndSave(player.level().getServer());
			refresh();
			return;
		}

		for (int i = 0; i < COLOR_SLOTS.length; i++) {
			if (COLOR_SLOTS[i] != slot) {
				continue;
			}
			TeamColor color = GlowPalette.ALL_COLORS.get(i);
			if (color.getSerializedName().equals(config.playerColors.get(key))) {
				config.playerColors.remove(key);
			} else {
				config.playerColors.put(key, color.getSerializedName());
			}
			GlowPlayers.applyAndSave(player.level().getServer());
			refresh();
			return;
		}
	}
}
