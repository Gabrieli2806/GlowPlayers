package com.g2806.glowplayers.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small helpers to build the display-only items the menus are made of. */
public final class GuiItems {
	public static final ItemStack FILLER = plain(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), Component.empty());

	private GuiItems() {
	}

	/** Item names and lore render italic by default in a GUI; this turns that off. */
	public static MutableComponent label(String text, ChatFormatting... styles) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(styles);
	}

	/** Same as {@link #label(String, ChatFormatting...)} but tinted with a team color. */
	public static MutableComponent label(String text, TeamColor color) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withColor(color.textColor());
	}

	public static ItemStack plain(Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, name);
		return stack;
	}

	public static ItemStack of(Item item, Component name, Component... lore) {
		return of(item, name, Arrays.asList(lore));
	}

	public static ItemStack of(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, name);
		if (!lore.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(new ArrayList<>(lore)));
		}
		return stack;
	}

	/** A green/red switch used for every boolean option. */
	public static ItemStack toggle(String title, boolean on, String description) {
		List<Component> lore = new ArrayList<>();
		lore.add(label(description, ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Component.empty()
			.append(label("State: ", ChatFormatting.GRAY))
			.append(label(on ? "ON" : "OFF", on ? ChatFormatting.GREEN : ChatFormatting.RED)));
		lore.add(label("Click to toggle", ChatFormatting.DARK_GRAY));
		return of(on ? Items.DYE.pick(DyeColor.LIME) : Items.DYE.pick(DyeColor.GRAY),
			label(title, on ? ChatFormatting.GREEN : ChatFormatting.RED),
			lore);
	}

	/** A cycling option (assignment mode, notification mode, ...). */
	public static ItemStack cycle(Item item, String title, String current, String description) {
		return of(item, label(title, ChatFormatting.AQUA),
			label(description, ChatFormatting.GRAY),
			Component.empty(),
			Component.empty()
				.append(label("Current: ", ChatFormatting.GRAY))
				.append(label(current, ChatFormatting.WHITE)),
			label("Click to cycle", ChatFormatting.DARK_GRAY));
	}

	public static ItemStack back() {
		return of(Items.ARROW, label("Back", ChatFormatting.YELLOW));
	}

	public static ItemStack close() {
		return of(Items.BARRIER, label("Close", ChatFormatting.RED));
	}
}
