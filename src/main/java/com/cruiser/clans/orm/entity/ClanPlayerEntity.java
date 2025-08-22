package com.cruiser.clans.orm.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "clan_players",
    indexes = {
        @Index(name = "idx_clan_players_name", columnList = "name"),
        @Index(name = "idx_clan_players_clan", columnList = "clan_id")
    }
)
public class ClanPlayerEntity {
    
    @Id
    @Column(length = 36, nullable = false)
    private String uuid; // Player UUID as primary key
    
    @Column(nullable = false, length = 16)
    private String name; // Player name case sensitive
    
    @ManyToOne(fetch = FetchType.EAGER) // ИЗМЕНЕНО НА EAGER!
    @JoinColumn(
        name = "clan_id",
        foreignKey = @ForeignKey(name = "fk_clan_players_clan")
    )
    private ClanEntity clan; // null = not in clan
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClanRole role = ClanRole.MEMBER;
    
    @Column(name = "joined_at")
    private Instant joinedAt; // When joined clan
    
    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;
    
    @Column(name = "player_level", nullable = false)
    private Integer playerLevel = 1;
    
    // Personal statistics
    @Column(nullable = false)
    private Integer kills = 0;
    
    @Column(nullable = false)
    private Integer deaths = 0;
    
    @Column(name = "clan_contribution", nullable = false)
    private Integer clanContribution = 0; // Contribution to clan (exp, kills etc.)
    
    // Invitations
    @Column(name = "invited_by_uuid", length = 36)
    private String invitedByUuid; // Who invited to clan
    
    @Column(name = "invite_pending_clan_id")
    private Integer invitePendingClanId; // Clan ID with active invitation
    
    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt; // When invitation expires
    
    // Permissions flags (bitmask for fast checking)
    @Column(name = "permissions", nullable = false)
    private Long permissions = 0L;
    
    @PrePersist
    @PreUpdate
    public void updateLastSeen() {
        if (lastSeen == null) lastSeen = Instant.now();
    }
    
    // Utility methods
    public boolean isInClan() {
        return clan != null;
    }
    
    public boolean hasPendingInvite() {
        return invitePendingClanId != null && 
               inviteExpiresAt != null && 
               inviteExpiresAt.isAfter(Instant.now());
    }
    
    public void clearInvite() {
        invitePendingClanId = null;
        inviteExpiresAt = null;
        invitedByUuid = null;
    }
    
    public double getKDR() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
    
    // Getters and Setters
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid.toString(); }
    public UUID getUuidAsUUID() { return UUID.fromString(uuid); }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public ClanEntity getClan() { return clan; }
    public void setClan(ClanEntity clan) { 
        this.clan = clan;
        if (clan != null && joinedAt == null) {
            joinedAt = Instant.now();
        }
    }
    
    public ClanRole getRole() { return role; }
    public void setRole(ClanRole role) { this.role = role; }
    
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
    
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    
    public Integer getPlayerLevel() { return playerLevel; }
    public void setPlayerLevel(Integer playerLevel) { this.playerLevel = playerLevel; }
    
    public Integer getKills() { return kills; }
    public void setKills(Integer kills) { this.kills = kills; }
    
    public Integer getDeaths() { return deaths; }
    public void setDeaths(Integer deaths) { this.deaths = deaths; }
    
    public Integer getClanContribution() { return clanContribution; }
    public void setClanContribution(Integer clanContribution) { this.clanContribution = clanContribution; }
    
    public String getInvitedByUuid() { return invitedByUuid; }
    public void setInvitedByUuid(String invitedByUuid) { this.invitedByUuid = invitedByUuid; }
    
    public Integer getInvitePendingClanId() { return invitePendingClanId; }
    public void setInvitePendingClanId(Integer invitePendingClanId) { this.invitePendingClanId = invitePendingClanId; }
    
    public Instant getInviteExpiresAt() { return inviteExpiresAt; }
    public void setInviteExpiresAt(Instant inviteExpiresAt) { this.inviteExpiresAt = inviteExpiresAt; }
    
    public Long getPermissions() { return permissions; }
    public void setPermissions(Long permissions) { this.permissions = permissions; }
}