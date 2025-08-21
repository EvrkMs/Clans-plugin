package com.cruiser.clans;

import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import com.cruiser.clans.listener.PlayerListener;
import com.cruiser.clans.orm.DataManager;
import com.cruiser.clans.orm.OrmManager;
import com.cruiser.clans.service.ClanDisplayService;
import com.cruiser.clans.service.ClanService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class ClanPlugin extends JavaPlugin {
    
    private Logger slf4jLogger;
    private OrmManager ormManager;
    private DataManager dataManager;
    private ClanDisplayService displayService;
    private ClanService clanService;
    
    @Override
    public void onEnable() {
        // Инициализация логгера
        this.slf4jLogger = getSLF4JLogger();
        
        getComponentLogger().info(Component.text("Загрузка ClanPlugin...", NamedTextColor.GREEN));
        
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();
        
        try {
            // Инициализация ORM
            getLogger().info("Инициализация базы данных...");
            this.ormManager = new OrmManager(this);
            this.ormManager.start();
            
            // Инициализация менеджера данных
            this.dataManager = new DataManager(this, ormManager);
            
            // Инициализация сервисов
            this.displayService = new ClanDisplayService(this);
            this.clanService = new ClanService(this, displayService);
            
            // Проверка подключения к БД
            validateDatabase();
            
            // Регистрация команд
            registerCommands();
            
            // Регистрация слушателей
            registerListeners();
            
            // Инициализация отображения для онлайн игроков
            displayService.initialize();
            
            getComponentLogger().info(Component.text("ClanPlugin успешно загружен!", NamedTextColor.GREEN));
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Критическая ошибка при загрузке плагина", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        getComponentLogger().info(Component.text("Выключение ClanPlugin...", NamedTextColor.YELLOW));
        
        // Очистка отображения
        if (displayService != null) {
            displayService.shutdown();
        }
        
        // Закрытие ORM
        if (ormManager != null) {
            ormManager.stop();
        }
        
        getComponentLogger().info(Component.text("ClanPlugin выключен", NamedTextColor.RED));
    }
    
    /**
     * Проверка работоспособности БД
     */
    private void validateDatabase() {
        dataManager.getClansCount().thenCombine(
            dataManager.getPlayersInClansCount(),
            (clans, players) -> {
                slf4jLogger.info("База данных готова: {} кланов, {} игроков в кланах", clans, players);
                return null;
            }
        ).exceptionally(ex -> {
            getLogger().log(Level.SEVERE, "Ошибка при проверке БД", ex);
            return null;
        });
    }
    
    /**
     * Регистрация команд
     */
    private void registerCommands() {
        // TODO: Регистрация команд
        // Objects.requireNonNull(getCommand("clan"), "clan command not found in plugin.yml")
        //     .setExecutor(new ClanCommand(this));
    }
    
    /**
     * Регистрация слушателей событий
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }
    
    // Геттеры для доступа к сервисам
    
    public DataManager getData() {
        return dataManager;
    }
    
    public ClanDisplayService getDisplayService() {
        return displayService;
    }
    
    public ClanService getClanService() {
        return clanService;
    }
    
    public OrmManager getOrmManager() {
        return ormManager;
    }
    
    public Logger getSLF4J() {
        return slf4jLogger;
    }
}