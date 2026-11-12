package com.iade;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.logging.Level;

public final class DeathListener implements Listener {

    private final Iade plugin;

    public DeathListener(Iade plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        try {
            handle(event);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Olum kaydi olusturulamadi", t);
        }
    }

    private void handle(PlayerDeathEvent event) {
        Settings s = plugin.getSettings();
        if (s.keepInventoryAtla && event.getKeepInventory()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = findKiller(victim);
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        String world = victim.getWorld().getName();
        if (!s.isWorldActive(world)) {
            return;
        }

        // Envanter ana thread'de klonlanir, serilestirme arka planda yapilir.
        ItemStack[] original = victim.getInventory().getContents();
        ItemStack[] contents = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                contents[i] = original[i].clone();
            }
        }

        Location loc = victim.getLocation();
        DeathRecord record = new DeathRecord(
                -1, victim.getUniqueId(), victim.getName(), killer.getName(), killer.getUniqueId(),
                "Bekleniyor...", System.currentTimeMillis(), false, 0L, null,
                DeathRecord.BEKLENIYOR, 0L,
                world, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        plugin.recordDeath(record, contents);
    }

    private Player findKiller(Player victim) {
        Player killer = victim.getKiller();
        if (killer != null) {
            return killer;
        }
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last instanceof EntityDamageByEntityEvent) {
            Object damager = ((EntityDamageByEntityEvent) last).getDamager();
            if (damager instanceof Player) {
                return (Player) damager;
            }
            if (damager instanceof Projectile) {
                ProjectileSource shooter = ((Projectile) damager).getShooter();
                if (shooter instanceof Player) {
                    return (Player) shooter;
                }
            }
        }
        return null;
    }
}
