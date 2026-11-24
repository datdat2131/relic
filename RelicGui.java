package com.astraxbox.relic;

import com.astraxbox.relic.Model.ActivePassive;
import com.astraxbox.relic.Model.GemDef;
import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.RarityInfo;
import com.astraxbox.relic.Model.RelicDef;
import com.astraxbox.relic.Model.RelicRecord;
import com.astraxbox.relic.Model.RelicSet;
import com.astraxbox.relic.Model.Stat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** GUI /relic: Menu chính, Codex, Bộ Relic. */
public final class RelicGui implements Listener {
    private enum Type { MAIN, CODEX, SETS, DETAIL }

    private static final class Holder implements InventoryHolder {
        final Type type;
        int page;
        String relicId;
        final String[] ids = new String[54];
        Inventory inv;

        Holder(Type type) { this.type = type; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private static final int[] EQUIP_SLOTS = {10, 12, 14, 16};
    private static final int[] SOCKET_SLOTS = {11, 13, 15};
    private static final int[] GEM_SLOTS = {28, 29, 30, 31, 32, 33, 34, 35};
    private static final int[] SET_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] CODEX_SLOTS = new int[28];

    static {
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) CODEX_SLOTS[i++] = row * 9 + col;
        }
    }

    private final AstraxRelic plugin;

    public RelicGui(AstraxRelic plugin) {
        this.plugin = plugin;
    }

    private RelicManager mgr() { return plugin.manager(); }

    // ===================== Mở GUI =====================

    public void openMain(Player p) {
        Holder h = new Holder(Type.MAIN);
        h.inv = Bukkit.createInventory(h, 45, AstraxRelic.c("&8✦ AstraxRelic ✦"));
        fillMain(h.inv, p);
        p.openInventory(h.inv);
    }

    public void openCodex(Player p, int page) {
        Holder h = new Holder(Type.CODEX);
        h.page = page;
        h.inv = Bukkit.createInventory(h, 54, AstraxRelic.c("&8✦ Codex Di Vật ✦"));
        fillCodex(h, p);
        p.openInventory(h.inv);
    }

    public void openSets(Player p) {
        Holder h = new Holder(Type.SETS);
        h.inv = Bukkit.createInventory(h, 27, AstraxRelic.c("&8✦ Bộ Relic ✦"));
        fillSets(h.inv, p);
        p.openInventory(h.inv);
    }

    public void openDetail(Player p, String relicId) {
        Holder h = new Holder(Type.DETAIL);
        h.relicId = relicId;
        h.inv = Bukkit.createInventory(h, 45, AstraxRelic.c("&8✦ Hồ sơ Di Vật ✦"));
        fillDetail(h, p);
        p.openInventory(h.inv);
    }

    private static String date(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy").format(new Date(ms));
    }

    private void fillDetail(Holder h, Player p) {
        PlayerData d = mgr().data(p);
        RelicDef r = mgr().relics.get(h.relicId);
        Inventory inv = h.inv;
        fillAll(inv);
        if (r == null || !d.owns(r.id)) {
            inv.setItem(40, item(Material.OAK_DOOR, "&7Quay lại Codex", null, false));
            return;
        }
        RarityInfo ri = mgr().rarities.get(r.rarity);
        RelicRecord rec = d.rec(r.id);
        inv.setItem(4, relicItem(d, r, List.of()));

        int sk = mgr().sockets(r);
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            int slot = SOCKET_SLOTS[i];
            if (i >= sk) {
                inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, "&8Không có ổ khảm", null, false));
                continue;
            }
            String gid;
            synchronized (d) { gid = rec.gems[i]; }
            GemDef g = gid == null ? null : mgr().gems.get(gid);
            if (g == null) {
                inv.setItem(slot, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&bỔ khảm " + (i + 1) + " &7(trống)",
                        List.of("&7Click chuột trái vào gem bên dưới", "&7để khảm vào ổ này."), false));
            } else {
                inv.setItem(slot, item(g.icon, g.name, List.of("&a" + g.stat.line(g.value), "", "&eClick: &7tháo gem"), true));
            }
        }

        List<String> bond = new ArrayList<>();
        bond.add("&7Chủ sở hữu: &f" + p.getName());
        bond.add("&7Ngày thức tỉnh: &f" + date(rec.foundAt));
        bond.add("&7Cấp hiện tại: &f" + d.level(r.id) + "&7/&f" + mgr().maxLevel);
        bond.add("");
        bond.add("&7Block đã đào cùng Relic: &f" + rec.blocks);
        bond.add("&7Boss đã cùng hạ: &f" + rec.bosses);
        bond.add("&7Quái đã cùng hạ: &f" + rec.mobs);
        inv.setItem(20, item(Material.WRITABLE_BOOK, ri.color + "Hồ sơ " + strip(r.name), bond, false));

        List<String> mem = new ArrayList<>();
        for (Map.Entry<String, String> e : RelicManager.MEMORY_NAMES.entrySet()) {
            Long t = rec.memory.get(e.getKey());
            if (t != null) mem.add("&a✔ &f" + e.getValue() + " &8(" + date(t) + ")");
            else mem.add("&8○ " + e.getValue());
        }
        inv.setItem(24, item(Material.ENCHANTED_BOOK, "&d✦ Ký ức Di Vật", mem, false));

        int gi = 0;
        for (GemDef g : mgr().gems.values()) {
            if (gi >= GEM_SLOTS.length) break;
            List<String> lore = new ArrayList<>();
            lore.add("&a" + g.stat.line(g.value));
            lore.add("&7Đang có: &f" + d.gemCount(g.id));
            lore.add("&7Chế tạo: &e" + g.costDust + " Dust" + (g.costCore > 0 ? " &d" + g.costCore + " Core" : ""));
            lore.add("");
            lore.add("&eClick trái: &7khảm vào Di Vật");
            lore.add("&eClick phải: &7chế tạo thêm");
            inv.setItem(GEM_SLOTS[gi++], item(g.icon, g.name, lore, false));
        }
        inv.setItem(40, item(Material.OAK_DOOR, "&7Quay lại Codex", null, false));
    }

    // ===================== Dựng nội dung =====================

    private void fillAll(Inventory inv) {
        inv.clear();
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "&r", null, false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void fillMain(Inventory inv, Player p) {
        PlayerData d = mgr().data(p);
        fillAll(inv);
        inv.setItem(4, item(Material.NETHER_STAR, "&b✦ AstraxRelic ✦", materialLore(d), false));
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            int slot = EQUIP_SLOTS[i];
            if (i >= mgr().slotsFor(d)) {
                if (i == 3) {
                    List<String> lore = new ArrayList<>();
                    lore.add("&7Slot đặc biệt thứ 4.");
                    lore.add("&7Mở khóa bằng &6Thử Thách Tối Thượng");
                    lore.add("&7hoặc do &cAdmin &7cấp.");
                    lore.add("");
                    lore.addAll(mgr().questLines(d));
                    lore.add("");
                    lore.add("&eClick: &7xem tiến độ trong chat");
                    inv.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, "&6✦ Slot đặc biệt &c(Khóa)", lore, false));
                } else {
                    inv.setItem(slot, item(Material.RED_STAINED_GLASS_PANE, "&cSlot bị khóa", null, false));
                }
                continue;
            }
            String id;
            synchronized (d) { id = d.equipped[i]; }
            RelicDef r = id == null ? null : mgr().relics.get(id);
            if (r == null) {
                inv.setItem(slot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Slot " + (i + 1) + " trống",
                        List.of("&8Mở Codex để trang bị Di Vật."), false));
            } else {
                inv.setItem(slot, relicItem(d, r, List.of("", "&eClick trái: &7tháo Di Vật")));
            }
        }
        inv.setItem(22, item(Material.WRITABLE_BOOK, "&e✦ Tổng chỉ số", bonusLore(d), false));
        inv.setItem(28, item(Material.BOOK, "&a✧ Codex", List.of("&7Xem, chế tạo, trang bị,", "&7nâng cấp và tái chế Di Vật."), false));
        inv.setItem(30, item(Material.ENCHANTED_BOOK, "&d✦ Bộ Relic", List.of("&7Xem hiệu ứng bộ (Set Bonus)."), false));
        inv.setItem(32, item(Material.GOLD_INGOT, "&6🏆 Xếp hạng", List.of("&7Top cấp Relic & sưu tầm."), false));
        List<String> ql = new ArrayList<>();
        if (d.slot4) {
            ql.add("&aBạn đã mở khóa Slot Relic thứ 4!");
        } else {
            ql.addAll(mgr().questLines(d));
            ql.add("");
            ql.add("&7Hoàn thành tất cả để mở slot thứ 4.");
        }
        inv.setItem(34, item(Material.DRAGON_EGG, "&6✦ Thử Thách Tối Thượng", ql, d.slot4));
        inv.setItem(40, item(Material.BARRIER, "&cĐóng", null, false));
    }

    private void fillCodex(Holder h, Player p) {
        PlayerData d = mgr().data(p);
        Inventory inv = h.inv;
        fillAll(inv);
        for (int i = 0; i < h.ids.length; i++) h.ids[i] = null;

        List<RelicDef> list = new ArrayList<>(mgr().relics.values());
        int perPage = CODEX_SLOTS.length;
        int maxPage = Math.max(0, (list.size() - 1) / perPage);
        if (h.page > maxPage) h.page = maxPage;
        if (h.page < 0) h.page = 0;
        int start = h.page * perPage;
        for (int i = 0; i < perPage && start + i < list.size(); i++) {
            RelicDef r = list.get(start + i);
            int slot = CODEX_SLOTS[i];
            if (!mgr().isVisible(d, r)) {
                List<String> ml = new ArrayList<>();
                ml.add("&7Độ hiếm: &8???");
                ml.add("");
                ml.add("&8\"" + r.hint + "\"");
                String[] sf = mgr().serverFirst(r.id);
                if (sf != null) {
                    ml.add("");
                    ml.add("&7Đã có người khám phá: &f" + sf[1]);
                }
                inv.setItem(slot, item(Material.GRAY_DYE, "&8??? — Di Vật bí ẩn", ml, false));
                continue;
            }
            h.ids[slot] = r.id;
            List<String> footer = new ArrayList<>();
            footer.add("");
            if (!d.owns(r.id)) {
                if (r.hidden) footer.add("&6Di Vật bí ẩn đã khám phá");
                footer.add("&eClick trái: &7chế tạo");
            } else {
                footer.add("&eClick trái: &7trang bị");
                footer.add("&eClick phải: &7tháo");
                footer.add("&eShift + Trái: &7nâng cấp");
                footer.add("&eShift + Phải: &7tái chế");
                footer.add("&eQ (thả đồ): &7Hồ sơ, Ký ức & Socket");
            }
            inv.setItem(slot, relicItem(d, r, footer));
        }
        inv.setItem(4, item(Material.NETHER_STAR, "&b✦ Nguyên liệu", materialLore(d), false));
        if (h.page > 0) inv.setItem(45, item(Material.ARROW, "&e← Trang trước", null, false));
        inv.setItem(49, item(Material.OAK_DOOR, "&7Quay lại menu", null, false));
        if (h.page < maxPage) inv.setItem(53, item(Material.ARROW, "&eTrang sau →", null, false));
    }

    private void fillSets(Inventory inv, Player p) {
        PlayerData d = mgr().data(p);
        fillAll(inv);
        int idx = 0;
        for (RelicSet set : mgr().sets.values()) {
            if (idx >= SET_SLOTS.length) break;
            int count = mgr().setCount(d, set.id);
            List<String> lore = new ArrayList<>();
            lore.add("&7Đang trang bị: &f" + count);
            lore.add("");
            lore.add("&6Thành viên:");
            for (RelicDef r : mgr().relics.values()) {
                if (!r.set.equals(set.id)) continue;
                String color = mgr().rarities.get(r.rarity).color;
                if (d.isEquipped(r.id)) lore.add("&a✔ " + color + r.name + " &8(đang trang bị)");
                else if (d.owns(r.id)) lore.add("&e● " + color + r.name + " &8(đã sở hữu)");
                else lore.add("&8○ &7" + strip(r.name));
            }
            lore.add("");
            lore.add("&6Hiệu ứng bộ:");
            for (Map.Entry<Integer, Map<Stat, Double>> b : set.bonuses.entrySet()) {
                boolean active = count >= b.getKey();
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Stat, Double> s : b.getValue().entrySet()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s.getKey().line(s.getValue()));
                }
                lore.add((active ? "&a[✔] " : "&8[ ] ") + (active ? "&f" : "&7") + b.getKey() + " món: " + (active ? "&a" : "&7") + sb);
            }
            inv.setItem(SET_SLOTS[idx++], item(set.icon, set.name, lore, count >= 2));
        }
        inv.setItem(22, item(Material.OAK_DOOR, "&7Quay lại menu", null, false));
    }

    // ===================== Item =====================

    private ItemStack relicItem(PlayerData d, RelicDef r, List<String> footer) {
        RarityInfo ri = mgr().rarities.get(r.rarity);
        boolean owned = d.owns(r.id);
        boolean eq = d.isEquipped(r.id);
        int lv = d.level(r.id);
        List<String> l = new ArrayList<>();
        l.add("&7Độ hiếm: " + ri.color + ri.name);
        l.add("&7Nhóm: &f" + groupName(r.group));
        RelicSet s = mgr().sets.get(r.set);
        if (s != null) l.add("&7Bộ: " + s.name);
        l.add("");
        for (String line : r.description) l.add("&7" + line);
        l.add("");
        int shown = Math.max(1, lv);
        for (Map.Entry<Stat, Double> e : r.stats.entrySet()) {
            l.add("&a" + e.getKey().line(mgr().statValue(e.getValue(), shown)));
        }
        l.add("");
        if (r.passive != null) {
            boolean unlocked = owned && lv >= r.passive.unlockLevel;
            if (unlocked) {
                double v = r.passive.type.scales ? mgr().statValue(r.passive.value, lv) : r.passive.value;
                l.add("&d✦ Nội tại: &f" + r.passive.type.display);
                l.add("&7  " + r.passive.type.describe(v, r.passive.chance, r.passive.cooldown));
            } else {
                l.add("&8✦ Nội tại ẩn: &8&k???????");
                l.add("&8  Mở khóa ở cấp " + r.passive.unlockLevel);
            }
        }
        if (r.curse != null) {
            l.add("&c☠ Lời nguyền: " + r.curse.stat.line(r.curse.value));
            if (!r.curse.text.isEmpty()) l.add("&8  " + r.curse.text);
        }
        if (r.orbit != null) l.add("&7Vật phẩm bay: &f" + r.orbit.style.display);
        if (mgr().sockets(r) > 0) l.add("&7Ổ khảm: &f" + mgr().sockets(r));
        if (r.hidden) {
            String[] sf = mgr().serverFirst(r.id);
            if (sf != null) l.add("&6✦ Người khám phá đầu tiên: &f" + sf[1] + " &8(" + date(Long.parseLong(sf[2])) + ")");
        }
        l.add("");
        if (owned) {
            l.add("&7Cấp: &f" + lv + "&7/&f" + mgr().maxLevel + (eq ? "  &a(Đang trang bị)" : ""));
            if (lv < mgr().maxLevel) {
                int dust = mgr().upgradeDust(r, lv);
                int cores = mgr().upgradeCores(r, lv);
                l.add("&7Nâng cấp: &e" + dust + " Dust" + (cores > 0 ? " &d" + cores + " Core" : ""));
            } else {
                l.add("&6Đã đạt cấp tối đa");
            }
        } else if (ri.craftable) {
            l.add("&cChưa sở hữu");
            l.add("&7Chế tạo: &b" + ri.craftShards + " Shard" + (ri.craftCores > 0 ? " &d" + ri.craftCores + " Core" : ""));
        } else {
            l.add("&cChưa sở hữu &8(chỉ nhận từ sự kiện/Admin)");
        }
        l.addAll(footer);
        Material icon = owned ? r.icon : (ri.craftable ? r.icon : Material.GRAY_DYE);
        return item(icon, ri.color + r.name, l, eq);
    }

    private List<String> materialLore(PlayerData d) {
        List<String> l = new ArrayList<>();
        l.add("&bShard: &f" + d.get("shard"));
        l.add("&eDust: &f" + d.get("dust"));
        l.add("&dCore: &f" + d.get("core"));
        return l;
    }

    private List<String> bonusLore(PlayerData d) {
        List<String> l = new ArrayList<>();
        Map<Stat, Double> b = mgr().bonuses(d);
        boolean any = false;
        for (Stat s : Stat.values()) {
            double v = b.getOrDefault(s, 0.0);
            if (v > 0) {
                l.add("&a" + s.line(v));
                any = true;
            }
        }
        if (!any) l.add("&7Chưa có chỉ số nào. Hãy trang bị Di Vật!");
        List<ActivePassive> ps = mgr().passives(d);
        if (!ps.isEmpty()) {
            l.add("");
            l.add("&dNội tại đang hoạt động:");
            for (ActivePassive a : ps) l.add("&7• &f" + a.type.display);
        }
        List<com.astraxbox.relic.Model.Synergy> sy = mgr().activeSynergies(d);
        if (!sy.isEmpty()) {
            l.add("");
            l.add("&bCộng hưởng đang hoạt động:");
            for (com.astraxbox.relic.Model.Synergy g : sy) l.add("&7• &f" + g.name);
        }
        return l;
    }

    private ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(AstraxRelic.c(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(AstraxRelic.c(s));
            meta.lore(l);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    private static String strip(String s) {
        return s.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    private static String groupName(String g) {
        return switch (g) {
            case "ATTACK" -> "Công kích";
            case "GUARD" -> "Phòng thủ";
            case "VITAL" -> "Sinh lực";
            case "SWIFT" -> "Tốc độ";
            case "MINER" -> "Thợ mỏ";
            default -> "Huyền bí";
        };
    }

    // ===================== Sự kiện click =====================

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getSlot();
        ClickType ct = e.getClick();

        switch (h.type) {
            case MAIN -> {
                for (int i = 0; i < EQUIP_SLOTS.length; i++) {
                    if (EQUIP_SLOTS[i] != slot) continue;
                    PlayerData d = mgr().data(p);
                    if (i >= mgr().slotsFor(d)) {
                        if (i == 3) { p.closeInventory(); mgr().sendQuest(p); }
                        return;
                    }
                    String id;
                    synchronized (d) { id = d.equipped[i]; }
                    RelicDef r = id == null ? null : mgr().relics.get(id);
                    if (r != null) {
                        mgr().unequip(p, r);
                        fillMain(h.inv, p);
                    }
                    return;
                }
                if (slot == 28) openCodex(p, 0);
                else if (slot == 30) openSets(p);
                else if (slot == 32) { p.closeInventory(); p.performCommand("relic top"); }
                else if (slot == 34) { p.closeInventory(); mgr().sendQuest(p); }
                else if (slot == 40) p.closeInventory();
            }
            case CODEX -> {
                if (slot == 45 && h.page > 0) { h.page--; fillCodex(h, p); return; }
                if (slot == 53) { h.page++; fillCodex(h, p); return; }
                if (slot == 49) { openMain(p); return; }
                String id = h.ids[slot];
                if (id == null) return;
                RelicDef r = mgr().relics.get(id);
                if (r == null) return;
                PlayerData d = mgr().data(p);
                switch (ct) {
                    case LEFT -> {
                        if (!d.owns(id)) mgr().craft(p, r);
                        else mgr().equip(p, r);
                    }
                    case RIGHT -> mgr().unequip(p, r);
                    case SHIFT_LEFT -> mgr().upgrade(p, r);
                    case SHIFT_RIGHT -> mgr().recycle(p, r);
                    case DROP, CONTROL_DROP -> {
                        if (d.owns(id)) openDetail(p, id);
                        return;
                    }
                    default -> { return; }
                }
                fillCodex(h, p);
            }
            case SETS -> {
                if (slot == 22) openMain(p);
            }
            case DETAIL -> {
                if (slot == 40) { openCodex(p, 0); return; }
                RelicDef r = h.relicId == null ? null : mgr().relics.get(h.relicId);
                if (r == null) return;
                for (int i = 0; i < SOCKET_SLOTS.length; i++) {
                    if (SOCKET_SLOTS[i] == slot) {
                        if (i < mgr().sockets(r)) mgr().removeGem(p, r, i);
                        fillDetail(h, p);
                        return;
                    }
                }
                for (int i = 0; i < GEM_SLOTS.length; i++) {
                    if (GEM_SLOTS[i] != slot) continue;
                    GemDef g = null;
                    int k = 0;
                    for (GemDef x : mgr().gems.values()) {
                        if (k++ == i) { g = x; break; }
                    }
                    if (g == null) return;
                    if (ct == ClickType.RIGHT) mgr().craftGem(p, g);
                    else if (ct == ClickType.LEFT) mgr().insertGem(p, r, g);
                    fillDetail(h, p);
                    return;
                }
            }
        }
    }
}
