package com.cruiser.clans.orm.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClanEntity {

    private Integer id;
    private String name;
    private String tag;
    private String description;
    private String leaderUuid;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer maxMembers = 10;
    private Boolean isPublic = true;
    private Integer minLevel = 0;
    private Integer totalKills = 0;
    private Integer totalDeaths = 0;
    private Integer clanLevel = 1;
    private Integer clanExp = 0;

    private Set<ClanPlayerEntity> members = new HashSet<>();

    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLeaderUuid() { return leaderUuid; }
    public void setLeaderUuid(String leaderUuid) { this.leaderUuid = leaderUuid; }
    public void setLeaderUuid(UUID uuid) { this.leaderUuid = uuid.toString(); }
    public UUID getLeaderUuidAsUUID() { return UUID.fromString(leaderUuid); }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }

    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }

    public Integer getTotalKills() { return totalKills; }
    public void setTotalKills(Integer totalKills) { this.totalKills = totalKills; }

    public Integer getTotalDeaths() { return totalDeaths; }
    public void setTotalDeaths(Integer totalDeaths) { this.totalDeaths = totalDeaths; }

    public Integer getClanLevel() { return clanLevel; }
    public void setClanLevel(Integer clanLevel) { this.clanLevel = clanLevel; }

    public Integer getClanExp() { return clanExp; }
    public void setClanExp(Integer clanExp) { this.clanExp = clanExp; }

    public Set<ClanPlayerEntity> getMembers() { return members; }
    public void setMembers(Set<ClanPlayerEntity> members) { this.members = members; }
}

