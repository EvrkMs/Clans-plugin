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
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ClanInviteManager {

    private final ClanPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private final int expireMinutes;

    public ClanInviteManager(ClanPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ClanInvite-Cleaner");
            t.setDaemon(true);
            return t;
        });
        this.expireMinutes = plugin.getConfig().getInt("invites.expire-minutes", 5);
        startCleanupTask();
    }

    public CompletableFuture<InviteResult> createInvite(UUID inviterUuid, UUID targetUuid, Integer clanId) {
        return plugin.getData().findPlayerByUuid(targetUuid).thenCompose(optTarget -> {
            if (optTarget.isEmpty()) {
                return CompletableFuture.completedFuture(InviteResult.error("Игрок не найден или еще не заходил"));
            }
            ClanPlayerEntity target = optTarget.get();
            if (target.isInClan()) {
                return CompletableFuture.completedFuture(InviteResult.error("Игрок уже состоит в клане"));
            }
            if (target.hasPendingInvite()) {
                Instant expiresAt = target.getInviteExpiresAt();
                long minutesLeft = Instant.now().until(expiresAt, ChronoUnit.MINUTES);
                return CompletableFuture.completedFuture(InviteResult.error("У игрока уже есть приглашение (истечет через " + minutesLeft + " мин.)"));
            }

            target.setInvitePendingClanId(clanId);
            target.setInviteExpiresAt(Instant.now().plus(expireMinutes, ChronoUnit.MINUTES));
            target.setInvitedByUuid(inviterUuid.toString());
            return plugin.getData().savePlayer(target).thenApply(saved -> {
                scheduleExpireNotification(targetUuid, Math.max(1, expireMinutes - 1));
                return InviteResult.success();
            });
        });
    }

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

    public CompletableFuture<InviteInfo> getInviteInfo(UUID playerUuid) {
        return plugin.getData().findPlayerByUuid(playerUuid).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().hasPendingInvite()) {
                return CompletableFuture.completedFuture(null);
            }
            ClanPlayerEntity p = optPlayer.get();
            Integer clanId = p.getInvitePendingClanId();
            if (clanId == null) return CompletableFuture.completedFuture(null);
            return plugin.getData().findClanById(clanId).thenApply(optClan ->
                optClan.map(clan -> new InviteInfo(clan, p.getInvitedByUuid(), p.getInviteExpiresAt())).orElse(null)
            );
        });
    }

    private void scheduleExpireNotification(UUID playerUuid, int delayMinutes) {
        scheduler.schedule(() -> {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                plugin.getData().findPlayerByUuid(playerUuid).thenAccept(optPlayer -> {
                    if (optPlayer.isPresent() && optPlayer.get().hasPendingInvite()) {
                        plugin.getData().runSync(() -> player.sendMessage(Component.text("Ваше приглашение в клан истекает через 1 минуту!", NamedTextColor.YELLOW)));
                    }
                });
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredInvites, 1, 1, TimeUnit.MINUTES);
    }

    private void cleanupExpiredInvites() {
        plugin.getData().findPlayersWithExpiredInvites(Instant.now()).thenAccept(players -> {
            if (players.isEmpty()) return;
            plugin.getSLF4J().debug("Автоочистка {} просроченных приглашений", players.size());
            for (ClanPlayerEntity p : players) {
                p.clearInvite();
                plugin.getData().savePlayer(p).thenRun(() -> {
                    Player bp = plugin.getServer().getPlayer(p.getUuidAsUUID());
                    if (bp != null && bp.isOnline()) {
                        plugin.getData().runSync(() -> bp.sendMessage(Component.text("Приглашение в клан истекло", NamedTextColor.GRAY)));
                    }
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка очистки приглашений: " + ex.getMessage());
            return null;
        });
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException ignored) {
            scheduler.shutdownNow();
        }
    }

    public static class InviteResult {
        private final boolean success;
        private final String error;
        private InviteResult(boolean success, String error) { this.success = success; this.error = error; }
        public static InviteResult success() { return new InviteResult(true, null); }
        public static InviteResult error(String error) { return new InviteResult(false, error); }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }

    public static class InviteInfo {
        private final ClanEntity clan;
        private final String inviterUuid;
        private final Instant expiresAt;
        public InviteInfo(ClanEntity clan, String inviterUuid, Instant expiresAt) { this.clan = clan; this.inviterUuid = inviterUuid; this.expiresAt = expiresAt; }
        public ClanEntity getClan() { return clan; }
        public String getInviterUuid() { return inviterUuid; }
        public Instant getExpiresAt() { return expiresAt; }
        public long getMinutesLeft() { return Instant.now().until(expiresAt, ChronoUnit.MINUTES); }
    }
}

