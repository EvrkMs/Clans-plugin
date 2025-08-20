package com.cruiser.tasks;

import com.cruiser.ClanPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class AutoSaveTask extends BukkitRunnable {
    private final ClanPlugin plugin;
    
    public AutoSaveTask(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void run() {
        // Сохраняем все изменения в базу данных
        plugin.getClanManager().saveAllClans();
        plugin.getPlayerDataManager().saveAllPlayers();
        
        // Логируем только если были изменения
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Автосохранение выполнено");
        }
    }
}