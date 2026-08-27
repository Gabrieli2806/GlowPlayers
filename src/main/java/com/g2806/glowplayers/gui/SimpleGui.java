package com.g2806.glowplayers.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * A read-only chest menu driven entirely by the server, so the mod stays usable by vanilla
 * clients. Every click is swallowed and turned into a callback; nothing can be taken out of the
 * menu and nothing can be put into it.
 */
public abstract class SimpleGui implements MenuProvider {
	protected final ServerPlayer player;
	protected final int rows;
	protected final SimpleContainer container;

	private GuiMenu menu;

	protected SimpleGui(ServerPlayer player, int rows) {
		this.player = player;
		this.rows = rows;
		this.container = new SimpleContainer(rows * 9);
	}

	/** Fills {@link #container} with the current state. Called before opening and on every redraw. */
	protected abstract void draw();

	/**
	 * @param slot  clicked slot, always inside the chest part of the menu
	 * @param input the kind of click the client sent
	 * @param button 0 for left click, 1 for right click
	 */
	protected abstract void onClick(int slot, ContainerInput input, int button);

	public void open() {
		draw();
		player.openMenu(this);
	}

	/** Redraws the menu in place, keeping it open. */
	public void refresh() {
		draw();
		if (menu != null && player.containerMenu == menu) {
			menu.broadcastFullState();
		}
	}

	protected void set(int slot, ItemStack stack) {
		if (slot >= 0 && slot < container.getContainerSize()) {
			container.setItem(slot, stack);
		}
	}

	protected void fillEmpty(ItemStack filler) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).isEmpty()) {
				container.setItem(slot, filler.copy());
			}
		}
	}

	protected void clearSlots() {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			container.setItem(slot, ItemStack.EMPTY);
		}
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
		menu = new GuiMenu(containerId, inventory);
		return menu;
	}

	private MenuType<ChestMenu> menuType() {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	private final class GuiMenu extends ChestMenu {
		private GuiMenu(int containerId, Inventory inventory) {
			super(menuType(), containerId, inventory, container, rows);
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
			if (slotId >= 0 && slotId < rows * 9) {
				onClick(slotId, input, button);
			}
			// The click may have opened another screen, which closed this menu already.
			if (clicker.containerMenu != this) {
				return;
			}
			// Nothing is ever moved, but the client already predicted the move: resync it.
			broadcastFullState();
		}

		@Override
		public ItemStack quickMoveStack(Player clicker, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player clicker) {
			return true;
		}
	}

	@Override
	public abstract Component getDisplayName();
}
