package com.iade;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bir oyuncunun baska bir oyuncu tarafindan olduruldugu kayit. Alanlar birden fazla thread'den okunur. */
public final class DeathRecord {

    public static final String BEKLENIYOR = "Bekleniyor";
    public static final String VERILDI = "Verildi";
    public static final String ALINDI = "Al\u0131nd\u0131";

    /** Banin nereden ogrenildigini gosteren sabit kaynaklar (API disi kaynaklar). */
    public static final String KAYNAK_KOMUT = "Komut";
    public static final String KAYNAK_MANUEL = "Manuel";
    public static final String KAYNAK_GIRIS = "Giris";

    private static final long HOUR = 3600000L;

    public volatile int id;
    public final UUID victimUuid;
    public final String victimName;
    public final String killerName;
    public final UUID killerUuid;
    public final long timestamp;
    public final String dunya;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public volatile String reason;
    public volatile boolean banned;
    public volatile long banTimestamp;
    /** Banin kaynagi: Vanilla, LiteBans, AdvancedBan, BanManager (API) veya Komut, Manuel, Giris. */
    public volatile String banSource;
    /** Banlayan yetkili (bilinmiyorsa null). */
    public volatile String banBanner;
    /** Ban bitisi: 0 = kalici, -1 = bilinmiyor, 0'dan buyuk = bitis zamani (epoch ms). */
    public volatile long banExpiry = -1L;
    /** Komuttaki ham sure yazisi (ornegin "7d"), yoksa null. */
    public volatile String banDurationText;
    public volatile String iadeDurumu;
    public volatile long iadeTimestamp;

    // --- Olum bilgisi (olum aninda bir kez yazilir; eski kayitlarda bos) ---
    /** Katilin ana eli: "TUR \t ozel ad \t buyu sayisi" ya da "AIR". Bilinmiyorsa null. */
    public volatile String weapon;
    /** Olum nedeni (DamageCause adi, orn. ENTITY_ATTACK). Bilinmiyorsa null. */
    public volatile String damageCause;
    /** Katil-kurban mesafesi (blok); bilinmiyorsa -1. */
    public volatile double distance = -1D;
    /** Kurban ve katil ayni IP'den baglanmisti (IP saklanmaz, yalnizca bu isaret). */
    public volatile boolean sameIp;

    /** Iade alma / islem sirasinda cift tiklamayi engeller. */
    public final AtomicBoolean busy = new AtomicBoolean(false);

    public DeathRecord(int id, UUID victimUuid, String victimName, String killerName, UUID killerUuid,
                       String reason, long timestamp, boolean banned, long banTimestamp, String banSource,
                       String iadeDurumu, long iadeTimestamp,
                       String dunya, double x, double y, double z, float yaw, float pitch) {
        this.id = id;
        this.victimUuid = victimUuid;
        this.victimName = victimName;
        this.killerName = killerName == null ? "?" : killerName;
        this.killerUuid = killerUuid;
        this.reason = reason == null ? "" : reason;
        this.timestamp = timestamp;
        this.banned = banned;
        this.banTimestamp = banTimestamp;
        this.banSource = banSource;
        this.iadeDurumu = iadeDurumu == null ? BEKLENIYOR : iadeDurumu;
        this.iadeTimestamp = iadeTimestamp;
        this.dunya = dunya;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** Ban bir ban sisteminin API'si ile dogrulandiysa true (unban taramasi sadece bunlara bakar). */
    public boolean isApiSourced() {
        String src = banSource;
        return src != null && !src.isEmpty()
                && !KAYNAK_KOMUT.equals(src) && !KAYNAK_MANUEL.equals(src) && !KAYNAK_GIRIS.equals(src);
    }

    public boolean isStatus(String status) {
        return status.equalsIgnoreCase(iadeDurumu);
    }

    /** Kaydin suresi doldu mu? Suresi dolan kayit, temizlik gorevi henuz silmemis olsa bile yok sayilir. */
    public boolean isExpired(Settings s, long now) {
        return expiresAt(s) < now;
    }

    /** Kaydin otomatik silinecegi an (ms). */
    public long expiresAt(Settings s) {
        if (isStatus(ALINDI)) {
            return base(iadeTimestamp) + s.alindiSaklamaSaat * HOUR;
        }
        if (isStatus(VERILDI)) {
            return base(iadeTimestamp) + s.iadeAlmaSaat * HOUR;
        }
        if (banned) {
            return base(banTimestamp) + s.banliSaklamaSaat * HOUR;
        }
        return timestamp + s.silinmeSuresiSaat * HOUR;
    }

    private long base(long t) {
        return t > 0 ? t : timestamp;
    }
}
