package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.ColorMode;
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
 * Per-player screen: pick a color (swapping with whoever had it), add or remove the player
 * from the fixed group, or exclude them.
 */
public class PlayerGui extends SimpleGui {
	private static final int[] COLOR_SLOTS = {
		19, 20, 21, 22, 23, 24, 25, 26,
		28, 29, 30, 31, 32, 33, 34, 35
	};

	private static final int SLOT_HEAD = 4;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_GROUP = 48;
	private static final int SLOT_EXCLUDE = 50;
	private static final int SLOT_CLOSE = 53;

	private final PlayersGui.Entry target;
	private final int returnPage;
	private final List<TeamColor> shown = new ArrayList<>();

	public PlayerGui(ServerPlayer player, PlayersGui.Entry target, int returnPage) {
		super(player, 6);
		this.target = target;
		this.returnPage = returnPage;
	}

	@Override
	public Component getDisplayName() {
		return GuiItems.label("GlowPlayers · " + PlayersGui.displayName(target), ChatFormatting.DARK_AQUA);
	}

	private String key() {
		return target.uuid().toString();
	}

	/** In join order mode only online players hold a color. */
	private boolean canPickColor(GlowConfig config) {
		return config.mode() == ColorMode.FIXED || server().getPlayerList().getPlayer(target.uuid()) != null;
	}

	@Override
	protected void draw() {
		GlowConfig config = GlowPlayers.configs().get();
		clearSlots();

		TeamColor current = PlayersGui.colorOf(target, config);
		boolean excluded = config.excludedPlayers.containsKey(key());
		boolean member = config.members.containsKey(key());
		boolean fixed = config.mode() == ColorMode.FIXED;
		boolean online = server().getPlayerList().getPlayer(target.uuid()) != null;

		List<Component> headLore = new ArrayList<>();
		headLore.add(GuiItems.field("Status", online
			? GuiItems.label("Online", ChatFormatting.GREEN)
			: GuiItems.label("Offline", ChatFormatting.DARK_GRAY)));
		headLore.add(GuiItems.field("Color", current == null
			? GuiItems.label("none", ChatFormatting.DARK_GRAY)
			: GuiItems.label(GlowPalette.displayName(current), current)));
		if (fixed) {
			headLore.add(GuiItems.field("In group", member ? GuiItems.label("yes", ChatFormatting.GREEN)
				: GuiItems.label("no", ChatFormatting.RED)));
		}
		String conflict = GlowPlayers.manager().conflictOf(target.uuid());
		if (conflict != null) {
			headLore.add(GuiItems.label("On foreign team \"" + conflict + "\"", ChatFormatting.YELLOW));
		}
		headLore.add(GuiItems.label(target.uuid().toString(), ChatFormatting.DARK_GRAY));
		set(SLOT_HEAD, GuiItems.head(target.uuid(), target.name(),
			current == null ? GuiItems.label(PlayersGui.displayName(target), ChatFormatting.WHITE)
				: GuiItems.label(PlayersGui.displayName(target), current),
			headLore));

		shown.clear();
		shown.addAll(GlowPalette.resolve(config.palette));
		for (TeamColor color : GlowPalette.ALL_COLORS) {
			if (!shown.contains(color)) {
				shown.add(color);
			}
		}
		boolean canPick = canPickColor(config);
		for (int i = 0; i < shown.size(); i++) {
			TeamColor color = shown.get(i);
			boolean selected = color == current;
			String owner = GlowPlayers.manager().ownerOf(server(), color, target.uuid());

			List<Component> lore = new ArrayList<>();
			if (selected) {
				lore.add(GuiItems.label("Current color", ChatFormatting.GREEN));
			}
			if (owner != null) {
				lore.add(GuiItems.field("Taken by", owner.isEmpty() ? "an offline member" : owner));
			}
			if (!config.palette.contains(color.getSerializedName())) {
				lore.add(GuiItems.label("Not in the palette", ChatFormatting.DARK_GRAY));
			}
			lore.add(Component.empty());
			lore.add(GuiItems.hint(!canPick ? "Player is offline"
				: owner != null ? "Click to swap colors with " + (owner.isEmpty() ? "them" : owner)
				: "Click to use this color"));

			ItemStack stack = GuiItems.colorIcon(color,
				GuiItems.label((selected ? "▶ " : "") + GlowPalette.displayName(color), color), lore);
			set(COLOR_SLOTS[i], GuiItems.glint(stack, selected));
		}

		if (fixed) {
			set(SLOT_GROUP, member
				? GuiItems.of(Items.ENDER_CHEST,
					GuiItems.label("Remove from group", ChatFormatting.RED),
					GuiItems.label("Frees their color for someone else.", ChatFormatting.GRAY),
					GuiItems.label(config.autoAddMembers
						? "Auto-add is on: they rejoin the group with a new color."
						: "Auto-add is off: they stop glowing.", ChatFormatting.GRAY))
				: GuiItems.of(Items.ENDER_CHEST,
					GuiItems.label("Add to group", ChatFormatting.GREEN),
					GuiItems.label("Reserves the next free color for them.", ChatFormatting.GRAY)));
		} else {
			set(SLOT_GROUP, GuiItems.of(Items.CLOCK,
				GuiItems.label("Reset to join order", ChatFormatting.AQUA),
				GuiItems.label("Gives them the lowest free color.", ChatFormatting.GRAY)));
		}
		set(SLOT_EXCLUDE, GuiItems.feature(Items.BARRIER, "Excluded", excluded,
			"Skip this player entirely:", "no glow, team or Locator Bar color."));
		set(SLOT_BACK, GuiItems.back());
		set(SLOT_CLOSE, GuiItems.close());
		frame();
	}

	@Override
	protected void onClick(int slot, ContainerInput input, int button) {
		GlowConfig config = GlowPlayers.configs().get();

		switch (slot) {
			case SLOT_BACK -> {
				new PlayersGui(player, returnPage).open();
				return;
			}
			case SLOT_CLOSE -> {
				player.closeContainer();
				return;
			}
			case SLOT_GROUP -> {
				if (config.mode() == ColorMode.FIXED && !config.members.containsKey(key())) {
					GlowPlayers.manager().addMember(server(), target.uuid(), target.name());
				} else {
					GlowPlayers.manager().resetColor(server(), target.uuid());
				}
				refresh();
				return;
			}
			case SLOT_EXCLUDE -> {
				if (config.excludedPlayers.remove(key()) == null) {
					config.excludedPlayers.put(key(), target.name());
				}
				GlowPlayers.applyAndSave(server());
				refresh();
				return;
			}
			default -> {
			}
		}

		for (int i = 0; i < COLOR_SLOTS.length; i++) {
			if (COLOR_SLOTS[i] != slot || i >= shown.size()) {
				continue;
			}
			if (!canPickColor(config)) {
				return;
			}
			GlowPlayers.manager().setColor(server(), target.uuid(), target.name(), shown.get(i));
			refresh();
			return;
		}
	}
}
