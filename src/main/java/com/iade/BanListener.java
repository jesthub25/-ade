package com.iade;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Ban ve unban komutlarini dinler. Komutun gercekten var olmasi ve (oyuncu ise) kullananin yetkisinin
 * bulunmasi sart. Sonuc 2 saniye sonra ban sistemleri uzerinden dogrulanir (BanService).
 * Not: Baska eklentilerin Bukkit.dispatchCommand ile gonderdigi gizli komutlar bu olayi tetiklemez;
 * onlari periyodik tarama ve giris dinleme yakalar.
 */
public final class BanListener implements Listener {

    private static final Pattern IP = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern FLAG = Pattern.compile("^-[A-Za-z]{1,2}$");
    /** Komut haritasina erisilemezse kullanilan yedek yetki dugumleri. */
    private static final String[] FALLBACK_PERMS = {
            "bukkit.command.ban.player", "bukkit.command.unban.player", "minecraft.command.ban",
            "minecraft.command.pardon", "litebans.ban", "litebans.unban", "advancedban.ban_use",
            "essentials.ban", "essentials.unban", "banmanager.command.ban", "banmanager.command.unban"
    };

    private final Iade plugin;
    private CommandMap commandMap;
    private boolean mapTried;

    public BanListener(Iade plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent event) {
        safeHandle(event.getSender(), event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayer(PlayerCommandPreprocessEvent event) {
        safeHandle(event.getPlayer(), event.getMessage());
    }

    private void safeHandle(CommandSender sender, String raw) {
        try {
            handle(sender, raw);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Ban komutu islenirken hata", t);
        }
    }

    /** Komut satirindan cikarilan ban/unban bilgisi. */
    static final class Parsed {
        final boolean ban;
        final String label;
        final String target;
        final String reason;
        /** Gecici ban komutu mu? durationToken: komuttaki sure yazisi (ornegin "7d"), yoksa bos. */
        final boolean temp;
        final String durationToken;

        Parsed(boolean ban, String label, String target, String reason, boolean temp, String durationToken) {
            this.ban = ban;
            this.label = label;
            this.target = target;
            this.reason = reason;
            this.temp = temp;
            this.durationToken = durationToken;
        }
    }

    /** Ban/unban komutu degilse veya hedef gecersizse null dondurur. Bukkit'e bagimli degildir. */
    static Parsed parse(String raw, Settings s) {
        if (raw == null) {
            return null;
        }
        String line = raw.trim();
        while (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return null;
        }

        String[] parts = line.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        String base = label.indexOf(':') >= 0 ? label.substring(label.indexOf(':') + 1) : label;

        int consumed = 1;
        boolean ban = s.banKomutlari.contains(label) || s.banKomutlari.contains(base);
        boolean unban = !ban && (s.unbanKomutlari.contains(label) || s.unbanKomutlari.contains(base));
        if (!ban && !unban && parts.length >= 3) {
            String two = base + " " + parts[1].toLowerCase(Locale.ROOT);
            if (s.banKomutlari.contains(two)) {
                ban = true;
                consumed = 2;
            } else if (s.unbanKomutlari.contains(two)) {
                unban = true;
                consumed = 2;
            }
        }
        if (!ban && !unban) {
            return null;
        }

        // Hedef oyuncu: bayraklari (-s gibi) atla
        int ti = consumed;
        while (ti < parts.length && FLAG.matcher(parts[ti]).matches()) {
            ti++;
        }
        if (ti >= parts.length) {
            return null;
        }
        String target = parts[ti];
        if (target.isEmpty() || target.length() > 32 || IP.matcher(target).matches()) {
            return null;
        }

        // Sebep: hedeften sonraki kelimeler (gecici banlarda once sure alinir)
        StringBuilder reason = new StringBuilder();
        boolean temp = false;
        String durationToken = "";
        if (ban) {
            int ri = ti + 1;
            temp = base.contains("temp") || base.equals("tban")
                    || (consumed == 2 && parts[1].toLowerCase(Locale.ROOT).contains("temp"));
            if (temp && ri < parts.length) {
                durationToken = parts[ri];
                ri++;
            }
            while (ri < parts.length && FLAG.matcher(parts[ri]).matches()) {
                ri++;
            }
            for (int i = ri; i < parts.length; i++) {
                if (reason.length() > 0) {
                    reason.append(' ');
                }
                reason.append(parts[i]);
            }
        }
        return new Parsed(ban, label, target, reason.toString(), temp, durationToken);
    }

    private void handle(CommandSender sender, String raw) {
        Settings s = plugin.getSettings();
        if (!s.komutDinleme) {
            return;
        }
        Parsed pc = parse(raw, s);
        if (pc == null) {
            return;
        }

        // Komut gercekten var mi, kullanan yetkili mi, komut hangi eklentiye ait?
        String owner = null;
        CommandMap map = commandMap();
        if (map != null) {
            Command cmd = map.getCommand(pc.label);
            if (cmd == null) {
                return; // bilinmeyen komut
            }
            if (sender instanceof Player && !cmd.testPermissionSilent(sender)) {
                return; // yetkisiz kullanici
            }
            if (cmd instanceof PluginCommand) {
                owner = ((PluginCommand) cmd).getPlugin().getName();
            }
        } else if (sender instanceof Player && !hasFallbackPermission(sender)) {
            return;
        }

        if (pc.ban) {
            plugin.getBanService().onBanCommand(pc.target, pc.reason, owner, sender.getName(), pc.temp, pc.durationToken);
        } else {
            plugin.getBanService().onUnbanCommand(pc.target, owner);
        }
    }

    private boolean hasFallbackPermission(CommandSender sender) {
        if (sender.isOp()) {
            return true;
        }
        for (String perm : FALLBACK_PERMS) {
            if (sender.hasPermission(perm)) {
                return true;
            }
        }
        return false;
    }

    /** CraftServer#getCommandMap() tum Spigot/Paper surumlerinde vardir; reflection ile alinir. */
    private CommandMap commandMap() {
        if (!mapTried) {
            mapTried = true;
            try {
                Object server = Bukkit.getServer();
                Object map = server.getClass().getMethod("getCommandMap").invoke(server);
                if (map instanceof CommandMap) {
                    commandMap = (CommandMap) map;
                }
            } catch (Throwable ignored) {
                commandMap = null;
            }
        }
        return commandMap;
    }
}
