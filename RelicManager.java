package com.astraxbox.relic;

import com.astraxbox.relic.Model.ActivePassive;
import com.astraxbox.relic.Model.AuraDef;
import com.astraxbox.relic.Model.AuraStyle;
import com.astraxbox.relic.Model.DropRule;
import com.astraxbox.relic.Model.Passive;
import com.astraxbox.relic.Model.PassiveDef;
import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.Rarity;
import com.astraxbox.relic.Model.RarityInfo;
import com.astraxbox.relic.Model.RelicDef;
import com.astraxbox.relic.Model.RelicSet;
import com.astraxbox.relic.Model.Stat;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Logic chính: config, chỉ số, nội tại ẩn, aura, chế tạo, nâng cấp, trang bị, tái chế, rơi nguyên liệu. */
public final class RelicManager {
    private final AstraxRelic plugin;

    public final Map<String, RelicDef> relics = new LinkedHashMap<>();
    public final Map<String, RelicSet> sets = new LinkedHashMap<>();
    public final Map<Rarity, RarityInfo> rarities = new EnumMap<>(Rarity.class);
    private final Map<String, List<DropRule>> drops = new HashMap<>();
    private final Map<Stat, Double> caps = new EnumMap<>(Stat.class);
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLoc = new ConcurrentHashMap<>();

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
    public boolean auraEnabled = true;
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
        rarities.clear();
        drops.clear();
        caps.clear();

        attrHealth = findAttr("max_health", "generic.max_health");
        attrArmor = findAttr("armor", "generic.armor");
        attrSpeed = findAttr("movement_speed", "generic.movement_speed");

        prefix = c.getString("messages.prefix", "&8[&bAstraxRelic&8] &7");
        slots = Math.max(1, Math.min(3, c.getInt("settings.slots", 3)));
        maxLevel = Math.max(1, c.getInt("settings.max-level", 30));
        levelScale = c.getDouble("settings.level-scale", 0.04);
        auraEnabled = c.getBoolean("settings.aura-enabled", true);
        lifestealCap = c.getDouble("settings.lifesteal-cap-per-hit", 4);
        dustBase = c.getDouble("upgrade.dust-base", 10);
        dustGrowth = c.getDouble("upgrade.dust-growth", 0.15);
        refundRatio = c.getDouble("recycle.refund-ratio", 0.5);
        mineable = upper(c.getStringList("mining.mineable-tokens"));
        bossMinHealth = c.getDouble("boss.min-health", 100);
        bossTypes = upper(c.getStringList("boss.types"));
        bossNames = lower(c.getStringList("boss.name-keywords"));

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
                r.aura = readAura(s.getConfigurationSection("aura"));
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
                set.aura = readAura(s.getConfigurationSection("aura"));
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

    private AuraDef readAura(ConfigurationSection s) {
        if (s == null) return null;
        AuraDef a = new AuraDef();
        try { a.particle = Particle.valueOf(s.getString("particle", "END_ROD").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { a.particle = Particle.END_ROD; }
        try { a.style = AuraStyle.valueOf(s.getString("style", "RING").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { a.style = AuraStyle.RING; }
        String col = s.getString("color", "");
        if (!col.isEmpty()) {
            try { a.color = Color.fromRGB(Integer.parseInt(col.replace("#", ""), 16)); }
            catch (IllegalArgumentException ignored) { }
        }
        return a;
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
        lastLoc.remove(p.getUniqueId());
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

    public List<RelicDef> equippedDefs(PlayerData d) {
        List<RelicDef> out = new ArrayList<>();
        synchronized (d) {
            for (int i = 0; i < Math.min(slots, d.equipped.length); i++) {
                RelicDef r = d.equipped[i] == null ? null : relics.get(d.equipped[i]);
                if (r != null) out.add(r);
            }
        }
        return out;
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
        for (Map.Entry<Stat, Double> cap : caps.entrySet()) {
            m.computeIfPresent(cap.getKey(), (k, v) -> Math.min(v, cap.getValue()));
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

    // ===================== Aura (hiệu ứng đặc trưng) =====================

    public void auraTick(Player p, long tick) {
        if (!auraEnabled || p.getGameMode() == GameMode.SPECTATOR || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) return;
        PlayerData d = data(p);
        List<RelicDef> eq = equippedDefs(d);
        if (eq.isEmpty()) return;
        Location loc = p.getLocation();
        Location prev = lastLoc.put(p.getUniqueId(), loc);
        boolean moved = prev != null && prev.getWorld() == loc.getWorld() && prev.distanceSquared(loc) > 0.01;
        for (RelicDef r : eq) {
            if (r.aura != null) drawAura(loc, r.aura, tick, moved, 1 + r.rarity.ordinal() / 2);
        }
        for (RelicSet s : sets.values()) {
            if (s.aura != null && setCount(d, s.id) >= 3) drawAura(loc, s.aura, tick + 9, moved, 2);
        }
    }

    private void drawAura(Location base, AuraDef a, long tick, boolean moved, int intensity) {
        World w = base.getWorld();
        if (w == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double t = tick * 0.35;
        switch (a.style) {
            case RING -> {
                int n = 2 + intensity;
                for (int i = 0; i < n; i++) {
                    double ang = t + i * (2 * Math.PI / n);
                    spawn(w, base.clone().add(Math.cos(ang) * 0.8, 0.15, Math.sin(ang) * 0.8), a, 1);
                }
            }
            case ORBIT -> {
                int n = 1 + intensity;
                double y = 1.0 + 0.3 * Math.sin(t);
                for (int i = 0; i < n; i++) {
                    double ang = t * 1.3 + i * (2 * Math.PI / n);
                    spawn(w, base.clone().add(Math.cos(ang) * 0.9, y, Math.sin(ang) * 0.9), a, 1);
                }
            }
            case HALO -> {
                for (int i = 0; i < 4; i++) {
                    double ang = t * 0.8 + i * (Math.PI / 2);
                    spawn(w, base.clone().add(Math.cos(ang) * 0.4, 2.25, Math.sin(ang) * 0.4), a, 1);
                }
            }
            case HELIX -> {
                double y = (tick * 0.07) % 2.0;
                for (int k = 0; k < 2; k++) {
                    double ang = t + k * Math.PI;
                    spawn(w, base.clone().add(Math.cos(ang) * 0.7, y, Math.sin(ang) * 0.7), a, 1);
                }
            }
            case SPARK -> {
                for (int i = 0; i < intensity + 1; i++) {
                    spawn(w, base.clone().add((rnd.nextDouble() - 0.5) * 1.2, 0.1 + rnd.nextDouble() * 1.8,
                            (rnd.nextDouble() - 0.5) * 1.2), a, 1);
                }
            }
            case TRAIL -> {
                if (moved) {
                    for (int i = 0; i < intensity + 1; i++) {
                        spawn(w, base.clone().add((rnd.nextDouble() - 0.5) * 0.3, 0.1, (rnd.nextDouble() - 0.5) * 0.3), a, 1);
                    }
                }
            }
        }
    }

    private void spawn(World w, Location l, AuraDef a, int count) {
        try {
            if (a.particle == Particle.DUST) {
                w.spawnParticle(Particle.DUST, l, count, 0, 0, 0, 0,
                        new Particle.DustOptions(a.color != null ? a.color : Color.WHITE, 1.0f));
            } else {
                w.spawnParticle(a.particle, l, count, 0, 0, 0, 0);
            }
        } catch (IllegalArgumentException ignored) { }
    }

    private void equipBurst(Player p, RelicDef r) {
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        if (r.aura == null) return;
        World w = p.getWorld();
        Location base = p.getLocation();
        for (int i = 0; i < 28; i++) {
            double ang = i * 0.5;
            spawn(w, base.clone().add(Math.cos(ang) * 0.9, i * 0.075, Math.sin(ang) * 0.9), r.aura, 2);
        }
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
        if (r.passive != null && lv + 1 == r.passive.unlockLevel) {
            msg(p, "&d✦ Nội tại ẩn đã mở khóa: &f" + r.passive.type.display + "&d!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
        refresh(p);
        return true;
    }

    public boolean equip(Player p, RelicDef r) {
        PlayerData d = data(p);
        if (!d.owns(r.id)) { msg(p, "&cBạn chưa sở hữu Di Vật này."); return false; }
        if (d.isEquipped(r.id)) { msg(p, "&eDi Vật này đang được trang bị."); return false; }
        int slot = d.freeSlot(slots);
        if (slot < 0) { msg(p, "&cChỉ trang bị được &f" + slots + " &cDi Vật. Hãy tháo một cái trước."); return false; }
        d.equipAt(slot, r.id);
        msg(p, "&aĐã trang bị " + rarities.get(r.rarity).color + r.name + "&a vào slot " + (slot + 1) + ".");
        refresh(p);
        equipBurst(p, r);
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
        double luck = 1.0 + passiveSum(passives(d), Passive.MATERIAL_LUCK) / 100.0;
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
