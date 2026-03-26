package com.g2806.glowplayers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GlowPlayers implements ModInitializer {
	public static final String MOD_ID = "glowplayers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Locator Bar color palette — ordered for maximum visual distinction
	private static final ChatFormatting[] GLOW_COLORS = {
		ChatFormatting.WHITE,          // Player 1
		ChatFormatting.BLUE,           // Player 2
		ChatFormatting.RED,            // Player 3
		ChatFormatting.GREEN,          // Player 4
		ChatFormatting.YELLOW,         // Player 5
		ChatFormatting.AQUA,           // Player 6
		ChatFormatting.LIGHT_PURPLE,   // Player 7
		ChatFormatting.GOLD,           // Player 8
		ChatFormatting.DARK_PURPLE,    // Player 9
		ChatFormatting.DARK_AQUA,      // Player 10
		ChatFormatting.DARK_GREEN,     // Player 11
		ChatFormatting.DARK_RED,       // Player 12
		ChatFormatting.DARK_BLUE,      // Player 13
		ChatFormatting.GRAY,           // Player 14
		ChatFormatting.DARK_GRAY,      // Player 15
		ChatFormatting.BLACK,          // Player 16
	};

	private static final String TEAM_PREFIX = "gp_glow_";

	// Track current color per player to avoid redundant messages
	private final Map<UUID, ChatFormatting> currentColors = new HashMap<>();
	private int tickCounter = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("GlowPlayers loaded — all players will glow with unique colors!");

		// Player joins server
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			server.execute(() -> {
				applyGlowing(handler.getPlayer());
				updateAllPlayerColors(server);
			});
		});

		// Player leaves server
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			currentColors.remove(player.getUUID());
			server.execute(() -> {
				removePlayerFromGlowTeam(server.getScoreboard(), player);
				updateAllPlayerColors(server);
			});
		});

		// Player respawns after death
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer server = newPlayer.level().getServer();
			server.execute(() -> {
				applyGlowing(newPlayer);
				updateAllPlayerColors(server);
			});
		});

		// Periodic check — reapply glow if removed (e.g. milk bucket)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (tickCounter >= 100) { // Every 5 seconds
				tickCounter = 0;
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (!player.hasEffect(MobEffects.GLOWING)) {
						applyGlowing(player);
					}
				}
			}
		});
	}

	private void applyGlowing(ServerPlayer player) {
		player.removeEffect(MobEffects.GLOWING);
		player.addEffect(new MobEffectInstance(
			MobEffects.GLOWING,
			MobEffectInstance.INFINITE_DURATION,
			0,      // amplifier
			false,  // ambient
			false,  // show particles
			false   // show icon
		));
	}

	private void updateAllPlayerColors(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		List<ServerPlayer> players = server.getPlayerList().getPlayers();

		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);
			ChatFormatting color = GLOW_COLORS[i % GLOW_COLORS.length];
			assignPlayerColor(scoreboard, player, color, i);
		}

		// Cleanup unused teams
		for (int i = players.size(); i < GLOW_COLORS.length; i++) {
			PlayerTeam team = scoreboard.getPlayerTeam(TEAM_PREFIX + i);
			if (team != null && team.getPlayers().isEmpty()) {
				scoreboard.removePlayerTeam(team);
			}
		}
	}

	private void assignPlayerColor(Scoreboard scoreboard, ServerPlayer player, ChatFormatting color, int index) {
		removePlayerFromGlowTeam(scoreboard, player);

		String teamName = TEAM_PREFIX + index;
		PlayerTeam team = scoreboard.getPlayerTeam(teamName);
		if (team == null) {
			team = scoreboard.addPlayerTeam(teamName);
		}
		team.setColor(color);

		scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

		// Only notify if color changed
		ChatFormatting previousColor = currentColors.get(player.getUUID());
		if (previousColor != color) {
			currentColors.put(player.getUUID(), color);
			player.sendSystemMessage(
				Component.empty()
					.append(Component.literal("Glow color: ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(formatColorName(color)).withStyle(color)),
				true // action bar overlay
			);
		}
	}

	private void removePlayerFromGlowTeam(Scoreboard scoreboard, ServerPlayer player) {
		String playerName = player.getScoreboardName();
		for (int i = 0; i < GLOW_COLORS.length; i++) {
			PlayerTeam team = scoreboard.getPlayerTeam(TEAM_PREFIX + i);
			if (team != null && team.getPlayers().contains(playerName)) {
				scoreboard.removePlayerFromTeam(playerName, team);
				break;
			}
		}
	}

	private String formatColorName(ChatFormatting color) {
		String[] parts = color.getName().split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0)));
			sb.append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}
}