package com.g2806.glowplayers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
	private static final Formatting[] GLOW_COLORS = {
		Formatting.WHITE,          // Player 1
		Formatting.BLUE,           // Player 2
		Formatting.RED,            // Player 3
		Formatting.GREEN,          // Player 4
		Formatting.YELLOW,         // Player 5
		Formatting.AQUA,           // Player 6
		Formatting.LIGHT_PURPLE,   // Player 7
		Formatting.GOLD,           // Player 8
		Formatting.DARK_PURPLE,    // Player 9
		Formatting.DARK_AQUA,      // Player 10
		Formatting.DARK_GREEN,     // Player 11
		Formatting.DARK_RED,       // Player 12
		Formatting.DARK_BLUE,      // Player 13
		Formatting.GRAY,           // Player 14
		Formatting.DARK_GRAY,      // Player 15
		Formatting.BLACK,          // Player 16
	};

	private static final String TEAM_PREFIX = "gp_glow_";

	// Track current color per player to avoid redundant messages
	private final Map<UUID, Formatting> currentColors = new HashMap<>();
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
			ServerPlayerEntity player = handler.getPlayer();
			currentColors.remove(player.getUuid());
			server.execute(() -> {
				removePlayerFromGlowTeam(server.getScoreboard(), player);
				updateAllPlayerColors(server);
			});
		});

		// Player respawns after death
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer server = newPlayer.getEntityWorld().getServer();
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
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					if (!player.hasStatusEffect(StatusEffects.GLOWING)) {
						applyGlowing(player);
					}
				}
			}
		});
	}

	private void applyGlowing(ServerPlayerEntity player) {
		player.removeStatusEffect(StatusEffects.GLOWING);
		player.addStatusEffect(new StatusEffectInstance(
			StatusEffects.GLOWING,
			StatusEffectInstance.INFINITE,
			0,      // amplifier
			false,  // ambient
			false,  // show particles
			false   // show icon
		));
	}

	private void updateAllPlayerColors(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

		for (int i = 0; i < players.size(); i++) {
			ServerPlayerEntity player = players.get(i);
			Formatting color = GLOW_COLORS[i % GLOW_COLORS.length];
			assignPlayerColor(scoreboard, player, color, i);
		}

		// Cleanup unused teams
		for (int i = players.size(); i < GLOW_COLORS.length; i++) {
			Team team = scoreboard.getTeam(TEAM_PREFIX + i);
			if (team != null && team.getPlayerList().isEmpty()) {
				scoreboard.removeTeam(team);
			}
		}
	}

	private void assignPlayerColor(Scoreboard scoreboard, ServerPlayerEntity player, Formatting color, int index) {
		removePlayerFromGlowTeam(scoreboard, player);

		String teamName = TEAM_PREFIX + index;
		Team team = scoreboard.getTeam(teamName);
		if (team == null) {
			team = scoreboard.addTeam(teamName);
		}
		team.setColor(color);

		scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);

		// Only notify if color changed
		Formatting previousColor = currentColors.get(player.getUuid());
		if (previousColor != color) {
			currentColors.put(player.getUuid(), color);
			player.sendMessage(
				Text.empty()
					.append(Text.literal("Glow color: ").formatted(Formatting.GRAY))
					.append(Text.literal(formatColorName(color)).formatted(color)),
				true // action bar overlay
			);
		}
	}

	private void removePlayerFromGlowTeam(Scoreboard scoreboard, ServerPlayerEntity player) {
		String playerName = player.getNameForScoreboard();
		for (int i = 0; i < GLOW_COLORS.length; i++) {
			Team team = scoreboard.getTeam(TEAM_PREFIX + i);
			if (team != null && team.getPlayerList().contains(playerName)) {
				scoreboard.removeScoreHolderFromTeam(playerName, team);
				break;
			}
		}
	}

	private String formatColorName(Formatting color) {
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