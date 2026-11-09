package com.g2806.glowplayers.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain data holder serialized to {@code config/glowplayers.json}.
 * Enums are stored as strings so the file stays readable and hand-editable.
 */
public class GlowConfig {
	public static final int CURRENT_VERSION = 3;

	/** Bumped whenever the layout changes; {@link ConfigMigration} upgrades older files. */
	public int configVersion = CURRENT_VERSION;

	/** Master switch. When false the mod stops touching players entirely. */
	public boolean enabled = true;

	/** Give players the glowing outline. */
	public boolean glowEffect = true;

	/** Color the Locator Bar waypoint. Independent from {@link #glowEffect}. */
	public boolean locatorBarColors = true;

	/** Keep scoreboard team colors even when the glow outline is off (colors names/tab list). */
	public boolean colorPlayerNames = false;

	/** {@link ColorMode#FIXED} or {@link ColorMode#SESSION}. */
	public String mode = ColorMode.FIXED.name();

	/** FIXED mode: players that are not in {@link #members} yet join the group on their first visit. */
	public boolean autoAddMembers = true;

	/**
	 * When a player is already on a scoreboard team that belongs to someone else (a map, a
	 * minigame, another mod), false leaves them there and only warns; true moves them anyway.
	 */
	public boolean overrideForeignTeams = false;

	/** Tell online admins in chat when a player is on someone else's team. */
	public boolean warnTeamConflicts = true;

	public String notifyMode = NotifyMode.ACTION_BAR.name();

	/** Ticks between the "is the glow still there" sweep. 0 disables the sweep. */
	public int reapplyIntervalTicks = 100;

	/** Let non-admins run /glowplayers color, auto and toggle on themselves. */
	public boolean allowSelfColorPick = false;

	/** Delete the mod's scoreboard teams when the server stops. */
	public boolean cleanupTeamsOnStop = true;

	/** Vanilla team flag; false hides invisible "teammates" (players sharing a color). */
	public boolean seeFriendlyInvisibles = false;

	/** Permission level (0-4) required for the admin GUI and admin subcommands. */
	public int adminPermissionLevel = 2;

	/** Ordered palette: the first color goes to the first player, and so on. */
	public List<String> palette = new ArrayList<>(GlowPalette.DEFAULT_PALETTE);

	/** Dimension ids (e.g. "minecraft:the_nether") where the mod does nothing. */
	public List<String> disabledDimensions = new ArrayList<>();

	/** FIXED mode group: UUID -> member. Colors stay reserved while the member is offline. */
	public Map<String, Member> members = new LinkedHashMap<>();

	/** UUID -> last known name of the players that are skipped completely. */
	public Map<String, String> excludedPlayers = new LinkedHashMap<>();

	public static class Member {
		/** Last known name, so offline members can be shown in the GUI. */
		public String name = "";
		public String color;

		public Member() {
		}

		public Member(String name, String color) {
			this.name = name;
			this.color = color;
		}
	}

	public ColorMode mode() {
		return ColorMode.parse(mode);
	}

	public NotifyMode notifyMode() {
		return NotifyMode.parse(notifyMode);
	}

	/** Fills in anything a hand-edited or older config file left null/invalid. */
	public void sanitize() {
		configVersion = CURRENT_VERSION;
		if (palette == null) {
			palette = new ArrayList<>(GlowPalette.DEFAULT_PALETTE);
		}
		palette.removeIf(name -> GlowPalette.byName(name) == null);
		if (palette.isEmpty()) {
			palette = new ArrayList<>(GlowPalette.DEFAULT_PALETTE);
		}
		if (disabledDimensions == null) {
			disabledDimensions = new ArrayList<>();
		}
		if (members == null) {
			members = new LinkedHashMap<>();
		}
		members.values().removeIf(member -> member == null || GlowPalette.byName(member.color) == null);
		members.values().forEach(member -> {
			if (member.name == null) {
				member.name = "";
			}
		});
		if (excludedPlayers == null) {
			excludedPlayers = new LinkedHashMap<>();
		}
		mode = mode().name();
		notifyMode = notifyMode().name();
		reapplyIntervalTicks = Math.max(0, Math.min(reapplyIntervalTicks, 12000));
		adminPermissionLevel = Math.max(0, Math.min(adminPermissionLevel, 4));
	}
}
