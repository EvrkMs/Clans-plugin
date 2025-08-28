package com.cruiser.clans;

import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import com.cruiser.clans.command.ClanAdminCommand;
import com.cruiser.clans.command.ClanCommand;
import com.cruiser.clans.listener.ChatListener;
import com.cruiser.clans.listener.PlayerListener;
import com.cruiser.clans.listener.RegionMarkerListener;
import com.cruiser.clans.listener.RegionProtectionListener;
import com.cruiser.clans.orm.DataManager;
import com.cruiser.clans.orm.Database;
import com.cruiser.clans.service.ClanDisplayService;
import com.cruiser.clans.service.ClanChatService;
import com.cruiser.clans.service.ClanMemberService;
import com.cruiser.clans.service.ClanRegionService;
import com.cruiser.clans.service.ClanService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class ClanPlugin extends JavaPlugin {
    
    private Logger slf4jLogger;
    private Database database;
    private DataManager dataManager;
    private ClanDisplayService displayService;
    private ClanService clanService;
    private ClanMemberService memberService;
    private ClanChatService chatService;
    private ClanRegionService regionService;
    
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
            this.database = new Database(this);
            this.database.start();
            
            // Инициализация менеджера данных
            this.dataManager = new DataManager(this, database);
            
            // Инициализация сервисов
            this.displayService = new ClanDisplayService(this);
            this.clanService = new ClanService(this, displayService);
            this.memberService = new ClanMemberService(this, displayService);
            this.chatService = new ClanChatService(this);
            this.regionService = new ClanRegionService(this);
            
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
        if (chatService != null) {
            chatService.clearAllChatModes();
        }

        // Закрытие ORM
        if (database != null) {
            database.stop();
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
        var clanCmd = new ClanCommand(this);
        Objects.requireNonNull(getCommand("clan"), "clan command not found in plugin.yml")
            .setExecutor(clanCmd);
        Objects.requireNonNull(getCommand("clan"))
            .setTabCompleter(clanCmd);

        var adminCmd = new ClanAdminCommand(this);
        Objects.requireNonNull(getCommand("clanadmin"), "clanadmin command not found in plugin.yml")
            .setExecutor(adminCmd);
        Objects.requireNonNull(getCommand("clanadmin"))
            .setTabCompleter(adminCmd);
    }
    
    /**
     * Регистрация слушателей событий
     */
    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ChatListener(this), this);

        if (getConfig().getBoolean("regions.enabled", true)) {
            pm.registerEvents(new RegionProtectionListener(this), this);
            pm.registerEvents(new RegionMarkerListener(this), this);
        }
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

    public ClanMemberService getMemberService() {
        return memberService;
    }

    public ClanChatService getChatService() {
        return chatService;
    }

    public ClanRegionService getRegionService() {
        return regionService;
    }
    
    public Logger getSLF4J() {
        return slf4jLogger;
    }
}
