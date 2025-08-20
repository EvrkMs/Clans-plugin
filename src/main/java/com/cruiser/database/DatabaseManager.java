package com.cruiser.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import com.cruiser.ClanPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseManager {
    
    private final ClanPlugin plugin;
    private HikariDataSource dataSource;
    private boolean sqlite = true;
    
    public DatabaseManager(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() throws SQLException {
        HikariConfig config = new HikariConfig();

        String dbType = plugin.getConfig().getString("database.type", "sqlite");
        sqlite = !dbType.equalsIgnoreCase("mysql");

        if (!sqlite) {
            config.setJdbcUrl("jdbc:mysql://" +
                plugin.getConfig().getString("database.host") + ":" +
                plugin.getConfig().getInt("database.port") + "/" +
                plugin.getConfig().getString("database.database") +
                "?useSSL=false&autoReconnect=true&characterEncoding=utf8&serverTimezone=UTC");
            config.setUsername(plugin.getConfig().getString("database.username"));
            config.setPassword(plugin.getConfig().getString("database.password"));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            // ВАЖНО: включаем foreign_keys через параметр
            String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/clans.db?foreign_keys=on";
            // (опц) небольшой busy_timeout, WAL и synchronous можно сделать PRAGMA'ми ниже
            // url += "&busy_timeout=5000";
            config.setJdbcUrl(url);
            config.setDriverClassName("org.sqlite.JDBC");
        }

        // Пул
        config.setMaximumPoolSize(sqlite ? 4 : 10);
        config.setMinimumIdle(sqlite ? 1 : 2);
        config.setIdleTimeout(300_000);
        config.setConnectionTimeout(10_000);
        config.setLeakDetectionThreshold(60_000);

        dataSource = new HikariDataSource(config);

        // Одноразовые PRAGMA (WAL/synchronous) — на отдельном соединении
        if (sqlite) {
            try (Connection c = dataSource.getConnection();
                 var st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA synchronous=NORMAL;");
                st.execute("PRAGMA foreign_keys=ON;"); // на всякий
            }
        }

        createTables(); 
        repairOrphansOnce();
    }
    private void repairOrphansOnce() {
        // только для SQLite; для MySQL это безопасно тоже
        try (Connection conn = getConnection(); var st = conn.createStatement()) {
            // игроки, у которых clan_id указывает на несуществующий клан
            st.executeUpdate("""
                UPDATE clan_players
                SET clan_id = NULL, role = 'MEMBER'
                WHERE clan_id IS NOT NULL
                  AND clan_id NOT IN (SELECT id FROM clans)
                """);
    
            // приглашения/союзы без кланов
            st.executeUpdate("""
                DELETE FROM clan_invites
                WHERE clan_id NOT IN (SELECT id FROM clans)
                """);
            st.executeUpdate("""
                DELETE FROM clan_alliances
                WHERE clan1_id NOT IN (SELECT id FROM clans)
                   OR clan2_id NOT IN (SELECT id FROM clans)
                """);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Orphan repair failed", e);
        }
    }
    
    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            // Таблица кланов
            String createClansTable = """
                CREATE TABLE IF NOT EXISTS clans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(32) UNIQUE NOT NULL,
                    tag VARCHAR(8) UNIQUE NOT NULL,
                    leader_uuid VARCHAR(36) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    level INTEGER DEFAULT 1,
                    balance DOUBLE DEFAULT 0,
                    max_members INTEGER DEFAULT 10,
                    home_world VARCHAR(64),
                    home_x DOUBLE,
                    home_y DOUBLE,
                    home_z DOUBLE,
                    home_yaw FLOAT,
                    home_pitch FLOAT
                )
                """;
            
            // Таблица игроков
            String createPlayersTable = """
                CREATE TABLE IF NOT EXISTS clan_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    clan_id INTEGER,
                    role VARCHAR(16) DEFAULT 'MEMBER',
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    kills INTEGER DEFAULT 0,
                    deaths INTEGER DEFAULT 0,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE SET NULL
                )
                """;
            
            // Таблица приглашений
            String createInvitesTable = """
                CREATE TABLE IF NOT EXISTS clan_invites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    invited_by VARCHAR(36) NOT NULL,
                    invited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                    UNIQUE(clan_id, player_uuid)
                )
                """;
            
            // Таблица союзов
            String createAlliancesTable = """
                CREATE TABLE IF NOT EXISTS clan_alliances (
                    clan1_id INTEGER NOT NULL,
                    clan2_id INTEGER NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (clan1_id, clan2_id),
                    FOREIGN KEY (clan1_id) REFERENCES clans(id) ON DELETE CASCADE,
                    FOREIGN KEY (clan2_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """;
            
            // Для MySQL немного другой синтаксис
            if (plugin.getConfig().getString("database.type", "sqlite").equalsIgnoreCase("mysql")) {
                createClansTable = createClansTable.replace("AUTOINCREMENT", "AUTO_INCREMENT");
                createInvitesTable = createInvitesTable.replace("AUTOINCREMENT", "AUTO_INCREMENT");
            }
            
            try (PreparedStatement ps = conn.prepareStatement(createClansTable)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(createPlayersTable)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(createInvitesTable)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(createAlliancesTable)) {
                ps.executeUpdate();
            }
        }
    }
    
    public Connection getConnection() throws SQLException {
        Connection conn = dataSource.getConnection();
        // ВАЖНО: включаем FK на КАЖДОМ соединении (SQLite требует этого)
        if (sqlite) {
            try (var st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON;");
            }
        }
        return conn;
    }
    
    // Асинхронное выполнение запросов
    public CompletableFuture<Void> executeAsync(DatabaseQuery query) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection()) {
                query.execute(conn);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка выполнения запроса", e);
            }
        });
    }
    
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    @FunctionalInterface
    public interface DatabaseQuery {
        void execute(Connection connection) throws SQLException;
    }
}