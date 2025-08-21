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
 * Сервис для отображения тегов кланов в табе и над головами игроков
 * Использует Scoreboard API для управления префиксами
 */
public class ClanDisplayService {
    
    private final ClanPlugin plugin;
    private final Scoreboard scoreboard;
    
    public ClanDisplayService(ClanPlugin plugin) {
        this.plugin = plugin;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }
    
    /**
     * Обновить отображение игрока (вызывается при входе, смене клана и т.д.)
     */
    public void updatePlayerDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Загружаем данные игрока из БД
        plugin.getData().findPlayerByUuid(uuid).thenAccept(optPlayer -> {
            plugin.getData().runSync(() -> {
                if (optPlayer.isPresent() && optPlayer.get().isInClan()) {
                    ClanPlayerEntity clanPlayer = optPlayer.get();
                    ClanEntity clan = clanPlayer.getClan();
                    
                    // Добавляем в команду scoreboard для отображения тега
                    addToTeam(player, clan);
                    
                    // Обновляем отображаемое имя
                    updateDisplayName(player, clan);
                } else {
                    // Убираем из всех команд если не в клане
                    removeFromAllTeams(player);
                    resetDisplayName(player);
                }
            });
        });
    }
    
    /**
     * Добавить игрока в команду scoreboard для отображения тега клана
     */
    private void addToTeam(Player player, ClanEntity clan) {
        String teamName = "clan_" + clan.getId();
        Team team = scoreboard.getTeam(teamName);
        
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            
            // Устанавливаем префикс с тегом клана
            Component prefix = Component.text("[" + clan.getTag() + "] ", getTagColor(clan));
            team.prefix(prefix);
            
            // Опции команды
            team.setAllowFriendlyFire(true); // Можно настроить PvP между кланами
            team.setCanSeeFriendlyInvisibles(false);
        }
        
        // Добавляем игрока в команду
        team.addPlayer(player);
    }
    
    /**
     * Убрать игрока из всех команд кланов
     */
    private void removeFromAllTeams(Player player) {
        scoreboard.getTeams().stream()
            .filter(team -> team.getName().startsWith("clan_"))
            .forEach(team -> team.removePlayer(player));
    }
    
    /**
     * Обновить отображаемое имя игрока с тегом клана
     */
    private void updateDisplayName(Player player, ClanEntity clan) {
        Component displayName = Component.text()
            .append(Component.text("[" + clan.getTag() + "] ", getTagColor(clan)))
            .append(Component.text(player.getName(), NamedTextColor.WHITE))
            .build();
        
        player.displayName(displayName);
        player.playerListName(displayName);
    }
    
    /**
     * Сбросить отображаемое имя на стандартное
     */
    private void resetDisplayName(Player player) {
        player.displayName(Component.text(player.getName()));
        player.playerListName(Component.text(player.getName()));
    }
    
    /**
     * Получить цвет тега в зависимости от уровня клана
     */
    private NamedTextColor getTagColor(ClanEntity clan) {
        int level = clan.getClanLevel();
        if (level >= 50) return NamedTextColor.DARK_RED;
        if (level >= 40) return NamedTextColor.RED;
        if (level >= 30) return NamedTextColor.GOLD;
        if (level >= 20) return NamedTextColor.YELLOW;
        if (level >= 10) return NamedTextColor.GREEN;
        if (level >= 5) return NamedTextColor.AQUA;
        return NamedTextColor.GRAY;
    }
    
    /**
     * Обновить всех игроков клана
     */
    public void updateClanDisplay(Integer clanId) {
        plugin.getData().getClanMembers(clanId).thenAccept(members -> {
            plugin.getData().runSync(() -> {
                for (ClanPlayerEntity member : members) {
                    Player player = Bukkit.getPlayer(member.getUuidAsUUID());
                    if (player != null && player.isOnline()) {
                        updatePlayerDisplay(player);
                    }
                }
            });
        });
    }
    
    /**
     * Удалить команду клана из scoreboard
     */
    public void removeClanTeam(Integer clanId) {
        String teamName = "clan_" + clanId;
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }
    
    /**
     * Инициализация при старте плагина - загрузить все кланы
     */
    public void initialize() {
        // Очищаем старые команды кланов
        scoreboard.getTeams().stream()
            .filter(team -> team.getName().startsWith("clan_"))
            .forEach(Team::unregister);
        
        // Обновляем всех онлайн игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerDisplay(player);
        }
    }
    
    /**
     * Очистка при выключении плагина
     */
    public void shutdown() {
        // Убираем все команды кланов
        scoreboard.getTeams().stream()
            .filter(team -> team.getName().startsWith("clan_"))
            .forEach(Team::unregister);
        
        // Сбрасываем имена всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetDisplayName(player);
        }
    }
}