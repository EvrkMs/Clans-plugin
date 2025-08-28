package com.cruiser.clans.orm;

import com.cruiser.clans.ClanPlugin;
import com.cruiser.clans.orm.entity.ClanEntity;
import com.cruiser.clans.orm.entity.ClanPlayerEntity;
import com.cruiser.clans.orm.entity.ClanRegionEntity;
import com.cruiser.clans.orm.entity.ClanRole;
import org.bukkit.Bukkit;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DataManager {

    private final ClanPlugin plugin;
    private final Database db;

    public DataManager(ClanPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    // region Clan queries

    public CompletableFuture<Optional<ClanEntity>> findClanById(Integer id) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clans WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapClan(rs));
                    return Optional.empty();
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Optional<ClanEntity>> findClanByName(String name) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clans WHERE name = ? LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapClan(rs));
                    return Optional.empty();
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Optional<ClanEntity>> findClanByTag(String tag) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clans WHERE tag = ? LIMIT 1")) {
                ps.setString(1, tag);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapClan(rs));
                    return Optional.empty();
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<List<ClanEntity>> getTopClansByKills(int limit) {
        return db.withConnection(c -> {
            List<ClanEntity> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clans ORDER BY total_kills DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapClan(rs));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        });
    }

    /**
     * Returns all clans ordered by level DESC, then kills DESC.
     */
    public CompletableFuture<List<ClanEntity>> getAllClansOrderedByLevelAndKills() {
        return db.withConnection(c -> {
            List<ClanEntity> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clans ORDER BY clan_level DESC, total_kills DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapClan(rs));
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        });
    }

    /**
     * Returns top N clans ordered by level DESC, then kills DESC.
     */
    public CompletableFuture<List<ClanEntity>> getClansOrderedByLevelAndKills(int limit) {
        return db.withConnection(c -> {
            List<ClanEntity> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clans ORDER BY clan_level DESC, total_kills DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapClan(rs));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        });
    }

    public CompletableFuture<List<String>> getAllClanNames() {
        return db.withConnection(c -> {
            List<String> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT name FROM clans"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString(1));
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        });
    }

    public CompletableFuture<ClanEntity> createClan(ClanEntity clan) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clans(name, tag, description, leader_uuid, created_at, updated_at, max_members, is_public, min_level, total_kills, total_deaths, clan_level, clan_exp) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, clan.getName());
                ps.setString(2, clan.getTag());
                ps.setString(3, clan.getDescription());
                ps.setString(4, clan.getLeaderUuid());
                ps.setLong(5, toEpoch(clan.getCreatedAt()));
                ps.setObject(6, clan.getUpdatedAt() == null ? null : toEpoch(clan.getUpdatedAt()), Types.BIGINT);
                ps.setInt(7, nvl(clan.getMaxMembers(), 10));
                ps.setInt(8, clan.getIsPublic() != null && clan.getIsPublic() ? 1 : 0);
                ps.setInt(9, nvl(clan.getMinLevel(), 0));
                ps.setInt(10, nvl(clan.getTotalKills(), 0));
                ps.setInt(11, nvl(clan.getTotalDeaths(), 0));
                ps.setInt(12, nvl(clan.getClanLevel(), 1));
                ps.setInt(13, nvl(clan.getClanExp(), 0));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) clan.setId(keys.getInt(1));
                }
                return clan;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<ClanEntity> updateClan(ClanEntity clan) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "UPDATE clans SET name=?, tag=?, description=?, leader_uuid=?, created_at=?, updated_at=?, max_members=?, is_public=?, min_level=?, total_kills=?, total_deaths=?, clan_level=?, clan_exp=? WHERE id=?")) {
                ps.setString(1, clan.getName());
                ps.setString(2, clan.getTag());
                ps.setString(3, clan.getDescription());
                ps.setString(4, clan.getLeaderUuid());
                ps.setLong(5, toEpoch(clan.getCreatedAt()));
                ps.setObject(6, clan.getUpdatedAt() == null ? null : toEpoch(clan.getUpdatedAt()), Types.BIGINT);
                ps.setInt(7, nvl(clan.getMaxMembers(), 10));
                ps.setInt(8, clan.getIsPublic() != null && clan.getIsPublic() ? 1 : 0);
                ps.setInt(9, nvl(clan.getMinLevel(), 0));
                ps.setInt(10, nvl(clan.getTotalKills(), 0));
                ps.setInt(11, nvl(clan.getTotalDeaths(), 0));
                ps.setInt(12, nvl(clan.getClanLevel(), 1));
                ps.setInt(13, nvl(clan.getClanExp(), 0));
                ps.setInt(14, clan.getId());
                ps.executeUpdate();
                return clan;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Void> deleteClan(Integer clanId) {
        return db.inTransaction(c -> {
            try (PreparedStatement clearPlayers = c.prepareStatement(
                    "UPDATE clan_players SET clan_id=NULL, role='MEMBER', joined_at=NULL, clan_contribution=0 WHERE clan_id=?");
                 PreparedStatement delRegion = c.prepareStatement(
                    "DELETE FROM clan_regions WHERE clan_id=?");
                 PreparedStatement delClan = c.prepareStatement(
                    "DELETE FROM clans WHERE id=?");
            ) {
                clearPlayers.setInt(1, clanId);
                clearPlayers.executeUpdate();
                delRegion.setInt(1, clanId);
                delRegion.executeUpdate();
                delClan.setInt(1, clanId);
                delClan.executeUpdate();
                return null;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    // endregion

    // region Player queries

    public CompletableFuture<Optional<ClanPlayerEntity>> findPlayerByUuid(UUID uuid) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.*, c.* FROM clan_players p LEFT JOIN clans c ON c.id = p.clan_id WHERE p.uuid = ? LIMIT 1")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapPlayerWithClan(rs));
                    return Optional.empty();
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Optional<ClanPlayerEntity>> findPlayerByName(String name) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.*, c.* FROM clan_players p LEFT JOIN clans c ON c.id = p.clan_id WHERE p.name = ? LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapPlayerWithClan(rs));
                    return Optional.empty();
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<List<ClanPlayerEntity>> getClanMembers(Integer clanId) {
        return db.withConnection(c -> {
            List<ClanPlayerEntity> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.*, c.* FROM clan_players p JOIN clans c ON c.id = p.clan_id WHERE p.clan_id = ?")) {
                ps.setInt(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapPlayerWithClan(rs));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        });
    }

    public CompletableFuture<ClanPlayerEntity> savePlayer(ClanPlayerEntity player) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clan_players(uuid, name, clan_id, role, joined_at, last_seen, player_level, kills, deaths, clan_contribution, invited_by_uuid, invite_pending_clan_id, invite_expires_at, permissions) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, clan_id=excluded.clan_id, role=excluded.role, joined_at=excluded.joined_at, last_seen=excluded.last_seen, player_level=excluded.player_level, kills=excluded.kills, deaths=excluded.deaths, clan_contribution=excluded.clan_contribution, invited_by_uuid=excluded.invited_by_uuid, invite_pending_clan_id=excluded.invite_pending_clan_id, invite_expires_at=excluded.invite_expires_at, permissions=excluded.permissions"
            )) {
                ps.setString(1, player.getUuid());
                ps.setString(2, player.getName());
                if (player.getClan() != null) ps.setInt(3, player.getClan().getId()); else ps.setNull(3, Types.INTEGER);
                ps.setString(4, player.getRole() == null ? ClanRole.MEMBER.name() : player.getRole().name());
                ps.setObject(5, player.getJoinedAt() == null ? null : toEpoch(player.getJoinedAt()), Types.BIGINT);
                ps.setLong(6, toEpoch(nvl(player.getLastSeen(), Instant.now())));
                ps.setInt(7, nvl(player.getPlayerLevel(), 1));
                ps.setInt(8, nvl(player.getKills(), 0));
                ps.setInt(9, nvl(player.getDeaths(), 0));
                ps.setInt(10, nvl(player.getClanContribution(), 0));
                ps.setString(11, player.getInvitedByUuid());
                if (player.getInvitePendingClanId() != null) ps.setInt(12, player.getInvitePendingClanId()); else ps.setNull(12, Types.INTEGER);
                ps.setObject(13, player.getInviteExpiresAt() == null ? null : toEpoch(player.getInviteExpiresAt()), Types.BIGINT);
                ps.setLong(14, nvl(player.getPermissions(), 0L));
                ps.executeUpdate();
                return player;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<List<ClanPlayerEntity>> findPlayersWithExpiredInvites(Instant now) {
        return db.withConnection(c -> {
            List<ClanPlayerEntity> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM clan_players WHERE invite_expires_at IS NOT NULL AND invite_expires_at < ?")) {
                ps.setLong(1, toEpoch(now));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapPlayer(rs));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        });
    }

    public CompletableFuture<Void> recordPlayerDeath(UUID victimUuid) {
        return db.inTransaction(c -> {
            try (PreparedStatement incDeaths = c.prepareStatement("UPDATE clan_players SET deaths=deaths+1 WHERE uuid=?");
                 PreparedStatement getClan = c.prepareStatement("SELECT clan_id FROM clan_players WHERE uuid=?");
                 PreparedStatement incClan = c.prepareStatement("UPDATE clans SET total_deaths=total_deaths+1 WHERE id=?");
            ) {
                incDeaths.setString(1, victimUuid.toString());
                incDeaths.executeUpdate();
                getClan.setString(1, victimUuid.toString());
                try (ResultSet rs = getClan.executeQuery()) {
                    if (rs.next()) {
                        int clanId = rs.getInt(1);
                        if (!rs.wasNull()) {
                            incClan.setInt(1, clanId);
                            incClan.executeUpdate();
                        }
                    }
                }
                return null;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Optional<ClanEntity>> recordPlayerKillAndReturnClan(UUID killerUuid, int expGain) {
        return db.inTransaction(c -> {
            try (PreparedStatement incKills = c.prepareStatement("UPDATE clan_players SET kills=kills+1 WHERE uuid=?");
                 PreparedStatement getClan = c.prepareStatement("SELECT clan_id FROM clan_players WHERE uuid=?");
                 PreparedStatement incClan = c.prepareStatement("UPDATE clans SET total_kills=total_kills+1, clan_exp=clan_exp+? WHERE id=?");
                 PreparedStatement incContr = c.prepareStatement("UPDATE clan_players SET clan_contribution=clan_contribution+? WHERE uuid=?");
                 PreparedStatement getClanRow = c.prepareStatement("SELECT * FROM clans WHERE id=?");
            ) {
                incKills.setString(1, killerUuid.toString());
                incKills.executeUpdate();
                getClan.setString(1, killerUuid.toString());
                try (ResultSet rs = getClan.executeQuery()) {
                    if (rs.next()) {
                        int clanId = rs.getInt(1);
                        if (!rs.wasNull()) {
                            incClan.setInt(1, expGain);
                            incClan.setInt(2, clanId);
                            incClan.executeUpdate();
                            incContr.setInt(1, expGain);
                            incContr.setString(2, killerUuid.toString());
                            incContr.executeUpdate();
                            getClanRow.setInt(1, clanId);
                            try (ResultSet crs = getClanRow.executeQuery()) {
                                if (crs.next()) return Optional.of(mapClan(crs));
                            }
                        }
                    }
                }
                return Optional.empty();
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    /**
     * Transfer clan leadership: demote old leader to OFFICER, promote new leader to LEADER,
     * and update clan leader_uuid. All within a single transaction.
     */
    public CompletableFuture<Boolean> transferLeadership(ClanPlayerEntity oldLeader, ClanPlayerEntity newLeader, ClanEntity clan) {
        return db.inTransaction(c -> {
            try (PreparedStatement demote = c.prepareStatement("UPDATE clan_players SET role=? WHERE uuid=?");
                 PreparedStatement promote = c.prepareStatement("UPDATE clan_players SET role=? WHERE uuid=?");
                 PreparedStatement updClan = c.prepareStatement("UPDATE clans SET leader_uuid=? WHERE id=?");
            ) {
                demote.setString(1, ClanRole.OFFICER.name());
                demote.setString(2, oldLeader.getUuid());
                demote.executeUpdate();

                promote.setString(1, ClanRole.LEADER.name());
                promote.setString(2, newLeader.getUuid());
                promote.executeUpdate();

                updClan.setString(1, newLeader.getUuid());
                updClan.setInt(2, clan.getId());
                updClan.executeUpdate();

                return true;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    // endregion

    // region Regions

    public CompletableFuture<ClanRegionEntity> createRegion(ClanRegionEntity region) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clan_regions(clan_id, world_name, marker_type, marker1_x, marker1_y, marker1_z, marker2_x, marker2_y, marker2_z) VALUES (?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, region.getClan().getId());
                ps.setString(2, region.getWorldName());
                ps.setString(3, region.getMarkerType());
                ps.setInt(4, region.getMarker1X());
                ps.setInt(5, region.getMarker1Y());
                ps.setInt(6, region.getMarker1Z());
                if (region.getMarker2X() != null) ps.setInt(7, region.getMarker2X()); else ps.setNull(7, Types.INTEGER);
                if (region.getMarker2Y() != null) ps.setInt(8, region.getMarker2Y()); else ps.setNull(8, Types.INTEGER);
                if (region.getMarker2Z() != null) ps.setInt(9, region.getMarker2Z()); else ps.setNull(9, Types.INTEGER);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) region.setId(keys.getInt(1));
                }
                return region;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<ClanRegionEntity> updateRegion(ClanRegionEntity region) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "UPDATE clan_regions SET clan_id=?, world_name=?, marker_type=?, marker1_x=?, marker1_y=?, marker1_z=?, marker2_x=?, marker2_y=?, marker2_z=? WHERE id=?")) {
                ps.setInt(1, region.getClan().getId());
                ps.setString(2, region.getWorldName());
                ps.setString(3, region.getMarkerType());
                ps.setInt(4, region.getMarker1X());
                ps.setInt(5, region.getMarker1Y());
                ps.setInt(6, region.getMarker1Z());
                if (region.getMarker2X() != null) ps.setInt(7, region.getMarker2X()); else ps.setNull(7, Types.INTEGER);
                if (region.getMarker2Y() != null) ps.setInt(8, region.getMarker2Y()); else ps.setNull(8, Types.INTEGER);
                if (region.getMarker2Z() != null) ps.setInt(9, region.getMarker2Z()); else ps.setNull(9, Types.INTEGER);
                ps.setInt(10, region.getId());
                ps.executeUpdate();
                return region;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Void> deleteRegion(Integer regionId) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_regions WHERE id=?")) {
                ps.setInt(1, regionId);
                ps.executeUpdate();
                return null;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Optional<ClanRegionEntity>> findRegionById(Integer id) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, c.* FROM clan_regions r JOIN clans c ON c.id = r.clan_id WHERE r.id = ? LIMIT 1")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRegionWithClan(rs));
                    return Optional.empty();
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Optional<ClanRegionEntity>> findClanRegion(Integer clanId) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, c.* FROM clan_regions r JOIN clans c ON c.id = r.clan_id WHERE r.clan_id = ? LIMIT 1")) {
                ps.setInt(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRegionWithClan(rs));
                    return Optional.empty();
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<List<ClanRegionEntity>> findRegionsByWorld(String worldName) {
        return db.withConnection(c -> {
            List<ClanRegionEntity> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, c.* FROM clan_regions r JOIN clans c ON c.id = r.clan_id WHERE r.world_name = ?")) {
                ps.setString(1, worldName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRegionWithClan(rs));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        });
    }

    // endregion

    // region Stats

    public CompletableFuture<Long> getClansCount() {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM clans"); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Long> getPlayersInClansCount() {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM clan_players WHERE clan_id IS NOT NULL"); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Long> getRegionsCount() {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM clan_regions"); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    // endregion

    // region Threading helper

    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    // endregion

    // region Mappers

    private static ClanEntity mapClan(ResultSet rs) throws SQLException {
        ClanEntity c = new ClanEntity();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setTag(rs.getString("tag"));
        c.setDescription(rs.getString("description"));
        c.setLeaderUuid(rs.getString("leader_uuid"));
        c.setCreatedAt(fromEpoch(rs.getLong("created_at")));
        long upd = rs.getLong("updated_at");
        if (!rs.wasNull()) c.setUpdatedAt(fromEpoch(upd));
        c.setMaxMembers(rs.getInt("max_members"));
        c.setIsPublic(rs.getInt("is_public") != 0);
        c.setMinLevel(rs.getInt("min_level"));
        c.setTotalKills(rs.getInt("total_kills"));
        c.setTotalDeaths(rs.getInt("total_deaths"));
        c.setClanLevel(rs.getInt("clan_level"));
        c.setClanExp(rs.getInt("clan_exp"));
        return c;
    }

    private static ClanPlayerEntity mapPlayer(ResultSet rs) throws SQLException {
        ClanPlayerEntity p = new ClanPlayerEntity();
        p.setUuid(rs.getString("uuid"));
        p.setName(rs.getString("name"));
        String role = rs.getString("role");
        p.setRole(role == null ? ClanRole.MEMBER : ClanRole.valueOf(role));
        long js = rs.getLong("joined_at"); if (!rs.wasNull()) p.setJoinedAt(fromEpoch(js));
        p.setLastSeen(fromEpoch(rs.getLong("last_seen")));
        p.setPlayerLevel(rs.getInt("player_level"));
        p.setKills(rs.getInt("kills"));
        p.setDeaths(rs.getInt("deaths"));
        p.setClanContribution(rs.getInt("clan_contribution"));
        p.setInvitedByUuid(rs.getString("invited_by_uuid"));
        int ipc = rs.getInt("invite_pending_clan_id"); if (!rs.wasNull()) p.setInvitePendingClanId(ipc);
        long iea = rs.getLong("invite_expires_at"); if (!rs.wasNull()) p.setInviteExpiresAt(fromEpoch(iea));
        p.setPermissions(rs.getLong("permissions"));
        return p;
    }

    private static ClanPlayerEntity mapPlayerWithClan(ResultSet rs) throws SQLException {
        ClanPlayerEntity p = mapPlayer(rs);
        // If the result set also contains clan columns (due to JOIN)
        int clanId = 0;
        try { clanId = rs.getInt("id"); } catch (SQLException ignored) {}
        if (clanId != 0 && !rs.wasNull()) {
            ClanEntity c = mapClan(rs);
            p.setClan(c);
        }
        return p;
    }

    private static ClanRegionEntity mapRegionWithClan(ResultSet rs) throws SQLException {
        ClanRegionEntity r = new ClanRegionEntity();
        r.setId(rs.getInt("id"));
        ClanEntity c = mapClan(rs);
        r.setClan(c);
        r.setWorldName(rs.getString("world_name"));
        r.setMarkerType(rs.getString("marker_type"));
        r.setMarker1X(rs.getInt("marker1_x"));
        r.setMarker1Y(rs.getInt("marker1_y"));
        r.setMarker1Z(rs.getInt("marker1_z"));
        int m2x = rs.getInt("marker2_x"); if (!rs.wasNull()) r.setMarker2X(m2x);
        int m2y = rs.getInt("marker2_y"); if (!rs.wasNull()) r.setMarker2Y(m2y);
        int m2z = rs.getInt("marker2_z"); if (!rs.wasNull()) r.setMarker2Z(m2z);
        return r;
    }

    // endregion

    // region Utils
    private static long toEpoch(Instant i) { return i.getEpochSecond(); }
    private static Instant fromEpoch(long s) { return Instant.ofEpochSecond(s); }
    private static int nvl(Integer v, int d) { return v == null ? d : v; }
    private static long nvl(Long v, long d) { return v == null ? d : v; }
    private static Instant nvl(Instant v, Instant d) { return v == null ? d : v; }
    // endregion
}
