package com.astraxbox.relic;

import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.Trigger;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RelicListener implements Listener {
    private final AstraxRelic plugin;

    public RelicListener(AstraxRelic plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.manager().preload(e.getUniqueId(), e.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = plugin.manager().data(e.getPlayer());
        d.name = e.getPlayer().getName();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.manager().unload(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (!plugin.manager().isMineable(e.getBlock().getType())) return;
        plugin.manager().progress(p, Trigger.BLOCK_BREAK, 1);
        plugin.manager().rollDrops(p, "block-break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (ent instanceof Player || ent instanceof ArmorStand) return;
        Player killer = ent.getKiller();
        if (killer == null) return;
        RelicManager m = plugin.manager();
        if (m.isBoss(ent)) {
            m.progress(killer, Trigger.BOSS_KILL, 1);
            m.rollDrops(killer, "boss-kill");
        } else {
            m.rollDrops(killer, "mob-kill");
        }
        m.progress(killer, Trigger.MOB_KILL, 1);
    }
}
