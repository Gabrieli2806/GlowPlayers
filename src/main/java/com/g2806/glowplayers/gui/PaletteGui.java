package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.GlowPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Turns the 16 available colors on or off, and reorders them. */
public class PaletteGui extends SimpleGui {
	/** Two centered rows of eight, one slot per color. */
	private static final int[] COLOR_SLOTS = {
		10, 11, 12, 13, 14, 15, 16, 17,
		19, 20, 21, 22, 23, 24, 25, 26
	};

	private static final int SLOT_BACK = 45;
	private static final int SLOT_RESET = 49;
	private static final int SLOT_CLOSE = 53;

	public PaletteGui(ServerPlayer player) {
		super(player, 6);
	}

	@Override
	public Component getDisplayName() {
		return GuiItems.label("GlowPlayers · Palette", ChatFormatting.DARK_AQUA);
	}

	@Override
	protected void draw() {
		GlowConfig config = GlowPlayers.configs().get();
		clearSlots();

		for (int i = 0; i < GlowPalette.ALL_COLORS.size(); i++) {
			TeamColor color = GlowPalette.ALL_COLORS.get(i);
			int position = config.palette.indexOf(color.getSerializedName());
			boolean active = position >= 0;

			List<Component> lore = new ArrayList<>();
			lore.add(Component.empty()
				.append(GuiItems.label("In palette: ", ChatFormatting.GRAY))
				.append(GuiItems.label(active ? "YES" : "NO", active ? ChatFormatting.GREEN : ChatFormatting.RED)));
			if (active) {
				lore.add(Component.empty()
					.append(GuiItems.label("Position: ", ChatFormatting.GRAY))
					.append(GuiItems.label("#" + (position + 1), ChatFormatting.WHITE)));
			}
			lore.add(Component.empty());
			lore.add(GuiItems.label("Left click to toggle", ChatFormatting.DARK_GRAY));
			lore.add(GuiItems.label("Right click to move it first", ChatFormatting.DARK_GRAY));

			set(COLOR_SLOTS[i], GuiItems.of(GlowPalette.icon(color),
				GuiItems.label(GlowPalette.displayName(color), color), lore));
		}

		set(SLOT_BACK, GuiItems.back());
		set(SLOT_RESET, GuiItems.of(Items.TNT,
			GuiItems.label("Reset palette", ChatFormatting.RED),
			GuiItems.label("Back to all 16 colors in default order", ChatFormatting.GRAY)));
		set(SLOT_CLOSE, GuiItems.close());
		fillEmpty(GuiItems.FILLER);
	}

	@Override
	protected void onClick(int slot, ContainerInput input, int button) {
		GlowConfig config = GlowPlayers.configs().get();

		if (slot == SLOT_BACK) {
			new MainGui(player).open();
			return;
		}
		if (slot == SLOT_CLOSE) {
			player.closeContainer();
			return;
		}
		if (slot == SLOT_RESET) {
			config.palette = new ArrayList<>(GlowPalette.DEFAULT_PALETTE);
			GlowPlayers.applyAndSave(player.level().getServer());
			refresh();
			return;
		}

		for (int i = 0; i < COLOR_SLOTS.length; i++) {
			if (COLOR_SLOTS[i] != slot) {
				continue;
			}
			String name = GlowPalette.ALL_COLORS.get(i).getSerializedName();
			if (button == 1) {
				config.palette.remove(name);
				config.palette.add(0, name);
			} else if (!config.palette.remove(name)) {
				config.palette.add(name);
			} else if (config.palette.isEmpty()) {
				// Never leave the mod without a single color to hand out.
				config.palette.add(name);
			}
			GlowPlayers.applyAndSave(player.level().getServer());
			refresh();
			return;
		}
	}
}
