package com.iade.ban;

/** Bir ban sistemine (vanilla, LiteBans, ...) baglanan sorgu katmani. */
public interface BanProvider {

    /** Gorunen ad (log ve /olumlistereload ciktisi icin). */
    String getName();

    /** Ban eklentisinin plugin.yml adi. Vanilla icin bos dondurur. */
    String getPluginName();

    /** true ise sorgu ana thread'de, false ise arka plan thread'inde yapilir. */
    boolean requiresMainThread();

    /** Sistem su an kullanilabilir mi (eklenti kurulu, acik ve API'ye ulasildi mi). */
    boolean isActive();

    /**
     * Hedef bu sistemde banliysa BanInfo, kesin olarak banli degilse null dondurur.
     * Belirlenemezse (API hazir degil, UUID yok, hata) exception firlatir; cagiran bunu "belirsiz" sayar.
     * Harici saglayicilar arka plan thread'inde cagrilir.
     */
    BanInfo query(BanTarget target) throws Exception;
}
