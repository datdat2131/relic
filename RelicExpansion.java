package com.astraxbox.relic;

import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.RelicDef;
import com.astraxbox.relic.Model.Stat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Placeholder:
 *  %astraxrelic_shard% / _dust / _core
 *  %astraxrelic_equipped_count% / _owned_count / _total_levels
 *  %astraxrelic_slot_1% ... _slot_5
 *  %astraxrelic_bonus_<stat>%      vd: bonus_mining_exp  => 12.5
 *  %astraxrelic_multiplier_<stat>% vd: multiplier_mining_exp => 1.125
 *  %astraxrelic_level_<relicid>%
 *  %astraxrelic_set_<setid>%       số món đang trang bị của bộ
 */
public final class RelicExpansion extends PlaceholderExpansion {
    private final AstraxRelic plugin;

    public RelicExpansion(AstraxRelic plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "astraxrelic"; }

    @Override public String getAuthor() { return "AstraxBox"; }

    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }

    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (offline == null) return "";
        Player p = offline.getPlayer();
        if (p == null) return "";
        RelicManager m = plugin.manager();
        PlayerData d = m.data(p);
        String key = params.toLowerCase();

        switch (key) {
            case "rush_active": return String.valueOf(m.isRush());
            case "rush_left": return String.valueOf(m.rushLeftSec());
            case "shard": return String.valueOf(d.get("shard"));
            case "dust": return String.valueOf(d.get("dust"));
            case "core": return String.valueOf(d.get("core"));
            case "owned_count": synchronized (d) { return String.valueOf(d.levels.size()); }
            case "total_levels": synchronized (d) { return String.valueOf(d.levels.values().stream().mapToInt(Integer::intValue).sum()); }
            case "equipped_count": {
                int n = 0;
                synchronized (d) { for (String s : d.equipped) if (s != null) n++; }
                return String.valueOf(n);
            }
            default: break;
        }

        if (key.startsWith("slot_")) {
            try {
                int i = Integer.parseInt(key.substring(5)) - 1;
                if (i < 0 || i >= m.slotsFor(d)) return "";
                String id;
                synchronized (d) { id = d.equipped[i]; }
                RelicDef r = id == null ? null : m.relics.get(id);
                if (r == null) return "Trống";
                String colored = m.rarities.get(r.rarity).color + r.name;
                return LegacyComponentSerializer.legacySection().serialize(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(colored));
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if (key.startsWith("bonus_")) {
            Stat s = Stat.parse(key.substring(6));
            return s == null ? "0" : RelicManager.fmt(m.bonus(d, s));
        }
        if (key.startsWith("multiplier_")) {
            Stat s = Stat.parse(key.substring(11));
            return s == null ? "1" : String.format(java.util.Locale.ROOT, "%.3f", 1.0 + m.bonus(d, s) / 100.0);
        }
        if (key.startsWith("level_")) return String.valueOf(d.level(key.substring(6)));
        if (key.startsWith("set_")) return String.valueOf(m.setCount(d, key.substring(4)));
        return null;
    }
}
