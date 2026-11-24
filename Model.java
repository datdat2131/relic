package com.astraxbox.relic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Color;
import org.bukkit.Material;

/** Các kiểu dữ liệu dùng chung của AstraxRelic. */
public final class Model {
    private Model() {}

    /** Chỉ số cộng trực tiếp: Máu, Sát thương, Giáp, Tốc độ. */
    public enum Stat {
        HEALTH("Máu tối đa", false),
        DAMAGE("Sát thương", true),
        ARMOR("Giáp", false),
        SPEED("Tốc độ", true);

        public final String display;
        public final boolean percent;

        Stat(String display, boolean percent) {
            this.display = display;
            this.percent = percent;
        }

        public String line(double v) {
            return (v >= 0 ? "+" : "") + RelicManager.fmt(v) + (percent ? "%" : "") + " " + display;
        }

        public static Stat parse(String s) {
            if (s == null) return null;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    /** Nội tại ẩn. scales = có tăng theo cấp Relic hay không. */
    public enum Passive {
        LIFESTEAL("Hút Máu", true),
        THORNS("Phản Đòn", true),
        CRIT("Chí Mạng", true),
        DODGE("Né Tránh", false),
        EXECUTE("Kết Liễu", true),
        BOSS_SLAYER("Sát Thủ Boss", true),
        LAST_STAND("Ý Chí Cuối Cùng", true),
        KILL_SPEED("Săn Mồi Thần Tốc", false),
        HASTE("Tay Thợ Mỏ", false),
        KILL_HEAL("Hấp Thụ Linh Hồn", true),
        LOW_HP_GUARD("Hộ Thể", true),
        MATERIAL_LUCK("Nhà Giả Kim", true),
        FALL_GUARD("Tiếp Đất Nhẹ", true),
        REGEN("Hồi Phục", true),
        COMBO("Bão Đòn", true),
        SHIELD("Khiên Năng Lượng", true),
        RAGE("Cuồng Nộ", true),
        PLUNDER("Cướp Đoạt", true);

        public final String display;
        public final boolean scales;

        Passive(String display, boolean scales) {
            this.display = display;
            this.scales = scales;
        }

        public String describe(double v, double chance, int cd) {
            String f = RelicManager.fmt(v);
            return switch (this) {
                case LIFESTEAL -> "Hồi " + f + "% sát thương gây ra thành máu";
                case THORNS -> "Phản lại " + f + "% sát thương nhận vào";
                case CRIT -> RelicManager.fmt(chance) + "% cơ hội gây thêm " + f + "% sát thương";
                case DODGE -> RelicManager.fmt(chance) + "% cơ hội né hoàn toàn đòn đánh";
                case EXECUTE -> "+" + f + "% sát thương lên mục tiêu dưới 30% máu";
                case BOSS_SLAYER -> "+" + f + "% sát thương lên Boss";
                case LAST_STAND -> "Khi sắp chết: hồi " + f + " máu, thoát chết (hồi chiêu " + cd + "s)";
                case KILL_SPEED -> "Hạ mục tiêu: chạy nhanh trong " + f + " giây";
                case HASTE -> "Luôn có Haste " + ((int) v + 1);
                case KILL_HEAL -> "Hồi " + f + " máu mỗi lần hạ mục tiêu";
                case LOW_HP_GUARD -> "Giảm " + f + "% sát thương nhận khi máu dưới 40%";
                case MATERIAL_LUCK -> "+" + f + "% tỉ lệ rơi nguyên liệu Relic";
                case FALL_GUARD -> "Giảm " + f + "% sát thương rơi";
                case REGEN -> "Hồi " + f + " máu mỗi 5 giây";
                case COMBO -> "Mỗi đòn liên tiếp +" + f + "% sát thương, tối đa " + (int) chance + " lớp (mất sau " + cd + "s không đánh)";
                case SHIELD -> "Chặn " + RelicManager.fmt(Math.min(100.0, v)) + "% sát thương của một đòn, hồi lại sau " + cd + "s";
                case RAGE -> "Máu dưới 50%: tăng tới +" + f + "% sát thương (càng thấp càng mạnh)";
                case PLUNDER -> f + "% cơ hội nhận thêm một lần rơi nguyên liệu khi hạ mục tiêu";
            };
        }
    }

    /** Kiểu bay của vật phẩm mini quanh người chơi. */
    public enum OrbitStyle {
        ORBIT("Bay vòng quanh ngực"),
        HALO("Xoay trên đầu"),
        FEET("Xoay quanh chân"),
        WING("Lơ lửng sau lưng như đôi cánh");

        public final String display;

        OrbitStyle(String display) { this.display = display; }
    }

    public enum Rarity { COMMON, RARE, EPIC, LEGENDARY, MYTHIC, CELESTIAL }

    public static final class RarityInfo {
        public String name;
        public String color = "&f";
        public boolean craftable = true;
        public int craftShards;
        public int craftCores;
        public int milestoneCores;
        public int sockets = 1;
        public double costMult = 1.0;
    }

    public static final class OrbitDef {
        public Material item = Material.PAPER;
        public OrbitStyle style = OrbitStyle.ORBIT;
        public float scale = 0.35f;
    }

    public static final class PassiveDef {
        public Passive type;
        public double value;
        public double chance;
        public int cooldown;
        public int unlockLevel = 10;
    }

    /** Nội tại đang hoạt động của người chơi (đã nhân theo cấp). */
    public static final class ActivePassive {
        public final Passive type;
        public final double value;
        public final double chance;
        public final int cooldown;
        public final String relicId;

        public ActivePassive(Passive type, double value, double chance, int cooldown, String relicId) {
            this.type = type;
            this.value = value;
            this.chance = chance;
            this.cooldown = cooldown;
            this.relicId = relicId;
        }
    }

    /** Lời nguyền: một chỉ số bị trừ cố định (không tăng theo cấp). */
    public static final class CurseDef {
        public Model.Stat stat;
        public double value;
        public String text = "";
    }

    /** Điều kiện khám phá Relic ẩn. */
    public static final class DiscoverDef {
        public String type = "EVENT"; // BLOCKS DEEP_BLOCKS BOSS_KILLS MOB_KILLS OWN_SET RELIC_LEVEL EVENT
        public double value;
        public String set = "";
        public String relic = "";
    }

    public static final class GemDef {
        public String id;
        public String name;
        public Stat stat;
        public double value;
        public int costDust;
        public int costCore;
        public Material icon = Material.EMERALD;
    }

    public static final class RelicDef {
        public String id;
        public String name;
        public String group = "MYSTIC";
        public String set = "";
        public Rarity rarity = Rarity.COMMON;
        public Material icon = Material.PAPER;
        public List<String> description = new ArrayList<>();
        public Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        public PassiveDef passive;
        public OrbitDef orbit;
        public CurseDef curse;
        public boolean hidden;
        public String hint = "";
        public DiscoverDef discover;
    }

    /** Cộng hưởng cặp: trang bị đủ các Relic trong danh sách sẽ nhận thêm chỉ số / nội tại. */
    public static final class Synergy {
        public String id;
        public String name;
        public List<String> relics = new ArrayList<>();
        public Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        public PassiveDef passive;
    }

    public static final class RelicSet {
        public String id;
        public String name;
        public Material icon = Material.AMETHYST_SHARD;
        public Color glow;
        public TreeMap<Integer, Map<Stat, Double>> bonuses = new TreeMap<>();
    }

    public static final class DropRule {
        public String material; // shard | dust | core
        public double chance;
        public int min = 1;
        public int max = 1;
    }

    /** Hồ sơ lịch sử của một Relic: ngày thức tỉnh, thống kê khi trang bị, ký ức, gem. */
    public static final class RelicRecord {
        public long foundAt;
        public int blocks;
        public int bosses;
        public int mobs;
        public final Map<String, Long> memory = new LinkedHashMap<>();
        public final String[] gems = new String[3];

        public RelicRecord(long foundAt) { this.foundAt = foundAt; }

        public RelicRecord copy() {
            RelicRecord c = new RelicRecord(foundAt);
            c.blocks = blocks;
            c.bosses = bosses;
            c.mobs = mobs;
            c.memory.putAll(memory);
            System.arraycopy(gems, 0, c.gems, 0, gems.length);
            return c;
        }
    }

    public static final class PlayerData {
        public final UUID uuid;
        public volatile String name;
        public int shard;
        public int dust;
        public int core;
        public final Map<String, Integer> levels = new HashMap<>();
        public final String[] equipped = new String[5];
        public int bossKills;
        public int mobKills;
        public int blocksMined;
        public int deepBlocks;
        public boolean slot4;
        public int combo;
        public long lastHitMs;
        public final Map<String, RelicRecord> records = new HashMap<>();
        public final Set<String> discovered = new HashSet<>();
        public final Map<String, Integer> gemInv = new HashMap<>();
        public volatile Map<Stat, Double> cache;
        public volatile List<ActivePassive> passiveCache;
        public final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

        public PlayerData(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public void invalidate() {
            cache = null;
            passiveCache = null;
        }

        public synchronized int get(String mat) {
            return switch (mat) {
                case "shard" -> shard;
                case "dust" -> dust;
                default -> core;
            };
        }

        public synchronized void add(String mat, int amt) {
            switch (mat) {
                case "shard" -> shard = Math.max(0, shard + amt);
                case "dust" -> dust = Math.max(0, dust + amt);
                default -> core = Math.max(0, core + amt);
            }
        }

        public synchronized int level(String id) { return levels.getOrDefault(id, 0); }

        public synchronized boolean owns(String id) { return levels.containsKey(id); }

        public synchronized boolean isEquipped(String id) {
            for (String s : equipped) if (id.equals(s)) return true;
            return false;
        }

        public synchronized int freeSlot(int slots) {
            for (int i = 0; i < Math.min(slots, equipped.length); i++) if (equipped[i] == null) return i;
            return -1;
        }

        public synchronized void equipAt(int slot, String id) {
            equipped[slot] = id;
            invalidate();
        }

        public synchronized void unequip(String id) {
            for (int i = 0; i < equipped.length; i++) if (id.equals(equipped[i])) equipped[i] = null;
            invalidate();
        }

        public synchronized RelicRecord rec(String id) {
            return records.computeIfAbsent(id, k -> new RelicRecord(System.currentTimeMillis()));
        }

        public synchronized void setLevel(String id, int level) {
            boolean isNew = !levels.containsKey(id);
            levels.put(id, level);
            if (isNew) {
                RelicRecord r = new RelicRecord(System.currentTimeMillis());
                r.memory.put("found", r.foundAt);
                records.put(id, r);
                discovered.add(id);
            }
            invalidate();
        }

        public synchronized void removeRelic(String id) {
            unequip(id);
            levels.remove(id);
            records.remove(id);
            invalidate();
        }

        public synchronized boolean isDiscovered(String id) { return discovered.contains(id) || levels.containsKey(id); }

        public synchronized boolean markDiscovered(String id) { return discovered.add(id); }

        public synchronized int gemCount(String id) { return gemInv.getOrDefault(id, 0); }

        public synchronized void addGem(String id, int n) {
            int v = gemInv.getOrDefault(id, 0) + n;
            if (v <= 0) gemInv.remove(id);
            else gemInv.put(id, v);
        }

        public synchronized PlayerData copy() {
            PlayerData c = new PlayerData(uuid, name);
            c.shard = shard;
            c.dust = dust;
            c.core = core;
            c.levels.putAll(levels);
            System.arraycopy(equipped, 0, c.equipped, 0, equipped.length);
            c.bossKills = bossKills;
            c.mobKills = mobKills;
            c.blocksMined = blocksMined;
            c.deepBlocks = deepBlocks;
            c.slot4 = slot4;
            for (Map.Entry<String, RelicRecord> e : records.entrySet()) c.records.put(e.getKey(), e.getValue().copy());
            c.discovered.addAll(discovered);
            c.gemInv.putAll(gemInv);
            return c;
        }
    }
}
