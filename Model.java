package com.astraxbox.relic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

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
            return "+" + RelicManager.fmt(v) + (percent ? "%" : "") + " " + display;
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
        REGEN("Hồi Phục", true);

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
            };
        }
    }

    public enum AuraStyle {
        RING("Vòng sáng quanh chân"),
        ORBIT("Quả cầu xoay quanh người"),
        HALO("Vầng hào quang trên đầu"),
        HELIX("Xoắn ốc quanh người"),
        SPARK("Tia sáng lấp lánh"),
        TRAIL("Vệt đuôi khi di chuyển");

        public final String display;

        AuraStyle(String display) { this.display = display; }
    }

    public enum Rarity { COMMON, RARE, EPIC, LEGENDARY, MYTHIC, CELESTIAL }

    public static final class RarityInfo {
        public String name;
        public String color = "&f";
        public boolean craftable = true;
        public int craftShards;
        public int craftCores;
        public int milestoneCores;
        public double costMult = 1.0;
    }

    public static final class AuraDef {
        public Particle particle = Particle.END_ROD;
        public AuraStyle style = AuraStyle.RING;
        public Color color;
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
        public AuraDef aura;
    }

    public static final class RelicSet {
        public String id;
        public String name;
        public Material icon = Material.AMETHYST_SHARD;
        public AuraDef aura;
        public TreeMap<Integer, Map<Stat, Double>> bonuses = new TreeMap<>();
    }

    public static final class DropRule {
        public String material; // shard | dust | core
        public double chance;
        public int min = 1;
        public int max = 1;
    }

    public static final class PlayerData {
        public final UUID uuid;
        public volatile String name;
        public int shard;
        public int dust;
        public int core;
        public final Map<String, Integer> levels = new HashMap<>();
        public final String[] equipped = new String[5];
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

        public synchronized void setLevel(String id, int level) {
            levels.put(id, level);
            invalidate();
        }

        public synchronized void removeRelic(String id) {
            unequip(id);
            levels.remove(id);
            invalidate();
        }

        public synchronized PlayerData copy() {
            PlayerData c = new PlayerData(uuid, name);
            c.shard = shard;
            c.dust = dust;
            c.core = core;
            c.levels.putAll(levels);
            System.arraycopy(equipped, 0, c.equipped, 0, equipped.length);
            return c;
        }
    }
}
