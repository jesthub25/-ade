package com.iade;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.logging.Level;

/**
 * Takip edilen bir katil sunucuya girmeye calisirken "banli" sonucu alirsa (vanilla, LiteBans,
 * AdvancedBan, BanManager, LibertyBans, CMI, MaxBans... herhangi biri) kayitlarini banli isaretler.
 * API'si olmayan ban sistemleri icin evrensel bir guvence katmanidir.
 */
public final class LoginListener implements Listener {

    private final Iade plugin;

    public LoginListener(Iade plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            if (plugin.getSettings().girisDinleme
                    && event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {
                plugin.getBanService().onLoginBanned(event.getUniqueId(), event.getName());
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Giris engeli islenirken hata", t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        try {
            if (plugin.getSettings().girisDinleme && event.getResult() == PlayerLoginEvent.Result.KICK_BANNED) {
                Player p = event.getPlayer();
                plugin.getBanService().onLoginBanned(p.getUniqueId(), p.getName());
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Giris engeli islenirken hata", t);
        }
    }
}
