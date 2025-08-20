package com.yourname.clans.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.yourname.clans.ClansPlugin;
import com.yourname.clans.model.Clan;

public class ClanDataManager {
    
    private final ClansPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    
    private final Map<String, Clan> clans; // tag -> clan
    private final Map<UUID, String> playerClanMap; // player -> clan tag
    
    public ClanDataManager(ClansPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "clans.yml");
        this.clans = new ConcurrentHashMap<>();
        this.playerClanMap = new ConcurrentHashMap<>();
    }
    
    public void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create clans.yml file: " + e.getMessage());
                return;
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // Загрузка кланов
        if (dataConfig.contains("clans")) {
            for (String tag : dataConfig.getConfigurationSection("clans").getKeys(false)) {
                try {
                    Clan clan = loadClan(tag);
                    clans.put(tag, clan);
                    
                    // Обновление карты игрок -> клан
                    for (UUID member : clan.getMembers()) {
                        playerClanMap.put(member, tag);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load clan " + tag + ": " + e.getMessage());
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + clans.size() + " clans");
    }
    
    private Clan loadClan(String tag) {
        String path = "clans." + tag;
        
        String name = dataConfig.getString(path + ".name");
        UUID owner = UUID.fromString(dataConfig.getString(path + ".owner"));
        
        Clan clan = new Clan(tag, name, owner);
        
        // Загрузка участников
        List<String> memberStrings = dataConfig.getStringList(path + ".members");
        for (String memberString : memberStrings) {
            clan.getMembers().add(UUID.fromString(memberString));
        }
        
        // Загрузка офицеров
        List<String> officerStrings = dataConfig.getStringList(path + ".officers");
        for (String officerString : officerStrings) {
            clan.getOfficers().add(UUID.fromString(officerString));
        }
        
        // Загрузка приглашений
        List<String> inviteStrings = dataConfig.getStringList(path + ".invites");
        for (String inviteString : inviteStrings) {
            clan.getInvites().add(UUID.fromString(inviteString));
        }
        
        // Загрузка дома
        if (dataConfig.contains(path + ".home")) {
            String worldName = dataConfig.getString(path + ".home.world");
            double x = dataConfig.getDouble(path + ".home.x");
            double y = dataConfig.getDouble(path + ".home.y");
            double z = dataConfig.getDouble(path + ".home.z");
            float yaw = (float) dataConfig.getDouble(path + ".home.yaw");
            float pitch = (float) dataConfig.getDouble(path + ".home.pitch");
            
            Location home = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
            clan.setHome(home);
        }
        
        // Загрузка настроек
        clan.setFriendlyFire(dataConfig.getBoolean(path + ".friendlyFire", false));
        
        return clan;
    }
    
    public void saveData() {
        for (Clan clan : clans.values()) {
            saveClan(clan);
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save clan data: " + e.getMessage());
        }
    }
    
    private void saveClan(Clan clan) {
        String path = "clans." + clan.getTag();
        
        dataConfig.set(path + ".name", clan.getName());
        dataConfig.set(path + ".owner", clan.getOwner().toString());
        
        // Сохранение участников
        List<String> memberStrings = new ArrayList<>();
        for (UUID member : clan.getMembers()) {
            memberStrings.add(member.toString());
        }
        dataConfig.set(path + ".members", memberStrings);
        
        // Сохранение офицеров
        List<String> officerStrings = new ArrayList<>();
        for (UUID officer : clan.getOfficers()) {
            officerStrings.add(officer.toString());
        }
        dataConfig.set(path + ".officers", officerStrings);
        
        // Сохранение приглашений
        List<String> inviteStrings = new ArrayList<>();
        for (UUID invite : clan.getInvites()) {
            inviteStrings.add(invite.toString());
        }
        dataConfig.set(path + ".invites", inviteStrings);
        
        // Сохранение дома
        if (clan.getHome() != null) {
            Location home = clan.getHome();
            dataConfig.set(path + ".home.world", home.getWorld().getName());
            dataConfig.set(path + ".home.x", home.getX());
            dataConfig.set(path + ".home.y", home.getY());
            dataConfig.set(path + ".home.z", home.getZ());
            dataConfig.set(path + ".home.yaw", home.getYaw());
            dataConfig.set(path + ".home.pitch", home.getPitch());
        }
        
        // Сохранение настроек
        dataConfig.set(path + ".friendlyFire", clan.isFriendlyFire());
    }
    
    // Методы управления кланами
    public boolean createClan(String tag, String name, UUID owner) {
        if (clans.containsKey(tag.toLowerCase())) {
            return false;
        }
        
        if (playerClanMap.containsKey(owner)) {
            return false;
        }
        
        Clan clan = new Clan(tag.toLowerCase(), name, owner);
        clans.put(tag.toLowerCase(), clan);
        playerClanMap.put(owner, tag.toLowerCase());
        
        saveClan(clan);
        return true;
    }
    
    public boolean disbandClan(String tag) {
        Clan clan = clans.get(tag.toLowerCase());
        if (clan == null) return false;
        
        // Удаляем всех игроков из карты
        for (UUID member : clan.getMembers()) {
            playerClanMap.remove(member);
        }
        
        clans.remove(tag.toLowerCase());
        dataConfig.set("clans." + tag.toLowerCase(), null);
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save after disbanding clan: " + e.getMessage());
        }
        
        return true;
    }
    
    public Clan getClan(String tag) {
        return clans.get(tag.toLowerCase());
    }
    
    public Clan getPlayerClan(UUID player) {
        String tag = playerClanMap.get(player);
        return tag != null ? clans.get(tag) : null;
    }
    
    public void updatePlayerClan(UUID player, String newClanTag) {
        if (newClanTag == null) {
            playerClanMap.remove(player);
        } else {
            playerClanMap.put(player, newClanTag.toLowerCase());
        }
    }
    
    public Collection<Clan> getAllClans() {
        return clans.values();
    }
    
    public boolean clanExists(String tag) {
        return clans.containsKey(tag.toLowerCase());
    }
}