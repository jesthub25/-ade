package com.iade.ban;

/**
 * Bir ban sisteminin dondurdugu sonuc.
 * expiry: 0 = kalici, -1 = bilinmiyor, 0'dan buyuk = bitis zamani (epoch ms).
 */
public final class BanInfo {

    public final String reason;
    public final String source;
    /** Banlayan yetkili (bilinmiyorsa bos). */
    public final String banner;
    public final long expiry;
    /** Komuttaki ham sure yazisi (ornegin "7d"); yoksa bos. */
    public final String durationText;

    public BanInfo(String reason, String source) {
        this(reason, source, null, -1L, null);
    }

    public BanInfo(String reason, String source, String banner, long expiry) {
        this(reason, source, banner, expiry, null);
    }

    public BanInfo(String reason, String source, String banner, long expiry, String durationText) {
        this.reason = reason == null ? "" : reason.trim();
        this.source = source;
        this.banner = banner == null ? "" : banner.trim();
        this.expiry = expiry;
        this.durationText = durationText == null ? "" : durationText.trim();
    }

    /** API sonucunun eksik alanlarini (sebep, yetkili, sure) komut bilgisiyle tamamlar. */
    public BanInfo withFallback(String reasonFb, String bannerFb, long expiryFb, String durationFb) {
        return new BanInfo(
                reason.isEmpty() ? reasonFb : reason,
                source,
                banner.isEmpty() ? bannerFb : banner,
                expiry == -1L ? expiryFb : expiry,
                durationText.isEmpty() ? durationFb : durationText);
    }
}
