package com.yourname.clans.listeners;

import com.yourname.clans.ClansPlugin;
import com.yourname.clans.model.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class CombatListener implements Listener {
    
    private final ClansPlugin plugin;
    
    public CombatListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        Clan damagerClan = plugin.getDataManager().getPlayerClan(damager.getUniqueId());
        Clan victimClan = plugin.getDataManager().getPlayerClan(victim.getUniqueId());
        
        // Проверка дружественного огня
        if (damagerClan != null && damagerClan.equals(victimClan)) {
            if (!damagerClan.isFriendlyFire()) {
                event.setCancelled(true);
                // Можно добавить сообщение, но это может спамить
                // damager.sendMessage(ChatColor.RED + "Нельзя атаковать союзника!");
            }
        }
    }
}