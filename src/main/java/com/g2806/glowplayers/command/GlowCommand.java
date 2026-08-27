package com.g2806.glowplayers.command;

import com.g2806.glowplayers.GlowPlayers;
import com.g2806.glowplayers.config.GlowConfig;
import com.g2806.glowplayers.config.GlowPalette;
import com.g2806.glowplayers.gui.MainGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TeamColorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/** {@code /glowplayers}, aliased as {@code /glow}. */
public final class GlowCommand {
	private GlowCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(build("glowplayers"));
		dispatcher.register(Commands.literal("glow")
			.executes(context -> openGui(context.getSource()))
			.redirect(root));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
		return Commands.literal(name)
			.executes(context -> openGui(context.getSource()))
			.then(Commands.literal("gui")
				.executes(context -> openGui(context.getSource())))
			.then(Commands.literal("info")
				.executes(context -> info(context.getSource())))
			.then(Commands.literal("reload")
				.executes(context -> reload(context.getSource())))
			.then(Commands.literal("refresh")
				.executes(context -> refresh(context.getSource())))
			.then(Commands.literal("color")
				.then(Commands.argument("color", TeamColorArgument.teamColor())
					.executes(context -> setOwnColor(context.getSource(), TeamColorArgument.getTeamColor(context, "color")))))
			.then(Commands.literal("auto")
				.executes(context -> clearOwnColor(context.getSource())))
			.then(Commands.literal("toggle")
				.executes(context -> toggleSelf(context.getSource())))
			.then(Commands.literal("set")
				.then(Commands.argument("target", EntityArgument.player())
					.then(Commands.argument("color", TeamColorArgument.teamColor())
						.executes(context -> setColor(context.getSource(),
							EntityArgument.getPlayer(context, "target"),
							TeamColorArgument.getTeamColor(context, "color"))))))
			.then(Commands.literal("exclude")
				.then(Commands.argument("target", EntityArgument.player())
					.executes(context -> toggleExclude(context.getSource(),
						EntityArgument.getPlayer(context, "target")))));
	}

	private static boolean isAdmin(CommandSourceStack source) {
		int level = GlowPlayers.configs().get().adminPermissionLevel;
		return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
	}

	private static boolean denyNonAdmin(CommandSourceStack source) {
		if (isAdmin(source)) {
			return false;
		}
		source.sendFailure(Component.literal("You are not allowed to do that."));
		return true;
	}

	private static int openGui(CommandSourceStack source) {
		if (denyNonAdmin(source)) {
			return 0;
		}
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("The GUI can only be opened by a player."));
			return 0;
		}
		new MainGui(player).open();
		return 1;
	}

	private static int info(CommandSourceStack source) {
		GlowConfig config = GlowPlayers.configs().get();
		source.sendSuccess(() -> Component.empty()
			.append(Component.literal("GlowPlayers").withStyle(ChatFormatting.DARK_AQUA))
			.append(Component.literal(" · enabled=" + config.enabled
				+ " glow=" + config.glowEffect
				+ " locatorBar=" + config.locatorBarColors
				+ " mode=" + config.assignMode()
				+ " palette=" + config.palette.size()).withStyle(ChatFormatting.GRAY)), false);

		ServerPlayer player = source.getPlayer();
		if (player != null) {
			TeamColor color = GlowPlayers.manager().colorOf(player.getUUID());
			source.sendSuccess(() -> Component.empty()
				.append(Component.literal("Your color: ").withStyle(ChatFormatting.GRAY))
				.append(color == null
					? Component.literal("none").withStyle(ChatFormatting.DARK_GRAY)
					: Component.literal(GlowPalette.displayName(color)).withColor(color.textColor())), false);
		}
		return 1;
	}

	private static int reload(CommandSourceStack source) {
		if (denyNonAdmin(source)) {
			return 0;
		}
		GlowPlayers.configs().load();
		GlowPlayers.manager().refreshAll(source.getServer());
		source.sendSuccess(() -> Component.literal("GlowPlayers config reloaded.").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int refresh(CommandSourceStack source) {
		if (denyNonAdmin(source)) {
			return 0;
		}
		GlowPlayers.manager().refreshAll(source.getServer());
		source.sendSuccess(() -> Component.literal("Glow colors refreshed.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int setOwnColor(CommandSourceStack source, TeamColor color) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only a player can run this."));
			return 0;
		}
		if (!GlowPlayers.configs().get().allowSelfColorPick && denyNonAdmin(source)) {
			return 0;
		}
		return setColor(source, player, color);
	}

	private static int clearOwnColor(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only a player can run this."));
			return 0;
		}
		if (!GlowPlayers.configs().get().allowSelfColorPick && denyNonAdmin(source)) {
			return 0;
		}
		GlowConfig config = GlowPlayers.configs().get();
		config.playerColors.remove(player.getStringUUID());
		config.assignedColors.remove(player.getStringUUID());
		GlowPlayers.applyAndSave(source.getServer());
		source.sendSuccess(() -> Component.literal("Back to the automatic color.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int setColor(CommandSourceStack source, ServerPlayer target, TeamColor color) {
		if (source.getPlayer() != target && denyNonAdmin(source)) {
			return 0;
		}
		if (GlowPalette.byName(color.getSerializedName()) == null) {
			source.sendFailure(Component.literal(color.getSerializedName() + " is not a usable glow color."));
			return 0;
		}
		GlowPlayers.configs().get().playerColors.put(target.getStringUUID(), color.getSerializedName());
		GlowPlayers.applyAndSave(source.getServer());
		source.sendSuccess(() -> Component.empty()
			.append(Component.literal(target.getScoreboardName() + " is now ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(GlowPalette.displayName(color)).withColor(color.textColor())), false);
		return 1;
	}

	private static int toggleSelf(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only a player can run this."));
			return 0;
		}
		if (!GlowPlayers.configs().get().allowSelfColorPick && denyNonAdmin(source)) {
			return 0;
		}
		return toggleExclude(source, player);
	}

	private static int toggleExclude(CommandSourceStack source, ServerPlayer target) {
		if (source.getPlayer() != target && denyNonAdmin(source)) {
			return 0;
		}
		GlowConfig config = GlowPlayers.configs().get();
		String key = target.getStringUUID();
		boolean nowExcluded;
		if (config.excludedPlayers.remove(key)) {
			nowExcluded = false;
		} else {
			config.excludedPlayers.add(key);
			nowExcluded = true;
		}
		MinecraftServer server = source.getServer();
		GlowPlayers.applyAndSave(server);
		source.sendSuccess(() -> Component.literal(target.getScoreboardName()
			+ (nowExcluded ? " no longer glows." : " glows again.")).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}
}
