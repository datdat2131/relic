package com.astraxbox.relic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.Material;

/** Các kiểu dữ liệu dùng chung của AstraxRelic. */
public final class Model {
    private Model() {}

    public enum Stat {
        MINING_EXP("Mining EXP"),
        COMBAT_EXP("Combat EXP"),
        EVENT_POINTS("Event Points"),
        BOSS_CONTRIBUTION("Boss Contribution"),
        QUEST_POINTS("Quest Points"),
        TREASURE("Treasure Progress"),
        MONEY("Tiền nhiệm vụ"),
        MATERIAL_LUCK("May mắn nguyên liệu");

        public final String display;

        Stat(String display) { this.display = display; }

        public static Stat parse(String s) {
            if (s == null) return null;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    public enum Trigger {
        BLOCK_BREAK("đào block"),
        MOB_KILL("hạ quái"),
        BOSS_KILL("hạ Boss"),
        QUEST_COMPLETE("hoàn thành nhiệm vụ"),
        EVENT_JOIN("tham gia sự kiện"),
        ONLINE_MINUTE("phút online");

        public final String display;

        Trigger(String display) { this.display = display; }

        public static Trigger parse(String s) {
            if (s == null) return null;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return null; }
        }
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

    public static final class RelicDef {
        public String id;
        public String name;
        public String group = "UTILITY";
        public String set = "";
        public Rarity rarity = Rarity.COMMON;
        public Material icon = Material.PAPER;
        public List<String> description = new ArrayList<>();
        public Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        public Trigger trigger = Trigger.BLOCK_BREAK;
        public int chargeNeeded = 100;
        public int rewardShard;
        public int rewardDust;
        public int rewardCore;
        public List<String> rewardCommands = new ArrayList<>();
    }

    public static final class RelicSet {
        public String id;
        public String name;
        public Material icon = Material.AMETHYST_SHARD;
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
        public final Map<String, Integer> charge = new HashMap<>();
        public final String[] equipped = new String[5];
        public volatile Map<Stat, Double> cache;

        public PlayerData(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
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
            cache = null;
        }

        public synchronized void unequip(String id) {
            for (int i = 0; i < equipped.length; i++) if (id.equals(equipped[i])) equipped[i] = null;
            cache = null;
        }

        public synchronized void setLevel(String id, int level) {
            levels.put(id, level);
            cache = null;
        }

        public synchronized void removeRelic(String id) {
            unequip(id);
            levels.remove(id);
            charge.remove(id);
            cache = null;
        }

        public synchronized PlayerData copy() {
            PlayerData c = new PlayerData(uuid, name);
            c.shard = shard;
            c.dust = dust;
            c.core = core;
            c.levels.putAll(levels);
            c.charge.putAll(charge);
            System.arraycopy(equipped, 0, c.equipped, 0, equipped.length);
            return c;
        }
    }
}
