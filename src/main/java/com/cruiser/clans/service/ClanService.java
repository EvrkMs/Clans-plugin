package com.cruiser.clans.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
 * Основной сервис для работы с кланами
 * Все операции выполняются асинхронно с БД
 */
public class ClanService {
    
    private final ClanPlugin plugin;
    private final ClanDisplayService displayService;
    
    // Настройки
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MIN_TAG_LENGTH = 2;
    private static final int MAX_TAG_LENGTH = 8;
    private static final int INVITE_EXPIRE_MINUTES = 5;
    
    public ClanService(ClanPlugin plugin, ClanDisplayService displayService) {
        this.plugin = plugin;
        this.displayService = displayService;
    }
    
    /**
     * Создать новый клан
     */
    public CompletableFuture<CreateClanResult> createClan(Player leader, String name, String tag) {
        UUID leaderUuid = leader.getUniqueId();
        
        // Валидация
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            return CompletableFuture.completedFuture(
                CreateClanResult.error("Имя клана должно быть от " + MIN_NAME_LENGTH + " до " + MAX_NAME_LENGTH + " символов")
            );
        }
        
        if (tag.length() < MIN_TAG_LENGTH || tag.length() > MAX_TAG_LENGTH) {
            return CompletableFuture.completedFuture(
                CreateClanResult.error("Тег клана должен быть от " + MIN_TAG_LENGTH + " до " + MAX_TAG_LENGTH + " символов")
            );
        }
        
        // Проверяем, не состоит ли игрок уже в клане
        return plugin.getData().findPlayerByUuid(leaderUuid).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty()) {
                // Создаем нового игрока
                ClanPlayerEntity newPlayer = new ClanPlayerEntity();
                newPlayer.setUuid(leaderUuid);
                newPlayer.setName(leader.getName());
                return plugin.getData().savePlayer(newPlayer).thenApply(Optional::of);
            }
            return CompletableFuture.completedFuture(optPlayer);
        }).thenCompose(optPlayer -> {
            ClanPlayerEntity player = optPlayer.get();
            
            if (player.isInClan()) {
                return CompletableFuture.completedFuture(
                    CreateClanResult.error("Вы уже состоите в клане")
                );
            }
            
            // Проверяем уникальность имени и тега
            return plugin.getData().findClanByName(name).thenCompose(existingByName -> {
                if (existingByName.isPresent()) {
                    return CompletableFuture.completedFuture(
                        CreateClanResult.error("Клан с таким именем уже существует")
                    );
                }
                
                return plugin.getData().findClanByTag(tag).thenCompose(existingByTag -> {
                    if (existingByTag.isPresent()) {
                        return CompletableFuture.completedFuture(
                            CreateClanResult.error("Клан с таким тегом уже существует")
                        );
                    }
                    
                    // Создаем клан
                    ClanEntity clan = new ClanEntity();
                    clan.setName(name);
                    clan.setTag(tag);
                    clan.setLeaderUuid(leaderUuid);
                    clan.setCreatedAt(Instant.now());
                    
                    return plugin.getData().createClan(clan).thenCompose(createdClan -> {
                        // Добавляем лидера в клан
                        player.setClan(createdClan);
                        player.setRole(ClanRole.LEADER);
                        player.setJoinedAt(Instant.now());
                        
                        return plugin.getData().savePlayer(player).thenApply(savedPlayer -> {
                            // Обновляем отображение
                            plugin.getData().runSync(() -> {
                                displayService.updatePlayerDisplay(leader);
                                leader.sendMessage(Component.text("Клан \"" + name + "\" успешно создан!", NamedTextColor.GREEN));
                            });
                            
                            return CreateClanResult.success(createdClan);
                        });
                    });
                });
            });
        });
    }
    
    /**
     * Удалить клан
     */
    public CompletableFuture<Boolean> disbandClan(Player player) {
        UUID uuid = player.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(uuid).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().isInClan()) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED))
                );
                return CompletableFuture.completedFuture(false);
            }

            ClanPlayerEntity clanPlayer = optPlayer.get();
            if (clanPlayer.getRole() != ClanRole.LEADER) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Только лидер может распустить клан", NamedTextColor.RED))
                );
                return CompletableFuture.completedFuture(false);
            }
            
            Integer clanId = clanPlayer.getClan().getId();
            String clanName = clanPlayer.getClan().getName();
            
            // Уведомляем всех членов клана
            return plugin.getData().getClanMembers(clanId).thenCompose(members -> {
                plugin.getData().runSync(() -> {
                    for (ClanPlayerEntity member : members) {
                        Player memberPlayer = plugin.getServer().getPlayer(member.getUuidAsUUID());
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                            memberPlayer.sendMessage(Component.text("Клан \"" + clanName + "\" был распущен", NamedTextColor.RED));
                            displayService.updatePlayerDisplay(memberPlayer);
                        }
                    }
                });
                
                // Удаляем клан
                return plugin.getData().deleteClan(clanId).thenApply(v -> {
                    plugin.getData().runSync(() -> {
                        displayService.removeClanTeam(clanId);
                    });
                    return true;
                });
            });
        });
    }
    
    /**
     * Пригласить игрока в клан
     */
    public CompletableFuture<Boolean> invitePlayer(Player inviter, String targetName) {
        UUID inviterUuid = inviter.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(inviterUuid).thenCompose(optInviter -> {
            if (optInviter.isEmpty() || !optInviter.get().isInClan()) {
                plugin.getData().runSync(() ->
                    inviter.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED))
                );
                return CompletableFuture.completedFuture(false);
            }

            ClanPlayerEntity inviterPlayer = optInviter.get();
            if (!inviterPlayer.getRole().canInvite()) {
                plugin.getData().runSync(() ->
                    inviter.sendMessage(Component.text("У вас нет прав для приглашения игроков", NamedTextColor.RED))
                );
                return CompletableFuture.completedFuture(false);
            }
            
            ClanEntity clan = inviterPlayer.getClan();
            
            // Проверяем лимит участников
            return plugin.getData().getClanMembers(clan.getId()).thenCompose(members -> {
                if (members.size() >= clan.getMaxMembers()) {
                    plugin.getData().runSync(() ->
                        inviter.sendMessage(Component.text("Достигнут лимит участников клана", NamedTextColor.RED))
                    );
                    return CompletableFuture.completedFuture(false);
                }
                
                // Ищем целевого игрока
                Player target = plugin.getServer().getPlayer(targetName);
                if (target == null || !target.isOnline()) {
                    plugin.getData().runSync(() ->
                        inviter.sendMessage(Component.text("Игрок не найден или оффлайн", NamedTextColor.RED))
                    );
                    return CompletableFuture.completedFuture(false);
                }
                
                return plugin.getData().findPlayerByUuid(target.getUniqueId()).thenCompose(optTarget -> {
                    ClanPlayerEntity targetPlayer;
                    
                    if (optTarget.isEmpty()) {
                        // Создаем нового игрока
                        targetPlayer = new ClanPlayerEntity();
                        targetPlayer.setUuid(target.getUniqueId());
                        targetPlayer.setName(target.getName());
                    } else {
                        targetPlayer = optTarget.get();
                        
                        if (targetPlayer.isInClan()) {
                            plugin.getData().runSync(() ->
                                inviter.sendMessage(Component.text("Игрок уже состоит в клане", NamedTextColor.RED))
                            );
                            return CompletableFuture.completedFuture(false);
                        }

                        if (targetPlayer.hasPendingInvite()) {
                            plugin.getData().runSync(() ->
                                inviter.sendMessage(Component.text("У игрока уже есть активное приглашение", NamedTextColor.RED))
                            );
                            return CompletableFuture.completedFuture(false);
                        }
                    }
                    
                    // Создаем приглашение
                    targetPlayer.setInvitePendingClanId(clan.getId());
                    targetPlayer.setInviteExpiresAt(Instant.now().plus(INVITE_EXPIRE_MINUTES, ChronoUnit.MINUTES));
                    targetPlayer.setInvitedByUuid(inviterUuid.toString());
                    
                    return plugin.getData().savePlayer(targetPlayer).thenApply(saved -> {
                        plugin.getData().runSync(() -> {
                            inviter.sendMessage(Component.text("Приглашение отправлено игроку " + target.getName(), NamedTextColor.GREEN));
                            
                            target.sendMessage(Component.text()
                                .append(Component.text("Вас пригласили в клан \"", NamedTextColor.YELLOW))
                                .append(Component.text(clan.getName(), NamedTextColor.GOLD))
                                .append(Component.text("\"", NamedTextColor.YELLOW))
                                .build());
                            target.sendMessage(Component.text("Используйте /clan accept для принятия или /clan decline для отклонения", NamedTextColor.GRAY));
                        });
                        
                        return true;
                    });
                });
            });
        });
    }
    
    /**
     * Принять приглашение в клан
     */
    public CompletableFuture<Boolean> acceptInvite(Player player) {
        UUID uuid = player.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(uuid).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty()) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("У вас нет активных приглашений", NamedTextColor.RED))
                );
                return CompletableFuture.completedFuture(false);
            }

            ClanPlayerEntity clanPlayer = optPlayer.get();

            if (!clanPlayer.hasPendingInvite()) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("У вас нет активных приглашений", NamedTextColor.RED))
                );
                return CompletableFuture.completedFuture(false);
            }

            if (clanPlayer.isInClan()) {
                plugin.getData().runSync(() ->
                    player.sendMessage(Component.text("Вы уже состоите в клане", NamedTextColor.RED))
                );
                clanPlayer.clearInvite();
                plugin.getData().savePlayer(clanPlayer);
                return CompletableFuture.completedFuture(false);
            }
            
            Integer clanId = clanPlayer.getInvitePendingClanId();
            
            return plugin.getData().findClanById(clanId).thenCompose(optClan -> {
                if (optClan.isEmpty()) {
                    plugin.getData().runSync(() ->
                        player.sendMessage(Component.text("Клан больше не существует", NamedTextColor.RED))
                    );
                    clanPlayer.clearInvite();
                    return plugin.getData().savePlayer(clanPlayer).thenApply(v -> false);
                }
                
                ClanEntity clan = optClan.get();
                
                // Проверяем лимит участников
                return plugin.getData().getClanMembers(clanId).thenCompose(members -> {
                    if (members.size() >= clan.getMaxMembers()) {
                        plugin.getData().runSync(() ->
                            player.sendMessage(Component.text("В клане достигнут лимит участников", NamedTextColor.RED))
                        );
                        clanPlayer.clearInvite();
                        return plugin.getData().savePlayer(clanPlayer).thenApply(v -> false);
                    }
                    
                    // Добавляем в клан
                    clanPlayer.setClan(clan);
                    clanPlayer.setRole(ClanRole.RECRUIT);
                    clanPlayer.setJoinedAt(Instant.now());
                    clanPlayer.clearInvite();
                    
                    return plugin.getData().savePlayer(clanPlayer).thenApply(saved -> {
                        plugin.getData().runSync(() -> {
                            player.sendMessage(Component.text("Вы вступили в клан \"" + clan.getName() + "\"", NamedTextColor.GREEN));
                            displayService.updatePlayerDisplay(player);
                            
                            // Уведомляем членов клана
                            for (ClanPlayerEntity member : members) {
                                Player memberPlayer = plugin.getServer().getPlayer(member.getUuidAsUUID());
                                if (memberPlayer != null && memberPlayer.isOnline()) {
                                    memberPlayer.sendMessage(Component.text(player.getName() + " вступил в клан", NamedTextColor.YELLOW));
                                }
                            }
                        });
                        
                        return true;
                    });
                });
            });
        });
    }
    
    // Вспомогательные классы
    public static class CreateClanResult {
        private final boolean success;
        private final String error;
        private final ClanEntity clan;
        
        private CreateClanResult(boolean success, String error, ClanEntity clan) {
            this.success = success;
            this.error = error;
            this.clan = clan;
        }
        
        public static CreateClanResult success(ClanEntity clan) {
            return new CreateClanResult(true, null, clan);
        }
        
        public static CreateClanResult error(String error) {
            return new CreateClanResult(false, error, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public ClanEntity getClan() { return clan; }
    }
}