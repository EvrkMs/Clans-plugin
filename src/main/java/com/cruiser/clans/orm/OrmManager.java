package com.cruiser.clans.orm;

import java.io.File;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;
import com.cruiser.clans.orm.entity.ClanRegionEntity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Менеджер ORM - управляет Hibernate и HikariCP
 * Настроен для работы с SQLite без кеширования в RAM
 */
public final class OrmManager {
    
    private final ClanPlugin plugin;
    private HikariDataSource dataSource;
    private SessionFactory sessionFactory;
    
    public OrmManager(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Инициализация ORM
     */
    public void start() {
        // Создаем директорию для БД если не существует
        File dbFile = new File(plugin.getDataFolder(), "clans.db");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Настройка HikariCP
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        
        // Настройки пула соединений - минимальные для SQLite
        hikariConfig.setMaximumPoolSize(2); // SQLite плохо работает с множественными соединениями
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(10000); // 10 секунд
        hikariConfig.setIdleTimeout(300000); // 5 минут
        hikariConfig.setMaxLifetime(600000); // 10 минут
        
        // ВАЖНО: Включаем внешние ключи для каждого соединения
        hikariConfig.setConnectionInitSql("PRAGMA foreign_keys=ON;");
        
        // Отключаем автокоммит для лучшего контроля транзакций
        hikariConfig.setAutoCommit(false);
        
        dataSource = new HikariDataSource(hikariConfig);
        
        // Оптимизация SQLite
        optimizeSQLite();
        
        // Настройка Hibernate
        Map<String, Object> hibernateProps = new HashMap<>();
        
        // Основные настройки
        hibernateProps.put(AvailableSettings.DATASOURCE, dataSource);
        hibernateProps.put(AvailableSettings.DIALECT, "org.hibernate.community.dialect.SQLiteDialect");
        hibernateProps.put(AvailableSettings.HBM2DDL_AUTO, "update"); // Автоматическое создание/обновление таблиц
        
        // Отключаем все виды кеширования для экономии RAM
        hibernateProps.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, false);
        hibernateProps.put(AvailableSettings.USE_QUERY_CACHE, false);
        hibernateProps.put(AvailableSettings.USE_STRUCTURED_CACHE, false);
        hibernateProps.put(AvailableSettings.STATEMENT_BATCH_SIZE, 0); // Отключаем батчинг
        
        // Настройки производительности
        hibernateProps.put(AvailableSettings.ORDER_INSERTS, true);
        hibernateProps.put(AvailableSettings.ORDER_UPDATES, true);
        hibernateProps.put(AvailableSettings.BATCH_VERSIONED_DATA, true);
        
        // Логирование (отключено в продакшене)
        hibernateProps.put(AvailableSettings.SHOW_SQL, plugin.getConfig().getBoolean("debug.show-sql", false));
        hibernateProps.put(AvailableSettings.FORMAT_SQL, plugin.getConfig().getBoolean("debug.format-sql", false));
        hibernateProps.put(AvailableSettings.USE_SQL_COMMENTS, false);
        
        // Статистика (только для отладки)
        hibernateProps.put(AvailableSettings.GENERATE_STATISTICS, plugin.getConfig().getBoolean("debug.statistics", false));
        
        // Создаем SessionFactory
        var registry = new StandardServiceRegistryBuilder()
            .applySettings(hibernateProps)
            .build();
        
        try {
            sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(ClanEntity.class)
                .addAnnotatedClass(ClanPlayerEntity.class)
                .addAnnotatedClass(ClanRegionEntity.class)
                .buildMetadata()
                .buildSessionFactory();
            
            plugin.getLogger().info("ORM успешно инициализирован");
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw new RuntimeException("Не удалось создать SessionFactory", e);
        }
    }
    
    /**
     * Оптимизация настроек SQLite
     */
    private void optimizeSQLite() {
        try (Connection conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                // WAL режим для лучшей производительности при конкурентном доступе
                stmt.execute("PRAGMA journal_mode=WAL;");
                
                // Нормальная синхронизация (баланс между производительностью и надежностью)
                stmt.execute("PRAGMA synchronous=NORMAL;");
                
                // Увеличиваем размер кеша страниц (в страницах по 4KB)
                stmt.execute("PRAGMA cache_size=10000;"); // ~40MB кеша
                
                // Размер страницы (оптимально для современных ОС)
                stmt.execute("PRAGMA page_size=4096;");
                
                // Временные таблицы в памяти
                stmt.execute("PRAGMA temp_store=MEMORY;");
                
                // Оптимизация для многопоточности
                stmt.execute("PRAGMA threads=2;");
                
                // Автоматическая оптимизация БД
                stmt.execute("PRAGMA optimize;");
            }
            conn.commit();
            
            plugin.getLogger().info("SQLite оптимизирован для работы");
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось оптимизировать SQLite: " + e.getMessage());
        }
    }
    
    /**
     * Получить SessionFactory
     */
    public SessionFactory sessionFactory() {
        if (sessionFactory == null || sessionFactory.isClosed()) {
            throw new IllegalStateException("SessionFactory не инициализирован или закрыт");
        }
        return sessionFactory;
    }
    
    /**
     * Остановка ORM
     */
    public void stop() {
        // Закрываем SessionFactory
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            try {
                sessionFactory.close();
                plugin.getLogger().info("SessionFactory закрыт");
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при закрытии SessionFactory: " + e.getMessage());
            }
        }
        
        // Закрываем пул соединений
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                // Финальная оптимизация перед закрытием
                try (Connection conn = dataSource.getConnection()) {
                    try (var stmt = conn.createStatement()) {
                        stmt.execute("PRAGMA optimize;");
                        stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                    }
                    conn.commit();
                }
                
                dataSource.close();
                plugin.getLogger().info("Пул соединений закрыт");
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при закрытии пула соединений: " + e.getMessage());
            }
        }
    }
    
    /**
     * Проверка работоспособности соединения
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed() && 
               sessionFactory != null && !sessionFactory.isClosed();
    }
    
    /**
     * Получить статистику пула соединений
     */
    public String getPoolStats() {
        if (dataSource == null) {
            return "Пул не инициализирован";
        }
        
        return String.format(
            "Активные: %d, Idle: %d, Всего: %d, Ожидают: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
}