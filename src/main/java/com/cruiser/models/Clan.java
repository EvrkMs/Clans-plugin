package com.cruiser.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Clan {
    private int id;
    private String name;
    private String tag;
    private UUID leaderUuid;
    private Timestamp createdAt;
    private int level;
    private double balance;
    private int maxMembers;
    private Location home;
    
    // Кеш членов клана для быстрого доступа
    private final Map<UUID, ClanMember> members = new ConcurrentHashMap<>();
    private final Set<Integer> allies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> invites = ConcurrentHashMap.newKeySet();
    
    // Флаг для отслеживания изменений
    private boolean modified = false;
    
    public Clan(int id, String name, String tag, UUID leaderUuid) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderUuid = leaderUuid;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.level = 1;
        this.balance = 0;
        this.maxMembers = 10;
    }
    
    // Геттеры
    public int getId() { return id; }
    public String getName() { return name; }
    public String getTag() { return tag; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public Timestamp getCreatedAt() { return createdAt; }
    public int getLevel() { return level; }
    public double getBalance() { return balance; }
    public int getMaxMembers() { return maxMembers; }
    public Location getHome() { return home; }
    public boolean isModified() { return modified; }
    
    // Сеттеры с пометкой изменений
    public void setId(int id) {
        this.id = id;
    }
    
    public void setName(String name) {
        this.name = name;
        this.modified = true;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
        this.modified = true;
    }
    
    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
        this.modified = true;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setLevel(int level) {
        this.level = level;
        this.modified = true;
    }
    
    public void setBalance(double balance) {
        this.balance = balance;
        this.modified = true;
    }
    
    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
        this.modified = true;
    }
    
    public void setHome(Location home) {
        this.home = home;
        this.modified = true;
    }
    
    public void setModified(boolean modified) {
        this.modified = modified;
    }
    
    // Методы для работы с членами клана
    public void addMember(ClanMember member) {
        members.put(member.getUuid(), member);
        this.modified = true;
    }
    
    public void removeMember(UUID uuid) {
        members.remove(uuid);
        this.modified = true;
    }
    
    public ClanMember getMember(UUID uuid) {
        return members.get(uuid);
    }
    
    public Collection<ClanMember> getMembers() {
        return members.values();
    }
    
    public int getMemberCount() {
        return members.size();
    }
    
    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }
    
    public boolean isFull() {
        return members.size() >= maxMembers;
    }
    
    // Методы для работы с союзами
    public void addAlly(int clanId) {
        allies.add(clanId);
        this.modified = true;
    }
    
    public void removeAlly(int clanId) {
        allies.remove(clanId);
        this.modified = true;
    }
    
    public boolean isAlly(int clanId) {
        return allies.contains(clanId);
    }
    
    public Set<Integer> getAllies() {
        return new HashSet<>(allies);
    }
    
    // Методы для работы с приглашениями
    public void addInvite(UUID playerUuid) {
        invites.add(playerUuid);
    }
    
    public void removeInvite(UUID playerUuid) {
        invites.remove(playerUuid);
    }
    
    public boolean hasInvite(UUID playerUuid) {
        return invites.contains(playerUuid);
    }
    
    public Set<UUID> getInvites() {
        return new HashSet<>(invites);
    }
    
    // Вспомогательные методы
    public List<UUID> getOnlineMembers() {
        List<UUID> online = new ArrayList<>();
        for (ClanMember member : members.values()) {
            if (Bukkit.getPlayer(member.getUuid()) != null) {
                online.add(member.getUuid());
            }
        }
        return online;
    }
    
    public void deposit(double amount) {
        this.balance += amount;
        this.modified = true;
    }
    
    public boolean withdraw(double amount) {
        if (balance >= amount) {
            this.balance -= amount;
            this.modified = true;
            return true;
        }
        return false;
    }
    
    public enum ClanRole {
        LEADER("Лидер", 3),
        OFFICER("Офицер", 2),
        MEMBER("Участник", 1);
        
        private final String displayName;
        private final int power;
        
        ClanRole(String displayName, int power) {
            this.displayName = displayName;
            this.power = power;
        }
        
        public String getDisplayName() { return displayName; }
        public int getPower() { return power; }
    }
}