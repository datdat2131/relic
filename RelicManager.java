package com.astraxbox.relic;

import com.astraxbox.relic.Model.ActivePassive;
import com.astraxbox.relic.Model.CurseDef;
import com.astraxbox.relic.Model.DiscoverDef;
import com.astraxbox.relic.Model.DropRule;
import com.astraxbox.relic.Model.GemDef;
import com.astraxbox.relic.Model.OrbitDef;
import com.astraxbox.relic.Model.OrbitStyle;
import com.astraxbox.relic.Model.Passive;
import com.astraxbox.relic.Model.PassiveDef;
import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.Rarity;
import com.astraxbox.relic.Model.RarityInfo;
import com.astraxbox.relic.Model.RelicDef;
import com.astraxbox.relic.Model.RelicRecord;
import com.astraxbox.relic.Model.RelicSet;
import com.astraxbox.relic.Model.Synergy;
import com.astraxbox.relic.Model.Stat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Logic chính: config, chỉ số, nội tại ẩn, aura, chế tạo, nâng cấp, trang bị, tái chế, rơi nguyên liệu. */
public final class RelicManager {
    private final AstraxRelic plugin;

    public final Map<String, RelicDef> relics = new LinkedHashMap<>();
    public final Map<String, RelicSet> sets = new LinkedHashMap<>();
    public final Map<Rarity, RarityInfo> rarities = new EnumMap<>(Rarity.class);
    public final List<Synergy> synergies = new ArrayList<>();
    public final Map<String, GemDef> gems = new LinkedHashMap<>();
    private final Map<String, String[]> serverFirst = new ConcurrentHashMap<>();
    private final Map<Stat, Double> floors = new EnumMap<>(Stat.class);
    public int deepY = -40;
    public static final Map<String, String> MEMORY_NAMES = new LinkedHashMap<>();

    static {
        MEMORY_NAMES.put("found", "Được thức tỉnh");
        MEMORY_NAMES.put("lv10", "Đạt cấp 10");
        MEMORY_NAMES.put("passive", "Mở khóa nội tại ẩn");
        MEMORY_NAMES.put("lvmax", "Đạt cấp tối đa");
        MEMORY_NAMES.put("boss1", "Lần đầu cùng hạ Boss");
        MEMORY_NAMES.put("boss10", "Cùng hạ 10 Boss");
        MEMORY_NAMES.put("boss50", "Cùng hạ 50 Boss");
        MEMORY_NAMES.put("blocks1k", "Cùng đào 1.000 block");
        MEMORY_NAMES.put("blocks10k", "Cùng đào 10.000 block");
        MEMORY_NAMES.put("blocks100k", "Cùng đào 100.000 block");
    }
    private final Map<String, List<DropRule>> drops = new HashMap<>();
    private final Map<Stat, Double> caps = new EnumMap<>(Stat.class);
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, ItemDisplay[]> orbits = new ConcurrentHashMap<>();

    private final NamespacedKey keyHealth;
    private final NamespacedKey keyArmor;
    private final NamespacedKey keySpeed;
    private Attribute attrHealth;
    private Attribute attrArmor;
    private Attribute attrSpeed;

    public int slots = 3;
    public int maxLevel = 30;
    public double levelScale = 0.04;
    public double dustBase = 10;
    public double dustGrowth = 0.15;
    public double refundRatio = 0.5;
    public double bossMinHealth = 100;
    public double lifestealCap = 4;
    public boolean orbitEnabled = true;
    public Rarity bcRarity = Rarity.LEGENDARY;
    public boolean rushAuto = false;
    public boolean rushBossbar = true;
    public int rushIntervalMin = 60;
    public int rushDurationMin = 10;
    public double rushMult = 2.0;
    private long rushEnd = 0;
    private long rushTotalMs = 1;
    private long nextRushAt = 0;
    private BossBar rushBar;
    public boolean orbitHideOwner = false;
    public int orbitUpdateTicks = 2;
    public float orbitScale = 0.35f;
    public boolean questEnabled = true;
    public boolean questBroadcast = true;
    public int qBoss = 30;
    public int qMob = 10000;
    public int qBlocks = 100000;
    public int qOwned = 15;
    public int qMaxLevel = 30;
    public Rarity qRarity = Rarity.MYTHIC;
    public List<String> mineable = new ArrayList<>();
    public List<String> bossTypes = new ArrayList<>();
    public List<String> bossNames = new ArrayList<>();
    public String prefix = "";

    public RelicManager(AstraxRelic plugin) {
        this.plugin = plugin;
        this.keyHealth = new NamespacedKey(plugin, "relic_health");
        this.keyArmor = new NamespacedKey(plugin, "relic_armor");
        this.keySpeed = new NamespacedKey(plugin, "relic_speed");
    }

    // ===================== Config =====================

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        relics.clear();
        sets.clear();
        synergies.clear();
        gems.clear();
        floors.clear();
        rarities.clear();
        drops.clear();
        caps.clear();

        attrHealth = findAttr("max_health", "generic.max_health");
        attrArmor = findAttr("armor", "generic.armor");
        attrSpeed = findAttr("movement_speed", "generic.movement_speed");

        prefix = c.getString("messages.prefix", "&8[&bAstraxRelic&8] &7");
        slots = Math.max(1, Math.min(3, c.getInt("settings.slots", 3))); // slot thứ 4 là slot đặc biệt
        maxLevel = Math.max(1, c.getInt("settings.max-level", 30));
        levelScale = c.getDouble("settings.level-scale", 0.04);
        orbitEnabled = c.getBoolean("orbit.enabled", true);
        try { bcRarity = Rarity.valueOf(c.getString("settings.broadcast-min-rarity", "LEGENDARY").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { bcRarity = null; }
        rushAuto = c.getBoolean("relic-rush.auto", false);
        rushBossbar = c.getBoolean("relic-rush.bossbar", true);
        rushIntervalMin = Math.max(1, c.getInt("relic-rush.interval-minutes", 60));
        rushDurationMin = Math.max(1, c.getInt("relic-rush.duration-minutes", 10));
        rushMult = Math.max(1.0, c.getDouble("relic-rush.multiplier", 2.0));
        if (nextRushAt == 0) nextRushAt = System.currentTimeMillis() + rushIntervalMin * 60_000L;
        orbitHideOwner = c.getBoolean("orbit.hide-from-owner", false);
        orbitUpdateTicks = Math.max(1, Math.min(10, c.getInt("orbit.update-ticks", 2)));
        orbitScale = (float) c.getDouble("orbit.default-scale", 0.35);
        questEnabled = c.getBoolean("ultimate-quest.enabled", true);
        questBroadcast = c.getBoolean("ultimate-quest.broadcast", true);
        qBoss = c.getInt("ultimate-quest.boss-kills", 30);
        qMob = c.getInt("ultimate-quest.mob-kills", 10000);
        qBlocks = c.getInt("ultimate-quest.blocks-mined", 100000);
        qOwned = c.getInt("ultimate-quest.relics-owned", 15);
        qMaxLevel = c.getInt("ultimate-quest.relic-max-level", 30);
        try { qRarity = Rarity.valueOf(c.getString("ultimate-quest.own-rarity", "MYTHIC").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { qRarity = null; }
        lifestealCap = c.getDouble("settings.lifesteal-cap-per-hit", 4);
        dustBase = c.getDouble("upgrade.dust-base", 10);
        dustGrowth = c.getDouble("upgrade.dust-growth", 0.15);
        refundRatio = c.getDouble("recycle.refund-ratio", 0.5);
        mineable = upper(c.getStringList("mining.mineable-tokens"));
        bossMinHealth = c.getDouble("boss.min-health", 100);
        bossTypes = upper(c.getStringList("boss.types"));
        bossNames = lower(c.getStringList("boss.name-keywords"));

        deepY = c.getInt("discovery.deep-y", -40);
        floors.put(Stat.HEALTH, c.getDouble("floors.HEALTH", -10));
        floors.put(Stat.DAMAGE, c.getDouble("floors.DAMAGE", -50));
        floors.put(Stat.ARMOR, c.getDouble("floors.ARMOR", -10));
        floors.put(Stat.SPEED, c.getDouble("floors.SPEED", -30));
        caps.put(Stat.HEALTH, c.getDouble("caps.HEALTH", 40));
        caps.put(Stat.DAMAGE, c.getDouble("caps.DAMAGE", 100));
        caps.put(Stat.ARMOR, c.getDouble("caps.ARMOR", 16));
        caps.put(Stat.SPEED, c.getDouble("caps.SPEED", 40));

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
            ri.sockets = Math.max(0, Math.min(3, s.getInt("sockets", 1)));
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
                r.group = s.getString("group", "MYSTIC").toUpperCase(Locale.ROOT);
                r.set = s.getString("set", "").toLowerCase(Locale.ROOT);
                try { r.rarity = Rarity.valueOf(s.getString("rarity", "COMMON").toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { r.rarity = Rarity.COMMON; }
                Material m = Material.matchMaterial(s.getString("icon", "PAPER"));
                r.icon = m != null ? m : Material.PAPER;
                r.description = s.getStringList("description");
                r.stats = readStats(s.getConfigurationSection("stats"));
                r.hidden = s.getBoolean("hidden", false);
                r.hint = s.getString("hint", "");
                ConfigurationSection dc = s.getConfigurationSection("discover");
                if (dc != null) {
                    DiscoverDef dd = new DiscoverDef();
                    dd.type = dc.getString("type", "EVENT").toUpperCase(Locale.ROOT);
                    dd.value = dc.getDouble("value", 0);
                    dd.set = dc.getString("set", "").toLowerCase(Locale.ROOT);
                    dd.relic = dc.getString("relic", "").toLowerCase(Locale.ROOT);
                    r.discover = dd;
                }
                ConfigurationSection cs = s.getConfigurationSection("curse");
                if (cs != null) {
                    Stat cst = Stat.parse(cs.getString("stat"));
                    if (cst != null) {
                        CurseDef cd = new CurseDef();
                        cd.stat = cst;
                        cd.value = cs.getDouble("value", 0);
                        cd.text = cs.getString("text", "");
                        r.curse = cd;
                    }
                }
                ConfigurationSection ps = s.getConfigurationSection("passive");
                if (ps != null) {
                    try {
                        PassiveDef pd = new PassiveDef();
                        pd.type = Passive.valueOf(ps.getString("type", "").toUpperCase(Locale.ROOT));
                        pd.value = ps.getDouble("value", 0);
                        pd.chance = ps.getDouble("chance", 0);
                        pd.cooldown = ps.getInt("cooldown", 0);
                        pd.unlockLevel = ps.getInt("unlock-level", 10);
                        r.passive = pd;
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Nội tại sai ở relic " + id);
                    }
                }
                r.orbit = readOrbit(s.getConfigurationSection("orbit"), r.icon);
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
                set.glow = readColor(s.getString("glow-color", ""));
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

        ConfigurationSection sy = c.getConfigurationSection("synergies");
        if (sy != null) {
            for (String id : sy.getKeys(false)) {
                ConfigurationSection s = sy.getConfigurationSection(id);
                if (s == null) continue;
                Synergy g = new Synergy();
                g.id = id.toLowerCase(Locale.ROOT);
                g.name = s.getString("name", id);
                for (String rid : s.getStringList("relics")) g.relics.add(rid.toLowerCase(Locale.ROOT));
                if (g.relics.size() < 2) continue;
                g.stats = readStats(s.getConfigurationSection("bonuses"));
                ConfigurationSection ps = s.getConfigurationSection("passive");
                if (ps != null) {
                    try {
                        PassiveDef pd = new PassiveDef();
                        pd.type = Passive.valueOf(ps.getString("type", "").toUpperCase(Locale.ROOT));
                        pd.value = ps.getDouble("value", 0);
                        pd.chance = ps.getDouble("chance", 0);
                        pd.cooldown = ps.getInt("cooldown", 0);
                        g.passive = pd;
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Nội tại sai ở cộng hưởng " + id);
                    }
                }
                synergies.add(g);
            }
        }

        ConfigurationSection gs = c.getConfigurationSection("gems");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(id);
                if (s == null) continue;
                Stat gst = Stat.parse(s.getString("stat"));
                if (gst == null) continue;
                GemDef g = new GemDef();
                g.id = id.toLowerCase(Locale.ROOT);
                g.name = s.getString("name", id);
                g.stat = gst;
                g.value = s.getDouble("value", 1);
                g.costDust = s.getInt("cost-dust", 50);
                g.costCore = s.getInt("cost-core", 0);
                Material gm = Material.matchMaterial(s.getString("icon", "EMERALD"));
                g.icon = gm != null && gm.isItem() ? gm : Material.EMERALD;
                gems.put(g.id, g);
            }
        }

        serverFirst.clear();
        serverFirst.putAll(plugin.store().loadDiscoveries());

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

        clearAllOrbits();
        for (PlayerData d : cache.values()) d.invalidate();
        for (Player p : plugin.getServer().getOnlinePlayers()) refresh(p);
    }

    private Attribute findAttr(String... names) {
        for (String n : names) {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(n));
            if (a != null) return a;
        }
        return null;
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

    private OrbitDef readOrbit(ConfigurationSection s, Material fallback) {
        OrbitDef o = new OrbitDef();
        o.item = fallback;
        o.scale = orbitScale;
        if (s != null) {
            Material m = Material.matchMaterial(s.getString("item", ""));
            if (m != null && m.isItem()) o.item = m;
            try { o.style = OrbitStyle.valueOf(s.getString("style", "ORBIT").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
            o.scale = (float) s.getDouble("scale", orbitScale);
        }
        if (!o.item.isItem()) o.item = Material.PAPER;
        return o;
    }

    private Color readColor(String col) {
        if (col == null || col.isEmpty()) return null;
        try { return Color.fromRGB(Integer.parseInt(col.replace("#", ""), 16)); }
        catch (IllegalArgumentException e) { return null; }
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
        clearOrbit(p);
        PlayerData d = cache.remove(p.getUniqueId());
        if (d != null) plugin.store().save(d);
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) plugin.store().save(d);
    }

    // ===================== Chỉ số & thuộc tính =====================

    public double statValue(double base, int level) {
        return base * (1.0 + (Math.max(1, level) - 1) * levelScale);
    }

    public int slotsFor(PlayerData d) {
        return Math.min(4, slots + (d.slot4 ? 1 : 0));
    }

    public int setCount(PlayerData d, String setId) {
        int n = 0;
        synchronized (d) {
            for (int i = 0; i < slotsFor(d); i++) {
                RelicDef r = d.equipped[i] == null ? null : relics.get(d.equipped[i]);
                if (r != null && r.set.equals(setId)) n++;
            }
        }
        return n;
    }

    public List<RelicDef> equippedDefs(PlayerData d) {
        List<RelicDef> out = new ArrayList<>();
        synchronized (d) {
            for (int i = 0; i < slotsFor(d); i++) {
                RelicDef r = d.equipped[i] == null ? null : relics.get(d.equipped[i]);
                if (r != null) out.add(r);
            }
        }
        return out;
    }

    public List<Synergy> activeSynergies(PlayerData d) {
        Set<String> ids = new HashSet<>();
        for (RelicDef r : equippedDefs(d)) ids.add(r.id);
        List<Synergy> out = new ArrayList<>();
        for (Synergy g : synergies) if (ids.containsAll(g.relics)) out.add(g);
        return out;
    }

    public String describeSynergy(Synergy g) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Stat, Double> e : g.stats.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey().line(e.getValue()));
        }
        if (g.passive != null) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(g.passive.type.display).append(": ").append(g.passive.type.describe(g.passive.value, g.passive.chance, g.passive.cooldown));
        }
        return sb.toString();
    }

    public Map<Stat, Double> bonuses(PlayerData d) {
        Map<Stat, Double> cached = d.cache;
        if (cached != null) return cached;
        Map<Stat, Double> m = new EnumMap<>(Stat.class);
        Map<String, Integer> setCounts = new HashMap<>();
        for (RelicDef r : equippedDefs(d)) {
            int lv = Math.max(1, d.level(r.id));
            for (Map.Entry<Stat, Double> e : r.stats.entrySet()) {
                m.merge(e.getKey(), statValue(e.getValue(), lv), Double::sum);
            }
            if (!r.set.isEmpty()) setCounts.merge(r.set, 1, Integer::sum);
            if (r.curse != null) m.merge(r.curse.stat, r.curse.value, Double::sum);
            RelicRecord rec = d.rec(r.id);
            int sk = sockets(r);
            for (int i = 0; i < sk; i++) {
                String gid;
                synchronized (d) { gid = rec.gems[i]; }
                GemDef g = gid == null ? null : gems.get(gid);
                if (g != null) m.merge(g.stat, g.value, Double::sum);
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
        for (Synergy g : activeSynergies(d)) {
            for (Map.Entry<Stat, Double> e : g.stats.entrySet()) m.merge(e.getKey(), e.getValue(), Double::sum);
        }
        for (Map.Entry<Stat, Double> cap : caps.entrySet()) {
            m.computeIfPresent(cap.getKey(), (k, v) -> Math.min(v, cap.getValue()));
        }
        for (Map.Entry<Stat, Double> fl : floors.entrySet()) {
            m.computeIfPresent(fl.getKey(), (k, v) -> Math.max(v, fl.getValue()));
        }
        d.cache = m;
        return m;
    }

    public double bonus(PlayerData d, Stat s) {
        return bonuses(d).getOrDefault(s, 0.0);
    }

    /** Áp Máu / Giáp / Tốc độ lên người chơi (sát thương xử lý trong listener). */
    public void refresh(Player p) {
        PlayerData d = data(p);
        Map<Stat, Double> b = bonuses(d);
        setMod(p, attrHealth, keyHealth, b.getOrDefault(Stat.HEALTH, 0.0), AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, attrArmor, keyArmor, b.getOrDefault(Stat.ARMOR, 0.0), AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, attrSpeed, keySpeed, b.getOrDefault(Stat.SPEED, 0.0) / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        double max = maxHealth(p);
        if (p.getHealth() > max) p.setHealth(max);
    }

    public void clear(Player p) {
        setMod(p, attrHealth, keyHealth, 0, AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, attrArmor, keyArmor, 0, AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, attrSpeed, keySpeed, 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        double max = maxHealth(p);
        if (p.getHealth() > max) p.setHealth(max);
    }

    private void setMod(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        if (attr == null) return;
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        for (AttributeModifier mod : new ArrayList<>(inst.getModifiers())) {
            if (mod.getKey().equals(key)) inst.removeModifier(mod);
        }
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    public double maxHealth(Player p) {
        AttributeInstance i = attrHealth == null ? null : p.getAttribute(attrHealth);
        return i == null ? 20.0 : i.getValue();
    }

    public void heal(Player p, double amt) {
        if (amt <= 0 || p.isDead()) return;
        p.setHealth(Math.min(maxHealth(p), p.getHealth() + amt));
    }

    // ===================== Nội tại ẩn =====================

    public List<ActivePassive> passives(PlayerData d) {
        List<ActivePassive> c = d.passiveCache;
        if (c != null) return c;
        List<ActivePassive> out = new ArrayList<>();
        for (RelicDef r : equippedDefs(d)) {
            if (r.passive == null) continue;
            int lv = d.level(r.id);
            if (lv < r.passive.unlockLevel) continue;
            double v = r.passive.type.scales ? statValue(r.passive.value, lv) : r.passive.value;
            out.add(new ActivePassive(r.passive.type, v, r.passive.chance, r.passive.cooldown, r.id));
        }
        for (Synergy g : activeSynergies(d)) {
            if (g.passive != null) {
                out.add(new ActivePassive(g.passive.type, g.passive.value, g.passive.chance, g.passive.cooldown, "syn:" + g.id));
            }
        }
        d.passiveCache = out;
        return out;
    }

    public double passiveSum(List<ActivePassive> list, Passive t) {
        double sum = 0;
        for (ActivePassive a : list) if (a.type == t) sum += a.value;
        return sum;
    }

    /** Kiểm tra hồi chiêu và tiêu thụ nếu dùng được. */
    public boolean tryUse(PlayerData d, ActivePassive a) {
        if (a.cooldown <= 0) return true;
        long now = System.currentTimeMillis();
        Long ready = d.cooldowns.get(a.relicId);
        if (ready != null && ready > now) return false;
        d.cooldowns.put(a.relicId, now + a.cooldown * 1000L);
        return true;
    }

    public void onKill(Player p) {
        PlayerData d = data(p);
        for (ActivePassive a : passives(d)) {
            if (a.type == Passive.KILL_HEAL) {
                heal(p, a.value);
            } else if (a.type == Passive.KILL_SPEED) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (a.value * 20), 1, true, true, true));
            }
        }
    }

    /** Gọi mỗi giây cho mỗi người chơi. */
    public void secondTick(Player p, int sec) {
        PlayerData d = data(p);
        List<ActivePassive> ps = passives(d);
        if (ps.isEmpty()) return;
        int haste = -1;
        double regen = 0;
        for (ActivePassive a : ps) {
            if (a.type == Passive.HASTE) haste = Math.max(haste, (int) a.value);
            else if (a.type == Passive.REGEN) regen += a.value;
        }
        if (haste >= 0) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, haste, true, false, false));
        if (regen > 0 && sec % 5 == 0) heal(p, regen);
    }

    // ===================== Vật phẩm mini bay quanh người =====================

    /** Gọi định kỳ cho mỗi người chơi: tạo/cập nhật/xóa các ItemDisplay bay quanh người. */
    public void orbitTick(Player p, long tick) {
        if (!orbitEnabled) return;
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            clearOrbit(p);
            return;
        }
        PlayerData d = data(p);
        RelicDef[] bySlot = new RelicDef[4];
        synchronized (d) {
            for (int i = 0; i < slotsFor(d); i++) {
                bySlot[i] = d.equipped[i] == null ? null : relics.get(d.equipped[i]);
            }
        }
        Color glow = null;
        for (RelicSet st : sets.values()) {
            if (st.glow != null && setCount(d, st.id) >= 3) { glow = st.glow; break; }
        }
        ItemDisplay[] arr = orbits.computeIfAbsent(p.getUniqueId(), k -> new ItemDisplay[4]);
        Location base = p.getLocation();
        double t = tick * 0.12 * orbitUpdateTicks;
        for (int i = 0; i < 4; i++) {
            RelicDef r = bySlot[i];
            ItemDisplay e = arr[i];
            if (r == null) {
                if (e != null) { e.remove(); arr[i] = null; }
                continue;
            }
            Location target = orbitPos(base, r.orbit.style, i, t);
            if (e == null || !e.isValid() || e.getWorld() != base.getWorld()) {
                if (e != null) e.remove();
                e = spawnOrbit(p, r, target);
                arr[i] = e;
                if (e == null) continue;
            } else {
                if (e.getItemStack() == null || e.getItemStack().getType() != r.orbit.item) {
                    e.setItemStack(new ItemStack(r.orbit.item));
                }
                e.teleport(target);
            }
            if (glow != null) {
                if (!e.isGlowing()) e.setGlowing(true);
                e.setGlowColorOverride(glow);
            } else if (e.isGlowing()) {
                e.setGlowing(false);
            }
        }
    }

    private Location orbitPos(Location base, OrbitStyle style, int slot, double t) {
        double yaw = Math.toRadians(base.getYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -fz;
        double rz = fx;
        double x;
        double y;
        double z;
        double quarter = slot * (Math.PI / 2);
        switch (style) {
            case HALO -> {
                double ang = t * 1.4 + quarter;
                x = Math.cos(ang) * 0.5;
                z = Math.sin(ang) * 0.5;
                y = 2.3 + 0.05 * Math.sin(t * 2 + slot);
            }
            case FEET -> {
                double ang = -t * 1.2 + quarter;
                x = Math.cos(ang) * 0.85;
                z = Math.sin(ang) * 0.85;
                y = 0.35;
            }
            case WING -> {
                double side = (slot % 2 == 0) ? 1.0 : -1.0;
                double row = slot / 2;
                double lateral = side * (0.55 + row * 0.25);
                double back = -0.45 - row * 0.1;
                x = fx * back + rx * lateral;
                z = fz * back + rz * lateral;
                y = 1.35 + row * 0.3 + 0.1 * Math.sin(t * 2 + slot);
            }
            default -> {
                double ang = t + quarter;
                x = Math.cos(ang) * 0.95;
                z = Math.sin(ang) * 0.95;
                y = 1.15 + 0.12 * Math.sin(t * 2 + slot);
            }
        }
        Location l = base.clone().add(x, y, z);
        l.setYaw((float) Math.toDegrees(t * 2 + slot));
        l.setPitch(0f);
        return l;
    }

    private ItemDisplay spawnOrbit(Player p, RelicDef r, Location at) {
        World w = at.getWorld();
        if (w == null) return null;
        final float sc = r.orbit.scale;
        final Material mat = r.orbit.item;
        final int ticks = orbitUpdateTicks;
        ItemDisplay e = w.spawn(at, ItemDisplay.class, disp -> {
            disp.setItemStack(new ItemStack(mat));
            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            disp.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(sc, sc, sc), new AxisAngle4f(0f, 0f, 0f, 1f)));
            disp.setBillboard(Display.Billboard.FIXED);
            disp.setViewRange(0.5f);
            disp.setPersistent(false);
            setTeleportDuration(disp, ticks);
        });
        if (orbitHideOwner) p.hideEntity(plugin, e);
        return e;
    }

    /** Dùng reflection để vẫn chạy được nếu API không có hàm này. */
    private static void setTeleportDuration(Display d, int ticks) {
        try {
            Display.class.getMethod("setTeleportDuration", int.class).invoke(d, ticks);
        } catch (ReflectiveOperationException ignored) { }
    }

    public void clearOrbit(Player p) {
        ItemDisplay[] arr = orbits.remove(p.getUniqueId());
        if (arr == null) return;
        for (ItemDisplay e : arr) if (e != null) e.remove();
    }

    public void clearAllOrbits() {
        for (ItemDisplay[] arr : orbits.values()) {
            for (ItemDisplay e : arr) if (e != null) e.remove();
        }
        orbits.clear();
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
        if (r.hidden && !d.isDiscovered(r.id)) { msg(p, "&cDi Vật này còn là bí ẩn, bạn chưa khám phá ra nó."); return false; }
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
        broadcastRare(p, r);
        checkQuest(p);
        return true;
    }

    private void broadcastRare(Player p, RelicDef r) {
        if (bcRarity == null || r.rarity.ordinal() < bcRarity.ordinal()) return;
        RarityInfo ri = rarities.get(r.rarity);
        Bukkit.broadcast(AstraxRelic.c(prefix + "&6✦ &f" + p.getName() + " &7vừa chế tạo " + ri.color + r.name + " &7(" + ri.color + ri.name + "&7)!"));
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
        if (lv + 1 >= 10) addMemory(p, r, "lv10", true);
        if (lv + 1 >= maxLevel) addMemory(p, r, "lvmax", true);
        msg(p, "&aNâng cấp " + rarities.get(r.rarity).color + r.name + " &alên cấp &f" + (lv + 1) + "&a.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        if (r.passive != null && lv + 1 == r.passive.unlockLevel) {
            msg(p, "&d✦ Nội tại ẩn đã mở khóa: &f" + r.passive.type.display + "&d!");
            addMemory(p, r, "passive", true);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
        refresh(p);
        checkQuest(p);
        return true;
    }

    public boolean equip(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (!d.owns(r.id)) { msg(p, "&cBạn chưa sở hữu Di Vật này."); return false; }
        if (d.isEquipped(r.id)) { msg(p, "&eDi Vật này đang được trang bị."); return false; }
        int slot = d.freeSlot(slotsFor(d));
        if (slot < 0) { msg(p, "&cChỉ trang bị được &f" + slotsFor(d) + " &cDi Vật. Hãy tháo một cái trước."); return false; }
        d.equipAt(slot, r.id);
        msg(p, "&aĐã trang bị " + rarities.get(r.rarity).color + r.name + "&a vào slot " + (slot + 1) + ".");
        refresh(p);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        return true;
    }

    public boolean unequip(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (!d.isEquipped(r.id)) { msg(p, "&eDi Vật này chưa được trang bị."); return false; }
        d.unequip(r.id);
        msg(p, "&7Đã tháo " + rarities.get(r.rarity).color + r.name + "&7.");
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.0f);
        refresh(p);
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

    public void grant(Player target, RelicDef r, int level) {
        PlayerData d = data(target);
        d.setLevel(r.id, Math.max(1, Math.min(maxLevel, level)));
        refresh(target);
    }

    // ===================== Nguyên liệu rơi =====================

    public static String materialName(String mat) {
        return switch (mat) {
            case "shard" -> "&bShard";
            case "dust" -> "&eDust";
            default -> "&dCore";
        };
    }

    public void rollDrops(Player p, String source) {
        List<DropRule> rules = drops.get(source);
        if (rules == null || rules.isEmpty()) return;
        PlayerData d = data(p);
        double luck = (1.0 + passiveSum(passives(d), Passive.MATERIAL_LUCK) / 100.0) * (isRush() ? rushMult : 1.0);
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

    // ===================== Slot thứ 4 & Thử Thách Tối Thượng =====================

    public void countBlock(Player p, int y) {
        PlayerData d = data(p);
        int n;
        synchronized (d) {
            n = ++d.blocksMined;
            if (y <= deepY) d.deepBlocks++;
        }
        trackEquipped(p, 1, 0, 0);
        if (n % 50 == 0) {
            checkQuest(p);
            checkDiscoveries(p);
        }
    }

    public void countKill(Player p, boolean boss) {
        PlayerData d = data(p);
        int n;
        synchronized (d) { n = boss ? ++d.bossKills : ++d.mobKills; }
        trackEquipped(p, 0, boss ? 1 : 0, boss ? 0 : 1);
        if (boss || n % 50 == 0) {
            checkQuest(p);
            checkDiscoveries(p);
        }
    }

    public List<String> questLines(PlayerData d) {
        List<String> l = new ArrayList<>();
        l.add(line("Hạ Boss", d.bossKills, qBoss));
        l.add(line("Hạ quái", d.mobKills, qMob));
        l.add(line("Đào block", d.blocksMined, qBlocks));
        int owned;
        int top = 0;
        boolean hasRarity = qRarity == null;
        synchronized (d) {
            owned = d.levels.size();
            for (Map.Entry<String, Integer> e : d.levels.entrySet()) {
                top = Math.max(top, e.getValue());
                RelicDef r = relics.get(e.getKey());
                if (r != null && qRarity != null && r.rarity.ordinal() >= qRarity.ordinal()) hasRarity = true;
            }
        }
        l.add(line("Sở hữu Di Vật", owned, qOwned));
        l.add(line("Di Vật cấp cao nhất", top, qMaxLevel));
        if (qRarity != null) {
            RarityInfo ri = rarities.get(qRarity);
            l.add((hasRarity ? "&a✔ " : "&c✘ ") + "&7Sở hữu Di Vật độ hiếm &f" + ri.color + ri.name + "&f+");
        }
        return l;
    }

    private static String line(String label, int cur, int need) {
        boolean ok = cur >= need;
        return (ok ? "&a✔ " : "&c✘ ") + "&7" + label + ": &f" + Math.min(cur, need) + "&7/&f" + need;
    }

    public boolean questComplete(PlayerData d) {
        for (String l : questLines(d)) if (l.startsWith("&c")) return false;
        return true;
    }

    public void checkQuest(Player p) {
        if (!questEnabled) return;
        PlayerData d = data(p);
        if (d.slot4 || !questComplete(d)) return;
        unlockSlot4(p, true);
        if (questBroadcast) {
            Bukkit.broadcast(AstraxRelic.c(prefix + "&6✦ &f" + p.getName()
                    + " &6đã hoàn thành &eThử Thách Tối Thượng &6và mở khóa Slot Relic thứ 4!"));
        }
    }

    public void unlockSlot4(Player p, boolean announce) {
        PlayerData d = data(p);
        synchronized (d) { d.slot4 = true; }
        d.invalidate();
        if (announce) {
            msg(p, "&6✦ Slot Relic thứ 4 đã được mở khóa!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    public void lockSlot4(Player p) {
        PlayerData d = data(p);
        synchronized (d) {
            d.slot4 = false;
            d.equipped[3] = null;
        }
        d.invalidate();
        refresh(p);
    }

    public void sendQuest(Player p) {
        PlayerData d = data(p);
        msg(p, "&6✦ Thử Thách Tối Thượng &7(mở Slot Relic thứ 4)");
        if (d.slot4) { p.sendMessage(AstraxRelic.c("&aBạn đã mở khóa slot này.")); return; }
        for (String l : questLines(d)) p.sendMessage(AstraxRelic.c("  " + l));
        p.sendMessage(AstraxRelic.c("&8Hoặc nhờ Admin cấp slot."));
    }

    // ===================== Relic Rush =====================

    public boolean isRush() { return rushEnd > System.currentTimeMillis(); }

    public long rushLeftSec() { return Math.max(0L, (rushEnd - System.currentTimeMillis()) / 1000L); }

    public void startRush(int minutes) {
        rushTotalMs = Math.max(1, minutes) * 60_000L;
        rushEnd = System.currentTimeMillis() + rushTotalMs;
        if (rushBar == null) {
            rushBar = BossBar.bossBar(AstraxRelic.c("&d✦ RELIC RUSH"), 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (rushBossbar) p.showBossBar(rushBar);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.4f);
        }
        Bukkit.broadcast(AstraxRelic.c(prefix + "&d✦ RELIC RUSH! &fTỉ lệ rơi nguyên liệu Relic &ex" + fmt(rushMult)
                + " &ftrong &e" + minutes + " phút&f! Đào mỏ và săn Boss ngay!"));
    }

    public void stopRush() {
        if (rushEnd == 0) return;
        rushEnd = 0;
        nextRushAt = System.currentTimeMillis() + rushIntervalMin * 60_000L;
        hideRushBar();
        Bukkit.broadcast(AstraxRelic.c(prefix + "&dRelic Rush đã kết thúc."));
    }

    public void hideRushBar() {
        if (rushBar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(rushBar);
    }

    public void showRush(Player p) {
        if (isRush() && rushBar != null && rushBossbar) p.showBossBar(rushBar);
    }

    /** Gọi mỗi giây. */
    public void rushTick() {
        long now = System.currentTimeMillis();
        if (rushEnd > 0) {
            if (now >= rushEnd) { stopRush(); return; }
            long left = (rushEnd - now) / 1000L;
            rushBar.progress(Math.max(0f, Math.min(1f, (rushEnd - now) / (float) rushTotalMs)));
            rushBar.name(AstraxRelic.c("&d✦ RELIC RUSH &7x" + fmt(rushMult) + " &8| &fcòn &e" + (left / 60) + "p " + (left % 60) + "s"));
        } else if (rushAuto && now >= nextRushAt) {
            startRush(rushDurationMin);
        }
    }

    // ===================== Ký ức & Hồ sơ Relic (Memory + Bond) =====================

    private void trackEquipped(Player p, int blocks, int bosses, int mobs) {
        PlayerData d = data(p);
        for (RelicDef r : equippedDefs(d)) {
            RelicRecord rec = d.rec(r.id);
            int b;
            int bo;
            synchronized (d) {
                rec.blocks += blocks;
                rec.bosses += bosses;
                rec.mobs += mobs;
                b = rec.blocks;
                bo = rec.bosses;
            }
            if (blocks > 0) {
                if (b >= 1000) addMemory(p, r, "blocks1k", true);
                if (b >= 10000) addMemory(p, r, "blocks10k", true);
                if (b >= 100000) addMemory(p, r, "blocks100k", true);
            }
            if (bosses > 0) {
                if (bo >= 1) addMemory(p, r, "boss1", true);
                if (bo >= 10) addMemory(p, r, "boss10", true);
                if (bo >= 50) addMemory(p, r, "boss50", true);
            }
        }
    }

    public void addMemory(Player p, RelicDef r, String code, boolean notify) {
        PlayerData d = data(p);
        RelicRecord rec = d.rec(r.id);
        boolean added;
        synchronized (d) { added = rec.memory.putIfAbsent(code, System.currentTimeMillis()) == null; }
        if (added && notify) {
            msg(p, "&d✦ Ký ức mới của " + rarities.get(r.rarity).color + r.name + "&d: &f" + MEMORY_NAMES.getOrDefault(code, code));
        }
    }

    // ===================== Socket & Gem =====================

    public int sockets(RelicDef r) {
        return Math.max(0, Math.min(3, rarities.get(r.rarity).sockets));
    }

    public boolean craftGem(Player p, GemDef g) {
        PlayerData d = data(p);
        if (d.get("dust") < g.costDust || d.get("core") < g.costCore) {
            msg(p, "&cThiếu nguyên liệu. Cần &e" + g.costDust + " Dust" + (g.costCore > 0 ? " &dvà " + g.costCore + " Core" : "") + "&c.");
            return false;
        }
        d.add("dust", -g.costDust);
        d.add("core", -g.costCore);
        d.addGem(g.id, 1);
        msg(p, "&aĐã chế tạo " + g.name + "&a.");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        return true;
    }

    public boolean insertGem(Player p, RelicDef r, GemDef g) {
        PlayerData d = data(p);
        if (!d.owns(r.id)) { msg(p, "&cBạn chưa sở hữu Di Vật này."); return false; }
        if (d.gemCount(g.id) <= 0) { msg(p, "&cBạn chưa có " + g.name + "&c. Chuột phải để chế tạo."); return false; }
        RelicRecord rec = d.rec(r.id);
        int sk = sockets(r);
        int slot = -1;
        synchronized (d) {
            for (int i = 0; i < sk; i++) {
                if (rec.gems[i] == null) { slot = i; break; }
            }
            if (slot >= 0) rec.gems[slot] = g.id;
        }
        if (slot < 0) { msg(p, "&cHết ổ khảm. Click gem trong ổ để tháo ra trước."); return false; }
        d.addGem(g.id, -1);
        d.invalidate();
        refresh(p);
        msg(p, "&aĐã khảm " + g.name + " &avào " + rarities.get(r.rarity).color + r.name + "&a.");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
        return true;
    }

    public boolean removeGem(Player p, RelicDef r, int socket) {
        PlayerData d = data(p);
        RelicRecord rec = d.rec(r.id);
        String gid;
        synchronized (d) {
            gid = socket >= 0 && socket < rec.gems.length ? rec.gems[socket] : null;
            if (gid != null) rec.gems[socket] = null;
        }
        if (gid == null) return false;
        d.addGem(gid, 1);
        d.invalidate();
        refresh(p);
        GemDef g = gems.get(gid);
        msg(p, "&7Đã tháo " + (g != null ? g.name : gid) + " &7khỏi Di Vật.");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.0f);
        return true;
    }

    // ===================== Relic bí ẩn & Discovery =====================

    public boolean isVisible(PlayerData d, RelicDef r) {
        return !r.hidden || d.isDiscovered(r.id);
    }

    /** Thông tin người khám phá đầu tiên trên server: {uuid, tên, thời gian} hoặc null. */
    public String[] serverFirst(String relicId) {
        return serverFirst.get(relicId);
    }

    public void checkDiscoveries(Player p) {
        PlayerData d = data(p);
        for (RelicDef r : relics.values()) {
            if (!r.hidden || r.discover == null || d.isDiscovered(r.id)) continue;
            if (conditionMet(d, r.discover)) discover(p, r);
        }
    }

    private boolean conditionMet(PlayerData d, DiscoverDef c) {
        switch (c.type) {
            case "BLOCKS": return d.blocksMined >= c.value;
            case "DEEP_BLOCKS": return d.deepBlocks >= c.value;
            case "BOSS_KILLS": return d.bossKills >= c.value;
            case "MOB_KILLS": return d.mobKills >= c.value;
            case "RELIC_LEVEL": return d.level(c.relic) >= c.value;
            case "OWN_SET": {
                boolean any = false;
                for (RelicDef r : relics.values()) {
                    if (r.set.equals(c.set) && !r.hidden) {
                        any = true;
                        if (!d.owns(r.id)) return false;
                    }
                }
                return any;
            }
            default: return false;
        }
    }

    /** Khám phá một Relic ẩn (tự động khi đủ điều kiện, hoặc Admin/sự kiện gọi). */
    public void discover(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (d.owns(r.id)) { d.markDiscovered(r.id); return; }
        d.markDiscovered(r.id);
        d.setLevel(r.id, 1);
        RarityInfo ri = rarities.get(r.rarity);
        boolean first = !serverFirst.containsKey(r.id) && plugin.store().tryRecordFirst(r.id, p.getUniqueId(), p.getName());
        p.showTitle(Title.title(AstraxRelic.c("&6✦ DISCOVERY! ✦"), AstraxRelic.c(ri.color + r.name)));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        if (first) {
            serverFirst.put(r.id, new String[]{p.getUniqueId().toString(), p.getName(), String.valueOf(System.currentTimeMillis())});
            Bukkit.broadcast(AstraxRelic.c("&8&m                                        "));
            Bukkit.broadcast(AstraxRelic.c("&6✦ DISCOVERY!"));
            Bukkit.broadcast(AstraxRelic.c("&f" + p.getName() + " &7đã khám phá Di Vật: " + ri.color + r.name));
            Bukkit.broadcast(AstraxRelic.c("&7Một Di Vật chưa từng được ghi nhận trong &bAstrax Archive&7!"));
            Bukkit.broadcast(AstraxRelic.c("&8&m                                        "));
        } else {
            msg(p, "&6✦ Bạn đã khám phá Di Vật bí ẩn: " + ri.color + r.name + "&6!");
        }
        refresh(p);
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
