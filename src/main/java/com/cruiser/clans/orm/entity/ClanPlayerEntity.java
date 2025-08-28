package com.cruiser.clans.orm.entity;

import java.time.Instant;
import java.util.UUID;

public class ClanPlayerEntity {

    private String uuid; // Player UUID as primary key
    private String name; // Player name case sensitive
    private ClanEntity clan; // null = not in clan
    private ClanRole role = ClanRole.MEMBER;
    private Instant joinedAt; // When joined clan
    private Instant lastSeen;
    private Integer playerLevel = 1;
    private Integer kills = 0;
    private Integer deaths = 0;
    private Integer clanContribution = 0; // Contribution to clan (exp, kills etc.)
    private String invitedByUuid; // Who invited to clan
    private Integer invitePendingClanId; // Clan ID with active invitation
    private Instant inviteExpiresAt; // When invitation expires
    private Long permissions = 0L;

    public void updateLastSeen() {
        if (lastSeen == null) lastSeen = Instant.now();
    }

    public boolean isInClan() { return clan != null; }

    public boolean hasPendingInvite() {
        return invitePendingClanId != null && inviteExpiresAt != null && inviteExpiresAt.isAfter(Instant.now());
    }

    public void clearInvite() {
        invitePendingClanId = null;
        inviteExpiresAt = null;
        invitedByUuid = null;
    }

    public double getKDR() { return deaths == 0 ? kills : (double) kills / deaths; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid.toString(); }
    public UUID getUuidAsUUID() { return UUID.fromString(uuid); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ClanEntity getClan() { return clan; }
    public void setClan(ClanEntity clan) {
        this.clan = clan;
        if (clan != null && joinedAt == null) joinedAt = Instant.now();
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

