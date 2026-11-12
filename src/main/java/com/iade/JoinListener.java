package com.iade;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;

/** Oyuncu girince alinmayi bekleyen iadesi varsa hatirlatir (ayar: giris-hatirlatma). */
public final class JoinListener implements Listener {

    private final Iade plugin;

    public JoinListener(Iade plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        try {
            plugin.getNotifier().scheduleJoinReminder(event.getPlayer());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Giris hatirlatmasi zamanlanamadi", t);
        }
    }
}
