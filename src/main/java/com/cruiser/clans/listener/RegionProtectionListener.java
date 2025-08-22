package com.cruiser.clans.listener;

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

        regionService.getRegionAtLocation(location).thenAccept(optRegion -> {
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
                    regionService.handleMarkerBreak(player, location).thenAccept(allowed -> {
                        if (!allowed) {
                            plugin.getData().runSync(() -> {
                                event.setCancelled(true);
                                String message = plugin.getConfig().getString("regions.protection.messages.no-permission",
                                    "&cВы не можете делать это на территории %clan%")
                                    .replace("%clan%", region.getClan().getName());
                                player.sendMessage(Component.text(message, NamedTextColor.RED));
                            });
                        }
                    });
                    return;
                }

                if (plugin.getConfig().getBoolean("regions.protection.block-break", true)) {
                    checkRegionPermission(player, region, event::setCancelled);
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
            return null;
        });
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
            regionService.handleMarkerPlacement(player, location, item).thenAccept(success -> {
                if (!success) {
                    plugin.getData().runSync(() -> event.setCancelled(true));
                }
            });
            return;
        }

        if (plugin.getConfig().getBoolean("regions.protection.block-place", true)) {
            regionService.getRegionAtLocation(location).thenAccept(optRegion -> {
                if (optRegion.isPresent()) {
                    checkRegionPermission(player, optRegion.get(), event::setCancelled);
                }
            }).exceptionally(ex -> {
                plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
                return null;
            });
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
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX, FURNACE, BLAST_FURNACE,
                 SMOKER, DROPPER, DISPENSER, HOPPER -> {
                Player player = event.getPlayer();
                Location location = event.getClickedBlock().getLocation();

                regionService.getRegionAtLocation(location).thenAccept(optRegion -> {
                    if (optRegion.isPresent()) {
                        checkRegionPermission(player, optRegion.get(), event::setCancelled);
                    }
                }).exceptionally(ex -> {
                    plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
                    return null;
                });
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

        regionService.getRegionAtLocation(location).thenAccept(optRegion -> {
            if (optRegion.isPresent()) {
                checkRegionPermission(player, optRegion.get(), event::setCancelled);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка проверки защиты региона: " + ex.getMessage());
            return null;
        });
    }

    private void checkRegionPermission(Player player, ClanRegionEntity region, Runnable denyAction) {
        plugin.getData().findPlayerByUuid(player.getUniqueId()).thenAccept(optPlayer -> {
            boolean hasPermission = false;
            if (optPlayer.isPresent() && optPlayer.get().isInClan()) {
                hasPermission = optPlayer.get().getClan().getId().equals(region.getClan().getId());
            }

            if (!hasPermission) {
                plugin.getData().runSync(() -> {
                    denyAction.run();
                    String message = plugin.getConfig().getString("regions.protection.messages.no-permission",
                        "&cВы не можете делать это на территории %clan%")
                        .replace("%clan%", region.getClan().getName());
                    player.sendMessage(Component.text(message, NamedTextColor.RED));
                });
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Ошибка проверки прав игрока: " + ex.getMessage());
            return null;
        });
    }
}
