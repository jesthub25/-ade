package com.iade.ban;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Date;

/**
 * Sunucunun ban listesini (banned-players.json) okur. Vanilla, EssentialsX (/ban, /tempban) ve
 * bircok anti-cheat bu listeyi kullanir. Sadece ana thread'de cagrilmalidir.
 * BanList.Type.NAME sabiti yeni surumlerde kaldirilirsa diye isimle (valueOf) erisilir;
 * her adim ayri ayri korunur.
 */
final class BukkitBanLists {

    private BukkitBanLists() {
    }

    /**
     * Banliysa BanInfo (sebep, banlayan, sure dahil), kesin olarak banli degilse null dondurur.
     * Liste hic okunamazsa IllegalStateException firlatir (sonuc "belirsiz" sayilir).
     */
    static BanInfo lookup(BanTarget t, String source) {
        boolean checked = false;
        boolean banned = false;
        String reason = null;
        String banner = null;
        long expiry = -1L;

        if (t.name != null) {
            try {
                BanList list = Bukkit.getBanList(BanList.Type.valueOf("NAME"));
                if (list != null) {
                    BanEntry entry = list.getBanEntry(t.name);
                    checked = true;
                    if (entry != null) {
                        banned = true;
                        reason = entry.getReason();
                        try {
                            banner = entry.getSource();
                        } catch (Throwable ignored) {
                            // banlayan okunamazsa bos kalir
                        }
                        try {
                            Date exp = entry.getExpiration();
                            expiry = exp == null ? 0L : exp.getTime();
                        } catch (Throwable ignored) {
                            // sure okunamazsa bilinmiyor kalir
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Bu surumde isim listesi yoksa asagidaki UUID kontrolu devreye girer.
            }
        }

        if (!banned && t.uuid != null) {
            // Yeni surumlerde (1.20.3+) ban listesi profil (UUID) tabanlidir; isim listesi kaldirilmis olsa bile
            // sebep / banlayan / sure bu yoldan okunur. Eski surumlerde PROFILE sabiti yoktur -> sessizce atlanir.
            try {
                BanEntry entry = profileEntry(t.uuid);
                checked = true;
                if (entry != null) {
                    Date exp = null;
                    try {
                        exp = entry.getExpiration();
                    } catch (Throwable ignored) {
                        // sure okunamazsa bilinmiyor kalir
                    }
                    if (exp == null || exp.getTime() > System.currentTimeMillis()) {
                        banned = true;
                        reason = entry.getReason();
                        try {
                            banner = entry.getSource();
                        } catch (Throwable ignored) {
                            // banlayan okunamazsa bos kalir
                        }
                        expiry = exp == null ? 0L : exp.getTime();
                    }
                }
            } catch (Throwable ignored) {
                // PROFILE listesi yok / okunamadi: asagidaki OfflinePlayer kontrolu devreye girer.
            }
        }

        if (!banned && t.uuid != null) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(t.uuid);
                if (op != null) {
                    checked = true;
                    if (op.isBanned()) {
                        banned = true;
                    }
                }
            } catch (Throwable ignored) {
                // yok say
            }
        }

        if (banned) {
            return new BanInfo(reason, source, banner, expiry);
        }
        if (!checked) {
            throw new IllegalStateException("Ban listesi okunamadi");
        }
        return null;
    }

    /**
     * Profil tabanli ban listesinden (1.20.3+) kaydi okur. Tamamen yansimayla yapilir; boylece 1.20.1 API'sine
     * karsi derlenen eklenti hem eski (PROFILE yok) hem yeni surumlerde ayni sekilde yuklenir.
     * Kayit yoksa null; liste/yontem bu surumde yoksa istisna firlatir (cagiran yakalar).
     */
    private static BanEntry profileEntry(java.util.UUID uuid) throws Exception {
        BanList.Type type = BanList.Type.valueOf("PROFILE");
        BanList list = Bukkit.getBanList(type);
        if (list == null) {
            throw new IllegalStateException("PROFILE listesi yok");
        }
        Object profile = Bukkit.class.getMethod("createPlayerProfile", java.util.UUID.class).invoke(null, uuid);
        Object entry = BanList.class.getMethod("getBanEntry", Object.class).invoke(list, profile);
        return (BanEntry) entry;
    }
}
