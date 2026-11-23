package com.astraxbox.relic;

import com.astraxbox.relic.Model.PlayerData;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Lưu dữ liệu người chơi bằng SQLite (driver có sẵn trong Paper). */
public final class DataStore {
    private final AstraxRelic plugin;
    private final Connection conn;

    public DataStore(AstraxRelic plugin) throws Exception {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        Class.forName("org.sqlite.JDBC");
        File file = new File(plugin.getDataFolder(), "astraxrelic.db");
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                    + "uuid TEXT PRIMARY KEY, name TEXT, shard INTEGER DEFAULT 0, "
                    + "dust INTEGER DEFAULT 0, core INTEGER DEFAULT 0, equipped TEXT DEFAULT '')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS relics ("
                    + "uuid TEXT, relic TEXT, level INTEGER, charge INTEGER DEFAULT 0, "
                    + "PRIMARY KEY (uuid, relic))");
        }
    }

    public synchronized PlayerData load(UUID uuid, String name) {
        PlayerData d = new PlayerData(uuid, name);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT shard,dust,core,equipped FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        d.shard = rs.getInt(1);
                        d.dust = rs.getInt(2);
                        d.core = rs.getInt(3);
                        String eq = rs.getString(4);
                        if (eq != null) {
                            String[] parts = eq.split(",", -1);
                            for (int i = 0; i < Math.min(parts.length, d.equipped.length); i++) {
                                d.equipped[i] = parts[i].isEmpty() ? null : parts[i];
                            }
                        }
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT relic,level,charge FROM relics WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        d.levels.put(rs.getString(1), rs.getInt(2));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi load dữ liệu " + uuid, e);
        }
        return d;
    }

    public synchronized void save(PlayerData live) {
        PlayerData d = live.copy();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO players (uuid,name,shard,dust,core,equipped) VALUES (?,?,?,?,?,?)")) {
                StringBuilder eq = new StringBuilder();
                for (int i = 0; i < d.equipped.length; i++) {
                    if (i > 0) eq.append(',');
                    if (d.equipped[i] != null) eq.append(d.equipped[i]);
                }
                ps.setString(1, d.uuid.toString());
                ps.setString(2, d.name);
                ps.setInt(3, d.shard);
                ps.setInt(4, d.dust);
                ps.setInt(5, d.core);
                ps.setString(6, eq.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM relics WHERE uuid=?")) {
                del.setString(1, d.uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO relics (uuid,relic,level,charge) VALUES (?,?,?,?)")) {
                for (Map.Entry<String, Integer> e : d.levels.entrySet()) {
                    ins.setString(1, d.uuid.toString());
                    ins.setString(2, e.getKey());
                    ins.setInt(3, e.getValue());
                    ins.setInt(4, 0);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) { }
            plugin.getLogger().log(Level.SEVERE, "Lỗi lưu dữ liệu " + d.uuid, e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    /** Trả về danh sách {tên, tổng cấp, số relic}. */
    public synchronized List<String[]> top(int limit) {
        List<String[]> out = new ArrayList<>();
        String sql = "SELECT p.name, COALESCE(SUM(r.level),0) AS total, COUNT(r.relic) AS cnt "
                + "FROM players p LEFT JOIN relics r ON r.uuid = p.uuid "
                + "GROUP BY p.uuid ORDER BY total DESC, cnt DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new String[]{rs.getString(1), String.valueOf(rs.getInt(2)), String.valueOf(rs.getInt(3))});
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi đọc bảng xếp hạng", e);
        }
        return out;
    }

    public synchronized void close() {
        try { conn.close(); } catch (SQLException ignored) { }
    }
}
