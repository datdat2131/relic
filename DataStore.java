package com.astraxbox.relic;

import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.RelicRecord;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
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
            st.executeUpdate("CREATE TABLE IF NOT EXISTS discoveries ("
                    + "relic TEXT PRIMARY KEY, uuid TEXT, name TEXT, time INTEGER)");
            String[] playerCols = {"boss_kills INTEGER DEFAULT 0", "mob_kills INTEGER DEFAULT 0",
                    "blocks_mined INTEGER DEFAULT 0", "slot4 INTEGER DEFAULT 0", "deep_blocks INTEGER DEFAULT 0",
                    "discovered TEXT DEFAULT ''", "gem_inv TEXT DEFAULT ''"};
            for (String col : playerCols) alter(st, "players", col);
            String[] relicCols = {"found_at INTEGER DEFAULT 0", "blocks INTEGER DEFAULT 0", "bosses INTEGER DEFAULT 0",
                    "mobs INTEGER DEFAULT 0", "memory TEXT DEFAULT ''", "gems TEXT DEFAULT ''"};
            for (String col : relicCols) alter(st, "relics", col);
        }
    }

    private static void alter(Statement st, String table, String col) {
        try {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + col);
        } catch (SQLException ignored) { /* cột đã tồn tại */ }
    }

    public synchronized PlayerData load(UUID uuid, String name) {
        PlayerData d = new PlayerData(uuid, name);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT shard,dust,core,equipped,boss_kills,mob_kills,blocks_mined,slot4,deep_blocks,discovered,gem_inv "
                            + "FROM players WHERE uuid=?")) {
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
                        d.bossKills = rs.getInt(5);
                        d.mobKills = rs.getInt(6);
                        d.blocksMined = rs.getInt(7);
                        d.slot4 = rs.getInt(8) != 0;
                        d.deepBlocks = rs.getInt(9);
                        String disc = rs.getString(10);
                        if (disc != null && !disc.isEmpty()) {
                            for (String s : disc.split(",")) if (!s.isEmpty()) d.discovered.add(s);
                        }
                        String gi = rs.getString(11);
                        if (gi != null && !gi.isEmpty()) {
                            for (String s : gi.split(",")) {
                                String[] kv = s.split(":");
                                if (kv.length == 2) {
                                    try { d.gemInv.put(kv[0], Integer.parseInt(kv[1])); } catch (NumberFormatException ignored) { }
                                }
                            }
                        }
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT relic,level,found_at,blocks,bosses,mobs,memory,gems FROM relics WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString(1);
                        d.levels.put(id, rs.getInt(2));
                        long found = rs.getLong(3);
                        RelicRecord rec = new RelicRecord(found > 0 ? found : System.currentTimeMillis());
                        rec.blocks = rs.getInt(4);
                        rec.bosses = rs.getInt(5);
                        rec.mobs = rs.getInt(6);
                        String mem = rs.getString(7);
                        if (mem != null && !mem.isEmpty()) {
                            for (String s : mem.split(",")) {
                                String[] kv = s.split(":");
                                if (kv.length == 2) {
                                    try { rec.memory.put(kv[0], Long.parseLong(kv[1])); } catch (NumberFormatException ignored) { }
                                }
                            }
                        }
                        String gm = rs.getString(8);
                        if (gm != null) {
                            String[] parts = gm.split(",", -1);
                            for (int i = 0; i < Math.min(parts.length, rec.gems.length); i++) {
                                rec.gems[i] = parts[i].isEmpty() ? null : parts[i];
                            }
                        }
                        d.records.put(id, rec);
                        d.discovered.add(id);
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
                    "INSERT OR REPLACE INTO players (uuid,name,shard,dust,core,equipped,boss_kills,mob_kills,blocks_mined,"
                            + "slot4,deep_blocks,discovered,gem_inv) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
                ps.setInt(7, d.bossKills);
                ps.setInt(8, d.mobKills);
                ps.setInt(9, d.blocksMined);
                ps.setInt(10, d.slot4 ? 1 : 0);
                ps.setInt(11, d.deepBlocks);
                ps.setString(12, String.join(",", d.discovered));
                StringBuilder gi = new StringBuilder();
                for (Map.Entry<String, Integer> e : d.gemInv.entrySet()) {
                    if (gi.length() > 0) gi.append(',');
                    gi.append(e.getKey()).append(':').append(e.getValue());
                }
                ps.setString(13, gi.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM relics WHERE uuid=?")) {
                del.setString(1, d.uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO relics (uuid,relic,level,charge,found_at,blocks,bosses,mobs,memory,gems) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                for (Map.Entry<String, Integer> e : d.levels.entrySet()) {
                    RelicRecord rec = d.records.get(e.getKey());
                    if (rec == null) rec = new RelicRecord(System.currentTimeMillis());
                    StringBuilder mem = new StringBuilder();
                    for (Map.Entry<String, Long> m : rec.memory.entrySet()) {
                        if (mem.length() > 0) mem.append(',');
                        mem.append(m.getKey()).append(':').append(m.getValue());
                    }
                    StringBuilder gm = new StringBuilder();
                    for (int i = 0; i < rec.gems.length; i++) {
                        if (i > 0) gm.append(',');
                        if (rec.gems[i] != null) gm.append(rec.gems[i]);
                    }
                    ins.setString(1, d.uuid.toString());
                    ins.setString(2, e.getKey());
                    ins.setInt(3, e.getValue());
                    ins.setInt(4, 0);
                    ins.setLong(5, rec.foundAt);
                    ins.setInt(6, rec.blocks);
                    ins.setInt(7, rec.bosses);
                    ins.setInt(8, rec.mobs);
                    ins.setString(9, mem.toString());
                    ins.setString(10, gm.toString());
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

    /** Trả về relic -> {uuid, tên, thời gian} của người khám phá đầu tiên trên server. */
    public synchronized Map<String, String[]> loadDiscoveries() {
        Map<String, String[]> out = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT relic,uuid,name,time FROM discoveries")) {
            while (rs.next()) {
                out.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3), String.valueOf(rs.getLong(4))});
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi đọc Astrax Archive", e);
        }
        return out;
    }

    /** Ghi người khám phá đầu tiên. Trả true nếu đây thật sự là người đầu tiên. */
    public synchronized boolean tryRecordFirst(String relic, UUID uuid, String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO discoveries (relic,uuid,name,time) VALUES (?,?,?,?)")) {
            ps.setString(1, relic);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setLong(4, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi ghi Astrax Archive", e);
            return false;
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
