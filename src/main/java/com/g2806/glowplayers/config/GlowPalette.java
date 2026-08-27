package com.g2806.glowplayers.config;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The 16 usable glow colors, their GUI icon and their display name. */
public final class GlowPalette {
	/** Every color Minecraft can use for a team, ordered for maximum visual distinction. */
	public static final List<ChatFormatting> ALL_COLORS = List.of(
		ChatFormatting.WHITE,
		ChatFormatting.BLUE,
		ChatFormatting.RED,
		ChatFormatting.GREEN,
		ChatFormatting.YELLOW,
		ChatFormatting.AQUA,
		ChatFormatting.LIGHT_PURPLE,
		ChatFormatting.GOLD,
		ChatFormatting.DARK_PURPLE,
		ChatFormatting.DARK_AQUA,
		ChatFormatting.DARK_GREEN,
		ChatFormatting.DARK_RED,
		ChatFormatting.DARK_BLUE,
		ChatFormatting.GRAY,
		ChatFormatting.DARK_GRAY,
		ChatFormatting.BLACK
	);

	public static final List<String> DEFAULT_PALETTE = ALL_COLORS.stream().map(ChatFormatting::getName).toList();

	private static final Map<ChatFormatting, Item> ICONS = new LinkedHashMap<>();

	static {
		ICONS.put(ChatFormatting.WHITE, Items.WHITE_WOOL);
		ICONS.put(ChatFormatting.BLUE, Items.LIGHT_BLUE_WOOL);
		ICONS.put(ChatFormatting.RED, Items.RED_WOOL);
		ICONS.put(ChatFormatting.GREEN, Items.LIME_WOOL);
		ICONS.put(ChatFormatting.YELLOW, Items.YELLOW_WOOL);
		ICONS.put(ChatFormatting.AQUA, Items.CYAN_WOOL);
		ICONS.put(ChatFormatting.LIGHT_PURPLE, Items.MAGENTA_WOOL);
		ICONS.put(ChatFormatting.GOLD, Items.ORANGE_WOOL);
		ICONS.put(ChatFormatting.DARK_PURPLE, Items.PURPLE_WOOL);
		ICONS.put(ChatFormatting.DARK_AQUA, Items.CYAN_WOOL);
		ICONS.put(ChatFormatting.DARK_GREEN, Items.GREEN_WOOL);
		ICONS.put(ChatFormatting.DARK_RED, Items.RED_WOOL);
		ICONS.put(ChatFormatting.DARK_BLUE, Items.BLUE_WOOL);
		ICONS.put(ChatFormatting.GRAY, Items.LIGHT_GRAY_WOOL);
		ICONS.put(ChatFormatting.DARK_GRAY, Items.GRAY_WOOL);
		ICONS.put(ChatFormatting.BLACK, Items.BLACK_WOOL);
	}

	private GlowPalette() {
	}

	public static ChatFormatting byName(String name) {
		if (name == null) {
			return null;
		}
		for (ChatFormatting color : ALL_COLORS) {
			if (color.getName().equalsIgnoreCase(name)) {
				return color;
			}
		}
		return null;
	}

	public static Item icon(ChatFormatting color) {
		return ICONS.getOrDefault(color, Items.WHITE_WOOL);
	}

	/** "dark_aqua" -> "Dark Aqua". */
	public static String displayName(ChatFormatting color) {
		StringBuilder sb = new StringBuilder();
		for (String part : color.getName().split("_")) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}

	/** RGB value the Locator Bar should use; black would be invisible so it is nudged like vanilla does. */
	public static int rgb(ChatFormatting color) {
		Integer value = color.getColor();
		int rgb = value == null ? 0xFFFFFF : value;
		return rgb == 0 ? 0x2F2F30 : rgb;
	}

	public static List<ChatFormatting> resolve(List<String> names) {
		List<ChatFormatting> colors = new ArrayList<>();
		for (String name : names) {
			ChatFormatting color = byName(name);
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
