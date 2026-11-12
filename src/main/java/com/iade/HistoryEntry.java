package com.iade;

/** Iade gecmisindeki bir satir (onay, teslim alma veya silme). Degismezdir. */
public final class HistoryEntry {

    public static final String ONAY = "ONAY";
    public static final String ALINDI = "ALINDI";
    public static final String SIL = "SIL";

    public final long time;
    public final String action;
    public final String victimUuid;
    public final String victimName;
    public final String killerName;
    /** Islemi yapan yetkili; oyuncunun kendi alisinda null. */
    public final String actor;
    public final int itemCount;
    /** Referans: kaydin olum zamani. */
    public final long deathTime;
    /** Katilin ban tespit zamani (onay suresi hesabi icin; bilinmiyorsa 0). */
    public final long banTime;
    public final boolean sameIp;

    public HistoryEntry(long time, String action, String victimUuid, String victimName, String killerName,
                        String actor, int itemCount, long deathTime, long banTime, boolean sameIp) {
        this.time = time;
        this.action = action;
        this.victimUuid = victimUuid;
        this.victimName = victimName;
        this.killerName = killerName;
        this.actor = actor;
        this.itemCount = itemCount;
        this.deathTime = deathTime;
        this.banTime = banTime;
        this.sameIp = sameIp;
    }

    /** Bir olum kaydindan gecmis satiri uretir. */
    static HistoryEntry of(String action, DeathRecord r, String actor, int itemCount) {
        return new HistoryEntry(System.currentTimeMillis(), action,
                r.victimUuid == null ? null : r.victimUuid.toString(), r.victimName, r.killerName, actor,
                Math.max(0, itemCount), r.timestamp, r.banTimestamp, r.sameIp);
    }
}
