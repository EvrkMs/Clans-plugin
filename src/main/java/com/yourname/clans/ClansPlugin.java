package com.yourname.clans;

import org.bukkit.plugin.java.JavaPlugin;

import com.yourname.clans.commands.ClanCommand;
import com.yourname.clans.data.ClanDataManager;
import com.yourname.clans.listeners.ChatListener;
import com.yourname.clans.listeners.CombatListener;
import com.yourname.clans.listeners.PlayerListener;

public class ClansPlugin extends JavaPlugin {
    
    private static ClansPlugin instance;
    private ClanDataManager dataManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Создание папки плагина если её нет
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // Инициализация системы данных
        dataManager = new ClanDataManager(this);
        dataManager.loadData();
        
        // Регистрация команд
        getCommand("clan").setExecutor(new ClanCommand(this));
        
        // Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        
        getLogger().info("Clans plugin enabled!");
    }
    
    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveData();
        }
        getLogger().info("Clans plugin disabled!");
    }
    
    public static ClansPlugin getInstance() {
        return instance;
    }
    
    public ClanDataManager getDataManager() {
        return dataManager;
    }
}