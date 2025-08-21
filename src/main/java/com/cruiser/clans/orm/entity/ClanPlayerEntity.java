package com.cruiser.clans.orm.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

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
    private String uuid; // UUID игрока как primary key
    
    @Column(nullable = false, length = 16)
    private String name; // Имя игрока с учетом регистра
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "clan_id",
        foreignKey = @ForeignKey(name = "fk_clan_players_clan")
    )
    private ClanEntity clan; // null = не в клане
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClanRole role = ClanRole.MEMBER;
    
    @Column(name = "joined_at")
    private Instant joinedAt; // Когда вступил в клан
    
    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;
    
    @Column(name = "player_level", nullable = false)
    private Integer playerLevel = 1;
    
    // Личная статистика
    @Column(nullable = false)
    private Integer kills = 0;
    
    @Column(nullable = false)
    private Integer deaths = 0;
    
    @Column(name = "clan_contribution", nullable = false)
    private Integer clanContribution = 0; // Вклад в клан (опыт, киллы и т.д.)
    
    // Приглашения
    @Column(name = "invited_by_uuid", length = 36)
    private String invitedByUuid; // Кто пригласил в клан
    
    @Column(name = "invite_pending_clan_id")
    private Integer invitePendingClanId; // ID клана с активным приглашением
    
    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt; // Когда истекает приглашение
    
    // Permissions flags (битовая маска для быстрой проверки)
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