package com.cruiser.clans.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;
import com.cruiser.clans.orm.entity.ClanRegionEntity;
import com.cruiser.clans.orm.entity.ClanRole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Сервис для работы с регионом клана
 */
public class ClanRegionService {

    private final ClanPlugin plugin;
    private final NamespacedKey markerKey;

    public ClanRegionService(ClanPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "clan_marker");
    }

    /**
     * Получить регион по локации
     */
    public CompletableFuture<Optional<ClanRegionEntity>> getRegionAtLocation(Location location) {
        return plugin.getData().findRegionsByWorld(location.getWorld().getName()).thenApply(list -> {
            for (ClanRegionEntity region : list) {
                if (region.contains(location)) {
                    return Optional.of(region);
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Обработка установки маркера региона
     */
    public CompletableFuture<Boolean> handleMarkerPlacement(Player player, Location location, ItemStack item) {
        String markerType = item.getType().name();
        return plugin.getData().findPlayerByUuid(player.getUniqueId()).thenCompose(optPlayer -> {
            if (optPlayer.isEmpty()) return CompletableFuture.completedFuture(false);
            ClanPlayerEntity cPlayer = optPlayer.get();
            if (!cPlayer.isInClan() || cPlayer.getRole() != ClanRole.LEADER) {
                return CompletableFuture.completedFuture(false);
            }
            ClanEntity clan = cPlayer.getClan();
            return plugin.getData().findClanRegion(clan.getId()).thenCompose(optRegion -> {
                if (optRegion.isEmpty()) {
                    ClanRegionEntity region = new ClanRegionEntity();
                    region.setClan(clan);
                    region.setWorldName(location.getWorld().getName());
                    region.setMarkerType(markerType);
                    region.setMarker1(location);
                    return plugin.getData().createRegion(region).thenApply(r -> true);
                } else {
                    ClanRegionEntity region = optRegion.get();
                    if (region.hasSecondMarker()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    int maxRadius = plugin.getConfig().getInt("regions.marker-blocks." + markerType + ".max-radius", 25);
                    int radius = Math.max(Math.abs(region.getMarker1X() - location.getBlockX()),
                                           Math.abs(region.getMarker1Z() - location.getBlockZ()));
                    if (radius > maxRadius) {
                        return CompletableFuture.completedFuture(false);
                    }
                    region.setMarker2(location);
                    return plugin.getData().updateRegion(region).thenApply(r -> true);
                }
            });
        });
    }

    /**
     * Обработка разрушения маркера
     * @return true если разрешено разрушить
     */
    public CompletableFuture<Boolean> handleMarkerBreak(Player player, Location location) {
        return getRegionAtLocation(location).thenCompose(optRegion -> {
            if (optRegion.isEmpty()) return CompletableFuture.completedFuture(false);
            ClanRegionEntity region = optRegion.get();
            return plugin.getData().findPlayerByUuid(player.getUniqueId()).thenCompose(optPlayer -> {
                if (optPlayer.isEmpty()) return CompletableFuture.completedFuture(false);
                ClanPlayerEntity cPlayer = optPlayer.get();
                if (!cPlayer.isInClan() || cPlayer.getRole() != ClanRole.LEADER ||
                        !cPlayer.getClan().getId().equals(region.getClan().getId())) {
                    return CompletableFuture.completedFuture(false);
                }
                return plugin.getData().deleteRegion(region.getId()).thenApply(v -> true);
            });
        });
    }

    /**
     * Проверка, является ли предмет маркером
     */
    public boolean isClanMarker(ItemStack item) {
        if (item == null) return false;
        var section = plugin.getConfig().getConfigurationSection("regions.marker-blocks");
        if (section == null) return false;
        if (!section.getKeys(false).contains(item.getType().name())) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * Выдать лидеру маркер региона
     */
    public void giveRegionMarker(Player player, String markerType) {
        var section = plugin.getConfig().getConfigurationSection("regions.marker-blocks." + markerType);
        if (section == null) {
            player.sendMessage(Component.text("Неизвестный тип маркера", NamedTextColor.RED));
            return;
        }

        var optPlayer = plugin.getData().findPlayerByUuid(player.getUniqueId()).join();
        if (optPlayer.isEmpty() || !optPlayer.get().isInClan()) {
            player.sendMessage(Component.text("Вы не состоите в клане", NamedTextColor.RED));
            return;
        }
        if (optPlayer.get().getRole() != ClanRole.LEADER) {
            player.sendMessage(Component.text("Маркер может получить только лидер клана", NamedTextColor.RED));
            return;
        }

        var clan = optPlayer.get().getClan();
        Optional<ClanRegionEntity> optRegion = plugin.getData().findClanRegion(clan.getId()).join();

        int markersInInv = Arrays.stream(player.getInventory().getContents())
            .filter(this::isClanMarker)
            .mapToInt(ItemStack::getAmount)
            .sum();

        if (optRegion.isPresent()) {
            if (optRegion.get().hasSecondMarker()) {
                player.sendMessage(Component.text("У вашего клана уже установлен регион", NamedTextColor.RED));
                return;
            }
            if (markersInInv >= 1) {
                player.sendMessage(Component.text("У вас уже есть маркер", NamedTextColor.RED));
                return;
            }
        } else {
            if (markersInInv >= 2) {
                player.sendMessage(Component.text("У вас уже есть два маркера", NamedTextColor.RED));
                return;
            }
        }

        Material mat;
        try {
            mat = Material.valueOf(markerType);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text("Неверный материал", NamedTextColor.RED));
            return;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String name = section.getString("display-name", markerType);
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
    }
}
