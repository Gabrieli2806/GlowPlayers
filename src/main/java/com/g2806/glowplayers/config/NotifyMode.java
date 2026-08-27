package com.g2806.glowplayers.config;

/** Where a player is told about their new color. */
public enum NotifyMode {
	OFF,
	ACTION_BAR,
	CHAT;

	public static NotifyMode parse(String name) {
		for (NotifyMode mode : values()) {
			if (mode.name().equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return ACTION_BAR;
	}

	public NotifyMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
