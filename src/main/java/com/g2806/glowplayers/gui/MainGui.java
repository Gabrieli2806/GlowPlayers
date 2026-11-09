package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.ColorMode;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.NotifyMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;

/**
 * Root admin screen.
 *
 * <pre>
 * row 0  ─ ─ ─ ─ [info] ─ ─ ─ ─
 * row 1    [mod]  [glow]  [bar]  [names]      main switches
 * row 2    [ON ]  [ON  ]  [ON ]  [OFF  ]      their state, also clickable
 * row 3  [mode][auto][self]  [teams][warn][notify]
 * row 4       [palette]  [players]  [dimension]
 * row 5  [interval][cleanup] ─ [close] ─ [reload][reset]
 * </pre>
 */
public class MainGui extends SimpleGui {
	private static final int SLOT_INFO = 4;
	private static final int SLOT_ENABLED = 10;
	private static final int SLOT_GLOW = 12;
	private static final int SLOT_LOCATOR = 14;
	private static final int SLOT_NAMES = 16;
	private static final int SLOT_MODE = 28;
	private static final int SLOT_AUTO_ADD = 29;
	private static final int SLOT_SELF_PICK = 30;
	private static final int SLOT_TEAMS = 32;
	private static final int SLOT_WARN = 33;
	private static final int SLOT_NOTIFY = 34;
	private static final int SLOT_PALETTE = 38;
	private static final int SLOT_PLAYERS = 40;
	private static final int SLOT_DIMENSION = 42;
	private static final int SLOT_INTERVAL = 45;
	private static final int SLOT_CLEANUP = 46;
	private static final int SLOT_CLOSE = 49;
	private static final int SLOT_RELOAD = 52;
	private static final int SLOT_RESET = 53;

	/** Each status pane sits under its switch and toggles the same option. */
	private static final int STATUS_OFFSET = 9;

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
		ColorMode mode = config.mode();

		MinecraftServer server = server();
		int online = server == null ? 0 : server.getPlayerList().getPlayers().size();
		set(SLOT_INFO, GuiItems.glint(GuiItems.of(Items.NETHER_STAR,
			GuiItems.label("GlowPlayers", ChatFormatting.BOLD, ChatFormatting.AQUA),
			GuiItems.field("Status", config.enabled
				? GuiItems.label("Running", ChatFormatting.GREEN)
				: GuiItems.label("Disabled", ChatFormatting.RED)),
			GuiItems.field("Mode", mode == ColorMode.FIXED ? "Fixed group" : "Join order"),
			GuiItems.field("Group members", String.valueOf(config.members.size())),
			GuiItems.field("Online", String.valueOf(online)),
			GuiItems.field("Palette", config.palette.size() + " / 16")), config.enabled));

		feature(SLOT_ENABLED, GuiItems.feature(Items.BEACON, "Mod enabled", config.enabled,
			"Master switch. Off removes every", "glow, team and Locator Bar color."), config.enabled);
		feature(SLOT_GLOW, GuiItems.feature(Items.GLOWSTONE_DUST, "Glow outline", config.glowEffect,
			"Players glow in their color,", "visible through walls."), config.glowEffect);
		feature(SLOT_LOCATOR, GuiItems.feature(Items.RECOVERY_COMPASS, "Locator Bar colors", config.locatorBarColors,
			"Paint each player's Locator Bar", "dot with their color."), config.locatorBarColors);
		feature(SLOT_NAMES, GuiItems.feature(Items.NAME_TAG, "Colored names", config.colorPlayerNames,
			"Color names and the tab list", "even when the glow is off."), config.colorPlayerNames);

		set(SLOT_MODE, GuiItems.glint(GuiItems.choice(mode == ColorMode.FIXED ? Items.ENDER_CHEST : Items.CLOCK,
			"Color mode", "How players get their color",
			List.of("Fixed group — every player owns a color",
				"Join order — 1st joins gets color 1"), mode.ordinal()), true));
		ItemStack autoAdd = GuiItems.feature(Items.HOPPER, "Auto-add new players", config.autoAddMembers,
			"Fixed group: newcomers join the", "group and get the next free color.",
			"Off: only players added by an admin glow.");
		set(SLOT_AUTO_ADD, mode == ColorMode.FIXED ? autoAdd : GuiItems.of(Items.HOPPER,
			GuiItems.label("Auto-add new players", ChatFormatting.DARK_GRAY),
			GuiItems.hint("Only used in Fixed group mode")));
		set(SLOT_SELF_PICK, GuiItems.feature(Items.FEATHER, "Players pick their color", config.allowSelfColorPick,
			"Lets everyone use /glow color,", "/glow auto and /glow toggle."));

		set(SLOT_TEAMS, GuiItems.choice(Items.SHIELD, "Other teams",
			"When a player is on a team that is not ours",
			List.of("Respect it (glow uses that team)", "Override it (move to our team)"),
			config.overrideForeignTeams ? 1 : 0));
		set(SLOT_WARN, GuiItems.feature(Items.BELL, "Team conflict warnings", config.warnTeamConflicts,
			"Tell admins in chat when a player", "is on someone else's team."));
		set(SLOT_NOTIFY, GuiItems.choice(Items.OAK_SIGN, "Color notifications",
			"Where players see their new color",
			Arrays.stream(NotifyMode.values()).map(value -> switch (value) {
				case OFF -> "Off";
				case ACTION_BAR -> "Action bar";
				case CHAT -> "Chat";
			}).toList(), config.notifyMode().ordinal()));

		set(SLOT_PALETTE, GuiItems.of(Items.PAINTING,
			GuiItems.label("Palette", ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE),
			GuiItems.label("Which colors are handed out, and in", ChatFormatting.GRAY),
			GuiItems.label("which order (1st color → 1st player).", ChatFormatting.GRAY),
			Component.empty(),
			GuiItems.field("Active", config.palette.size() + " / 16"),
			GuiItems.hint("Click to open")));
		set(SLOT_PLAYERS, GuiItems.of(Items.PLAYER_HEAD,
			GuiItems.label("Players", ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE),
			GuiItems.label(mode == ColorMode.FIXED ? "Group members and online players" : "Online players", ChatFormatting.GRAY),
			GuiItems.label("Change colors or exclude someone.", ChatFormatting.GRAY),
			Component.empty(),
			GuiItems.field("Online", String.valueOf(online)),
			GuiItems.hint("Click to open")));

		String dimension = player.level().dimension().identifier().toString();
		boolean dimensionOn = !config.disabledDimensions.contains(dimension);
		set(SLOT_DIMENSION, GuiItems.feature(Items.ENDER_EYE, "This dimension", dimensionOn,
			"Glow in the dimension you are in:", dimension));

		set(SLOT_INTERVAL, GuiItems.of(Items.REPEATER,
			GuiItems.label("Reapply interval", ChatFormatting.AQUA),
			GuiItems.label("How often a lost glow (milk, totem)", ChatFormatting.GRAY),
			GuiItems.label("is given back.", ChatFormatting.GRAY),
			Component.empty(),
			GuiItems.field("Current", config.reapplyIntervalTicks == 0 ? "never"
				: config.reapplyIntervalTicks + " ticks (" + config.reapplyIntervalTicks / 20.0 + "s)"),
			GuiItems.hint("Left click +20, right click -20")));
		set(SLOT_CLEANUP, GuiItems.feature(Items.CAULDRON, "Clean up teams on stop", config.cleanupTeamsOnStop,
			"Delete the gp_ scoreboard teams", "when the server stops."));
		set(SLOT_CLOSE, GuiItems.close());
		set(SLOT_RELOAD, GuiItems.of(Items.WRITABLE_BOOK,
			GuiItems.label("Reload from disk", ChatFormatting.YELLOW),
			GuiItems.label("Re-reads config/glowplayers.json", ChatFormatting.GRAY)));
		set(SLOT_RESET, GuiItems.of(Items.TNT,
			GuiItems.label("Reset colors", ChatFormatting.RED),
			GuiItems.label("Empties the group and hands every", ChatFormatting.GRAY),
			GuiItems.label("online player a fresh color.", ChatFormatting.GRAY),
			Component.empty(),
			GuiItems.hint("Shift + click to reset")));

		frame();
	}

	/** A main switch: its icon plus the status pane right below it. */
	private void feature(int slot, ItemStack icon, boolean on) {
		set(slot, icon);
		set(slot + STATUS_OFFSET, GuiItems.status(on));
	}

	@Override
	protected void onClick(int slot, ContainerInput input, int button) {
		GlowConfig config = GlowPlayers.configs().get();
		boolean rightClick = button == 1;
		int main = switch (slot) {
			case SLOT_ENABLED + STATUS_OFFSET, SLOT_GLOW + STATUS_OFFSET,
				SLOT_LOCATOR + STATUS_OFFSET, SLOT_NAMES + STATUS_OFFSET -> slot - STATUS_OFFSET;
			default -> slot;
		};

		switch (main) {
			case SLOT_ENABLED -> config.enabled = !config.enabled;
			case SLOT_GLOW -> config.glowEffect = !config.glowEffect;
			case SLOT_LOCATOR -> config.locatorBarColors = !config.locatorBarColors;
			case SLOT_NAMES -> config.colorPlayerNames = !config.colorPlayerNames;
			case SLOT_MODE -> {
				config.mode = config.mode().next().name();
				GlowPlayers.manager().onModeChanged();
			}
			case SLOT_AUTO_ADD -> {
				if (config.mode() != ColorMode.FIXED) {
					return;
				}
				config.autoAddMembers = !config.autoAddMembers;
			}
			case SLOT_SELF_PICK -> config.allowSelfColorPick = !config.allowSelfColorPick;
			case SLOT_TEAMS -> config.overrideForeignTeams = !config.overrideForeignTeams;
			case SLOT_WARN -> config.warnTeamConflicts = !config.warnTeamConflicts;
			case SLOT_NOTIFY -> config.notifyMode = config.notifyMode().next().name();
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
			case SLOT_INTERVAL -> config.reapplyIntervalTicks =
				Math.max(0, Math.min(12000, config.reapplyIntervalTicks + (rightClick ? -20 : 20)));
			case SLOT_CLEANUP -> config.cleanupTeamsOnStop = !config.cleanupTeamsOnStop;
			case SLOT_RESET -> {
				if (input != ContainerInput.QUICK_MOVE) {
					return;
				}
				config.members.clear();
				GlowPlayers.manager().onModeChanged();
			}
			case SLOT_RELOAD -> {
				GlowPlayers.configs().load();
				GlowPlayers.manager().refreshAll(server());
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

		GlowPlayers.applyAndSave(server());
		refresh();
	}
}
