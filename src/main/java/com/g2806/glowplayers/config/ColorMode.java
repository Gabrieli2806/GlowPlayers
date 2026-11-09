package com.g2806.glowplayers.config;

/** How players get their color. */
public enum ColorMode {
	/**
	 * Every player owns a color for good. It is reserved while they are offline, so a group of
	 * friends always sees the same colors no matter who is online.
	 */
	FIXED,
	/**
	 * Colors follow the order players joined in. Leaving frees the color and the next player to
	 * join takes the lowest free one; players already online never change color.
	 */
	SESSION;

	public static ColorMode parse(String name) {
		for (ColorMode mode : values()) {
			if (mode.name().equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return FIXED;
	}

	public ColorMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
