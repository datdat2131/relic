package com.astraxbox.relic;

import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.RelicDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class RelicCommand implements CommandExecutor, TabCompleter {
    private final AstraxRelic plugin;

    public RelicCommand(AstraxRelic plugin) {
        this.plugin = plugin;
    }

    private RelicManager mgr() { return plugin.manager(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("relicadmin")) return admin(sender, args);
        return player(sender, args);
    }

    // ===================== /relic =====================

    private boolean player(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("top")) { top(sender); return true; }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Lệnh này dành cho người chơi.");
            return true;
        }
        switch (sub) {
            case "" -> plugin.gui().openMain(p);
            case "codex" -> plugin.gui().openCodex(p, 0);
            case "sets" -> plugin.gui().openSets(p);
            case "equip", "unequip", "upgrade", "recycle" -> {
                if (args.length < 2) { mgr().msg(p, "&cDùng: /relic " + sub + " <id>"); return true; }
                RelicDef r = mgr().relics.get(args[1].toLowerCase(Locale.ROOT));
                if (r == null) { mgr().msg(p, "&cKhông tìm thấy Di Vật &f" + args[1]); return true; }
                switch (sub) {
                    case "equip" -> mgr().equip(p, r);
                    case "unequip" -> mgr().unequip(p, r);
                    case "upgrade" -> mgr().upgrade(p, r);
                    default -> mgr().recycle(p, r);
                }
            }
            default -> mgr().msg(p, "&7Dùng: &f/relic &8[codex|sets|top|equip|unequip|upgrade|recycle <id>]");
        }
        return true;
    }

    private void top(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            mgr().saveAll();
            List<String[]> rows = plugin.store().top(10);
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(AstraxRelic.c("&8&m      &r &b✦ Top Di Vật &8&m      "));
                if (rows.isEmpty()) sender.sendMessage(AstraxRelic.c("&7Chưa có dữ liệu."));
                int i = 1;
                for (String[] r : rows) {
                    sender.sendMessage(AstraxRelic.c("&e#" + i++ + " &f" + r[0] + " &8- &7Tổng cấp &a" + r[1] + " &8| &7Sưu tầm &d" + r[2]));
                }
            });
        });
    }

    // ===================== /relicadmin =====================

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astraxrelic.admin")) {
            mgr().msg(sender, "&cBạn không có quyền.");
            return true;
        }
        if (args.length == 0) { usage(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            mgr().reload();
            mgr().msg(sender, "&aĐã reload config. &7(" + mgr().relics.size() + " Di Vật)");
            return true;
        }
        if (args.length < 2) { usage(sender); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { mgr().msg(sender, "&cNgười chơi không online."); return true; }
        PlayerData d = mgr().data(target);

        switch (sub) {
            case "give" -> {
                if (args.length < 3) { usage(sender); return true; }
                RelicDef r = mgr().relics.get(args[2].toLowerCase(Locale.ROOT));
                if (r == null) { mgr().msg(sender, "&cKhông có Di Vật &f" + args[2]); return true; }
                int lv = args.length > 3 ? parse(args[3], 1) : 1;
                mgr().grant(target, r, lv);
                mgr().msg(sender, "&aĐã cấp &f" + r.id + " &acho &f" + target.getName() + ".");
                mgr().msg(target, "&aBạn nhận được Di Vật " + mgr().rarities.get(r.rarity).color + r.name + "&a!");
            }
            case "remove" -> {
                if (args.length < 3) { usage(sender); return true; }
                RelicDef r = mgr().relics.get(args[2].toLowerCase(Locale.ROOT));
                if (r == null) { mgr().msg(sender, "&cKhông có Di Vật &f" + args[2]); return true; }
                d.removeRelic(r.id);
                mgr().refresh(target);
                mgr().msg(sender, "&aĐã xóa &f" + r.id + " &akhỏi &f" + target.getName() + ".");
            }
            case "material" -> {
                if (args.length < 4) { usage(sender); return true; }
                String mat = args[2].toLowerCase(Locale.ROOT);
                if (!mat.equals("shard") && !mat.equals("dust") && !mat.equals("core")) {
                    mgr().msg(sender, "&cLoại nguyên liệu: shard | dust | core");
                    return true;
                }
                int amt = parse(args[3], 0);
                d.add(mat, amt);
                mgr().msg(sender, "&aĐã chỉnh &f" + mat + " &a" + (amt >= 0 ? "+" : "") + amt + " cho &f" + target.getName() + ".");
            }
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        mgr().msg(s, "&7/relicadmin give <người chơi> <relic> [cấp]");
        mgr().msg(s, "&7/relicadmin remove <người chơi> <relic>");
        mgr().msg(s, "&7/relicadmin material <người chơi> <shard|dust|core> <số>");
        mgr().msg(s, "&7/relicadmin reload");
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    // ===================== Tab =====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("relicadmin")) {
            if (!sender.hasPermission("astraxrelic.admin")) return List.of();
            if (args.length == 1) return filter(List.of("give", "remove", "material", "reload"), args[0]);
            if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[1]);
            }
            if (args.length == 3) {
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "give", "remove" -> { return filter(mgr().relics.keySet(), args[2]); }
                    case "material" -> { return filter(List.of("shard", "dust", "core"), args[2]); }
                    default -> { return List.of(); }
                }
            }
            return List.of();
        }
        if (args.length == 1) return filter(List.of("codex", "sets", "top", "equip", "unequip", "upgrade", "recycle"), args[0]);
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "equip", "unequip", "upgrade", "recycle" -> { return filter(mgr().relics.keySet(), args[1]); }
                default -> { return List.of(); }
            }
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
