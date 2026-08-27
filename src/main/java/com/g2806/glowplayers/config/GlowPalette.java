package com.g2806.glowplayers.config;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The 16 usable glow colors, their GUI icon and their display name. */
public final class GlowPalette {
	/** Every color a team can use, ordered for maximum visual distinction. */
	public static final List<TeamColor> ALL_COLORS = List.of(
		TeamColor.WHITE,
		TeamColor.BLUE,
		TeamColor.RED,
		TeamColor.GREEN,
		TeamColor.YELLOW,
		TeamColor.AQUA,
		TeamColor.LIGHT_PURPLE,
		TeamColor.GOLD,
		TeamColor.DARK_PURPLE,
		TeamColor.DARK_AQUA,
		TeamColor.DARK_GREEN,
		TeamColor.DARK_RED,
		TeamColor.DARK_BLUE,
		TeamColor.GRAY,
		TeamColor.DARK_GRAY,
		TeamColor.BLACK
	);

	public static final List<String> DEFAULT_PALETTE = ALL_COLORS.stream().map(TeamColor::getSerializedName).toList();

	private static final Map<TeamColor, DyeColor> ICON_DYES = new LinkedHashMap<>();

	static {
		ICON_DYES.put(TeamColor.WHITE, DyeColor.WHITE);
		ICON_DYES.put(TeamColor.BLUE, DyeColor.LIGHT_BLUE);
		ICON_DYES.put(TeamColor.RED, DyeColor.RED);
		ICON_DYES.put(TeamColor.GREEN, DyeColor.LIME);
		ICON_DYES.put(TeamColor.YELLOW, DyeColor.YELLOW);
		ICON_DYES.put(TeamColor.AQUA, DyeColor.CYAN);
		ICON_DYES.put(TeamColor.LIGHT_PURPLE, DyeColor.MAGENTA);
		ICON_DYES.put(TeamColor.GOLD, DyeColor.ORANGE);
		ICON_DYES.put(TeamColor.DARK_PURPLE, DyeColor.PURPLE);
		ICON_DYES.put(TeamColor.DARK_AQUA, DyeColor.CYAN);
		ICON_DYES.put(TeamColor.DARK_GREEN, DyeColor.GREEN);
		ICON_DYES.put(TeamColor.DARK_RED, DyeColor.RED);
		ICON_DYES.put(TeamColor.DARK_BLUE, DyeColor.BLUE);
		ICON_DYES.put(TeamColor.GRAY, DyeColor.LIGHT_GRAY);
		ICON_DYES.put(TeamColor.DARK_GRAY, DyeColor.GRAY);
		ICON_DYES.put(TeamColor.BLACK, DyeColor.BLACK);
	}

	private GlowPalette() {
	}

	public static TeamColor byName(String name) {
		if (name == null) {
			return null;
		}
		for (TeamColor color : ALL_COLORS) {
			if (color.getSerializedName().equalsIgnoreCase(name)) {
				return color;
			}
		}
		return null;
	}

	public static Item icon(TeamColor color) {
		return Items.WOOL.pick(ICON_DYES.getOrDefault(color, DyeColor.WHITE));
	}

	/** "dark_aqua" -> "Dark Aqua". */
	public static String displayName(TeamColor color) {
		StringBuilder sb = new StringBuilder();
		for (String part : color.getSerializedName().split("_")) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}

	/** RGB value the Locator Bar should use; black would be invisible so it is nudged like vanilla does. */
	public static int rgb(TeamColor color) {
		int rgb = color.rgb();
		return rgb == 0 ? 0x2F2F30 : rgb;
	}

	public static List<TeamColor> resolve(List<String> names) {
		List<TeamColor> colors = new ArrayList<>();
		for (String name : names) {
			TeamColor color = byName(name);
			if (color != null && !colors.contains(color)) {
				colors.add(color);
			}
		}
		if (colors.isEmpty()) {
			colors.addAll(ALL_COLORS);
		}
		return colors;
	}
}
