package com.cruiser.clans.manager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Менеджер приглашений в кланы
 * Отслеживает истечение приглашений и очищает их
 */
public class ClanInviteManager {
    
    private final ClanPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private final int expireMinutes;
    
    public ClanInviteManager(ClanPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ClanInvite-Cleaner");
            thread.setDaemon(true);
            return thread;
        });
        this.expireMinutes = plugin.getConfig().getInt("invites.expire-minutes", 5);
        
        // Запускаем очистку истекших приглашений каждую минуту
        startCleanupTask();
    }
    
    /**
     * Создать приглашение
     */
    public CompletableFuture<InviteResult> createInvite(UUID inviterUuid, UUID targetUuid, Integer clanId) {
        return plugin.getData().findPlayerByUuid(targetUuid).thenCompose(optTarget -> {
            if (optTarget.isEmpty()) {
                return CompletableFuture.completedFuture(
                    InviteResult.error("Игрок не найден в базе данных")
                );
            }
            
            ClanPlayerEntity target = optTarget.get();
            
            // Проверки
            if (target.isInClan()) {
                return CompletableFuture.completedFuture(
                    InviteResult.error("Игрок уже состоит в клане")
                );
            }
            
            if (target.hasPendingInvite()) {
                Instant expiresAt = target.getInviteExpiresAt();
                long minutesLeft = Instant.now().until(expiresAt, ChronoUnit.MINUTES);
                return CompletableFuture.completedFuture(
                    InviteResult.error("У игрока уже есть приглашение (истечет через " + minutesLeft + " мин.)")
                );
            }
            
            // Создаем приглашение
            target.setInvitePendingClanId(clanId);
            target.setInviteExpiresAt(Instant.now().plus(expireMinutes, ChronoUnit.MINUTES));
            target.setInvitedByUuid(inviterUuid.toString());
            
            return plugin.getData().savePlayer(target).thenApply(saved -> {
                // Планируем напоминание об истечении
                scheduleExpireNotification(targetUuid, expireMinutes - 1);
                
                return InviteResult.success();
            });
        });
    }
    
    /**
     * Отменить приглашение
     */
    public CompletableFuture<Boolean> cancelInvite(UUID targetUuid) {
        return plugin.getData().findPlayerByUuid(targetUuid).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().hasPendingInvite()) {
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity player = optPlayer.get();
            player.clearInvite();
            
            return plugin.getData().savePlayer(player).thenApply(saved -> true);
        });
    }
    
    /**
     * Получить информацию о приглашении
     */
    public CompletableFuture<InviteInfo> getInviteInfo(UUID playerUuid) {
        return plugin.getData().findPlayerByUuid(playerUuid).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().hasPendingInvite()) {
                return CompletableFuture.completedFuture(null);
            }
            
            ClanPlayerEntity player = optPlayer.get();
            Integer clanId = player.getInvitePendingClanId();
            
            return plugin.getData().findClanById(clanId).thenApply(optClan -> {
                if (optClan.isEmpty()) {
                    // Клан удален, очищаем приглашение
                    player.clearInvite();
                    plugin.getData().savePlayer(player);
                    return null;
                }
                
                return new InviteInfo(
                    optClan.get(),
                    player.getInvitedByUuid(),
                    player.getInviteExpiresAt()
                );
            });
        });
    }
    
    /**
     * Запланировать уведомление об истечении приглашения
     */
    private void scheduleExpireNotification(UUID playerUuid, long delayMinutes) {
        if (delayMinutes <= 0) return;
        
        scheduler.schedule(() -> {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                plugin.getData().findPlayerByUuid(playerUuid).thenAccept(optPlayer -> {
                    if (optPlayer.isPresent() && optPlayer.get().hasPendingInvite()) {
                        plugin.getData().runSync(() -> {
                            player.sendMessage(Component.text(
                                "Ваше приглашение в клан истечет через 1 минуту!", 
                                NamedTextColor.YELLOW
                            ));
                        });
                    }
                });
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Запустить задачу очистки истекших приглашений
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            cleanupExpiredInvites();
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Очистить истекшие приглашения
     */
    private void cleanupExpiredInvites() {
        plugin.getData().query(session -> {
            // Находим всех игроков с истекшими приглашениями
            return session.createQuery(
                "FROM ClanPlayerEntity p WHERE p.inviteExpiresAt IS NOT NULL AND p.inviteExpiresAt < :now",
                ClanPlayerEntity.class
            )
            .setParameter("now", Instant.now())
            .list();
        }).thenAccept(players -> {
            if (players.isEmpty()) return;
            
            plugin.getSLF4J().debug("Очистка {} истекших приглашений", players.size());
            
            // Очищаем приглашения
            for (ClanPlayerEntity player : players) {
                player.clearInvite();
                plugin.getData().savePlayer(player).thenRun(() -> {
                    // Уведомляем игрока если онлайн
                    Player bukkitPlayer = plugin.getServer().getPlayer(player.getUuidAsUUID());
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        plugin.getData().runSync(() -> {
                            bukkitPlayer.sendMessage(Component.text(
                                "Ваше приглашение в клан истекло", 
                                NamedTextColor.GRAY
                            ));
                        });
                    }
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка при очистке приглашений: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * Остановить менеджер
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
    
    // Вспомогательные классы
    
    public static class InviteResult {
        private final boolean success;
        private final String error;
        
        private InviteResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
        
        public static InviteResult success() {
            return new InviteResult(true, null);
        }
        
        public static InviteResult error(String error) {
            return new InviteResult(false, error);
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    public static class InviteInfo {
        private final com.cruiser.clans.orm.entity.ClanEntity clan;
        private final String inviterUuid;
        private final Instant expiresAt;
        
        public InviteInfo(com.cruiser.clans.orm.entity.ClanEntity clan, String inviterUuid, Instant expiresAt) {
            this.clan = clan;
            this.inviterUuid = inviterUuid;
            this.expiresAt = expiresAt;
        }
        
        public com.cruiser.clans.orm.entity.ClanEntity getClan() { return clan; }
        public String getInviterUuid() { return inviterUuid; }
        public Instant getExpiresAt() { return expiresAt; }
        
        public long getMinutesLeft() {
            return Instant.now().until(expiresAt, ChronoUnit.MINUTES);
        }
    }
}