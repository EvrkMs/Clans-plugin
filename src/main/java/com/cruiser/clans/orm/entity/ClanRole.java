package com.cruiser.clans.orm.entity;

public enum ClanRole {
    LEADER(3, "Лидер"),
    OFFICER(2, "Офицер"),
    MEMBER(1, "Участник"),
    RECRUIT(0, "Новобранец");
    
    private final int power;
    private final String displayName;
    
    ClanRole(int power, String displayName) {
        this.power = power;
        this.displayName = displayName;
    }
    
    public int getPower() {
        return power;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean canKick(ClanRole other) {
        return this.power > other.power;
    }
    
    public boolean canInvite() {
        return this.power >= OFFICER.power;
    }
    
    public boolean canPromote(ClanRole other) {
        return this == LEADER && other.power < OFFICER.power;
    }
    
    public boolean canDemote(ClanRole other) {
        return this == LEADER && other != LEADER && other != MEMBER;
    }
    
    public boolean canEditClan() {
        return this == LEADER;
    }
    
    public boolean canManageAlliances() {
        return this.power >= OFFICER.power;
    }
}