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

public final class DataManager {
    
    private final ClanPlugin plugin;
    private final OrmManager orm;
    
    public DataManager(ClanPlugin plugin, OrmManager orm) {
        this.plugin = plugin;
        this.orm = orm;
    }
    
    /**
     * Выполнить операцию чтения БД (без транзакции)
     * ВАЖНО: результат не кешируется, каждый вызов = новый запрос к БД
     */
    public <T> CompletableFuture<T> query(Function<Session, T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try (Session session = orm.sessionFactory().openSession()) {
                return operation.apply(session);
            } catch (Exception e) {
                plugin.getLogger().severe("Query failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Выполнить операцию записи БД (с транзакцией)
     * После выполнения все данные очищаются из памяти
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
                throw new RuntimeException(e);
            } finally {
                session.close(); // Важно: закрываем сессию, освобождаем память
            }
        });
    }
    
    /**
     * Выполнить операцию записи без возврата результата
     */
    public CompletableFuture<Void> execute(Consumer<Session> operation) {
        return transaction(session -> {
            operation.accept(session);
            return null;
        });
    }
    
    // === Специфичные методы для работы с кланами ===
    
    /**
     * Найти клан по ID (каждый вызов = запрос к БД)
     */
    public CompletableFuture<Optional<ClanEntity>> findClanById(Integer id) {
        return query(session -> 
            Optional.ofNullable(session.get(ClanEntity.class, id))
        );
    }
    
    /**
     * Найти клан по имени (с учетом регистра)
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
     * Найти клан по тегу
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
     * Найти игрока по UUID
     */
    public CompletableFuture<Optional<ClanPlayerEntity>> findPlayerByUuid(UUID uuid) {
        return query(session -> 
            Optional.ofNullable(session.get(ClanPlayerEntity.class, uuid.toString()))
        );
    }
    
    /**
     * Найти игрока по имени (с учетом регистра)
     */
    public CompletableFuture<Optional<ClanPlayerEntity>> findPlayerByName(String name) {
        return query(session -> {
            List<ClanPlayerEntity> players = session.createQuery(
                "FROM ClanPlayerEntity p WHERE p.name = :name", ClanPlayerEntity.class)
                .setParameter("name", name)
                .setMaxResults(1)
                .list();
            return players.isEmpty() ? Optional.empty() : Optional.of(players.get(0));
        });
    }
    
    /**
     * Получить список всех членов клана
     */
    public CompletableFuture<List<ClanPlayerEntity>> getClanMembers(Integer clanId) {
        return query(session -> 
            session.createQuery(
                "FROM ClanPlayerEntity p WHERE p.clan.id = :clanId", ClanPlayerEntity.class)
                .setParameter("clanId", clanId)
                .list()
        );
    }
    
    /**
     * Получить топ кланов по убийствам
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
     * Создать новый клан
     */
    public CompletableFuture<ClanEntity> createClan(ClanEntity clan) {
        return transaction(session -> {
            session.persist(clan);
            session.flush(); // Форсируем запись чтобы получить ID
            return clan;
        });
    }
    
    /**
     * Обновить клан
     */
    public CompletableFuture<ClanEntity> updateClan(ClanEntity clan) {
        return transaction(session -> 
            session.merge(clan)
        );
    }
    
    /**
     * Удалить клан и всех его участников
     */
    public CompletableFuture<Void> deleteClan(Integer clanId) {
        return execute(session -> {
            // Сначала убираем всех игроков из клана
            session.createQuery(
                "UPDATE ClanPlayerEntity p SET p.clan = null, p.role = 'MEMBER', p.joinedAt = null " +
                "WHERE p.clan.id = :clanId")
                .setParameter("clanId", clanId)
                .executeUpdate();
            
            // Затем удаляем сам клан
            ClanEntity clan = session.get(ClanEntity.class, clanId);
            if (clan != null) {
                session.remove(clan);
            }
        });
    }
    
    /**
     * Сохранить или обновить игрока
     */
    public CompletableFuture<ClanPlayerEntity> savePlayer(ClanPlayerEntity player) {
        return transaction(session -> 
            session.merge(player)
        );
    }
    
    /**
     * Выполнить операцию в главном потоке Bukkit
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Получить количество кланов
     */
    public CompletableFuture<Long> getClansCount() {
        return query(session -> 
            session.createQuery("SELECT COUNT(c) FROM ClanEntity c", Long.class)
                .uniqueResult()
        );
    }
    
    /**
     * Получить количество игроков в кланах
     */
    public CompletableFuture<Long> getPlayersInClansCount() {
        return query(session -> 
            session.createQuery("SELECT COUNT(p) FROM ClanPlayerEntity p WHERE p.clan IS NOT NULL", Long.class)
                .uniqueResult()
        );
    }
}