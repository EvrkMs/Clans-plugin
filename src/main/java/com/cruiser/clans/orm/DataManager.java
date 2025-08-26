package com.cruiser.clans.orm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.hibernate.Session;
import org.hibernate.Transaction;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;
import com.cruiser.clans.orm.entity.ClanRegionEntity;

public final class DataManager {
    
    private final ClanPlugin plugin;
    private final OrmManager orm;
    
    public DataManager(ClanPlugin plugin, OrmManager orm) {
        this.plugin = plugin;
        this.orm = orm;
    }
    
    /**
     * Execute read operation (without transaction)
     * IMPORTANT: result is not cached, each call = new database query
     */
    public <T> CompletableFuture<T> query(Function<Session, T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try (Session session = orm.sessionFactory().openSession()) {
                return operation.apply(session);
            } catch (Exception e) {
                plugin.getLogger().severe("Query failed: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Execute write operation (with transaction)
     * After execution all data is cleared from memory
     */
    public <T> CompletableFuture<T> transaction(Function<Session, T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            Session session = orm.sessionFactory().openSession();
            Transaction tx = null;
            try {
                tx = session.beginTransaction();
                T result = operation.apply(session);
                tx.commit();
                return result;
            } catch (Exception e) {
                if (tx != null && tx.isActive()) {
                    tx.rollback();
                }
                plugin.getLogger().severe("Transaction failed: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                session.close(); // Important: close session, free memory
            }
        });
    }
    
    /**
     * Execute write operation without return value
     */
    public CompletableFuture<Void> execute(Consumer<Session> operation) {
        return transaction(session -> {
            operation.accept(session);
            return null;
        });
    }
    
    // === Specific methods for working with clans ===
    
    /**
     * Find clan by ID (each call = database query)
     */
    public CompletableFuture<Optional<ClanEntity>> findClanById(Integer id) {
        return query(session -> 
            Optional.ofNullable(session.get(ClanEntity.class, id))
        );
    }
    
    /**
     * Find clan by name (case sensitive)
     */
    public CompletableFuture<Optional<ClanEntity>> findClanByName(String name) {
        return query(session -> {
            List<ClanEntity> clans = session.createQuery(
                "FROM ClanEntity c WHERE c.name = :name", ClanEntity.class)
                .setParameter("name", name)
                .setMaxResults(1)
                .list();
            return clans.isEmpty() ? Optional.empty() : Optional.of(clans.get(0));
        });
    }
    
    /**
     * Find clan by tag
     */
    public CompletableFuture<Optional<ClanEntity>> findClanByTag(String tag) {
        return query(session -> {
            List<ClanEntity> clans = session.createQuery(
                "FROM ClanEntity c WHERE c.tag = :tag", ClanEntity.class)
                .setParameter("tag", tag)
                .setMaxResults(1)
                .list();
            return clans.isEmpty() ? Optional.empty() : Optional.of(clans.get(0));
        });
    }
    
    /**
     * Find player by UUID WITH CLAN DATA (to avoid LazyInitializationException)
     */
    public CompletableFuture<Optional<ClanPlayerEntity>> findPlayerByUuid(UUID uuid) {
        return query(session -> {
            List<ClanPlayerEntity> players = session.createQuery(
                "FROM ClanPlayerEntity p LEFT JOIN FETCH p.clan WHERE p.uuid = :uuid", 
                ClanPlayerEntity.class)
                .setParameter("uuid", uuid.toString())
                .setMaxResults(1)
                .list();
            return players.isEmpty() ? Optional.empty() : Optional.of(players.get(0));
        });
    }
    
    /**
     * Find player by name (case sensitive) WITH CLAN DATA
     */
    public CompletableFuture<Optional<ClanPlayerEntity>> findPlayerByName(String name) {
        return query(session -> {
            List<ClanPlayerEntity> players = session.createQuery(
                "FROM ClanPlayerEntity p LEFT JOIN FETCH p.clan WHERE p.name = :name", 
                ClanPlayerEntity.class)
                .setParameter("name", name)
                .setMaxResults(1)
                .list();
            return players.isEmpty() ? Optional.empty() : Optional.of(players.get(0));
        });
    }
    
    /**
     * Get list of all clan members WITH CLAN DATA
     */
    public CompletableFuture<List<ClanPlayerEntity>> getClanMembers(Integer clanId) {
        return query(session -> 
            session.createQuery(
                "FROM ClanPlayerEntity p JOIN FETCH p.clan WHERE p.clan.id = :clanId", 
                ClanPlayerEntity.class)
                .setParameter("clanId", clanId)
                .list()
        );
    }
    
    /**
     * Get top clans by kills
     */
    public CompletableFuture<List<ClanEntity>> getTopClansByKills(int limit) {
        return query(session -> 
            session.createQuery(
                "FROM ClanEntity c ORDER BY c.totalKills DESC", ClanEntity.class)
                .setMaxResults(limit)
                .list()
        );
    }
    
    /**
     * Create new clan
     */
    public CompletableFuture<ClanEntity> createClan(ClanEntity clan) {
        return transaction(session -> {
            session.persist(clan);
            session.flush(); // Force write to get ID
            return clan;
        });
    }
    
    /**
     * Update clan
     */
    public CompletableFuture<ClanEntity> updateClan(ClanEntity clan) {
        return transaction(session -> 
            session.merge(clan)
        );
    }
    
    /**
     * Delete clan and all its members
     */
    public CompletableFuture<Void> deleteClan(Integer clanId) {
        return execute(session -> {
            // First remove all players from clan
            session.createQuery(
                "UPDATE ClanPlayerEntity p SET p.clan = null, p.role = 'MEMBER', p.joinedAt = null, p.clanContribution = 0 " +
                "WHERE p.clan.id = :clanId")
                .setParameter("clanId", clanId)
                .executeUpdate();
            
            // Delete clan regions
            session.createQuery("DELETE FROM ClanRegionEntity r WHERE r.clan.id = :clanId")
                .setParameter("clanId", clanId)
                .executeUpdate();

            // Then delete clan itself
            ClanEntity clan = session.get(ClanEntity.class, clanId);
            if (clan != null) {
                session.remove(clan);
            }
        });
    }
    
    /**
     * Save or update player
     */
    public CompletableFuture<ClanPlayerEntity> savePlayer(ClanPlayerEntity player) {
        return transaction(session ->
            session.merge(player)
        );
    }

    // === Region-specific methods ===

    /**
     * Create new region
     */
    public CompletableFuture<ClanRegionEntity> createRegion(ClanRegionEntity region) {
        return transaction(session -> {
            session.persist(region);
            session.flush();
            return region;
        });
    }

    /**
     * Update region
     */
    public CompletableFuture<ClanRegionEntity> updateRegion(ClanRegionEntity region) {
        return transaction(session ->
            session.merge(region)
        );
    }

    /**
     * Delete region
     */
    public CompletableFuture<Void> deleteRegion(Integer regionId) {
        return execute(session -> {
            ClanRegionEntity region = session.get(ClanRegionEntity.class, regionId);
            if (region != null) {
                session.remove(region);
            }
        });
    }

    /**
     * Find region by ID
     */
    public CompletableFuture<Optional<ClanRegionEntity>> findRegionById(Integer id) {
        return query(session -> {
            List<ClanRegionEntity> regions = session.createQuery(
                "FROM ClanRegionEntity r JOIN FETCH r.clan WHERE r.id = :id",
                ClanRegionEntity.class)
                .setParameter("id", id)
                .setMaxResults(1)
                .list();
            return regions.isEmpty() ? Optional.empty() : Optional.of(regions.get(0));
        });
    }

    /**
     * Find clan region
     */
    public CompletableFuture<Optional<ClanRegionEntity>> findClanRegion(Integer clanId) {
        return query(session -> {
            List<ClanRegionEntity> regions = session.createQuery(
                "FROM ClanRegionEntity r JOIN FETCH r.clan WHERE r.clan.id = :clanId",
                ClanRegionEntity.class)
                .setParameter("clanId", clanId)
                .setMaxResults(1)
                .list();
            return regions.isEmpty() ? Optional.empty() : Optional.of(regions.get(0));
        });
    }

    /**
     * Find regions by world
     */
    public CompletableFuture<List<ClanRegionEntity>> findRegionsByWorld(String worldName) {
        return query(session ->
            session.createQuery(
                "FROM ClanRegionEntity r JOIN FETCH r.clan WHERE r.worldName = :world",
                ClanRegionEntity.class)
                .setParameter("world", worldName)
                .list()
        );
    }
    
    /**
     * Execute operation in main Bukkit thread
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Get clan count
     */
    public CompletableFuture<Long> getClansCount() {
        return query(session -> 
            session.createQuery("SELECT COUNT(c) FROM ClanEntity c", Long.class)
                .uniqueResult()
        );
    }
    
    /**
     * Get players in clans count
     */
    public CompletableFuture<Long> getPlayersInClansCount() {
        return query(session ->
            session.createQuery("SELECT COUNT(p) FROM ClanPlayerEntity p WHERE p.clan IS NOT NULL", Long.class)
                .uniqueResult()
        );
    }

    /**
     * Get regions count
     */
    public CompletableFuture<Long> getRegionsCount() {
        return query(session ->
            session.createQuery("SELECT COUNT(r) FROM ClanRegionEntity r", Long.class)
                .uniqueResult()
        );
    }
}