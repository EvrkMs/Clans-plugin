package com.cruiser.models;

import java.sql.Timestamp;
import java.util.UUID;

public class ClanMember {
    private final UUID uuid;
    private String name;
    private int clanId;
    private Clan.ClanRole role;
    private Timestamp joinedAt;
    private int kills;
    private int deaths;
    private boolean modified;
    
    public ClanMember(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.role = Clan.ClanRole.MEMBER;
        this.joinedAt = new Timestamp(System.currentTimeMillis());
        this.kills = 0;
        this.deaths = 0;
        this.modified = false;
    }
    
    // Геттеры
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getClanId() { return clanId; }
    public Clan.ClanRole getRole() { return role; }
    public Timestamp getJoinedAt() { return joinedAt; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public boolean isModified() { return modified; }
    
    // Сеттеры с пометкой изменений
    public void setName(String name) {
        this.name = name;
        this.modified = true;
    }
    
    public void setClanId(int clanId) {
        this.clanId = clanId;
        this.modified = true;
    }
    
    public void setRole(Clan.ClanRole role) {
        this.role = role;
        this.modified = true;
    }
    
    public void setJoinedAt(Timestamp joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    public void setKills(int kills) {
        this.kills = kills;
        this.modified = true;
    }
    
    public void setDeaths(int deaths) {
        this.deaths = deaths;
        this.modified = true;
    }
    
    public void setModified(boolean modified) {
        this.modified = modified;
    }
    
    // Вспомогательные методы
    public void addKill() {
        this.kills++;
        this.modified = true;
    }
    
    public void addDeath() {
        this.deaths++;
        this.modified = true;
    }
    
    public double getKDR() {
        if (deaths == 0) return kills;
        return (double) kills / deaths;
    }
    
    public boolean isLeader() {
        return role == Clan.ClanRole.LEADER;
    }
    
    public boolean isOfficer() {
        return role == Clan.ClanRole.OFFICER;
    }
    
    public boolean hasClan() {
        return clanId > 0;
    }
}