package com.cruiser.clans.orm.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "clans",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_clan_name", columnNames = "name"),
        @UniqueConstraint(name = "uq_clan_tag", columnNames = "tag")
    }
)
public class ClanEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(nullable = false, length = 32, unique = true)
    private String name; // Полное имя клана с учетом регистра
    
    @Column(nullable = false, length = 8, unique = true)
    private String tag; // Тег клана для отображения
    
    @Column(name = "description", length = 256)
    private String description;
    
    @Column(name = "leader_uuid", nullable = false, length = 36)
    private String leaderUuid; // UUID лидера
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "max_members", nullable = false)
    private Integer maxMembers = 10; // Лимит участников
    
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true; // Открытый/закрытый клан
    
    @Column(name = "min_level")
    private Integer minLevel = 0; // Минимальный уровень для вступления
    
    // Статистика клана
    @Column(name = "total_kills", nullable = false)
    private Integer totalKills = 0;
    
    @Column(name = "total_deaths", nullable = false)
    private Integer totalDeaths = 0;
    
    @Column(name = "clan_level", nullable = false)
    private Integer clanLevel = 1;
    
    @Column(name = "clan_exp", nullable = false)
    private Integer clanExp = 0;
    
    // Связь с игроками - LAZY для производительности
    @OneToMany(mappedBy = "clan", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<ClanPlayerEntity> members = new HashSet<>();
    
    // Lifecycle hooks
    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
    
    // Getters and Setters
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