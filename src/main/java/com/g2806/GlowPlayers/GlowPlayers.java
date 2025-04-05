package com.g2806.GlowPlayers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlowPlayers extends JavaPlugin implements Listener {

    // Mapa para almacenar el estado de brillo de cada jugador
    private final Map<UUID, Boolean> glowingStates = new HashMap<>();
    // Mapa para rastrear el orden de unión de los jugadores
    private final Map<UUID, Integer> joinOrder = new HashMap<>();
    private Scoreboard scoreboard;
    private FileConfiguration config;

    // Colores disponibles en Minecraft (15 colores, blanco reservado para el primero)
    private final ChatColor[] colors = {
            ChatColor.BLUE,      // Jugador 2
            ChatColor.RED,       // Jugador 3
            ChatColor.GREEN,     // Jugador 4
            ChatColor.YELLOW,    // Jugador 5
            ChatColor.AQUA,      // Jugador 6
            ChatColor.GOLD,      // Jugador 7
            ChatColor.LIGHT_PURPLE, // Jugador 8
            ChatColor.DARK_PURPLE,  // Jugador 9
            ChatColor.DARK_BLUE,    // Jugador 10
            ChatColor.DARK_GREEN,   // Jugador 11
            ChatColor.DARK_AQUA,    // Jugador 12
            ChatColor.DARK_RED,     // Jugador 13
            ChatColor.GRAY,         // Jugador 14
            ChatColor.DARK_GRAY,    // Jugador 15
            ChatColor.BLACK         // Jugador 16
    };

    @Override
    public void onEnable() {
        getLogger().info("GlowPlayer has been enabled!");
        // Cargar configuración
        saveDefaultConfig();
        config = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        // Crear equipo especial para el primer jugador (blanco)
        Team whiteTeam = scoreboard.getTeam("GlowTeamWhite");
        if (whiteTeam == null) {
            whiteTeam = scoreboard.registerNewTeam("GlowTeamWhite");
            whiteTeam.setColor(ChatColor.WHITE);
        }
        // Crear equipos para los demás colores
        for (int i = 0; i < colors.length; i++) {
            String teamName = "GlowTeam" + i;
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.setColor(colors[i]);
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("GlowPlayer has been disabled!");
        // Limpiar equipos al desactivar
        Team whiteTeam = scoreboard.getTeam("GlowTeamWhite");
        if (whiteTeam != null) {
            whiteTeam.unregister();
        }
        for (int i = 0; i < colors.length; i++) {
            Team team = scoreboard.getTeam("GlowTeam" + i);
            if (team != null) {
                team.unregister();
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Asignar brillo automáticamente al unirse
        glowingStates.put(uuid, true);
        player.setGlowing(true);

        // Actualizar el orden y asignar color
        updatePlayerColors();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Remover el estado de brillo y actualizar colores al salir
        glowingStates.remove(uuid);
        joinOrder.remove(uuid);
        updatePlayerColors();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Restaurar brillo y color al reaparecer
        boolean glowing = glowingStates.getOrDefault(uuid, true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.setGlowing(glowing);
            updatePlayerColors();
        }, 1L);
    }

    // Método para actualizar los colores de todos los jugadores conectados
    private void updatePlayerColors() {
        Player[] onlinePlayers = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        joinOrder.clear(); // Limpiar el orden anterior

        for (int i = 0; i < onlinePlayers.length; i++) {
            Player player = onlinePlayers[i];
            UUID uuid = player.getUniqueId();
            joinOrder.put(uuid, i + 1); // Asignar posición (1-based)
            assignTeam(player, i); // Asignar equipo según posición
        }
    }

    // Método para asignar equipo y color según la posición
    private void assignTeam(Player player, int position) {
        // Remover al jugador de cualquier equipo previo
        Team whiteTeam = scoreboard.getTeam("GlowTeamWhite");
        if (whiteTeam != null && whiteTeam.hasEntry(player.getName())) {
            whiteTeam.removeEntry(player.getName());
        }
        for (int i = 0; i < colors.length; i++) {
            Team oldTeam = scoreboard.getTeam("GlowTeam" + i);
            if (oldTeam != null && oldTeam.hasEntry(player.getName())) {
                oldTeam.removeEntry(player.getName());
            }
        }

        // Obtener idioma desde config.yml (inglés por defecto)
        String language = config.getString("language", "en");

        // Asignar al equipo según la posición
        if (position == 0) { // Primer jugador
            Team team = scoreboard.getTeam("GlowTeamWhite");
            if (team != null) {
                team.addEntry(player.getName());
                String message = language.equals("es") ?
                        ChatColor.GREEN + "Te ha sido asignado el color " + ChatColor.WHITE + "BLANCO" + ChatColor.GREEN + " (primer jugador)." :
                        ChatColor.GREEN + "You have been assigned the color " + ChatColor.WHITE + "WHITE" + ChatColor.GREEN + " (first player).";
                player.sendMessage(message);
            }
        } else { // Otros jugadores
            int colorIndex = (position - 1) % colors.length; // Ajustar índice para empezar desde el segundo
            Team team = scoreboard.getTeam("GlowTeam" + colorIndex);
            if (team != null) {
                team.addEntry(player.getName());
                String colorName = team.getColor().name();
                String message = language.equals("es") ?
                        ChatColor.GREEN + "Te ha sido asignado el color " + colors[colorIndex] + colorName :
                        ChatColor.GREEN + "You have been assigned the color " + colors[colorIndex] + colorName;
                player.sendMessage(message);
            }
        }
    }
}