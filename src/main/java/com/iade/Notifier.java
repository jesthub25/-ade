package com.iade;

import com.iade.ban.BanInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Oyunculara ve yetkililere giden bildirimler: giris hatirlatmasi ve "katil banlandi" duyurusu. */
public final class Notifier {

    static final String PERM_BILDIRIM = "iade.bildirim";

    private final Iade plugin;

    public Notifier(Iade plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------------------
    // Giris hatirlatmasi
    // ------------------------------------------------------------------------------------

    /** Ana thread. Alinacak iadesi olmayan oyuncu icin hicbir zamanlayici olusturulmaz. */
    public void scheduleJoinReminder(final Player player) {
        Settings s = plugin.getSettings();
        if (!s.girisHatirlatmaAktif || !plugin.hasClaimable(player.getUniqueId())) {
            return;
        }
        long delay = Math.max(1L, (long) s.girisHatirlatmaGecikmeSaniye * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    remind(player);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Giris hatirlatmasi gonderilemedi", t);
                }
            }
        }, delay);
    }

    /** Alinmayi bekleyen iadeleri sayar ve en yakin silinme suresiyle oyuncuya yazar. */
    void remind(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Settings s = plugin.getSettings();
        UUID id = player.getUniqueId();
        List<DeathRecord> list = plugin.getDeathStorage().get(id);
        if (list == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int count = 0;
        long soonest = Long.MAX_VALUE;
        for (DeathRecord r : list) {
            if (r.banned && r.isStatus(DeathRecord.VERILDI) && !r.isExpired(s, now)) {
                count++;
                soonest = Math.min(soonest, r.expiresAt(s));
            }
        }
        if (count == 0) {
            return;
        }
        String text = plugin.getMessages().get("giris-hatirlatma",
                "%adet%", String.valueOf(count),
                "%sure%", plugin.getMenus().remaining(Math.max(0L, soonest - now)));
        Chat.send(player, text, plugin.getMessages().get("tiklayin-ipucu"), "/iadelerim", true, s.girisHatirlatmaTiklanabilir);
        Sounds.play(plugin, player, Sounds.BILDIRIM);
    }

    // ------------------------------------------------------------------------------------
    // Yetkili bildirimi: katil banlandi
    // ------------------------------------------------------------------------------------

    /**
     * Bir katilin kayitlari "banli" durumuna gectiginde cagrilir (her thread). Yetkiye (iade.bildirim) sahip
     * cevrimici oyunculara ve konsola haber verir; ayar aciksa Discord'a embed gonderir.
     * Elle isaretlemede (/olumlistebanla) komutu yazan zaten bilgilidir, bildirim gonderilmez.
     */
    public void banDetected(final String killerName, final BanInfo info, final List<DeathRecord> changed) {
        final Settings s = plugin.getSettings();
        if (!s.bildirimAktif || changed.isEmpty() || DeathRecord.KAYNAK_MANUEL.equals(info.source)) {
            return;
        }
        Set<String> victims = new HashSet<String>();
        int sameIp = 0;
        for (DeathRecord r : changed) {
            victims.add(r.victimUuid != null ? r.victimUuid.toString() : String.valueOf(r.victimName));
            if (r.sameIp) {
                sameIp++;
            }
        }
        final String name = changed.get(0).killerName != null ? changed.get(0).killerName : killerName;
        final int victimCount = victims.size();
        final int recordCount = changed.size();
        final int sameIpCount = sameIp;
        final String reason = Text.isBlank(info.reason) ? "-" : Text.truncate(info.reason.trim(), 80);
        final String banner = info.banner == null || info.banner.isEmpty() ? "-" : info.banner;

        plugin.getLogger().info("Katil banlandi: " + name + " (" + victimCount + " kurban, " + recordCount
                + " kayıt" + (sameIpCount > 0 ? ", " + sameIpCount + " aynı IP şüphesi" : "") + ")");

        plugin.runMain(new Runnable() {
            @Override
            public void run() {
                try {
                    sendStaff(s, name, reason, banner, victimCount, recordCount, sameIpCount);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Yetkili bildirimi gonderilemedi", t);
                }
            }
        });

        if (s.bildirimDiscord) {
            DiscordWebhook.sendBanLog(plugin, s.webhookUrl, name, reason, banner, banSource(info),
                    banDuration(info, s), victimCount, recordCount, sameIpCount);
        }
    }

    private void sendStaff(Settings s, String name, String reason, String banner,
                           int victimCount, int recordCount, int sameIpCount) {
        Messages m = plugin.getMessages();
        String text = m.get("yetkili-ban-bildirimi", "%oyuncu%", name, "%sebep%", reason, "%banlayan%", banner,
                "%kurban%", String.valueOf(victimCount), "%adet%", String.valueOf(recordCount));
        String ipText = sameIpCount > 0 ? m.get("yetkili-ip-uyari", "%adet%", String.valueOf(sameIpCount)) : null;
        String hint = m.get("tiklayin-ipucu");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission(PERM_BILDIRIM)) {
                continue;
            }
            Chat.send(p, text, hint, "/olumliste liste", false, s.bildirimTiklanabilir);
            if (ipText != null) {
                p.sendMessage(ipText);
            }
            Sounds.play(plugin, p, Sounds.BILDIRIM);
        }
    }

    private String banSource(BanInfo info) {
        Messages m = plugin.getMessages();
        String src = info.source;
        if (Text.isBlank(src)) {
            return "-";
        }
        if (DeathRecord.KAYNAK_KOMUT.equals(src)) {
            return strip(m.get("kaynak-komut"));
        }
        if (DeathRecord.KAYNAK_GIRIS.equals(src)) {
            return strip(m.get("kaynak-giris"));
        }
        return src;
    }

    private String banDuration(BanInfo info, Settings s) {
        Messages m = plugin.getMessages();
        if (info.expiry == 0L) {
            return strip(m.get("ban-sure-kalici"));
        }
        if (info.expiry > 0L) {
            return s.formatShort(info.expiry);
        }
        if (!Text.isBlank(info.durationText)) {
            return info.durationText;
        }
        return "-";
    }

    /** Discord'a giden metinlerden renk kodlarini temizler. */
    private static String strip(String colored) {
        if (colored == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(colored.length());
        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);
            if (c == '§' && i + 1 < colored.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        String out = sb.toString();
        return out.isEmpty() ? "-" : out;
    }
}
