package com.astraxbox.relic;

import com.astraxbox.relic.Model.ActivePassive;
import com.astraxbox.relic.Model.Passive;
import com.astraxbox.relic.Model.PlayerData;
import com.astraxbox.relic.Model.Stat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
        plugin.manager().refresh(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.manager().refresh(p));
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
        plugin.manager().rollDrops(p, "block-break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (ent instanceof Player || ent instanceof ArmorStand) return;
        Player killer = ent.getKiller();
        if (killer == null) return;
        RelicManager m = plugin.manager();
        m.rollDrops(killer, m.isBoss(ent) ? "boss-kill" : "mob-kill");
        m.onKill(killer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        RelicManager m = plugin.manager();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Entity victim = e.getEntity();

        Player vp = victim instanceof Player vpl ? vpl : null;
        PlayerData vd = vp != null ? m.data(vp) : null;
        List<ActivePassive> vps = vd != null ? m.passives(vd) : List.of();

        // ---------- Người bị đánh là player ----------
        if (vp != null && !vps.isEmpty()) {
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                double red = m.passiveSum(vps, Passive.FALL_GUARD);
                if (red > 0) e.setDamage(e.getDamage() * Math.max(0.0, 1.0 - red / 100.0));
            }
            if (e instanceof EntityDamageByEntityEvent) {
                for (ActivePassive a : vps) {
                    if (a.type == Passive.DODGE && rnd.nextDouble() * 100.0 < a.chance) {
                        e.setCancelled(true);
                        vp.sendActionBar(AstraxRelic.c("&b✦ Né tránh!"));
                        vp.playSound(vp.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.7f);
                        return;
                    }
                }
                if (vp.getHealth() < m.maxHealth(vp) * 0.4) {
                    double g = m.passiveSum(vps, Passive.LOW_HP_GUARD);
                    if (g > 0) e.setDamage(e.getDamage() * Math.max(0.0, 1.0 - g / 100.0));
                }
            }
        }

        // ---------- Có kẻ tấn công ----------
        if (e instanceof EntityDamageByEntityEvent ee) {
            Entity damager = ee.getDamager();
            Player atk = null;
            LivingEntity atkLiving = null;
            if (damager instanceof Player apl) {
                atk = apl;
                atkLiving = apl;
            } else if (damager instanceof Projectile pr && pr.getShooter() instanceof LivingEntity sh) {
                atkLiving = sh;
                if (sh instanceof Player spl) atk = spl;
            } else if (damager instanceof LivingEntity le) {
                atkLiving = le;
            }

            // Phản đòn
            if (vp != null && atkLiving != null && atkLiving != vp) {
                double th = m.passiveSum(vps, Passive.THORNS);
                if (th > 0) {
                    double reflect = Math.min(20.0, e.getFinalDamage() * th / 100.0);
                    if (reflect > 0) atkLiving.damage(reflect);
                }
            }

            // Buff người đánh
            if (atk != null && victim instanceof LivingEntity target && victim != atk) {
                PlayerData ad = m.data(atk);
                double pct = m.bonus(ad, Stat.DAMAGE);
                boolean crit = false;
                double lifesteal = 0;
                for (ActivePassive a : m.passives(ad)) {
                    switch (a.type) {
                        case CRIT -> {
                            if (rnd.nextDouble() * 100.0 < a.chance) {
                                pct += a.value;
                                crit = true;
                            }
                        }
                        case EXECUTE -> {
                            if (target.getHealth() < target.getMaxHealth() * 0.3) pct += a.value;
                        }
                        case BOSS_SLAYER -> {
                            if (m.isBoss(target)) pct += a.value;
                        }
                        case LIFESTEAL -> lifesteal += a.value;
                        default -> { }
                    }
                }
                if (pct != 0) e.setDamage(e.getDamage() * Math.max(0.0, 1.0 + pct / 100.0));
                if (lifesteal > 0) m.heal(atk, Math.min(m.lifestealCap, e.getDamage() * lifesteal / 100.0));
                if (crit) {
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.2);
                }
            }
        }

        // ---------- Ý Chí Cuối Cùng ----------
        if (vp != null && !vps.isEmpty() && !e.isCancelled() && e.getFinalDamage() >= vp.getHealth()) {
            for (ActivePassive a : vps) {
                if (a.type == Passive.LAST_STAND && m.tryUse(vd, a)) {
                    e.setCancelled(true);
                    vp.setHealth(Math.min(m.maxHealth(vp), Math.max(1.0, a.value)));
                    vp.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 2));
                    vp.playSound(vp.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                    vp.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, vp.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
                    vp.sendActionBar(AstraxRelic.c("&6✦ Ý Chí Cuối Cùng đã cứu bạn!"));
                    break;
                }
            }
        }
    }
}
