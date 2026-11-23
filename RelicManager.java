package com.astraxbox.relic;

import com.astraxbox.relic.Model.DropRule;
import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.Rarity;
import com.astraxbox.relic.Model.RarityInfo;
import com.astraxbox.relic.Model.RelicDef;
import com.astraxbox.relic.Model.RelicSet;
import com.astraxbox.relic.Model.Stat;
import com.astraxbox.relic.Model.Trigger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Toàn bộ logic: đọc config, chỉ số, chế tạo, nâng cấp, trang bị, tái chế, tiến độ, rơi nguyên liệu. */
public final class RelicManager {
    private final AstraxRelic plugin;

    public final Map<String, RelicDef> relics = new LinkedHashMap<>();
    public final Map<String, RelicSet> sets = new LinkedHashMap<>();
    public final Map<Rarity, RarityInfo> rarities = new EnumMap<>(Rarity.class);
    private final Map<String, List<DropRule>> drops = new HashMap<>();
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public int slots = 5;
    public int maxLevel = 30;
    public double levelScale = 0.04;
    public double dustBase = 10;
    public double dustGrowth = 0.15;
    public double refundRatio = 0.5;
    public double maxMoneyBonus = 10;
    public double bossMinHealth = 100;
    public List<String> mineable = new ArrayList<>();
    public List<String> bossTypes = new ArrayList<>();
    public List<String> bossNames = new ArrayList<>();
    public String prefix = "";

    public RelicManager(AstraxRelic plugin) {
        this.plugin = plugin;
    }

    // ===================== Config =====================

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        relics.clear();
        sets.clear();
        rarities.clear();
        drops.clear();

        prefix = c.getString("messages.prefix", "&8[&bAstraxRelic&8] &7");
        slots = Math.max(1, Math.min(5, c.getInt("settings.slots", 5)));
        maxLevel = Math.max(1, c.getInt("settings.max-level", 30));
        levelScale = c.getDouble("settings.level-scale", 0.04);
        maxMoneyBonus = c.getDouble("settings.max-money-bonus", 10);
        dustBase = c.getDouble("upgrade.dust-base", 10);
        dustGrowth = c.getDouble("upgrade.dust-growth", 0.15);
        refundRatio = c.getDouble("recycle.refund-ratio", 0.5);
        mineable = upper(c.getStringList("mining.mineable-tokens"));
        bossMinHealth = c.getDouble("boss.min-health", 100);
        bossTypes = upper(c.getStringList("boss.types"));
        bossNames = lower(c.getStringList("boss.name-keywords"));

        for (Rarity r : Rarity.values()) {
            ConfigurationSection s = c.getConfigurationSection("rarities." + r.name());
            if (s == null) s = new MemoryConfiguration();
            RarityInfo ri = new RarityInfo();
            ri.name = s.getString("name", r.name());
            ri.color = s.getString("color", "&f");
            ri.craftable = s.getBoolean("craftable", true);
            ri.craftShards = s.getInt("craft-shards", 20);
            ri.craftCores = s.getInt("craft-cores", 0);
            ri.milestoneCores = s.getInt("milestone-cores", 0);
            ri.costMult = s.getDouble("cost-mult", 1.0);
            rarities.put(r, ri);
        }

        ConfigurationSection rs = c.getConfigurationSection("relics");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) continue;
                RelicDef r = new RelicDef();
                r.id = id.toLowerCase(Locale.ROOT);
                r.name = s.getString("name", id);
                r.group = s.getString("group", "UTILITY").toUpperCase(Locale.ROOT);
                r.set = s.getString("set", "").toLowerCase(Locale.ROOT);
                try { r.rarity = Rarity.valueOf(s.getString("rarity", "COMMON").toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { r.rarity = Rarity.COMMON; }
                Material m = Material.matchMaterial(s.getString("icon", "PAPER"));
                r.icon = m != null ? m : Material.PAPER;
                r.description = s.getStringList("description");
                Trigger t = Trigger.parse(s.getString("trigger", "BLOCK_BREAK"));
                r.trigger = t != null ? t : Trigger.BLOCK_BREAK;
                r.chargeNeeded = Math.max(1, s.getInt("charge-needed", 100));
                r.rewardShard = s.getInt("reward.shard", 0);
                r.rewardDust = s.getInt("reward.dust", 0);
                r.rewardCore = s.getInt("reward.core", 0);
                r.rewardCommands = s.getStringList("reward-commands");
                r.stats = readStats(s.getConfigurationSection("stats"));
                relics.put(r.id, r);
            }
        }

        ConfigurationSection ss = c.getConfigurationSection("sets");
        if (ss != null) {
            for (String id : ss.getKeys(false)) {
                ConfigurationSection s = ss.getConfigurationSection(id);
                if (s == null) continue;
                RelicSet set = new RelicSet();
                set.id = id.toLowerCase(Locale.ROOT);
                set.name = s.getString("name", id);
                Material m = Material.matchMaterial(s.getString("icon", "AMETHYST_SHARD"));
                set.icon = m != null ? m : Material.AMETHYST_SHARD;
                ConfigurationSection bs = s.getConfigurationSection("bonuses");
                if (bs != null) {
                    for (String k : bs.getKeys(false)) {
                        try {
                            set.bonuses.put(Integer.parseInt(k), readStats(bs.getConfigurationSection(k)));
                        } catch (NumberFormatException ignored) { }
                    }
                }
                sets.put(set.id, set);
            }
        }

        ConfigurationSection ds = c.getConfigurationSection("drops");
        if (ds != null) {
            for (String source : ds.getKeys(false)) {
                ConfigurationSection src = ds.getConfigurationSection(source);
                if (src == null) continue;
                List<DropRule> list = new ArrayList<>();
                for (String mat : src.getKeys(false)) {
                    if (!mat.equals("shard") && !mat.equals("dust") && !mat.equals("core")) continue;
                    ConfigurationSection s = src.getConfigurationSection(mat);
                    if (s == null) continue;
                    DropRule dr = new DropRule();
                    dr.material = mat;
                    dr.chance = s.getDouble("chance", 0);
                    dr.min = Math.max(1, s.getInt("min", 1));
                    dr.max = Math.max(dr.min, s.getInt("max", dr.min));
                    list.add(dr);
                }
                drops.put(source, list);
            }
        }

        for (PlayerData d : cache.values()) d.cache = null;
    }

    private Map<Stat, Double> readStats(ConfigurationSection s) {
        Map<Stat, Double> m = new EnumMap<>(Stat.class);
        if (s == null) return m;
        for (String k : s.getKeys(false)) {
            Stat st = Stat.parse(k);
            if (st != null) m.put(st, s.getDouble(k));
        }
        return m;
    }

    private static List<String> upper(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(s.toUpperCase(Locale.ROOT));
        return out;
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    // ===================== Dữ liệu người chơi =====================

    public PlayerData data(Player p) {
        PlayerData d = cache.get(p.getUniqueId());
        if (d == null) {
            d = plugin.store().load(p.getUniqueId(), p.getName());
            PlayerData prev = cache.putIfAbsent(p.getUniqueId(), d);
            if (prev != null) d = prev;
        }
        return d;
    }

    public void preload(UUID id, String name) {
        if (cache.containsKey(id)) return;
        PlayerData d = plugin.store().load(id, name);
        cache.putIfAbsent(id, d);
    }

    public void unload(Player p) {
        PlayerData d = cache.remove(p.getUniqueId());
        if (d != null) plugin.store().save(d);
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) plugin.store().save(d);
    }

    // ===================== Chỉ số =====================

    public double statValue(double base, int level) {
        return base * (1.0 + (Math.max(1, level) - 1) * levelScale);
    }

    public int setCount(PlayerData d, String setId) {
        int n = 0;
        synchronized (d) {
            for (int i = 0; i < Math.min(slots, d.equipped.length); i++) {
                RelicDef r = d.equipped[i] == null ? null : relics.get(d.equipped[i]);
                if (r != null && r.set.equals(setId)) n++;
            }
        }
        return n;
    }

    public Map<Stat, Double> bonuses(PlayerData d) {
        Map<Stat, Double> cached = d.cache;
        if (cached != null) return cached;
        Map<Stat, Double> m = new EnumMap<>(Stat.class);
        Map<String, Integer> setCounts = new HashMap<>();
        synchronized (d) {
            for (int i = 0; i < Math.min(slots, d.equipped.length); i++) {
                String id = d.equipped[i];
                RelicDef r = id == null ? null : relics.get(id);
                if (r == null) continue;
                int lv = Math.max(1, d.level(id));
                for (Map.Entry<Stat, Double> e : r.stats.entrySet()) {
                    m.merge(e.getKey(), statValue(e.getValue(), lv), Double::sum);
                }
                if (!r.set.isEmpty()) setCounts.merge(r.set, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : setCounts.entrySet()) {
            RelicSet set = sets.get(e.getKey());
            if (set == null) continue;
            for (Map.Entry<Integer, Map<Stat, Double>> b : set.bonuses.entrySet()) {
                if (e.getValue() >= b.getKey()) {
                    for (Map.Entry<Stat, Double> s : b.getValue().entrySet()) {
                        m.merge(s.getKey(), s.getValue(), Double::sum);
                    }
                }
            }
        }
        m.computeIfPresent(Stat.MONEY, (k, v) -> Math.min(v, maxMoneyBonus));
        d.cache = m;
        return m;
    }

    public double bonus(PlayerData d, Stat s) {
        return bonuses(d).getOrDefault(s, 0.0);
    }

    // ===================== Chi phí =====================

    public int upgradeDust(RelicDef r, int level) {
        RarityInfo ri = rarities.get(r.rarity);
        return (int) Math.ceil(dustBase * (1.0 + level * dustGrowth) * ri.costMult);
    }

    public int upgradeCores(RelicDef r, int level) {
        RarityInfo ri = rarities.get(r.rarity);
        return (level > 0 && level % 10 == 0) ? ri.milestoneCores : 0;
    }

    // ===================== Hành động =====================

    public void msg(CommandSender s, String text) {
        s.sendMessage(AstraxRelic.c(prefix + text));
    }

    public boolean craft(Player p, RelicDef r) {
        PlayerData d = data(p);
        RarityInfo ri = rarities.get(r.rarity);
        if (d.owns(r.id)) { msg(p, "&cBạn đã sở hữu Di Vật này."); return false; }
        if (!ri.craftable) { msg(p, "&cDi Vật này không thể chế tạo, chỉ nhận qua sự kiện hoặc Admin."); return false; }
        if (d.get("shard") < ri.craftShards || d.get("core") < ri.craftCores) {
            msg(p, "&cThiếu nguyên liệu. Cần &b" + ri.craftShards + " Shard"
                    + (ri.craftCores > 0 ? " &dvà " + ri.craftCores + " Core" : "") + "&c.");
            return false;
        }
        d.add("shard", -ri.craftShards);
        d.add("core", -ri.craftCores);
        d.setLevel(r.id, 1);
        msg(p, "&aĐã chế tạo " + ri.color + r.name + "&a!");
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        return true;
    }

    public boolean upgrade(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (!d.owns(r.id)) { msg(p, "&cBạn chưa sở hữu Di Vật này."); return false; }
        int lv = d.level(r.id);
        if (lv >= maxLevel) { msg(p, "&eDi Vật đã đạt cấp tối đa."); return false; }
        int dust = upgradeDust(r, lv);
        int cores = upgradeCores(r, lv);
        if (d.get("dust") < dust || d.get("core") < cores) {
            msg(p, "&cThiếu nguyên liệu. Cần &e" + dust + " Dust"
                    + (cores > 0 ? " &dvà " + cores + " Core" : "") + "&c.");
            return false;
        }
        d.add("dust", -dust);
        d.add("core", -cores);
        d.setLevel(r.id, lv + 1);
        msg(p, "&aNâng cấp " + rarities.get(r.rarity).color + r.name + " &alên cấp &f" + (lv + 1) + "&a.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        return true;
    }

    public boolean equip(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (!d.owns(r.id)) { msg(p, "&cBạn chưa sở hữu Di Vật này."); return false; }
        if (d.isEquipped(r.id)) { msg(p, "&eDi Vật này đang được trang bị."); return false; }
        int slot = d.freeSlot(slots);
        if (slot < 0) { msg(p, "&cĐã hết slot. Hãy tháo một Di Vật trước."); return false; }
        d.equipAt(slot, r.id);
        msg(p, "&aĐã trang bị " + rarities.get(r.rarity).color + r.name + "&a vào slot " + (slot + 1) + ".");
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.8f, 1.2f);
        return true;
    }

    public boolean unequip(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (!d.isEquipped(r.id)) { msg(p, "&eDi Vật này chưa được trang bị."); return false; }
        d.unequip(r.id);
        msg(p, "&7Đã tháo " + rarities.get(r.rarity).color + r.name + "&7.");
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.0f);
        return true;
    }

    public boolean recycle(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (!d.owns(r.id)) { msg(p, "&cBạn chưa sở hữu Di Vật này."); return false; }
        if (d.isEquipped(r.id)) { msg(p, "&cHãy tháo Di Vật trước khi tái chế."); return false; }
        RarityInfo ri = rarities.get(r.rarity);
        int lv = d.level(r.id);
        double shard = ri.craftShards;
        double core = ri.craftCores;
        double dust = 0;
        for (int l = 1; l < lv; l++) {
            dust += upgradeDust(r, l);
            core += upgradeCores(r, l);
        }
        int rs = (int) Math.round(shard * refundRatio);
        int rd = (int) Math.round(dust * refundRatio);
        int rc = (int) Math.round(core * refundRatio);
        d.removeRelic(r.id);
        d.add("shard", rs);
        d.add("dust", rd);
        d.add("core", rc);
        msg(p, "&7Đã tái chế " + ri.color + r.name + "&7: &b+" + rs + " Shard&7, &e+" + rd + " Dust&7, &d+" + rc + " Core&7.");
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.6f, 1.2f);
        return true;
    }

    /** Dùng cho Admin: cấp Di Vật với cấp độ chỉ định. */
    public void grant(Player target, RelicDef r, int level) {
        PlayerData d = data(target);
        d.setLevel(r.id, Math.max(1, Math.min(maxLevel, level)));
    }

    // ===================== Tiến độ & phần thưởng =====================

    public void progress(Player p, Trigger t, int amount) {
        if (amount <= 0) return;
        PlayerData d = data(p);
        List<RelicDef> done = new ArrayList<>();
        synchronized (d) {
            for (int i = 0; i < Math.min(slots, d.equipped.length); i++) {
                String id = d.equipped[i];
                RelicDef r = id == null ? null : relics.get(id);
                if (r == null || r.trigger != t) continue;
                int cur = d.charge.getOrDefault(id, 0) + amount;
                int loops = 0;
                while (cur >= r.chargeNeeded && loops++ < 20) {
                    cur -= r.chargeNeeded;
                    done.add(r);
                }
                d.charge.put(id, Math.min(cur, r.chargeNeeded - 1));
            }
        }
        for (RelicDef r : done) complete(p, d, r);
    }

    private void complete(Player p, PlayerData d, RelicDef r) {
        StringBuilder rw = new StringBuilder();
        if (r.rewardShard > 0) { d.add("shard", r.rewardShard); rw.append("&b+").append(r.rewardShard).append(" Shard "); }
        if (r.rewardDust > 0) { d.add("dust", r.rewardDust); rw.append("&e+").append(r.rewardDust).append(" Dust "); }
        if (r.rewardCore > 0) { d.add("core", r.rewardCore); rw.append("&d+").append(r.rewardCore).append(" Core "); }
        for (String cmd : r.rewardCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        String color = rarities.get(r.rarity).color;
        p.sendActionBar(AstraxRelic.c(color + r.name + " &7kích hoạt! " + rw));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        p.spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.6);
    }

    public static String materialName(String mat) {
        return switch (mat) {
            case "shard" -> "&bShard";
            case "dust" -> "&eDust";
            default -> "&dCore";
        };
    }

    /** Roll nguyên liệu rơi theo nguồn: block-break, mob-kill, boss-kill. */
    public void rollDrops(Player p, String source) {
        List<DropRule> rules = drops.get(source);
        if (rules == null || rules.isEmpty()) return;
        PlayerData d = data(p);
        double luck = 1.0 + bonus(d, Stat.MATERIAL_LUCK) / 100.0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (DropRule dr : rules) {
            if (rnd.nextDouble() < dr.chance * luck) {
                int amt = dr.min + rnd.nextInt(dr.max - dr.min + 1);
                d.add(dr.material, amt);
                sb.append("&f+").append(amt).append(' ').append(materialName(dr.material)).append("  ");
            }
        }
        if (sb.length() > 0) p.sendActionBar(AstraxRelic.c(sb.toString()));
    }

    // ===================== Nhận diện =====================

    public boolean isMineable(Material m) {
        String n = m.name();
        for (String t : mineable) if (n.contains(t)) return true;
        return false;
    }

    public boolean isBoss(LivingEntity e) {
        if (bossTypes.contains(e.getType().name())) return true;
        if (e.getMaxHealth() >= bossMinHealth) return true;
        if (e.customName() != null && !bossNames.isEmpty()) {
            String n = PlainTextComponentSerializer.plainText().serialize(e.customName()).toLowerCase(Locale.ROOT);
            for (String k : bossNames) if (n.contains(k)) return true;
        }
        return false;
    }

    public static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
