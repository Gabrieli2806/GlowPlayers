package com.g2806.glowplayers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GlowPlayers implements ModInitializer {
	public static final String MOD_ID = "glowplayers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String TEAM_PREFIX = "gp_glow_";
	private static final String CONFIG_DIRECTORY = MOD_ID;
	private static final String CONFIG_FILE_NAME = "preferences.json";
	private static final int GLOW_REFRESH_TICKS = 100;

	private static final ChatFormatting[] GLOW_COLORS = {
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
	};

	private final List<UUID> joinOrder = new ArrayList<>();
	private final Map<UUID, ChatFormatting> currentColors = new HashMap<>();
	private final Map<UUID, Boolean> playerPreferences = new HashMap<>();

	private Path preferencesFile;
	private boolean defaultGlowing = true;
	private int tickCounter = 0;

	@Override
	public void onInitialize() {
		preferencesFile = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIRECTORY).resolve(CONFIG_FILE_NAME);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("glow")
				.executes(context -> sendGlowStatus(context.getSource()))
				.then(Commands.literal("on").executes(context -> setGlow(context.getSource(), true)))
				.then(Commands.literal("off").executes(context -> setGlow(context.getSource(), false)))
				.then(Commands.literal("toggle").executes(context -> toggleGlow(context.getSource())))
				.then(Commands.literal("status").executes(context -> sendGlowStatus(context.getSource())))
		));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			loadPreferences();
			joinOrder.clear();
			currentColors.clear();
			tickCounter = 0;
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			savePreferences();
			joinOrder.clear();
			currentColors.clear();
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> handleJoin(server, handler.getPlayer())));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			server.execute(() -> handleDisconnect(server, player));
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer server = newPlayer.level().getServer();
			if (server == null) {
				return;
			}

			server.execute(() -> {
				if (isGlowEnabled(newPlayer.getUUID())) {
					applyGlowing(newPlayer);
				}
				updateAllPlayerColors(server);
			});
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (tickCounter < GLOW_REFRESH_TICKS) {
				return;
			}

			tickCounter = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (isGlowEnabled(player.getUUID()) && !player.hasEffect(MobEffects.GLOWING)) {
					applyGlowing(player);
				}
			}
		});

		LOGGER.info("GlowPlayers loaded.");
	}

	private void handleJoin(MinecraftServer server, ServerPlayer player) {
		UUID playerId = player.getUUID();
		joinOrder.remove(playerId);
		joinOrder.add(playerId);

		if (isGlowEnabled(playerId)) {
			applyGlowing(player);
		}

		updateAllPlayerColors(server);
	}

	private void handleDisconnect(MinecraftServer server, ServerPlayer player) {
		UUID playerId = player.getUUID();
		joinOrder.remove(playerId);
		currentColors.remove(playerId);
		removePlayerFromGlowTeams(server.getScoreboard(), player);
		updateAllPlayerColors(server);
	}

	private int setGlow(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		setGlowEnabled(player.getUUID(), enabled);
		savePreferences();

		if (enabled) {
			applyGlowing(player);
		} else {
			player.removeEffect(MobEffects.GLOWING);
		}

		updateAllPlayerColors(source.getServer());
		source.sendSuccess(() -> Component.literal(enabled ? "Glow enabled." : "Glow disabled.").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
		return Command.SINGLE_SUCCESS;
	}

	private int toggleGlow(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		return setGlow(source, !isGlowEnabled(player.getUUID()));
	}

	private int sendGlowStatus(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean enabled = isGlowEnabled(player.getUUID());
		source.sendSuccess(() -> Component.literal(enabled ? "Your glow is enabled." : "Your glow is disabled.").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
		return Command.SINGLE_SUCCESS;
	}

	private void updateAllPlayerColors(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();

		joinOrder.removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
		for (ServerPlayer player : onlinePlayers) {
			UUID playerId = player.getUUID();
			if (!joinOrder.contains(playerId)) {
				joinOrder.add(playerId);
			}
			removePlayerFromGlowTeams(scoreboard, player);
		}

		int colorPosition = 0;
		for (UUID playerId : joinOrder) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				continue;
			}

			if (!isGlowEnabled(playerId)) {
				currentColors.remove(playerId);
				continue;
			}

			int colorIndex = colorPosition % GLOW_COLORS.length;
			ChatFormatting color = GLOW_COLORS[colorIndex];
			assignPlayerColor(scoreboard, player, color, colorIndex);
			colorPosition++;
		}
	}

	private void assignPlayerColor(Scoreboard scoreboard, ServerPlayer player, ChatFormatting color, int colorIndex) {
		PlayerTeam team = scoreboard.getPlayerTeam(teamName(colorIndex));
		if (team == null) {
			team = scoreboard.addPlayerTeam(teamName(colorIndex));
		}

		team.setColor(color);
		scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

		ChatFormatting previousColor = currentColors.get(player.getUUID());
		if (previousColor != color) {
			currentColors.put(player.getUUID(), color);
			player.sendSystemMessage(
				Component.literal("Glow color: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(formatColorName(color)).withStyle(color)),
				true
			);
		}
	}

	private void removePlayerFromGlowTeams(Scoreboard scoreboard, ServerPlayer player) {
		String playerName = player.getScoreboardName();
		for (int index = 0; index < GLOW_COLORS.length; index++) {
			PlayerTeam team = scoreboard.getPlayerTeam(teamName(index));
			if (team != null && team.getPlayers().contains(playerName)) {
				scoreboard.removePlayerFromTeam(playerName, team);
			}
		}
	}

	private void applyGlowing(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(
			MobEffects.GLOWING,
			MobEffectInstance.INFINITE_DURATION,
			0,
			false,
			false,
			false
		));
	}

	private boolean isGlowEnabled(UUID playerId) {
		return playerPreferences.getOrDefault(playerId, defaultGlowing);
	}

	private void setGlowEnabled(UUID playerId, boolean enabled) {
		if (enabled == defaultGlowing) {
			playerPreferences.remove(playerId);
			return;
		}

		playerPreferences.put(playerId, enabled);
	}

	private void loadPreferences() {
		playerPreferences.clear();
		defaultGlowing = true;

		try {
			Path configDirectory = preferencesFile.getParent();
			if (configDirectory != null) {
				Files.createDirectories(configDirectory);
			}

			if (!Files.exists(preferencesFile)) {
				savePreferences();
				return;
			}

			try (Reader reader = Files.newBufferedReader(preferencesFile, StandardCharsets.UTF_8)) {
				JsonElement rootElement = JsonParser.parseReader(reader);
				if (!rootElement.isJsonObject()) {
					LOGGER.warn("GlowPlayers preferences file is not a JSON object. Resetting to defaults.");
					savePreferences();
					return;
				}

				JsonObject root = rootElement.getAsJsonObject();
				if (root.has("defaultGlowing") && root.get("defaultGlowing").isJsonPrimitive()) {
					defaultGlowing = root.get("defaultGlowing").getAsBoolean();
				}

				JsonObject players = root.has("players") && root.get("players").isJsonObject()
					? root.getAsJsonObject("players")
					: new JsonObject();

				for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
					try {
						playerPreferences.put(UUID.fromString(entry.getKey()), entry.getValue().getAsBoolean());
					} catch (IllegalArgumentException exception) {
						LOGGER.warn("Ignoring invalid player UUID in GlowPlayers preferences: {}", entry.getKey());
					}
				}
			}
		} catch (IOException exception) {
			LOGGER.error("Failed to load GlowPlayers preferences.", exception);
		}
	}

	private void savePreferences() {
		try {
			Path configDirectory = preferencesFile.getParent();
			if (configDirectory != null) {
				Files.createDirectories(configDirectory);
			}

			JsonObject root = new JsonObject();
			root.addProperty("defaultGlowing", defaultGlowing);

			JsonObject players = new JsonObject();
			List<UUID> sortedPlayerIds = new ArrayList<>(playerPreferences.keySet());
			sortedPlayerIds.sort(Comparator.comparing(UUID::toString));
			for (UUID playerId : sortedPlayerIds) {
				players.addProperty(playerId.toString(), playerPreferences.get(playerId));
			}

			root.add("players", players);

			try (Writer writer = Files.newBufferedWriter(preferencesFile, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException exception) {
			LOGGER.error("Failed to save GlowPlayers preferences.", exception);
		}
	}

	private String teamName(int colorIndex) {
		return TEAM_PREFIX + colorIndex;
	}

	private String formatColorName(ChatFormatting color) {
		String[] parts = color.getName().split("_");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (builder.length() > 0) {
				builder.append(' ');
			}

			builder.append(Character.toUpperCase(part.charAt(0)));
			builder.append(part.substring(1).toLowerCase());
		}

		return builder.toString();
	}
}