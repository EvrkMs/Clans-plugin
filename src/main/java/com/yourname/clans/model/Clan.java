package com.yourname.clans.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;

public class Clan {
    
    private final String tag;
    private String name;
    private final UUID owner;
    private final Set<UUID> members;
    private final Set<UUID> officers;
    private final Set<UUID> invites;
    private Location home;
    private boolean friendlyFire;
    private final long createdDate;
    
    public Clan(String tag, String name, UUID owner) {
        this.tag = tag;
        this.name = name;
        this.owner = owner;
        this.members = new HashSet<>();
        this.officers = new HashSet<>();
        this.invites = new HashSet<>();
        this.friendlyFire = false;
        this.createdDate = System.currentTimeMillis();
        
        // Владелец автоматически становится участником
        this.members.add(owner);
    }
    
    // Геттеры и сеттеры
    public String getTag() { return tag; }
    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getOfficers() { return officers; }
    public Set<UUID> getInvites() { return invites; }
    public Location getHome() { return home; }
    public boolean isFriendlyFire() { return friendlyFire; }
    public long getCreatedDate() { return createdDate; }
    
    public void setName(String name) { this.name = name; }
    public void setHome(Location home) { this.home = home; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }
    
    // Методы управления участниками
    public void addMember(UUID player) {
        members.add(player);
        invites.remove(player);
    }
    
    public void removeMember(UUID player) {
        members.remove(player);
        officers.remove(player);
    }
    
    public void addOfficer(UUID player) {
        if (members.contains(player)) {
            officers.add(player);
        }
    }
    
    public void removeOfficer(UUID player) {
        officers.remove(player);
    }
    
    public void addInvite(UUID player) {
        invites.add(player);
    }
    
    public void removeInvite(UUID player) {
        invites.remove(player);
    }
    
    // Проверки ролей
    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }
    
    public boolean isOfficer(UUID player) {
        return officers.contains(player);
    }
    
    public boolean isMember(UUID player) {
        return members.contains(player);
    }
    
    public boolean hasInvite(UUID player) {
        return invites.contains(player);
    }
    
    public boolean canManage(UUID player) {
        return isOwner(player) || isOfficer(player);
    }
    
    public int getTotalMembers() {
        return members.size();
    }
}