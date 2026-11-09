package com.g2806.glowplayers.gui;

import com.g2806.glowplayers.config.GlowPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Small helpers to build the display-only items the menus are made of. */
public final class GuiItems {
	/** Background of the content area. */
	public static final ItemStack FILLER = plain(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), Component.empty());
	/** Top and bottom bars, so the content area reads as a framed panel. */
	public static final ItemStack BORDER = plain(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK), Component.empty());

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

	/** "Key: value" lore line. */
	public static MutableComponent field(String key, MutableComponent value) {
		return Component.empty().append(label(key + ": ", ChatFormatting.GRAY)).append(value);
	}

	public static MutableComponent field(String key, String value, ChatFormatting... styles) {
		return field(key, label(value, styles.length == 0 ? new ChatFormatting[] {ChatFormatting.WHITE} : styles));
	}

	public static MutableComponent hint(String text) {
		return label(text, ChatFormatting.DARK_GRAY);
	}

	public static MutableComponent onOff(boolean on) {
		return label(on ? "ON" : "OFF", ChatFormatting.BOLD, on ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	/** Decoration only: no tooltip at all when hovered. */
	public static ItemStack plain(Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, name);
		stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
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

	public static ItemStack glint(ItemStack stack, boolean glint) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
		return stack;
	}

	/**
	 * The icon half of an on/off switch: the feature's own item, shining while it is on.
	 * The matching {@link #status} pane sits right below it.
	 */
	public static ItemStack feature(Item item, String title, boolean on, String... description) {
		List<Component> lore = new ArrayList<>();
		for (String line : description) {
			lore.add(label(line, ChatFormatting.GRAY));
		}
		lore.add(Component.empty());
		lore.add(field("State", onOff(on)));
		lore.add(hint("Click to toggle"));
		return glint(of(item, label(title, ChatFormatting.BOLD, on ? ChatFormatting.GREEN : ChatFormatting.RED), lore), on);
	}

	/** Green or red pane shown under a {@link #feature} icon. */
	public static ItemStack status(boolean on) {
		return of(Items.STAINED_GLASS_PANE.pick(on ? DyeColor.LIME : DyeColor.RED),
			on ? label("Enabled", ChatFormatting.BOLD, ChatFormatting.GREEN) : label("Disabled", ChatFormatting.BOLD, ChatFormatting.RED),
			hint("Click to toggle"));
	}

	/** An option that cycles through a few values; the current one is highlighted. */
	public static ItemStack choice(Item item, String title, String description, List<String> values, int current) {
		List<Component> lore = new ArrayList<>();
		lore.add(label(description, ChatFormatting.GRAY));
		lore.add(Component.empty());
		for (int i = 0; i < values.size(); i++) {
			lore.add(i == current
				? label(" ▶ " + values.get(i), ChatFormatting.AQUA)
				: label("    " + values.get(i), ChatFormatting.DARK_GRAY));
		}
		lore.add(Component.empty());
		lore.add(hint("Click to change"));
		return of(item, label(title, ChatFormatting.BOLD, ChatFormatting.AQUA), lore);
	}

	public static ItemStack colorIcon(TeamColor color, Component name, List<Component> lore) {
		return of(GlowPalette.icon(color), name, lore);
	}

	public static ItemStack head(UUID uuid, String name, Component title, List<Component> lore) {
		ItemStack stack = of(Items.PLAYER_HEAD, title, lore);
		stack.set(DataComponents.PROFILE, name == null || name.isEmpty()
			? ResolvableProfile.createUnresolved(uuid)
			: ResolvableProfile.createUnresolved(name));
		return stack;
	}

	public static ItemStack back() {
		return of(Items.ARROW, label("Back", ChatFormatting.YELLOW));
	}

	public static ItemStack close() {
		return of(Items.BARRIER, label("Close", ChatFormatting.RED));
	}
}
