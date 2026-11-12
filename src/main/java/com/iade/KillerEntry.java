package com.iade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /olumliste liste menusundaki bir kafa: ayni katile ait tum (suresi dolmamis) olum kayitlarinin ozeti.
 * Siralama: once banlilar (en yakin ban en uste), sonra henuz banli isaretlenmemis takip edilen katiller
 * (en son oldurme en uste). Ayar "yeniden-eskiye: false" ise her grup eskiden yeniye siralanir.
 */
public final class KillerEntry {

    /** En yeni kayittaki yazilis (buyuk-kucuk harf degismis olabilir). */
    public String name;
    private long nameTime;
    public UUID uuid;

    public boolean banned;
    public long banTime;
    public String reason = "";
    public String banner = "";
    public String source = "";
    public long banExpiry = -1L;
    public String durationText = "";

    public int kills;
    public long lastDeath;
    public int pending;
    public int approved;
    public int taken;
    /** Bu katilin son kaydinin silinecegi an: kafa bu andan sonra listeden kalkar. */
    public long removeAt;
    /** Ayni IP supheli (suresi dolmamis) kayit sayisi. */
    public int sameIp;
    /** Ard arda oldurme supheli (suresi dolmamis) kayit sayisi. */
    public int ardarda;
    /** En yeni kayit (silah / olum turu / mesafe bilgisi bundan okunur). */
    public DeathRecord latest;
    public final List<String> victims = new ArrayList<String>();
    private final Set<String> victimSet = new HashSet<String>();

    KillerEntry(String name) {
        this.name = name;
    }

    /** Ham kayit listesinden (suresi dolanlari atlayarak) menu girislerini uretir. */
    public static List<KillerEntry> build(Iade plugin, ListFilter filter, boolean newestFirst) {
        Settings s = plugin.getSettings();
        long now = System.currentTimeMillis();
        Map<String, KillerEntry> map = new LinkedHashMap<String, KillerEntry>();

        for (List<DeathRecord> list : plugin.getDeathStorage().values()) {
            for (DeathRecord r : list) {
                if (r.isExpired(s, now)) {
                    continue;
                }
                String key = r.killerName.toLowerCase(Locale.ROOT);
                KillerEntry e = map.get(key);
                if (e == null) {
                    e = new KillerEntry(r.killerName);
                    map.put(key, e);
                }
                e.add(r, s);
            }
        }

        List<KillerEntry> out = new ArrayList<KillerEntry>();
        for (KillerEntry e : map.values()) {
            if (filter == ListFilter.BANLI && !e.banned) {
                continue;
            }
            if (filter == ListFilter.BANLISIZ && e.banned) {
                continue;
            }
            out.add(e);
        }
        Collections.sort(out, comparator(newestFirst));
        return out;
    }

    static Comparator<KillerEntry> comparator(final boolean newestFirst) {
        return new Comparator<KillerEntry>() {
            @Override
            public int compare(KillerEntry a, KillerEntry b) {
                if (a.banned != b.banned) {
                    return a.banned ? -1 : 1; // banlilar hep once
                }
                long ka = a.banned ? a.banTime : a.lastDeath;
                long kb = b.banned ? b.banTime : b.lastDeath;
                int cmp = Long.compare(kb, ka); // yeni -> eski
                if (cmp == 0) {
                    cmp = a.name.compareToIgnoreCase(b.name);
                }
                return newestFirst ? cmp : -cmp;
            }
        };
    }

    private static int infoScore(DeathRecord r) {
        int score = 0;
        if (r.banBanner != null && !r.banBanner.isEmpty()) {
            score++;
        }
        if (r.banExpiry != -1L) {
            score++;
        }
        if (r.reason != null && !r.reason.isEmpty()) {
            score++;
        }
        return score;
    }

    private int currentScore() {
        int score = 0;
        if (!banner.isEmpty()) {
            score++;
        }
        if (banExpiry != -1L) {
            score++;
        }
        if (!reason.isEmpty()) {
            score++;
        }
        return score;
    }

    private void add(DeathRecord r, Settings s) {
        if (uuid == null && r.killerUuid != null) {
            uuid = r.killerUuid;
        }
        if (r.timestamp >= nameTime) {
            nameTime = r.timestamp;
            name = r.killerName;
        }
        kills++;
        if (r.timestamp > lastDeath) {
            lastDeath = r.timestamp;
        }
        if (latest == null || r.timestamp >= latest.timestamp) {
            latest = r;
        }
        if (r.sameIp) {
            sameIp++;
        }
        if (r.ardarda > 0) {
            ardarda++;
        }
        String victim = r.victimName == null ? "?" : r.victimName;
        if (victimSet.add(victim.toLowerCase(Locale.ROOT))) {
            victims.add(victim);
        }
        if (r.isStatus(DeathRecord.ALINDI)) {
            taken++;
        } else if (r.isStatus(DeathRecord.VERILDI)) {
            approved++;
        } else {
            pending++;
        }
        long expiry = r.expiresAt(s);
        if (expiry > removeAt) {
            removeAt = expiry;
        }
        if (r.banned) {
            long bt = r.banTimestamp > 0 ? r.banTimestamp : r.timestamp;
            // En yeni ban kazanir; ayni anda isaretlenmis kayitlarda daha ayrintili olan (banlayan/sure bilinen) secilir.
            if (!banned || bt > banTime || (bt == banTime && infoScore(r) > currentScore())) {
                banned = true;
                banTime = bt;
                reason = r.reason == null ? "" : r.reason;
                banner = r.banBanner == null ? "" : r.banBanner;
                source = r.banSource == null ? "" : r.banSource;
                banExpiry = r.banExpiry;
                durationText = r.banDurationText == null ? "" : r.banDurationText;
            }
        }
    }
}
