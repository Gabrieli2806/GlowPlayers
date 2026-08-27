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
	/** Master switch. When false the mod stops touching players entirely. */
	public boolean enabled = true;

	/** Give players the glowing outline. */
	public boolean glowEffect = true;

	/** Color the Locator Bar waypoint. Independent from {@link #glowEffect}. */
	public boolean locatorBarColors = true;

	/** Keep scoreboard team colors even when the glow outline is off (colors names/tab list). */
	public boolean colorPlayerNames = false;

	public String assignMode = AssignMode.JOIN_ORDER.name();

	public String notifyMode = NotifyMode.ACTION_BAR.name();

	/** Ticks between the "is the glow still there" sweep. 0 disables the sweep. */
	public int reapplyIntervalTicks = 100;

	/** Remember the auto-assigned color of a player across sessions. */
	public boolean persistColors = true;

	/** Let non-admins run /glowplayers color and /glowplayers toggle on themselves. */
	public boolean allowSelfColorPick = false;

	/** Delete the mod's scoreboard teams when the server stops. */
	public boolean cleanupTeamsOnStop = true;

	/** Vanilla team flag; false hides invisible "teammates" (each player is alone on their team anyway). */
	public boolean seeFriendlyInvisibles = false;

	/** Permission level (0-4) required for the admin GUI and admin subcommands. */
	public int adminPermissionLevel = 2;

	/** Ordered palette. Only these ChatFormatting colors get handed out. */
	public List<String> palette = new ArrayList<>(GlowPalette.DEFAULT_PALETTE);

	/** Dimension ids (e.g. "minecraft:the_nether") where the mod does nothing. */
	public List<String> disabledDimensions = new ArrayList<>();

	/** UUID -> color name. Set by an admin (or the player); wins over any automatic mode. */
	public Map<String, String> playerColors = new LinkedHashMap<>();

	/** UUIDs that are skipped completely. */
	public List<String> excludedPlayers = new ArrayList<>();

	/** UUID -> color name remembered from a previous automatic assignment. */
	public Map<String, String> assignedColors = new LinkedHashMap<>();

	public AssignMode assignMode() {
		return AssignMode.parse(assignMode);
	}

	public NotifyMode notifyMode() {
		return NotifyMode.parse(notifyMode);
	}

	/** Fills in anything a hand-edited or older config file left null/invalid. */
	public void sanitize() {
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
		if (playerColors == null) {
			playerColors = new LinkedHashMap<>();
		}
		if (excludedPlayers == null) {
			excludedPlayers = new ArrayList<>();
		}
		if (assignedColors == null) {
			assignedColors = new LinkedHashMap<>();
		}
		assignMode = assignMode().name();
		notifyMode = notifyMode().name();
		reapplyIntervalTicks = Math.max(0, Math.min(reapplyIntervalTicks, 12000));
		adminPermissionLevel = Math.max(0, Math.min(adminPermissionLevel, 4));
	}
}
