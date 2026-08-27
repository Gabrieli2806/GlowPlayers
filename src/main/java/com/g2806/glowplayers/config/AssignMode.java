package com.g2806.glowplayers.config;

/** How a color gets picked for a player that has no manual override. */
public enum AssignMode {
	/** Palette index follows the player's position in the online player list. */
	JOIN_ORDER,
	/** First free color of the palette, picked at random. */
	RANDOM,
	/** Derived from the player name, so the same name keeps the same color. */
	NAME_HASH,
	/** Palette index follows the alphabetical order of the online players. */
	ALPHABETICAL;

	public static AssignMode parse(String name) {
		for (AssignMode mode : values()) {
			if (mode.name().equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return JOIN_ORDER;
	}

	public AssignMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
