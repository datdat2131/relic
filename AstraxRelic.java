package com.astraxbox.relic;

import com.astraxbox.relic.Model.Stat;
import com.astraxbox.relic.Model.Trigger;
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

        for (Player p : Bukkit.getOnlinePlayers()) manager.data(p);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RelicExpansion(this).register();
            getLogger().info("Đã kết nối PlaceholderAPI.");
        }

        // Mỗi phút online
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) manager.progress(p, Trigger.ONLINE_MINUTE, 1);
        }, 1200L, 1200L);
        // Tự động lưu mỗi 5 phút
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> manager.saveAll(), 6000L, 6000L);

        getLogger().info("AstraxRelic đã bật với " + manager.relics.size() + " Di Vật.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.saveAll();
        if (store != null) store.close();
    }

    // ===================== API cho plugin khác =====================

    /** Tổng bonus (đơn vị %) của một chỉ số, ví dụ 12.5 = +12.5%. */
    public double getBonus(Player p, Stat stat) {
        return manager.bonus(manager.data(p), stat);
    }

    /** Hệ số nhân, ví dụ bonus 12.5% => 1.125. */
    public double getMultiplier(Player p, Stat stat) {
        return 1.0 + getBonus(p, stat) / 100.0;
    }

    /** Cộng tiến độ cho các Di Vật đang trang bị có trigger tương ứng. */
    public void progress(Player p, Trigger trigger, int amount) {
        manager.progress(p, trigger, amount);
    }
}
