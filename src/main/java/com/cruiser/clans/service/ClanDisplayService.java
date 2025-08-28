package com.cruiser.clans.service;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Service for displaying clan tags in tab and above player heads
 * Uses Scoreboard API for prefix management
 */
public class ClanDisplayService {
    
    private final ClanPlugin plugin;
    private final Scoreboard scoreboard;
    
    public ClanDisplayService(ClanPlugin plugin) {
        this.plugin = plugin;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }
    
    /**
     * Update player display (called on join, clan change etc.)
     */
    public void updatePlayerDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Load player data from database
        plugin.getData().findPlayerByUuid(uuid).thenAccept(optPlayer -> {
            plugin.getData().runSync(() -> {
                try {
                    if (optPlayer.isPresent() && optPlayer.get().isInClan()) {
                        ClanPlayerEntity clanPlayer = optPlayer.get();
                        ClanEntity clan = clanPlayer.getClan();
                        
                        // Проверяем что clan не null (на всякий случай)
                        if (clan != null) {
                            // Add to scoreboard team for tag display
                            addToTeam(player, clan);
                            
                            // Update display name
                            updateDisplayName(player, clan);
                        } else {
                            // Remove from all teams if clan is null somehow
                            removeFromAllTeams(player);
                            resetDisplayName(player);
                        }
                    } else {
                        // Remove from all teams if not in clan
                        removeFromAllTeams(player);
                        resetDisplayName(player);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Error in updatePlayerDisplay", e);
                    // Fallback - remove from teams
                    removeFromAllTeams(player);
                    resetDisplayName(player);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error updating player display for " + player.getName(), ex);
            // Fallback on main thread
            plugin.getData().runSync(() -> {
                removeFromAllTeams(player);
                resetDisplayName(player);
            });
            return null;
        });
    }
    
    /**
     * Add player to scoreboard team for clan tag display
     */
    private void addToTeam(Player player, ClanEntity clan) {
        try {
            String teamName = "clan_" + clan.getId();
            Team team = scoreboard.getTeam(teamName);
            
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                
                // Set prefix with clan tag
                Component prefix = Component.text("[" + clan.getTag() + "] ", getTagColor(clan));
                team.prefix(prefix);
                
                // Team options
                team.setAllowFriendlyFire(true); // Can configure PvP between clans
                team.setCanSeeFriendlyInvisibles(false);
            }
            
            // Add player to team
            team.addPlayer(player);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error adding player to team", e);
        }
    }
    
    /**
     * Remove player from all clan teams
     */
    private void removeFromAllTeams(Player player) {
        try {
            scoreboard.getTeams().stream()
                .filter(team -> team.getName().startsWith("clan_"))
                .forEach(team -> {
                    try {
                        team.removePlayer(player);
                    } catch (Exception e) {
                        // Ignore errors when removing from teams
                    }
                });
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error removing player from teams", e);
        }
    }
    
    /**
     * Update player's display name with clan tag
     */
    private void updateDisplayName(Player player, ClanEntity clan) {
        try {
            if (!plugin.getConfig().getBoolean("display.show-in-tablist", true)) {
                return;
            }
            
            Component displayName = Component.text()
                .append(Component.text("[" + clan.getTag() + "] ", getTagColor(clan)))
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .build();
            
            player.displayName(displayName);
            player.playerListName(displayName);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error updating display name", e);
        }
    }
    
    /**
     * Reset display name to default
     */
    private void resetDisplayName(Player player) {
        try {
            player.displayName(Component.text(player.getName()));
            player.playerListName(Component.text(player.getName()));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error resetting display name", e);
        }
    }
    
    /**
     * Get tag color depending on clan level
     */
    private NamedTextColor getTagColor(ClanEntity clan) {
        try {
            int level = clan.getClanLevel();
            if (level >= 50) return NamedTextColor.DARK_RED;
            if (level >= 40) return NamedTextColor.RED;
            if (level >= 30) return NamedTextColor.GOLD;
            if (level >= 20) return NamedTextColor.YELLOW;
            if (level >= 10) return NamedTextColor.GREEN;
            if (level >= 5) return NamedTextColor.AQUA;
            return NamedTextColor.GRAY;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error getting tag color", e);
            return NamedTextColor.GRAY;
        }
    }
    
    /**
     * Update all clan players
     */
    public void updateClanDisplay(Integer clanId) {
        plugin.getData().getClanMembers(clanId).thenAccept(members -> {
            plugin.getData().runSync(() -> {
                for (ClanPlayerEntity member : members) {
                    try {
                        Player player = Bukkit.getPlayer(member.getUuidAsUUID());
                        if (player != null && player.isOnline()) {
                            updatePlayerDisplay(player);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "Error updating clan member display", e);
                    }
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error updating clan display", ex);
            return null;
        });
    }
    
    /**
     * Remove clan team from scoreboard
     */
    public void removeClanTeam(Integer clanId) {
        try {
            String teamName = "clan_" + clanId;
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error removing clan team", e);
        }
    }
    
    /**
     * Initialize on plugin start - load all clans
     */
    public void initialize() {
        try {
            // Clean old clan teams
            scoreboard.getTeams().stream()
                .filter(team -> team.getName().startsWith("clan_"))
                .forEach(team -> {
                    try {
                        team.unregister();
                    } catch (Exception e) {
                        // Ignore errors
                    }
                });
            
            // Update all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayerDisplay(player);
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error initializing display service", e);
        }
    }
    
    /**
     * Cleanup on plugin disable
     */
    public void shutdown() {
        try {
            // Remove all clan teams
            scoreboard.getTeams().stream()
                .filter(team -> team.getName().startsWith("clan_"))
                .forEach(team -> {
                    try {
                        team.unregister();
                    } catch (Exception e) {
                        // Ignore errors
                    }
                });
            
            // Reset all player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    resetDisplayName(player);
                } catch (Exception e) {
                    // Ignore errors
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error shutting down display service", e);
        }
    }
}
