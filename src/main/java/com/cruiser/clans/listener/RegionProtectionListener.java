package com.cruiser.clans.listener;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanRegionEntity;
import com.cruiser.clans.service.ClanRegionService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Слушатель защиты регионов клана
 */
public class RegionProtectionListener implements Listener {

    private final ClanPlugin plugin;
    private final ClanRegionService regionService;

    public RegionProtectionListener(ClanPlugin plugin) {
        this.plugin = plugin;
        this.regionService = plugin.getRegionService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("regions.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        try {
            Optional<ClanRegionEntity> optRegion = regionService.getRegionAtLocation(location).join();
            if (optRegion.isPresent()) {
                ClanRegionEntity region = optRegion.get();

                boolean isMarker1 = location.getBlockX() == region.getMarker1X() &&
                                   location.getBlockY() == region.getMarker1Y() &&
                                   location.getBlockZ() == region.getMarker1Z();

                boolean isMarker2 = region.hasSecondMarker() &&
                                   location.getBlockX() == region.getMarker2X() &&
                                   location.getBlockY() == region.getMarker2Y() &&
                                   location.getBlockZ() == region.getMarker2Z();

                if (isMarker1 || isMarker2) {
                    boolean allowed = regionService.handleMarkerBreak(player, location).join();
                    if (!allowed) {
                        event.setCancelled(true);
                        String message = plugin.getConfig().getString("regions.protection.messages.no-permission",
                            "&cВы не можете делать это на территории %clan%")
                            .replace("%clan%", region.getClan().getName());
                        player.sendMessage(Component.text(message, NamedTextColor.RED));
                    }
                    return;
                }

                if (plugin.getConfig().getBoolean("regions.protection.block-break", true)) {
                    if (!checkRegionPermission(player, region)) {
                        event.setCancelled(true);
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("regions.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        ItemStack item = event.getItemInHand();

        if (regionService.isClanMarker(item)) {
            boolean success = regionService.handleMarkerPlacement(player, location, item).join();
            if (!success) {
                event.setCancelled(true);
            }
            return;
        }

        if (plugin.getConfig().getBoolean("regions.protection.block-place", true)) {
            try {
                Optional<ClanRegionEntity> optRegion = regionService.getRegionAtLocation(location).join();
                if (optRegion.isPresent()) {
                    if (!checkRegionPermission(player, optRegion.get())) {
                        event.setCancelled(true);
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("regions.enabled", true) ||
            !plugin.getConfig().getBoolean("regions.protection.container-access", true)) {
            return;
        }

        if (event.getClickedBlock() == null) return;

        switch (event.getClickedBlock().getType()) {
            case CHEST,
                 TRAPPED_CHEST,
                 BARREL,
                 SHULKER_BOX,
                 FURNACE,
                 BLAST_FURNACE,
                 SMOKER,
                 DROPPER,
                 DISPENSER,
                 HOPPER -> {
                Player player = event.getPlayer();
                Location location = event.getClickedBlock().getLocation();
                try {
                    Optional<ClanRegionEntity> optRegion = regionService.getRegionAtLocation(location).join();
                    if (optRegion.isPresent()) {
                        if (!checkRegionPermission(player, optRegion.get())) {
                            event.setCancelled(true);
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("regions.enabled", true) ||
            !plugin.getConfig().getBoolean("regions.protection.entity-damage", true)) {
            return;
        }

        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        Location location = event.getEntity().getLocation();

        try {
            Optional<ClanRegionEntity> optRegion = regionService.getRegionAtLocation(location).join();
            if (optRegion.isPresent()) {
                if (!checkRegionPermission(player, optRegion.get())) {
                    event.setCancelled(true);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
        }
    }

    private boolean checkRegionPermission(Player player, ClanRegionEntity region) {
        try {
            Optional<com.cruiser.clans.orm.entity.ClanPlayerEntity> optPlayer =
                plugin.getData().findPlayerByUuid(player.getUniqueId()).join();
            boolean hasPermission = optPlayer.isPresent() && optPlayer.get().isInClan() &&
                optPlayer.get().getClan().getId().equals(region.getClan().getId());
            if (!hasPermission) {
                String message = plugin.getConfig().getString("regions.protection.messages.no-permission",
                    "&cВы не можете делать это на территории %clan%")
                    .replace("%clan%", region.getClan().getName());
                player.sendMessage(Component.text(message, NamedTextColor.RED));
            }
            return hasPermission;
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка проверки прав игрока: " + ex.getMessage());
            return true;
        }
    }
}
