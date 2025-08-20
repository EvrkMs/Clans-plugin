package com.cruiser.listeners;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.cruiser.ClanPlugin;
import com.cruiser.models.Clan;
import com.cruiser.models.ClanMember;

public class ClanEventListener implements Listener {
    private final ClanPlugin plugin;
    private final Set<UUID> clanChatMode = new HashSet<>();
    
    public ClanEventListener(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Загружаем или создаем данные игрока
        ClanMember member = plugin.getPlayerDataManager().getOrCreatePlayer(
            player.getUniqueId(), 
            player.getName()
        );
        
        // Проверяем изменение имени
        if (!member.getName().equals(player.getName())) {
            member.setName(player.getName());
        }
        
        // Приветственное сообщение от клана
        if (member.hasClan()) {
            Clan clan = plugin.getClanManager().getClanById(member.getClanId());
            if (clan != null) {
                player.sendMessage(ChatColor.GREEN + "Добро пожаловать! Вы член клана " + 
                    ChatColor.GOLD + clan.getName() + ChatColor.GREEN + " [" + clan.getTag() + "]");
                
                // Уведомляем других членов клана
                for (UUID memberUuid : clan.getOnlineMembers()) {
                    if (!memberUuid.equals(player.getUniqueId())) {
                        Player p = plugin.getServer().getPlayer(memberUuid);
                        if (p != null) {
                            p.sendMessage(ChatColor.YELLOW + member.getName() + 
                                " из вашего клана зашёл на сервер!");
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clanChatMode.remove(player.getUniqueId());
        
        ClanMember member = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (member != null && member.hasClan()) {
            Clan clan = plugin.getClanManager().getClanById(member.getClanId());
            if (clan != null) {
                // Уведомляем других членов клана
                for (UUID memberUuid : clan.getOnlineMembers()) {
                    if (!memberUuid.equals(player.getUniqueId())) {
                        Player p = plugin.getServer().getPlayer(memberUuid);
                        if (p != null) {
                            p.sendMessage(ChatColor.YELLOW + member.getName() + 
                                " из вашего клана вышел с сервера");
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ClanMember member = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        
        if (member == null) return;
        
        // Проверяем режим кланового чата
        if (clanChatMode.contains(player.getUniqueId()) && member.hasClan()) {
            event.setCancelled(true);
            
            Clan clan = plugin.getClanManager().getClanById(member.getClanId());
            if (clan == null) return;
            
            String message = plugin.getConfig().getString("chat.format", "&7[&6Клан&7] &f%player%: &e%message%")
                .replace("%player%", player.getName())
                .replace("%message%", event.getMessage());
            message = ChatColor.translateAlternateColorCodes('&', message);
            
            // Отправляем сообщение всем членам клана
            for (UUID memberUuid : clan.getOnlineMembers()) {
                Player p = plugin.getServer().getPlayer(memberUuid);
                if (p != null) {
                    p.sendMessage(message);
                }
            }
            
            // Логируем в консоль
            plugin.getLogger().info("[ClanChat] [" + clan.getName() + "] " + 
                player.getName() + ": " + event.getMessage());
            
            return;
        }
        
        // Добавляем тег клана в обычный чат
        if (member.hasClan()) {
            Clan clan = plugin.getClanManager().getClanById(member.getClanId());
            if (clan != null) {
                String tagFormat = plugin.getConfig().getString("chat.tag_format", "&7[&6%tag%&7] ")
                    .replace("%tag%", clan.getTag());
                tagFormat = ChatColor.translateAlternateColorCodes('&', tagFormat);
                
                event.setFormat(tagFormat + event.getFormat());
            }
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer == null) return;
        
        ClanMember victimMember = plugin.getPlayerDataManager().getPlayerData(victim.getUniqueId());
        ClanMember killerMember = plugin.getPlayerDataManager().getPlayerData(killer.getUniqueId());
        
        if (victimMember == null || killerMember == null) return;
        
        // Обновляем статистику
        victimMember.addDeath();
        killerMember.addKill();
        
        // Проверяем на убийство союзника
        if (victimMember.hasClan() && killerMember.hasClan()) {
            Clan victimClan = plugin.getClanManager().getClanById(victimMember.getClanId());
            Clan killerClan = plugin.getClanManager().getClanById(killerMember.getClanId());
            
            if (victimClan != null && killerClan != null) {
                // Убийство члена своего клана
                if (victimClan.getId() == killerClan.getId()) {
                    killer.sendMessage(ChatColor.RED + "Вы убили члена своего клана!");
                    
                    // Уведомляем клан
                    for (UUID memberUuid : victimClan.getOnlineMembers()) {
                        Player p = plugin.getServer().getPlayer(memberUuid);
                        if (p != null && !p.equals(killer) && !p.equals(victim)) {
                            p.sendMessage(ChatColor.RED + killer.getName() + 
                                " убил члена клана " + victim.getName() + "!");
                        }
                    }
                    return; // Не даем награду
                }
                
                // Убийство союзника
                if (victimClan.isAlly(killerClan.getId())) {
                    killer.sendMessage(ChatColor.YELLOW + "Вы убили союзника!");
                    
                    if (!plugin.getConfig().getBoolean("pvp.reward_ally_kills", false)) {
                        return; // Не даем награду за убийство союзника
                    }
                }
            }
        }
        
        // Награда за убийство
        double killReward = plugin.getConfig().getDouble("pvp.kill_reward", 100.0);
        if (killReward > 0 && killerMember.hasClan()) {
            Clan killerClan = plugin.getClanManager().getClanById(killerMember.getClanId());
            if (killerClan != null) {
                killerClan.deposit(killReward);
                killer.sendMessage(ChatColor.GREEN + "+" + killReward + " в казну клана за убийство!");
            }
        }
        
        // Штраф за смерть
        double deathPenalty = plugin.getConfig().getDouble("pvp.death_penalty", 50.0);
        if (deathPenalty > 0 && victimMember.hasClan()) {
            Clan victimClan = plugin.getClanManager().getClanById(victimMember.getClanId());
            if (victimClan != null && victimClan.withdraw(deathPenalty)) {
                victim.sendMessage(ChatColor.RED + "-" + deathPenalty + " из казны клана за смерть!");
            }
        }
    }
    
    // Методы для управления режимом кланового чата
    public void toggleClanChat(Player player) {
        if (clanChatMode.contains(player.getUniqueId())) {
            clanChatMode.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Клановый чат выключен");
        } else {
            ClanMember member = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (member != null && member.hasClan()) {
                clanChatMode.add(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Клановый чат включен");
            } else {
                player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
            }
        }
    }
    
    public boolean isInClanChat(Player player) {
        return clanChatMode.contains(player.getUniqueId());
    }
}