package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.GlowConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/** Root admin screen: every global setting plus the way into the sub screens. */
public class MainGui extends SimpleGui {
	private static final int SLOT_ENABLED = 10;
	private static final int SLOT_GLOW = 12;
	private static final int SLOT_LOCATOR = 14;
	private static final int SLOT_NAMES = 16;
	private static final int SLOT_ASSIGN_MODE = 20;
	private static final int SLOT_NOTIFY = 22;
	private static final int SLOT_INTERVAL = 24;
	private static final int SLOT_PERSIST = 29;
	private static final int SLOT_SELF_PICK = 31;
	private static final int SLOT_CLEANUP = 33;
	private static final int SLOT_PALETTE = 38;
	private static final int SLOT_PLAYERS = 40;
	private static final int SLOT_DIMENSION = 42;
	private static final int SLOT_RESET_COLORS = 47;
	private static final int SLOT_CLOSE = 49;
	private static final int SLOT_RELOAD = 51;

	public MainGui(ServerPlayer player) {
		super(player, 6);
	}

	@Override
	public Component getDisplayName() {
		return GuiItems.label("GlowPlayers", ChatFormatting.DARK_AQUA);
	}

	@Override
	protected void draw() {
		GlowConfig config = GlowPlayers.configs().get();
		clearSlots();

		set(SLOT_ENABLED, GuiItems.toggle("Mod enabled", config.enabled,
			"Master switch for the whole mod"));
		set(SLOT_GLOW, GuiItems.toggle("Glow outline", config.glowEffect,
			"Give players the glowing effect"));
		set(SLOT_LOCATOR, GuiItems.toggle("Locator Bar colors", config.locatorBarColors,
			"Color the Locator Bar waypoints"));
		set(SLOT_NAMES, GuiItems.toggle("Colored names", config.colorPlayerNames,
			"Keep team colors on names when the glow is off"));

		set(SLOT_ASSIGN_MODE, GuiItems.cycle(Items.COMPASS, "Assignment mode",
			config.assignMode().name(), "How colors are handed out"));
		set(SLOT_NOTIFY, GuiItems.cycle(Items.PAPER, "Notifications",
			config.notifyMode().name(), "Where players are told their color"));
		set(SLOT_INTERVAL, GuiItems.of(Items.CLOCK,
			GuiItems.label("Reapply interval", ChatFormatting.AQUA),
			GuiItems.label("How often a lost glow is restored", ChatFormatting.GRAY),
			Component.empty(),
			Component.empty()
				.append(GuiItems.label("Current: ", ChatFormatting.GRAY))
				.append(GuiItems.label(config.reapplyIntervalTicks + " ticks", ChatFormatting.WHITE)),
			GuiItems.label("Left click +20, right click -20", ChatFormatting.DARK_GRAY),
			GuiItems.label("0 disables the check", ChatFormatting.DARK_GRAY)));

		set(SLOT_PERSIST, GuiItems.toggle("Persistent colors", config.persistColors,
			"Players keep their color between sessions"));
		set(SLOT_SELF_PICK, GuiItems.toggle("Players pick their color", config.allowSelfColorPick,
			"Allows /glowplayers color and toggle for everyone"));
		set(SLOT_CLEANUP, GuiItems.toggle("Clean up teams on stop", config.cleanupTeamsOnStop,
			"Delete the mod's scoreboard teams on shutdown"));

		set(SLOT_PALETTE, GuiItems.of(Items.PAINTING,
			GuiItems.label("Palette", ChatFormatting.LIGHT_PURPLE),
			GuiItems.label("Choose which colors get handed out", ChatFormatting.GRAY),
			Component.empty(),
			Component.empty()
				.append(GuiItems.label("Active: ", ChatFormatting.GRAY))
				.append(GuiItems.label(config.palette.size() + " / 16", ChatFormatting.WHITE))));

		MinecraftServer server = player.level().getServer();
		int online = server == null ? 0 : server.getPlayerList().getPlayers().size();
		set(SLOT_PLAYERS, GuiItems.of(Items.PLAYER_HEAD,
			GuiItems.label("Players", ChatFormatting.LIGHT_PURPLE),
			GuiItems.label("Set a color or exclude someone", ChatFormatting.GRAY),
			Component.empty(),
			Component.empty()
				.append(GuiItems.label("Online: ", ChatFormatting.GRAY))
				.append(GuiItems.label(String.valueOf(online), ChatFormatting.WHITE))));

		String dimension = player.level().dimension().identifier().toString();
		boolean dimensionOn = !config.disabledDimensions.contains(dimension);
		set(SLOT_DIMENSION, GuiItems.of(Items.ENDER_PEARL,
			GuiItems.label("This dimension", dimensionOn ? ChatFormatting.GREEN : ChatFormatting.RED),
			GuiItems.label(dimension, ChatFormatting.GRAY),
			Component.empty(),
			Component.empty()
				.append(GuiItems.label("Active here: ", ChatFormatting.GRAY))
				.append(GuiItems.label(dimensionOn ? "YES" : "NO",
					dimensionOn ? ChatFormatting.GREEN : ChatFormatting.RED)),
			GuiItems.label("Click to toggle", ChatFormatting.DARK_GRAY)));

		set(SLOT_RESET_COLORS, GuiItems.of(Items.TNT,
			GuiItems.label("Reset colors", ChatFormatting.RED),
			GuiItems.label("Forget every remembered and manual color", ChatFormatting.GRAY),
			GuiItems.label("Click to reset", ChatFormatting.DARK_GRAY)));
		set(SLOT_RELOAD, GuiItems.of(Items.WRITABLE_BOOK,
			GuiItems.label("Reload from disk", ChatFormatting.YELLOW),
			GuiItems.label("Re-reads config/glowplayers.json", ChatFormatting.GRAY)));
		set(SLOT_CLOSE, GuiItems.close());

		fillEmpty(GuiItems.FILLER);
	}

	@Override
	protected void onClick(int slot, ContainerInput input, int button) {
		GlowConfig config = GlowPlayers.configs().get();
		boolean rightClick = button == 1;

		switch (slot) {
			case SLOT_ENABLED -> config.enabled = !config.enabled;
			case SLOT_GLOW -> config.glowEffect = !config.glowEffect;
			case SLOT_LOCATOR -> config.locatorBarColors = !config.locatorBarColors;
			case SLOT_NAMES -> config.colorPlayerNames = !config.colorPlayerNames;
			case SLOT_ASSIGN_MODE -> config.assignMode = config.assignMode().next().name();
			case SLOT_NOTIFY -> config.notifyMode = config.notifyMode().next().name();
			case SLOT_INTERVAL -> config.reapplyIntervalTicks =
				Math.max(0, Math.min(12000, config.reapplyIntervalTicks + (rightClick ? -20 : 20)));
			case SLOT_PERSIST -> {
				config.persistColors = !config.persistColors;
				if (!config.persistColors) {
					config.assignedColors.clear();
				}
			}
			case SLOT_SELF_PICK -> config.allowSelfColorPick = !config.allowSelfColorPick;
			case SLOT_CLEANUP -> config.cleanupTeamsOnStop = !config.cleanupTeamsOnStop;
			case SLOT_PALETTE -> {
				new PaletteGui(player).open();
				return;
			}
			case SLOT_PLAYERS -> {
				new PlayersGui(player, 0).open();
				return;
			}
			case SLOT_DIMENSION -> {
				String dimension = player.level().dimension().identifier().toString();
				if (!config.disabledDimensions.remove(dimension)) {
					config.disabledDimensions.add(dimension);
				}
			}
			case SLOT_RESET_COLORS -> {
				config.assignedColors.clear();
				config.playerColors.clear();
			}
			case SLOT_RELOAD -> {
				GlowPlayers.configs().load();
				GlowPlayers.manager().refreshAll(player.level().getServer());
				refresh();
				return;
			}
			case SLOT_CLOSE -> {
				player.closeContainer();
				return;
			}
			default -> {
				return;
			}
		}

		GlowPlayers.applyAndSave(player.level().getServer());
		refresh();
	}
}
