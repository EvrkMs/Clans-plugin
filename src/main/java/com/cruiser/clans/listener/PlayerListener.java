package com.cruiser.clans.listener;

import java.time.Instant;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;

public class PlayerListener implements Listener {

    private final ClanPlugin plugin;

    public PlayerListener(ClanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty()) {
                ClanPlayerEntity newPlayer = new ClanPlayerEntity();
                newPlayer.setUuid(player.getUniqueId());
                newPlayer.setName(player.getName());
                newPlayer.setLastSeen(Instant.now());

                plugin.getData().savePlayer(newPlayer).thenRun(() -> {
                    plugin.getSLF4J().debug("Registered first join for: {}", player.getName());
                });
            } else {
                ClanPlayerEntity existingPlayer = optPlayer.get();
                existingPlayer.setName(player.getName());
                existingPlayer.setLastSeen(Instant.now());

                plugin.getData().savePlayer(existingPlayer).thenRun(() -> {
                    plugin.getData().runSync(() -> plugin.getDisplayService().updatePlayerDisplay(player));
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Error processing player join for " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            if (optPlayer.isPresent()) {
                ClanPlayerEntity clanPlayer = optPlayer.get();
                clanPlayer.setLastSeen(Instant.now());

                plugin.getData().savePlayer(clanPlayer).thenRun(() -> {
                    plugin.getSLF4J().debug("Updated lastSeen on quit for: {}", player.getName());
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Error processing player quit for " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();

        // Update victim stats and clan total deaths
        plugin.getData().recordPlayerDeath(victim.getUniqueId());

        // Update killer stats and clan; check for level up
        if (killer != null && killer != victim) {
            int expGain = calculateKillExp(victim, killer);
            plugin.getData().recordPlayerKillAndReturnClan(killer.getUniqueId(), expGain)
                .thenAccept(optClan -> optClan.ifPresent(this::checkClanLevelUp));
        }
    }

    private int calculateKillExp(Player victim, Player killer) {
        int baseExp = 10;
        return baseExp;
    }

    private void checkClanLevelUp(com.cruiser.clans.orm.entity.ClanEntity clan) {
        int currentLevel = clan.getClanLevel();
        int currentExp = clan.getClanExp();
        int requiredExp = getRequiredExpForLevel(currentLevel + 1);

        if (currentExp >= requiredExp) {
            clan.setClanLevel(currentLevel + 1);
            clan.setClanExp(currentExp - requiredExp);

            if ((currentLevel + 1) % 5 == 0) {
                clan.setMaxMembers(clan.getMaxMembers() + 2);
            }

            plugin.getData().runSync(() -> {
                plugin.getData().getClanMembers(clan.getId()).thenAccept(members -> {
                    for (ClanPlayerEntity member : members) {
                        Player player = plugin.getServer().getPlayer(member.getUuidAsUUID());
                        if (player != null && player.isOnline()) {
                            player.sendMessage(net.kyori.adventure.text.Component.text()
                                .append(net.kyori.adventure.text.Component.text("Клан повысил уровень до ", net.kyori.adventure.text.format.NamedTextColor.GOLD))
                                .append(net.kyori.adventure.text.Component.text(clan.getClanLevel() + "!", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                                .build());
                        }
                    }
                });
            });
        }
    }

    private int getRequiredExpForLevel(int level) {
        return 100 * level + (level * level * 10);
    }
}

