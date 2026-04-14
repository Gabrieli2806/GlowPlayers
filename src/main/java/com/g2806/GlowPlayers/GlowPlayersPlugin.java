package com.g2806.glowplayers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GlowPlayersPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final String WHITE_TEAM_NAME = "gpwhite";
    private static final String TEAM_PREFIX = "gp";
    private static final String PLAYER_DATA_ROOT = "players";

    private final ChatColor[] colors = {
            ChatColor.BLUE,
            ChatColor.RED,
            ChatColor.GREEN,
            ChatColor.YELLOW,
            ChatColor.AQUA,
            ChatColor.GOLD,
            ChatColor.LIGHT_PURPLE,
            ChatColor.DARK_PURPLE,
            ChatColor.DARK_BLUE,
            ChatColor.DARK_GREEN,
            ChatColor.DARK_AQUA,
            ChatColor.DARK_RED,
            ChatColor.GRAY,
            ChatColor.DARK_GRAY,
            ChatColor.BLACK
    };

    private final List<UUID> onlineOrder = new ArrayList<>();

    private Scoreboard scoreboard;
    private File playerDataFile;
    private FileConfiguration playerData;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlayerData();

        if (Bukkit.getScoreboardManager() == null) {
            throw new IllegalStateException("Scoreboard manager is unavailable.");
        }

        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        registerTeams();
        registerCommand();
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!onlineOrder.contains(playerId)) {
                onlineOrder.add(playerId);
            }
            applyGlowState(player, isGlowEnabled(playerId));
        }

        updatePlayerColors();
        getLogger().info(ChatColor.stripColor(getMessage("enabled", "GlowPlayers has been enabled.")));
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearTeamEntries(player);
            player.setGlowing(false);
        }

        Team whiteTeam = scoreboard.getTeam(WHITE_TEAM_NAME);
        if (whiteTeam != null) {
            whiteTeam.unregister();
        }

        for (int index = 0; index < colors.length; index++) {
            Team team = scoreboard.getTeam(teamName(index));
            if (team != null) {
                team.unregister();
            }
        }

        savePlayerData();
        getLogger().info(ChatColor.stripColor(getMessage("disabled", "GlowPlayers has been disabled.")));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        onlineOrder.remove(playerId);
        onlineOrder.add(playerId);
        applyGlowState(player, isGlowEnabled(playerId));
        updatePlayerColors();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        onlineOrder.remove(player.getUniqueId());
        clearTeamEntries(player);
        updatePlayerColors();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            applyGlowState(player, isGlowEnabled(playerId));
            updatePlayerColors();
        }, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("player-only", "&cOnly players can use this command."));
            return true;
        }

        String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        UUID playerId = player.getUniqueId();
        boolean currentState = isGlowEnabled(playerId);

        switch (action) {
            case "on" -> {
                setGlowEnabled(playerId, true);
                applyGlowState(player, true);
                player.sendMessage(getMessage("glow-on", "&aYour glow is now enabled."));
            }
            case "off" -> {
                setGlowEnabled(playerId, false);
                applyGlowState(player, false);
                player.sendMessage(getMessage("glow-off", "&eYour glow is now disabled."));
            }
            case "toggle" -> {
                boolean newState = !currentState;
                setGlowEnabled(playerId, newState);
                applyGlowState(player, newState);
                player.sendMessage(getMessage(newState ? "glow-on" : "glow-off", newState ? "&aYour glow is now enabled." : "&eYour glow is now disabled."));
            }
            case "status" -> {
                player.sendMessage(getMessage(currentState ? "glow-status-on" : "glow-status-off", currentState ? "&aYour glow is currently enabled." : "&eYour glow is currently disabled."));
                return true;
            }
            default -> {
                player.sendMessage(getMessage("usage", "&eUsage: /glow [on|off|toggle|status]"));
                return true;
            }
        }

        savePlayerData();
        updatePlayerColors();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();

        for (String option : List.of("on", "off", "toggle", "status")) {
            if (option.startsWith(partial)) {
                completions.add(option);
            }
        }

        return completions;
    }

    private void registerCommand() {
        PluginCommand glowCommand = getCommand("glow");
        if (glowCommand == null) {
            getLogger().warning("The /glow command is missing from plugin.yml.");
            return;
        }

        glowCommand.setExecutor(this);
        glowCommand.setTabCompleter(this);
    }

    private void registerTeams() {
        Team whiteTeam = scoreboard.getTeam(WHITE_TEAM_NAME);
        if (whiteTeam == null) {
            whiteTeam = scoreboard.registerNewTeam(WHITE_TEAM_NAME);
        }
        whiteTeam.setColor(ChatColor.WHITE);

        for (int index = 0; index < colors.length; index++) {
            Team team = scoreboard.getTeam(teamName(index));
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName(index));
            }
            team.setColor(colors[index]);
        }
    }

    private void loadPlayerData() {
        playerDataFile = new File(getDataFolder(), "player-data.yml");
        if (!playerDataFile.exists()) {
            File dataFolder = playerDataFile.getParentFile();
            if (dataFolder != null && !dataFolder.exists() && !dataFolder.mkdirs()) {
                getLogger().warning("Could not create the plugin data folder.");
            }

            try {
                if (!playerDataFile.createNewFile()) {
                    getLogger().warning("Could not create player-data.yml.");
                }
            } catch (IOException exception) {
                getLogger().severe("Could not create player-data.yml: " + exception.getMessage());
            }
        }

        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void savePlayerData() {
        if (playerData == null || playerDataFile == null) {
            return;
        }

        try {
            playerData.save(playerDataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save player-data.yml: " + exception.getMessage());
        }
    }

    private boolean isGlowEnabled(UUID playerId) {
        return playerData.getBoolean(playerDataPath(playerId), getConfig().getBoolean("default-glowing", true));
    }

    private void setGlowEnabled(UUID playerId, boolean enabled) {
        playerData.set(playerDataPath(playerId), enabled);
    }

    private String playerDataPath(UUID playerId) {
        return PLAYER_DATA_ROOT + "." + playerId;
    }

    private void applyGlowState(Player player, boolean enabled) {
        player.setGlowing(enabled);
        if (!enabled) {
            clearTeamEntries(player);
        }
    }

    private void updatePlayerColors() {
        onlineOrder.removeIf(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            return player == null || !player.isOnline();
        });

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            clearTeamEntries(onlinePlayer);
        }

        int position = 0;
        for (UUID playerId : onlineOrder) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            if (!isGlowEnabled(playerId)) {
                player.setGlowing(false);
                continue;
            }

            player.setGlowing(true);
            assignTeam(player, position);
            position++;
        }
    }

    private void assignTeam(Player player, int position) {
        Team team = position == 0 ? scoreboard.getTeam(WHITE_TEAM_NAME) : scoreboard.getTeam(teamName((position - 1) % colors.length));
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    private void clearTeamEntries(Player player) {
        Team whiteTeam = scoreboard.getTeam(WHITE_TEAM_NAME);
        if (whiteTeam != null && whiteTeam.hasEntry(player.getName())) {
            whiteTeam.removeEntry(player.getName());
        }

        for (int index = 0; index < colors.length; index++) {
            Team team = scoreboard.getTeam(teamName(index));
            if (team != null && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    private String teamName(int index) {
        return TEAM_PREFIX + index;
    }

    private String getMessage(String key, String fallback) {
        String language = getConfig().getString("language", "en");
        String path = "messages." + language + "." + key;
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString(path, fallback));
    }
}