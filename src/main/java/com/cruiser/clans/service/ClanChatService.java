package com.cruiser.clans.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Сервис для управления чатом клана
 */
public class ClanChatService {
    
    private final ClanPlugin plugin;
    private final Set<UUID> clanChatMode; // Игроки в режиме чата клана
    
    public ClanChatService(ClanPlugin plugin) {
        this.plugin = plugin;
        this.clanChatMode = new HashSet<>();
    }
    
    /**
     * Отправить сообщение в чат клана
     */
    public CompletableFuture<Boolean> sendClanMessage(Player sender, String message) {
        UUID senderUuid = sender.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(senderUuid).thenCompose(optSender -> {
            if (optSender.isEmpty() || !optSender.get().isInClan()) {
                plugin.getData().runSync(() ->
                    sender.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED))
                );
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity senderPlayer = optSender.get();
            Integer clanId = senderPlayer.getClan().getId();
            String clanTag = senderPlayer.getClan().getTag();
            
            // Форматируем сообщение
            Component formattedMessage = formatClanMessage(sender.getName(), senderPlayer.getRole().getDisplayName(), clanTag, message);
            
            // Получаем всех членов клана и отправляем им сообщение
            return plugin.getData().getClanMembers(clanId).thenAccept(members -> {
                plugin.getData().runSync(() -> {
                    for (ClanPlayerEntity member : members) {
                        Player memberPlayer = plugin.getServer().getPlayer(member.getUuidAsUUID());
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                            memberPlayer.sendMessage(formattedMessage);
                        }
                    }
                    
                    // Логируем в консоль если включено
                    if (plugin.getConfig().getBoolean("debug.log-actions", false)) {
                        plugin.getLogger().info("[ClanChat] [" + clanTag + "] " + sender.getName() + ": " + message);
                    }
                });
            }).thenApply(v -> true);
        });
    }
    
    /**
     * Переключить режим чата клана для игрока
     */
    public CompletableFuture<Boolean> toggleClanChatMode(Player player) {
        UUID uuid = player.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(uuid).thenApply(optPlayer -> {
            if (optPlayer.isEmpty() || !optPlayer.get().isInClan()) {
                plugin.getData().runSync(() -> {
                    player.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                });
                return false;
            }
            
            boolean newMode;
            if (clanChatMode.contains(uuid)) {
                clanChatMode.remove(uuid);
                newMode = false;
            } else {
                clanChatMode.add(uuid);
                newMode = true;
            }
            
            plugin.getData().runSync(() -> {
                if (newMode) {
                    player.sendMessage(Component.text("Режим чата клана включен", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Режим чата клана выключен", NamedTextColor.YELLOW));
                }
            });
            
            return true;
        });
    }
    
    /**
     * Проверить, находится ли игрок в режиме чата клана
     */
    public boolean isInClanChatMode(Player player) {
        return clanChatMode.contains(player.getUniqueId());
    }
    
    /**
     * Обработать сообщение игрока (для перехвата в слушателе)
     */
    public CompletableFuture<Boolean> handlePlayerMessage(Player player, String message) {
        if (!isInClanChatMode(player)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return sendClanMessage(player, message);
    }
    
    /**
     * Отправить системное сообщение клану
     */
    public CompletableFuture<Void> sendSystemMessage(Integer clanId, Component message) {
        return plugin.getData().getClanMembers(clanId).thenAccept(members -> {
            plugin.getData().runSync(() -> {
                Component systemMessage = Component.text()
                    .append(Component.text("[CLAN] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(message)
                    .build();
                
                for (ClanPlayerEntity member : members) {
                    Player player = plugin.getServer().getPlayer(member.getUuidAsUUID());
                    if (player != null && player.isOnline()) {
                        player.sendMessage(systemMessage);
                    }
                }
            });
        });
    }
    
    /**
     * Отправить объявление от лидера
     */
    public CompletableFuture<Boolean> sendAnnouncement(Player leader, String announcement) {
        UUID leaderUuid = leader.getUniqueId();
        
        return plugin.getData().findPlayerByUuid(leaderUuid).thenCompose(optLeader -> {
            if (optLeader.isEmpty() || !optLeader.get().isInClan()) {
                leader.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            ClanPlayerEntity leaderPlayer = optLeader.get();
            
            if (leaderPlayer.getRole() != com.cruiser.clans.orm.entity.ClanRole.LEADER) {
                leader.sendMessage(Component.text("Только лидер может делать объявления", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            Component announcementMessage = Component.text()
                .append(Component.text("═══════════════════════════", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("ОБЪЯВЛЕНИЕ КЛАНА", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(announcement, NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("— " + leader.getName() + ", Лидер", NamedTextColor.GRAY, TextDecoration.ITALIC))
                .append(Component.newline())
                .append(Component.text("═══════════════════════════", NamedTextColor.GOLD))
                .build();
            
            return sendSystemMessage(leaderPlayer.getClan().getId(), announcementMessage)
                .thenApply(v -> true);
        });
    }
    
    /**
     * Форматировать сообщение чата клана
     */
    private Component formatClanMessage(String playerName, String role, String clanTag, String message) {
        String formatPattern = plugin.getConfig().getString("chat.format", "%prefix% &7%player%: &f%message%");
        String prefix = plugin.getConfig().getString("chat.prefix", "&7[&6CLAN&7]");
        
        // Заменяем плейсхолдеры
        String formatted = formatPattern
            .replace("%prefix%", prefix)
            .replace("%player%", playerName)
            .replace("%role%", role)
            .replace("%tag%", clanTag)
            .replace("%message%", message);
        
        // Конвертируем цветовые коды и возвращаем Component
        return LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    }
    
    /**
     * Очистить игрока из режима чата при выходе
     */
    public void removeFromChatMode(Player player) {
        clanChatMode.remove(player.getUniqueId());
    }
    
    /**
     * Получить количество игроков в режиме чата клана
     */
    public int getClanChatModeCount() {
        return clanChatMode.size();
    }
    
    /**
     * Очистить все режимы чата (при выключении плагина)
     */
    public void clearAllChatModes() {
        clanChatMode.clear();
    }
}