package com.cruiser.clans.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.service.ClanRegionService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Слушатель предметов-маркеров региона
 */
public class RegionMarkerListener implements Listener {

    private final ClanRegionService regionService;

    public RegionMarkerListener(ClanPlugin plugin) {
        this.regionService = plugin.getRegionService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (regionService.isClanMarker(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage(Component.text(
                "Вы не можете выбрасывать маркеры региона клана!",
                NamedTextColor.RED
            ));
        }
    }
}
