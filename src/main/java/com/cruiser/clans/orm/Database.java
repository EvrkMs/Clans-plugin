package com.cruiser.clans.orm;

import com.cruiser.clans.ClanPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Lightweight JDBC database manager for SQLite.
 * Replaces Hibernate/Hikari to reduce jar size and RAM usage.
 */
public final class Database {

    private final ClanPlugin plugin;
    private final ExecutorService executor;
    private String jdbcUrl;

    public Database(ClanPlugin plugin) {
        this.plugin = plugin;
        // Single-thread executor is enough for SQLite and avoids write contention
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "clans-db");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialize database and schema.
     */
    public void start() {
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.filename", "clans.db"));
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try (Connection conn = newConnection()) {
            // Create/upgrade schema within an explicit transaction.
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                // Schema
                st.execute("""
                    CREATE TABLE IF NOT EXISTS clans (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT NOT NULL UNIQUE,
                      tag TEXT NOT NULL UNIQUE,
                      description TEXT,
                      leader_uuid TEXT NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER,
                      max_members INTEGER NOT NULL DEFAULT 10,
                      is_public INTEGER NOT NULL DEFAULT 1,
                      min_level INTEGER DEFAULT 0,
                      total_kills INTEGER NOT NULL DEFAULT 0,
                      total_deaths INTEGER NOT NULL DEFAULT 0,
                      clan_level INTEGER NOT NULL DEFAULT 1,
                      clan_exp INTEGER NOT NULL DEFAULT 0
                    );
                """);

                st.execute("""
                    CREATE TABLE IF NOT EXISTS clan_players (
                      uuid TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      clan_id INTEGER REFERENCES clans(id) ON DELETE SET NULL,
                      role TEXT NOT NULL DEFAULT 'MEMBER',
                      joined_at INTEGER,
                      last_seen INTEGER NOT NULL,
                      player_level INTEGER NOT NULL DEFAULT 1,
                      kills INTEGER NOT NULL DEFAULT 0,
                      deaths INTEGER NOT NULL DEFAULT 0,
                      clan_contribution INTEGER NOT NULL DEFAULT 0,
                      invited_by_uuid TEXT,
                      invite_pending_clan_id INTEGER,
                      invite_expires_at INTEGER,
                      permissions INTEGER NOT NULL DEFAULT 0
                    );
                """);

                st.execute("CREATE INDEX IF NOT EXISTS idx_clan_players_name ON clan_players(name);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_clan_players_clan ON clan_players(clan_id);");

                st.execute("""
                    CREATE TABLE IF NOT EXISTS clan_regions (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      clan_id INTEGER NOT NULL UNIQUE REFERENCES clans(id) ON DELETE CASCADE,
                      world_name TEXT NOT NULL,
                      marker_type TEXT NOT NULL,
                      marker1_x INTEGER NOT NULL,
                      marker1_y INTEGER NOT NULL,
                      marker1_z INTEGER NOT NULL,
                      marker2_x INTEGER,
                      marker2_y INTEGER,
                      marker2_z INTEGER
                    );
                """);
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize database", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Close the executor.
     */
    public void stop() {
        executor.shutdownNow();
        // Optionally run checkpoint
        try (Connection conn = newConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA optimize;");
                st.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            }
        } catch (Exception ignored) {}
    }

    public Connection newConnection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        // Apply per-connection PRAGMAs outside of a transaction
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA journal_mode = WAL;");
            st.execute("PRAGMA synchronous = NORMAL;");
            st.execute("PRAGMA wal_autocheckpoint = 1000;");
            st.execute("PRAGMA temp_store = MEMORY;");
        }
        return c;
    }

    public <T> CompletableFuture<T> withConnection(Function<Connection, T> op) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = newConnection()) {
                c.setAutoCommit(true);
                return op.apply(c);
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "DB error", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public <T> CompletableFuture<T> inTransaction(Function<Connection, T> op) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = newConnection()) {
                c.setAutoCommit(false);
                try {
                    T res = op.apply(c);
                    c.commit();
                    return res;
                } catch (RuntimeException ex) {
                    try { c.rollback(); } catch (SQLException ignored) {}
                    throw ex;
                } catch (Exception ex) {
                    try { c.rollback(); } catch (SQLException ignored) {}
                    throw new RuntimeException(ex);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "DB tx error", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }
}
