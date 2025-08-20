package com.cruiser.managers;

import com.cruiser.ClanPlugin;
import com.cruiser.models.Clan;
import com.cruiser.models.ClanMember;
import org.bukkit.Location;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ClanManager {
    private final ClanPlugin plugin;
    private final Map<Integer, Clan> clansById = new ConcurrentHashMap<>();
    private final Map<String, Clan> clansByName = new ConcurrentHashMap<>();
    private final Map<String, Clan> clansByTag = new ConcurrentHashMap<>();
    
    public ClanManager(ClanPlugin plugin) {
        this.plugin = plugin;
    }
    
    // Создание клана
    public Clan createClan(String name, String tag, UUID leaderUuid, String leaderName) {
        if (clansByName.containsKey(name.toLowerCase()) || clansByTag.containsKey(tag.toLowerCase())) {
            return null; // Клан с таким именем или тегом уже существует
        }
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "INSERT INTO clans (name, tag, leader_uuid) VALUES (?, ?, ?)";
            
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, tag);
                ps.setString(3, leaderUuid.toString());
                ps.executeUpdate();
                
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    int clanId = rs.getInt(1);
                    Clan clan = new Clan(clanId, name, tag, leaderUuid);
                    
                    // Добавляем лидера как члена клана
                    ClanMember leader = new ClanMember(leaderUuid, leaderName);
                    leader.setClanId(clanId);
                    leader.setRole(Clan.ClanRole.LEADER);
                    clan.addMember(leader);
                    
                    // Сохраняем лидера в БД
                    saveMember(leader);
                    
                    // Добавляем клан в кеш
                    registerClan(clan);
                    
                    return clan;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка создания клана", e);
        }
        
        return null;
    }
    
    // Загрузка всех кланов из БД
    public void loadAllClans() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "SELECT * FROM clans";
            
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                while (rs.next()) {
                    Clan clan = loadClanFromResultSet(rs);
                    loadClanMembers(clan);
                    loadClanAlliances(clan);
                    registerClan(clan);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки кланов", e);
        }
    }
    
    private Clan loadClanFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String tag = rs.getString("tag");
        UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
        
        Clan clan = new Clan(id, name, tag, leaderUuid);
        clan.setCreatedAt(rs.getTimestamp("created_at"));
        clan.setLevel(rs.getInt("level"));
        clan.setBalance(rs.getDouble("balance"));
        clan.setMaxMembers(rs.getInt("max_members"));
        
        // Загрузка дома клана
        String world = rs.getString("home_world");
        if (world != null) {
            double x = rs.getDouble("home_x");
            double y = rs.getDouble("home_y");
            double z = rs.getDouble("home_z");
            float yaw = rs.getFloat("home_yaw");
            float pitch = rs.getFloat("home_pitch");
            
            if (plugin.getServer().getWorld(world) != null) {
                Location home = new Location(
                    plugin.getServer().getWorld(world),
                    x, y, z, yaw, pitch
                );
                clan.setHome(home);
            }
        }
        
        clan.setModified(false);
        return clan;
    }
    
    private void loadClanMembers(Clan clan) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "SELECT * FROM clan_players WHERE clan_id = ?";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, clan.getId());
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("name");
                        
                        ClanMember member = new ClanMember(uuid, name);
                        member.setClanId(clan.getId());
                        member.setRole(Clan.ClanRole.valueOf(rs.getString("role")));
                        member.setJoinedAt(rs.getTimestamp("joined_at"));
                        member.setKills(rs.getInt("kills"));
                        member.setDeaths(rs.getInt("deaths"));
                        member.setModified(false);
                        
                        clan.addMember(member);
                    }
                }
            }
        }
        clan.setModified(false);
    }
    
    private void loadClanAlliances(Clan clan) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "SELECT * FROM clan_alliances WHERE clan1_id = ? OR clan2_id = ?";
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, clan.getId());
                ps.setInt(2, clan.getId());
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int clan1Id = rs.getInt("clan1_id");
                        int clan2Id = rs.getInt("clan2_id");
                        
                        if (clan1Id == clan.getId()) {
                            clan.addAlly(clan2Id);
                        } else {
                            clan.addAlly(clan1Id);
                        }
                    }
                }
            }
        }
        clan.setModified(false);
    }
    
    // Сохранение клана в БД
    public void saveClan(Clan clan) {
        if (!clan.isModified()) return;
        
        plugin.getDatabaseManager().executeAsync(conn -> {
            String sql = """
                UPDATE clans SET 
                    name = ?, tag = ?, leader_uuid = ?, 
                    level = ?, balance = ?, max_members = ?,
                    home_world = ?, home_x = ?, home_y = ?, 
                    home_z = ?, home_yaw = ?, home_pitch = ?
                WHERE id = ?
                """;
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clan.getName());
                ps.setString(2, clan.getTag());
                ps.setString(3, clan.getLeaderUuid().toString());
                ps.setInt(4, clan.getLevel());
                ps.setDouble(5, clan.getBalance());
                ps.setInt(6, clan.getMaxMembers());
                
                Location home = clan.getHome();
                if (home != null) {
                    ps.setString(7, home.getWorld().getName());
                    ps.setDouble(8, home.getX());
                    ps.setDouble(9, home.getY());
                    ps.setDouble(10, home.getZ());
                    ps.setFloat(11, home.getYaw());
                    ps.setFloat(12, home.getPitch());
                } else {
                    for (int i = 7; i <= 12; i++) {
                        ps.setNull(i, Types.NULL);
                    }
                }
                
                ps.setInt(13, clan.getId());
                ps.executeUpdate();
                
                clan.setModified(false);
            }
        });
    }
    
    // Сохранение члена клана
    public void saveMember(ClanMember member) {
        plugin.getDatabaseManager().executeAsync(conn -> {
            String sql = """
                INSERT INTO clan_players (uuid, name, clan_id, role, joined_at, kills, deaths)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    clan_id = VALUES(clan_id),
                    role = VALUES(role),
                    kills = VALUES(kills),
                    deaths = VALUES(deaths)
                """;
            
            // Для SQLite используем другой синтаксис
            if (plugin.getConfig().getString("database.type", "sqlite").equalsIgnoreCase("sqlite")) {
                sql = """
                    INSERT OR REPLACE INTO clan_players 
                    (uuid, name, clan_id, role, joined_at, kills, deaths)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            }
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, member.getUuid().toString());
                ps.setString(2, member.getName());
                
                if (member.hasClan()) {
                    ps.setInt(3, member.getClanId());
                    ps.setString(4, member.getRole().name());
                } else {
                    ps.setNull(3, Types.INTEGER);
                    ps.setString(4, Clan.ClanRole.MEMBER.name());
                }
                
                ps.setTimestamp(5, member.getJoinedAt());
                ps.setInt(6, member.getKills());
                ps.setInt(7, member.getDeaths());
                
                ps.executeUpdate();
                member.setModified(false);
            }
        });
    }
    
    // Сохранение всех кланов
    public void saveAllClans() {
        for (Clan clan : clansById.values()) {
            if (clan.isModified()) {
                saveClan(clan);
            }
            
            // Сохраняем измененных членов
            for (ClanMember member : clan.getMembers()) {
                if (member.isModified()) {
                    saveMember(member);
                }
            }
        }
    }
    
    // Удаление клана
    public void disbandClan(Clan clan) {
        int clanId = clan.getId();
    
        // 1) БД: всё в одной транзакции
        plugin.getDatabaseManager().executeAsync(conn -> {
            boolean oldAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // А) отвязать игроков
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE clan_players SET clan_id = NULL, role = 'MEMBER' WHERE clan_id = ?")) {
                    ps.setInt(1, clanId);
                    ps.executeUpdate();
                }
    
                // B) подчистить зависимости (на случай если FK были выключены)
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM clan_alliances WHERE clan1_id = ? OR clan2_id = ?")) {
                    ps.setInt(1, clanId);
                    ps.setInt(2, clanId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM clan_invites WHERE clan_id = ?")) {
                    ps.setInt(1, clanId);
                    ps.executeUpdate();
                }
    
                // C) удалить сам клан
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM clans WHERE id = ?")) {
                    ps.setInt(1, clanId);
                    ps.executeUpdate();
                }
    
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAuto);
            }
        });
    
        // 2) Память: отвязать игроков, чтобы кэш не перетёр БД
        plugin.getPlayerDataManager().detachPlayersFromClan(clanId);
    
        // 3) Удалить из кэша кланы
        unregisterClan(clan);
    }
    
    // Вспомогательные методы
    private void registerClan(Clan clan) {
        clansById.put(clan.getId(), clan);
        clansByName.put(clan.getName().toLowerCase(), clan);
        clansByTag.put(clan.getTag().toLowerCase(), clan);
    }
    
    private void unregisterClan(Clan clan) {
        clansById.remove(clan.getId());
        clansByName.remove(clan.getName().toLowerCase());
        clansByTag.remove(clan.getTag().toLowerCase());
    }
    
    // Геттеры
    public Clan getClanById(int id) {
        return clansById.get(id);
    }
    
    public Clan getClanByName(String name) {
        return clansByName.get(name.toLowerCase());
    }
    
    public Clan getClanByTag(String tag) {
        return clansByTag.get(tag.toLowerCase());
    }
    
    public Collection<Clan> getAllClans() {
        return clansById.values();
    }
    
    public List<String> getAllClanNames() {
        return new ArrayList<>(clansByName.keySet());
    }
}