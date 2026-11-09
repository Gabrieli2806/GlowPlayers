package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.GlowPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns colors on or off and orders them. Active colors come first, in hand-out order, with
 * their position as the stack size; disabled ones follow.
 */
public class PaletteGui extends SimpleGui {
	/** Two centered rows of eight, one slot per color. */
	private static final int[] COLOR_SLOTS = {
		10, 11, 12, 13, 14, 15, 16, 17,
		19, 20, 21, 22, 23, 24, 25, 26
	};

	private static final int SLOT_HELP = 4;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_RESET = 49;
	private static final int SLOT_CLOSE = 53;

	private final List<TeamColor> shown = new ArrayList<>();

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

		shown.clear();
		shown.addAll(GlowPalette.resolve(config.palette));
		for (TeamColor color : GlowPalette.ALL_COLORS) {
			if (!shown.contains(color)) {
				shown.add(color);
			}
		}

		set(SLOT_HELP, GuiItems.of(Items.BOOK,
			GuiItems.label("Palette", ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE),
			GuiItems.label("The 1st color goes to the 1st player,", ChatFormatting.GRAY),
			GuiItems.label("the 2nd to the 2nd, and so on.", ChatFormatting.GRAY),
			Component.empty(),
			GuiItems.hint("Left click: turn a color on/off"),
			GuiItems.hint("Right click: move it one place earlier"),
			GuiItems.hint("Shift + click: move it first")));

		for (int i = 0; i < shown.size(); i++) {
			TeamColor color = shown.get(i);
			int position = config.palette.indexOf(color.getSerializedName());
			boolean active = position >= 0;

			List<Component> lore = new ArrayList<>();
			lore.add(GuiItems.field("In palette", GuiItems.onOff(active)));
			if (active) {
				lore.add(GuiItems.field("Position", "#" + (position + 1)));
			}
			ItemStack stack = GuiItems.colorIcon(color,
				active
					? GuiItems.label(GlowPalette.displayName(color), color)
					: GuiItems.label(GlowPalette.displayName(color) + " (off)", ChatFormatting.DARK_GRAY),
				lore);
			stack.setCount(active ? position + 1 : 1);
			set(COLOR_SLOTS[i], GuiItems.glint(stack, active));
		}

		set(SLOT_BACK, GuiItems.back());
		set(SLOT_RESET, GuiItems.of(Items.TNT,
			GuiItems.label("Reset palette", ChatFormatting.RED),
			GuiItems.label("All 16 colors in the default order", ChatFormatting.GRAY)));
		set(SLOT_CLOSE, GuiItems.close());
		frame();
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
			GlowPlayers.applyAndSave(server());
			refresh();
			return;
		}

		for (int i = 0; i < COLOR_SLOTS.length; i++) {
			if (COLOR_SLOTS[i] != slot || i >= shown.size()) {
				continue;
			}
			String name = shown.get(i).getSerializedName();
			int position = config.palette.indexOf(name);
			if (input == ContainerInput.QUICK_MOVE) {
				config.palette.remove(name);
				config.palette.addFirst(name);
			} else if (button == 1) {
				if (position > 0) {
					config.palette.remove(position);
					config.palette.add(position - 1, name);
				}
			} else if (position >= 0) {
				// Never leave the mod without a single color to hand out.
				if (config.palette.size() > 1) {
					config.palette.remove(position);
				}
			} else {
				config.palette.add(name);
			}
			GlowPlayers.applyAndSave(server());
			refresh();
			return;
		}
	}
}
