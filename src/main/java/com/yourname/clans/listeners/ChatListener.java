package com.yourname.clans.listeners;

import com.yourname.clans.ClansPlugin;
import com.yourname.clans.model.Clan;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.util.UUID;

public class ChatListener implements Listener {
    
    private final ClansPlugin plugin;
    
    public ChatListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        @SuppressWarnings("deprecation")
        String message = event.getMessage();
        
        // Проверка на клан-чат через @c
        if (message.startsWith("@c ")) {
            event.setCancelled(true);
            
            Clan clan = plugin.getDataManager().getPlayerClan(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(ChatColor.RED + "Вы не состоите в клане!");
                return;
            }
            
            String clanMessage = message.substring(3); // Убираем "@c "
            String formattedMessage = ChatColor.GOLD + "[" + clan.getTag() + "] " + player.getName() + ": " + ChatColor.WHITE + clanMessage;
            
            // Отправка сообщения всем участникам клана
            for (UUID memberUuid : clan.getMembers()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null) {
                    member.sendMessage(formattedMessage);
                }
            }
            return;
        }
        
        // Добавление тега клана к обычным сообщениям
        Clan clan = plugin.getDataManager().getPlayerClan(player.getUniqueId());
        if (clan != null) {
            String clanPrefix = ChatColor.GRAY + "[" + clan.getTag() + "] ";
            event.setFormat(clanPrefix + event.getFormat());
        }
    }
}