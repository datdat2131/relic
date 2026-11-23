package com.astraxbox.relic;

import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.RarityInfo;
import com.astraxbox.relic.Model.RelicDef;
import com.astraxbox.relic.Model.RelicSet;
import com.astraxbox.relic.Model.Stat;
import java.util.ArrayList;
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
    private enum Type { MAIN, CODEX, SETS }

    private static final class Holder implements InventoryHolder {
        final Type type;
        int page;
        final String[] ids = new String[54];
        Inventory inv;

        Holder(Type type) { this.type = type; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private static final int[] EQUIP_SLOTS = {11, 12, 13, 14, 15};
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
        for (int i = 0; i < 5; i++) {
            int slot = EQUIP_SLOTS[i];
            if (i >= mgr().slots) {
                inv.setItem(slot, item(Material.RED_STAINED_GLASS_PANE, "&cSlot bị khóa", null, false));
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
        inv.setItem(29, item(Material.BOOK, "&a✧ Codex", List.of("&7Xem, chế tạo, trang bị,", "&7nâng cấp và tái chế Di Vật."), false));
        inv.setItem(31, item(Material.ENCHANTED_BOOK, "&d✦ Bộ Relic", List.of("&7Xem hiệu ứng bộ (Set Bonus)."), false));
        inv.setItem(33, item(Material.GOLD_INGOT, "&6🏆 Xếp hạng", List.of("&7Top cấp Relic & sưu tầm."), false));
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
            h.ids[slot] = r.id;
            List<String> footer = new ArrayList<>();
            footer.add("");
            if (!d.owns(r.id)) {
                footer.add("&eClick trái: &7chế tạo");
            } else {
                footer.add("&eClick trái: &7trang bị");
                footer.add("&eClick phải: &7tháo");
                footer.add("&eShift + Trái: &7nâng cấp");
                footer.add("&eShift + Phải: &7tái chế");
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
                    sb.append('+').append(RelicManager.fmt(s.getValue())).append("% ").append(s.getKey().display);
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
            l.add("&a+" + RelicManager.fmt(mgr().statValue(e.getValue(), shown)) + "% &f" + e.getKey().display);
        }
        l.add("");
        l.add("&7Cơ chế: &fmỗi &e" + r.chargeNeeded + " &f" + r.trigger.display);
        StringBuilder rw = new StringBuilder();
        if (r.rewardShard > 0) rw.append("&b+").append(r.rewardShard).append(" Shard ");
        if (r.rewardDust > 0) rw.append("&e+").append(r.rewardDust).append(" Dust ");
        if (r.rewardCore > 0) rw.append("&d+").append(r.rewardCore).append(" Core ");
        if (!r.rewardCommands.isEmpty()) rw.append("&f+quà đặc biệt");
        if (rw.length() > 0) l.add("&7Thưởng: " + rw);
        l.add("");
        if (owned) {
            l.add("&7Cấp: &f" + lv + "&7/&f" + mgr().maxLevel + (eq ? "  &a(Đang trang bị)" : ""));
            int charge;
            synchronized (d) { charge = d.charge.getOrDefault(r.id, 0); }
            l.add("&7Tiến độ: &f" + charge + "&7/&f" + r.chargeNeeded);
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
                l.add("&a+" + RelicManager.fmt(v) + "% &f" + s.display);
                any = true;
            }
        }
        if (!any) l.add("&7Chưa có chỉ số nào. Hãy trang bị Di Vật!");
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
            case "MINING" -> "Khai thác";
            case "COMBAT" -> "Chiến đấu";
            case "ECONOMY" -> "Kinh tế";
            case "FUN" -> "Độc lạ";
            default -> "Tiện ích";
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
                    if (EQUIP_SLOTS[i] != slot || i >= mgr().slots) continue;
                    PlayerData d = mgr().data(p);
                    String id;
                    synchronized (d) { id = d.equipped[i]; }
                    RelicDef r = id == null ? null : mgr().relics.get(id);
                    if (r != null) {
                        mgr().unequip(p, r);
                        fillMain(h.inv, p);
                    }
                    return;
                }
                if (slot == 29) openCodex(p, 0);
                else if (slot == 31) openSets(p);
                else if (slot == 33) { p.closeInventory(); p.performCommand("relic top"); }
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
                    default -> { return; }
                }
                fillCodex(h, p);
            }
            case SETS -> {
                if (slot == 22) openMain(p);
            }
        }
    }
}
