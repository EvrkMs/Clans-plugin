package com.yourname.clans.listeners;

import com.yourname.clans.ClansPlugin;
import com.yourname.clans.model.Clan;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
    
    private final ClansPlugin plugin;
    
    public PlayerListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Clan clan = plugin.getDataManager().getPlayerClan(event.getPlayer().getUniqueId());
        if (clan != null) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Добро пожаловать в клан '" + clan.getName() + "' [" + clan.getTag() + "]!");
        }
        
        // Проверка на активные приглашения
        for (Clan existingClan : plugin.getDataManager().getAllClans()) {
            if (existingClan.hasInvite(event.getPlayer().getUniqueId())) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "У вас есть приглашение в клан '" + existingClan.getName() + "' [" + existingClan.getTag() + "]");
                event.getPlayer().sendMessage(ChatColor.YELLOW + "Используйте /clan accept для принятия или /clan deny для отклонения");
                break;
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Сохранение данных при выходе игрока
        plugin.getDataManager().saveData();
    }
}