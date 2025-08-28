package com.cruiser.clans.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;
import com.cruiser.clans.orm.entity.ClanRole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Сервис для управления участниками клана
 * Обрабатывает выход, исключение, повышение/понижение и передачу лидерства
 */
public class ClanMemberService {
    
    private final ClanPlugin plugin;
    private final ClanDisplayService displayService;
    
    public ClanMemberService(ClanPlugin plugin, ClanDisplayService displayService) {
        this.plugin = plugin;
        this.displayService = displayService;
    }
    
    /**
     * Покинуть клан
     */
    public CompletableFuture<Boolean> leaveClan(Player player) {
        UUID uuid = player.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(uuid).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().isInClan()) {
                player.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity clanPlayer = optPlayer.get();
            ClanEntity clan = clanPlayer.getClan();
            
            // Лидер не может просто покинуть клан
            if (clanPlayer.getRole() == ClanRole.LEADER) {
                return plugin.getData().getClanMembers(clan.getId()).thenCompose(members -> {
                    if (members.size() > 1) {
                        plugin.getData().runSync(() -> {
                            player.sendMessage(Component.text("Вы лидер клана! Передайте лидерство или распустите клан", NamedTextColor.RED));
                        });
                        return CompletableFuture.completedFuture(false);
                    } else {
                        // Если лидер один в клане - распускаем клан
                        return disbandClanInternal(clan, player);
                    }
                });
            }
            
            // Убираем игрока из клана
            String clanName = clan.getName();
            clanPlayer.setClan(null);
            clanPlayer.setRole(ClanRole.MEMBER);
            clanPlayer.setJoinedAt(null);
            clanPlayer.setClanContribution(0);
            
            return plugin.getData().savePlayer(clanPlayer).thenCompose(saved -> {
                // Уведомляем участников клана
                return notifyMembers(clan.getId(), 
                    Component.text(player.getName() + " покинул клан", NamedTextColor.YELLOW)
                ).thenApply(v -> {
                    plugin.getData().runSync(() -> {
                        player.sendMessage(Component.text("Вы покинули клан \"" + clanName + "\"", NamedTextColor.GREEN));
                        displayService.updatePlayerDisplay(player);
                    });
                    return true;
                });
            });
        });
    }
    
    /**
     * Исключить игрока из клана
     */
    public CompletableFuture<Boolean> kickMember(Player kicker, String targetName) {
        UUID kickerUuid = kicker.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(kickerUuid).thenCompose(optKicker -> {
            if (optKicker.isEmpty() || !optKicker.get().isInClan()) {
                kicker.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity kickerPlayer = optKicker.get();
            ClanEntity clan = kickerPlayer.getClan();
            
            // Ищем цель
            return plugin.getData().findPlayerByName(targetName).thenCompose(optTarget -> {
                if (optTarget.isEmpty()) {
                    kicker.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                ClanPlayerEntity targetPlayer = optTarget.get();
                
                // Проверяем, что цель в том же клане
                if (!targetPlayer.isInClan() || !targetPlayer.getClan().getId().equals(clan.getId())) {
                    kicker.sendMessage(Component.text("Игрок не состоит в вашем клане", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                // Проверяем права на исключение
                if (!kickerPlayer.getRole().canKick(targetPlayer.getRole())) {
                    kicker.sendMessage(Component.text("У вас недостаточно прав для исключения этого игрока", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                // Нельзя кикнуть самого себя
                if (kickerUuid.equals(targetPlayer.getUuidAsUUID())) {
                    kicker.sendMessage(Component.text("Вы не можете исключить себя. Используйте /clan leave", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                // Исключаем игрока
                targetPlayer.setClan(null);
                targetPlayer.setRole(ClanRole.MEMBER);
                targetPlayer.setJoinedAt(null);
                targetPlayer.setClanContribution(0);
                
                return plugin.getData().savePlayer(targetPlayer).thenCompose(saved -> {
                    // Уведомляем исключенного если онлайн
                    Player targetBukkitPlayer = plugin.getServer().getPlayer(targetPlayer.getUuidAsUUID());
                    if (targetBukkitPlayer != null && targetBukkitPlayer.isOnline()) {
                        plugin.getData().runSync(() -> {
                            targetBukkitPlayer.sendMessage(Component.text("Вас исключили из клана \"" + clan.getName() + "\"", NamedTextColor.RED));
                            displayService.updatePlayerDisplay(targetBukkitPlayer);
                        });
                    }
                    
                    // Уведомляем остальных
                    return notifyMembers(clan.getId(), 
                        Component.text(targetName + " был исключен из клана игроком " + kicker.getName(), NamedTextColor.YELLOW)
                    ).thenApply(v -> {
                        plugin.getData().runSync(() -> {
                            kicker.sendMessage(Component.text("Игрок " + targetName + " исключен из клана", NamedTextColor.GREEN));
                        });
                        return true;
                    });
                });
            });
        });
    }
    
    /**
     * Повысить игрока
     */
    public CompletableFuture<Boolean> promoteMember(Player promoter, String targetName) {
        UUID promoterUuid = promoter.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(promoterUuid).thenCompose(optPromoter -> {
            if (optPromoter.isEmpty() || !optPromoter.get().isInClan()) {
                promoter.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity promoterPlayer = optPromoter.get();
            
            if (promoterPlayer.getRole() != ClanRole.LEADER) {
                promoter.sendMessage(Component.text("Только лидер может повышать участников", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanEntity clan = promoterPlayer.getClan();
            
            return plugin.getData().findPlayerByName(targetName).thenCompose(optTarget -> {
                if (optTarget.isEmpty()) {
                    promoter.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                ClanPlayerEntity targetPlayer = optTarget.get();
                
                if (!targetPlayer.isInClan() || !targetPlayer.getClan().getId().equals(clan.getId())) {
                    promoter.sendMessage(Component.text("Игрок не состоит в вашем клане", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                ClanRole currentRole = targetPlayer.getRole();
                ClanRole newRole = getNextRole(currentRole);
                
                if (newRole == null) {
                    promoter.sendMessage(Component.text("Игрок уже имеет максимальную роль", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                targetPlayer.setRole(newRole);
                
                return plugin.getData().savePlayer(targetPlayer).thenCompose(saved -> {
                    // Уведомляем игрока
                    Player targetBukkitPlayer = plugin.getServer().getPlayer(targetPlayer.getUuidAsUUID());
                    if (targetBukkitPlayer != null && targetBukkitPlayer.isOnline()) {
                        plugin.getData().runSync(() -> {
                            targetBukkitPlayer.sendMessage(Component.text("Вас повысили до " + newRole.getDisplayName(), NamedTextColor.GREEN));
                        });
                    }
                    
                    return notifyMembers(clan.getId(),
                        Component.text(targetName + " повышен до " + newRole.getDisplayName(), NamedTextColor.GREEN)
                    ).thenApply(v -> true);
                });
            });
        });
    }
    
    /**
     * Понизить игрока
     */
    public CompletableFuture<Boolean> demoteMember(Player demoter, String targetName) {
        UUID demoterUuid = demoter.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(demoterUuid).thenCompose(optDemoter -> {
            if (optDemoter.isEmpty() || !optDemoter.get().isInClan()) {
                demoter.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity demoterPlayer = optDemoter.get();
            
            if (demoterPlayer.getRole() != ClanRole.LEADER) {
                demoter.sendMessage(Component.text("Только лидер может понижать участников", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanEntity clan = demoterPlayer.getClan();
            
            return plugin.getData().findPlayerByName(targetName).thenCompose(optTarget -> {
                if (optTarget.isEmpty()) {
                    demoter.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                ClanPlayerEntity targetPlayer = optTarget.get();
                
                if (!targetPlayer.isInClan() || !targetPlayer.getClan().getId().equals(clan.getId())) {
                    demoter.sendMessage(Component.text("Игрок не состоит в вашем клане", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                ClanRole currentRole = targetPlayer.getRole();
                ClanRole newRole = getPreviousRole(currentRole);
                
                if (newRole == null) {
                    demoter.sendMessage(Component.text("Игрок уже имеет минимальную роль", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                targetPlayer.setRole(newRole);
                
                return plugin.getData().savePlayer(targetPlayer).thenCompose(saved -> {
                    // Уведомляем игрока
                    Player targetBukkitPlayer = plugin.getServer().getPlayer(targetPlayer.getUuidAsUUID());
                    if (targetBukkitPlayer != null && targetBukkitPlayer.isOnline()) {
                        plugin.getData().runSync(() -> {
                            targetBukkitPlayer.sendMessage(Component.text("Вас понизили до " + newRole.getDisplayName(), NamedTextColor.YELLOW));
                        });
                    }
                    
                    return notifyMembers(clan.getId(),
                        Component.text(targetName + " понижен до " + newRole.getDisplayName(), NamedTextColor.YELLOW)
                    ).thenApply(v -> true);
                });
            });
        });
    }
    
    /**
     * Передать лидерство
     */
    public CompletableFuture<Boolean> transferLeadership(Player currentLeader, String newLeaderName) {
        UUID currentLeaderUuid = currentLeader.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(currentLeaderUuid).thenCompose(optLeader -> {
            if (optLeader.isEmpty() || !optLeader.get().isInClan()) {
                currentLeader.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity leaderPlayer = optLeader.get();
            
            if (leaderPlayer.getRole() != ClanRole.LEADER) {
                currentLeader.sendMessage(Component.text("Только лидер может передать лидерство", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanEntity clan = leaderPlayer.getClan();
            
            return plugin.getData().findPlayerByName(newLeaderName).thenCompose(optNewLeader -> {
                if (optNewLeader.isEmpty()) {
                    currentLeader.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                ClanPlayerEntity newLeaderPlayer = optNewLeader.get();
                
                if (!newLeaderPlayer.isInClan() || !newLeaderPlayer.getClan().getId().equals(clan.getId())) {
                    currentLeader.sendMessage(Component.text("Игрок не состоит в вашем клане", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                if (newLeaderPlayer.getUuidAsUUID().equals(currentLeaderUuid)) {
                    currentLeader.sendMessage(Component.text("Вы уже являетесь лидером", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(false);
                }
                
                // Выполняем передачу лидерства в транзакции
                return plugin.getData().transferLeadership(leaderPlayer, newLeaderPlayer, clan).thenCompose(success -> {
                    // Уведомляем нового лидера
                    Player newLeaderBukkitPlayer = plugin.getServer().getPlayer(newLeaderPlayer.getUuidAsUUID());
                    if (newLeaderBukkitPlayer != null && newLeaderBukkitPlayer.isOnline()) {
                        plugin.getData().runSync(() -> {
                            newLeaderBukkitPlayer.sendMessage(Component.text("Вы стали лидером клана!", NamedTextColor.GOLD));
                        });
                    }
                    
                    return notifyMembers(clan.getId(),
                        Component.text("Лидерство передано от " + currentLeader.getName() + " к " + newLeaderName, NamedTextColor.GOLD)
                    ).thenApply(v -> true);
                });
            });
        });
    }
    
    /**
     * Внутренний метод для роспуска клана
     */
    private CompletableFuture<Boolean> disbandClanInternal(ClanEntity clan, Player leader) {
        return plugin.getData().deleteClan(clan.getId()).thenApply(v -> {
            plugin.getData().runSync(() -> {
                leader.sendMessage(Component.text("Клан \"" + clan.getName() + "\" распущен", NamedTextColor.GREEN));
                displayService.updatePlayerDisplay(leader);
                displayService.removeClanTeam(clan.getId());
            });
            return true;
        });
    }
    
    /**
     * Уведомить всех членов клана
     */
    private CompletableFuture<Void> notifyMembers(Integer clanId, Component message) {
        return plugin.getData().getClanMembers(clanId).thenAccept(members -> {
            plugin.getData().runSync(() -> {
                for (ClanPlayerEntity member : members) {
                    Player player = plugin.getServer().getPlayer(member.getUuidAsUUID());
                    if (player != null && player.isOnline()) {
                        player.sendMessage(message);
                    }
                }
            });
        });
    }
    
    /**
     * Получить следующую роль при повышении
     */
    private ClanRole getNextRole(ClanRole current) {
        return switch (current) {
            case RECRUIT -> ClanRole.MEMBER;
            case MEMBER -> ClanRole.OFFICER;
            case OFFICER, LEADER -> null; // Officer не может стать лидером через promote
        };
    }
    
    /**
     * Получить предыдущую роль при понижении
     */
    private ClanRole getPreviousRole(ClanRole current) {
        return switch (current) {
            case LEADER -> ClanRole.OFFICER;
            case OFFICER -> ClanRole.MEMBER;
            case MEMBER -> ClanRole.RECRUIT;
            case RECRUIT -> null;
        };
    }
}
