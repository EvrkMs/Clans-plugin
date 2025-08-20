package com.cruiser.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.cruiser.ClanPlugin;
import com.cruiser.models.Clan;
import com.cruiser.models.ClanMember;

public class PlayerDataManager {
    private final ClanPlugin plugin;
    private final Map<UUID, ClanMember> playerData = new ConcurrentHashMap<>();

    public PlayerDataManager(ClanPlugin plugin) {
        this.plugin = plugin;
    }

    // Загрузка всех игроков из БД
    public void loadAllPlayers() {
        String sql = "SELECT uuid, name, clan_id, role, joined_at, kills, deaths FROM clan_players";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");

                ClanMember member = new ClanMember(uuid, name);

                // --- ВАЖНО: SQLite возвращает INTEGER как long (или NULL) ---
                int clanId = 0;
                long rawClanId = rs.getLong("clan_id"); // 0 если NULL
                if (!rs.wasNull()) {
                    // Если у тебя в моделях int — безопасно привести (id автонумерации в int укладываются)
                    clanId = (int) rawClanId;
                    member.setClanId(clanId);

                    // role может быть NULL → защищаемся
                    String roleStr = rs.getString("role");
                    if (roleStr != null && !roleStr.isEmpty()) {
                        try {
                            member.setRole(Clan.ClanRole.valueOf(roleStr));
                        } catch (IllegalArgumentException ignored) {
                            // если в БД мусор — поставим MEMBER
                            member.setRole(Clan.ClanRole.MEMBER);
                        }
                    }
                } else {
                    // нет клана
                    member.setClanId(0);
                    member.setRole(Clan.ClanRole.MEMBER);
                }

                // joined_at в SQLite обычно TEXT (TIMESTAMP) → getTimestamp может вернуть null.
                member.setJoinedAt(readTimestampSafely(rs, "joined_at"));

                // Числа NULL → getInt вернёт 0 — нам это подходит.
                member.setKills(rs.getInt("kills"));
                member.setDeaths(rs.getInt("deaths"));

                member.setModified(false);
                playerData.put(uuid, member);
                count++;
            }
            plugin.getLogger().info("[ClanPlugin] Загружено игроков: " + count);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка загрузки данных игроков", e);
        }
    }

    // Получение или создание данных игрока
    public ClanMember getOrCreatePlayer(UUID uuid, String name) {
        ClanMember member = playerData.get(uuid);

        if (member == null) {
            member = new ClanMember(uuid, name);
            playerData.put(uuid, member);
            // Сохраняем нового игрока в БД
            plugin.getClanManager().saveMember(member);
        } else if (!member.getName().equals(name)) {
            // Обновляем имя если изменилось
            member.setName(name);
            member.setModified(true);
        }

        return member;
    }

    // Получение данных игрока
    public ClanMember getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }

    // Сохранение всех игроков
    public void saveAllPlayers() {
        for (ClanMember member : playerData.values()) {
            if (member.isModified()) {
                plugin.getClanManager().saveMember(member);
                member.setModified(false);
            }
        }
    }

    // Удаление игрока из клана
    public void leaveClan(UUID playerUuid) {
        ClanMember member = playerData.get(playerUuid);
        if (member != null && member.hasClan()) {
            Clan clan = plugin.getClanManager().getClanById(member.getClanId());
            if (clan != null) {
                clan.removeMember(playerUuid);
            }
            member.setClanId(0);
            member.setRole(Clan.ClanRole.MEMBER);
            member.setModified(true);
            // Сохраняем изменения
            plugin.getClanManager().saveMember(member);
            member.setModified(false);
        }
    }

    // Проверка наличия клана у игрока
    public boolean hasClan(UUID playerUuid) {
        ClanMember member = playerData.get(playerUuid);
        return member != null && member.hasClan();
    }

    // Получение клана игрока
    public Clan getPlayerClan(UUID playerUuid) {
        ClanMember member = playerData.get(playerUuid);
        if (member != null && member.hasClan()) {
            return plugin.getClanManager().getClanById(member.getClanId());
        }
        return null;
    }

    public void detachPlayersFromClan(int clanId) {
        for (ClanMember m : playerData.values()) {
            if (m.hasClan() && m.getClanId() == clanId) {
                m.setClanId(0);
                m.setRole(Clan.ClanRole.MEMBER);
                m.setModified(true);
                // сохраним (для MySQL/если у кого-то FK были off)
                plugin.getClanManager().saveMember(m);
                m.setModified(false);
            }
        }
    }
    // ---------- helpers ----------

    private static Timestamp readTimestampSafely(ResultSet rs, String column) {
        try {
            // Попробуем нативно — для MySQL это работает хорошо.
            Timestamp ts = rs.getTimestamp(column);
            if (ts != null) return ts;
        } catch (Exception ignored) {
        }
        try {
            // Для SQLite TIMESTAMP обычно текстовый
            String s = rs.getString(column);
            if (s == null || s.isEmpty()) return null;
            // Часто формат "YYYY-MM-DD HH:MM:SS"
            // На всякий случай заменим 'T' на пробел (если ISO-строка)
            s = s.replace('T', ' ');
            return Timestamp.valueOf(s);
        } catch (Exception ignored) {
            return null;
        }
    }
}
