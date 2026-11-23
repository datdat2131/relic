package com.astraxbox.relic;

import com.astraxbox.relic.Model.Stat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AstraxRelic extends JavaPlugin {
    private static AstraxRelic instance;
    private DataStore store;
    private RelicManager manager;
    private RelicGui gui;
    private long auraTicks;
    private int seconds;

    public static AstraxRelic get() { return instance; }

    public DataStore store() { return store; }

    public RelicManager manager() { return manager; }

    public RelicGui gui() { return gui; }

    /** Chuyển chuỗi có mã màu & thành Component (không in nghiêng). */
    public static Component c(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        try {
            store = new DataStore(this);
        } catch (Exception e) {
            getLogger().severe("Không thể mở SQLite: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        manager = new RelicManager(this);
        manager.reload();
        gui = new RelicGui(this);

        getServer().getPluginManager().registerEvents(new RelicListener(this), this);
        getServer().getPluginManager().registerEvents(gui, this);

        RelicCommand cmd = new RelicCommand(this);
        for (String name : new String[]{"relic", "relicadmin"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            manager.data(p);
            manager.refresh(p);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RelicExpansion(this).register();
            getLogger().info("Đã kết nối PlaceholderAPI.");
        }

        // Aura mỗi 3 tick
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            auraTicks++;
            for (Player p : Bukkit.getOnlinePlayers()) manager.auraTick(p, auraTicks);
        }, 20L, 3L);
        // Nội tại theo giây (Haste, Hồi Phục)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            seconds++;
            for (Player p : Bukkit.getOnlinePlayers()) manager.secondTick(p, seconds);
        }, 20L, 20L);
        // Tự động lưu mỗi 5 phút
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> manager.saveAll(), 6000L, 6000L);

        getLogger().info("AstraxRelic đã bật với " + manager.relics.size() + " Di Vật.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) manager.clear(p);
            manager.saveAll();
        }
        if (store != null) store.close();
    }

    // ===================== API cho plugin khác =====================

    /** Tổng bonus của một chỉ số (Máu/Giáp là điểm, Sát thương/Tốc độ là %). */
    public double getBonus(Player p, Stat stat) {
        return manager.bonus(manager.data(p), stat);
    }
}
