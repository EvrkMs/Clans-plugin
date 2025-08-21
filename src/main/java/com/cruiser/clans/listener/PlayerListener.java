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

/**
 * Слушатель событий игроков
 * Обрабатывает вход/выход, смерти, убийства
 */
public class PlayerListener implements Listener {
    
    private final ClanPlugin plugin;
    
    public PlayerListener(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Обновляем данные игрока в БД
        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty()) {
                // Создаем нового игрока
                ClanPlayerEntity newPlayer = new ClanPlayerEntity();
                newPlayer.setUuid(player.getUniqueId());
                newPlayer.setName(player.getName());
                newPlayer.setLastSeen(Instant.now());
                
                plugin.getData().savePlayer(newPlayer).thenRun(() -> {
                    plugin.getSLF4J().debug("Создан новый игрок: {}", player.getName());
                });
            } else {
                // Обновляем существующего игрока
                ClanPlayerEntity existingPlayer = optPlayer.get();
                existingPlayer.setName(player.getName()); // Обновляем имя если изменилось
                existingPlayer.setLastSeen(Instant.now());
                
                plugin.getData().savePlayer(existingPlayer).thenRun(() -> {
                    // Обновляем отображение клана
                    plugin.getData().runSync(() -> {
                        plugin.getDisplayService().updatePlayerDisplay(player);
                    });
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка при обработке входа игрока " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }
    
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Обновляем время последнего визита
        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            if (optPlayer.isPresent()) {
                ClanPlayerEntity clanPlayer = optPlayer.get();
                clanPlayer.setLastSeen(Instant.now());
                
                plugin.getData().savePlayer(clanPlayer).thenRun(() -> {
                    plugin.getSLF4J().debug("Обновлено время выхода для игрока: {}", player.getName());
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка при обработке выхода игрока " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        
        // Обновляем статистику жертвы
        plugin.getData().findPlayerByUuid(victim.getUniqueId()).thenAccept(optVictim -> {
            if (optVictim.isPresent()) {
                ClanPlayerEntity victimPlayer = optVictim.get();
                victimPlayer.setDeaths(victimPlayer.getDeaths() + 1);
                
                // Обновляем статистику клана если игрок в клане
                if (victimPlayer.isInClan()) {
                    plugin.getData().transaction(session -> {
                        ClanPlayerEntity managedVictim = session.merge(victimPlayer);
                        managedVictim.getClan().setTotalDeaths(managedVictim.getClan().getTotalDeaths() + 1);
                        return managedVictim;
                    });
                } else {
                    plugin.getData().savePlayer(victimPlayer);
                }
            }
        });
        
        // Обновляем статистику убийцы если есть
        if (killer != null && killer != victim) {
            plugin.getData().findPlayerByUuid(killer.getUniqueId()).thenAccept(optKiller -> {
                if (optKiller.isPresent()) {
                    ClanPlayerEntity killerPlayer = optKiller.get();
                    killerPlayer.setKills(killerPlayer.getKills() + 1);
                    
                    // Обновляем статистику и опыт клана
                    if (killerPlayer.isInClan()) {
                        plugin.getData().transaction(session -> {
                            ClanPlayerEntity managedKiller = session.merge(killerPlayer);
                            managedKiller.getClan().setTotalKills(managedKiller.getClan().getTotalKills() + 1);
                            
                            // Добавляем опыт клану за убийство
                            int expGain = calculateKillExp(victim, killer);
                            managedKiller.getClan().setClanExp(managedKiller.getClan().getClanExp() + expGain);
                            managedKiller.setClanContribution(managedKiller.getClanContribution() + expGain);
                            
                            // Проверяем повышение уровня клана
                            checkClanLevelUp(managedKiller.getClan());
                            
                            return managedKiller;
                        });
                    } else {
                        plugin.getData().savePlayer(killerPlayer);
                    }
                }
            });
        }
    }
    
    /**
     * Рассчитать опыт за убийство
     */
    private int calculateKillExp(Player victim, Player killer) {
        // Базовый опыт
        int baseExp = 10;
        
        // TODO: Добавить модификаторы опыта
        // - За убийство игрока из вражеского клана
        // - За убийство игрока с высоким уровнем
        // - За серию убийств
        
        return baseExp;
    }
    
    /**
     * Проверить повышение уровня клана
     */
    private void checkClanLevelUp(com.cruiser.clans.orm.entity.ClanEntity clan) {
        int currentLevel = clan.getClanLevel();
        int currentExp = clan.getClanExp();
        int requiredExp = getRequiredExpForLevel(currentLevel + 1);
        
        if (currentExp >= requiredExp) {
            clan.setClanLevel(currentLevel + 1);
            clan.setClanExp(currentExp - requiredExp);
            
            // Увеличиваем лимит участников каждые 5 уровней
            if ((currentLevel + 1) % 5 == 0) {
                clan.setMaxMembers(clan.getMaxMembers() + 2);
            }
            
            // Уведомляем всех членов клана
            plugin.getData().runSync(() -> {
                plugin.getData().getClanMembers(clan.getId()).thenAccept(members -> {
                    for (ClanPlayerEntity member : members) {
                        Player player = plugin.getServer().getPlayer(member.getUuidAsUUID());
                        if (player != null && player.isOnline()) {
                            player.sendMessage(net.kyori.adventure.text.Component.text()
                                .append(net.kyori.adventure.text.Component.text("Клан достиг ", net.kyori.adventure.text.format.NamedTextColor.GOLD))
                                .append(net.kyori.adventure.text.Component.text(clan.getClanLevel() + " уровня!", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                                .build());
                        }
                    }
                });
            });
        }
    }
    
    /**
     * Получить требуемый опыт для уровня
     */
    private int getRequiredExpForLevel(int level) {
        // Прогрессивная формула: каждый уровень требует больше опыта
        return 100 * level + (level * level * 10);
    }
}