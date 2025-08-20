package com.cruiser;

import java.sql.SQLException;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

import com.cruiser.commands.ClanCommand;
import com.cruiser.database.DatabaseManager;
import com.cruiser.listeners.ClanEventListener;
import com.cruiser.managers.ClanManager;
import com.cruiser.managers.PlayerDataManager;
import com.cruiser.tasks.AutoSaveTask;
import com.cruiser.utils.LoggingSetup;

public class ClanPlugin extends JavaPlugin {
    
    private static ClanPlugin instance;
    private DatabaseManager databaseManager;
    private ClanManager clanManager;
    private PlayerDataManager playerDataManager;
    private ClanEventListener eventListener;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Настройка логирования
        LoggingSetup.setupLogging(getLogger());
        
        // Загрузка конфигурации
        saveDefaultConfig();
        
        // Инициализация базы данных
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            getLogger().info("База данных успешно инициализирована");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Ошибка инициализации базы данных", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Инициализация менеджеров
        clanManager = new ClanManager(this);
        playerDataManager = new PlayerDataManager(this);
        
        // Загрузка данных
        clanManager.loadAllClans();
        playerDataManager.loadAllPlayers();
        
        // Регистрация команд
        getCommand("clan").setExecutor(new ClanCommand(this));
        getCommand("clan").setTabCompleter(new ClanCommand(this));
        
        // Регистрация событий
        eventListener = new ClanEventListener(this);
        getServer().getPluginManager().registerEvents(eventListener, this);
        
        // Запуск автосохранения каждые 5 минут
        new AutoSaveTask(this).runTaskTimerAsynchronously(this, 6000L, 6000L); // 5 минут = 6000 тиков
        
        getLogger().info("ClanPlugin успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        // Сохранение всех данных перед выключением
        if (clanManager != null) {
            clanManager.saveAllClans();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayers();
        }
        
        // Закрытие соединения с БД
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("ClanPlugin выключен!");
    }
    
    public static ClanPlugin getInstance() {
        return instance;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public ClanManager getClanManager() {
        return clanManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public ClanEventListener getEventListener() {
        return eventListener;
    }
}