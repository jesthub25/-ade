package com.iade;

import java.util.Locale;

/** Tek bir ses: Minecraft ses anahtari (noktali yazim), seviye ve perde. Degismezdir. */
public final class SoundSpec {

    public final String key;
    public final float volume;
    public final float pitch;

    public SoundSpec(String key, float volume, float pitch) {
        this.key = key;
        this.volume = volume;
        this.pitch = pitch;
    }

    /** Anahtari kucuk harfe cevirir ve kirpar. */
    static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Anahtar gecerli degilse nedenini (uyari metni parcasi) dondurur, gecerliyse null.
     * Sound enum'u KULLANILMAZ (1.21'de enum yapisi degisti); bu yuzden "ENTITY_PLAYER_LEVELUP" gibi
     * enum adlari degil, "entity.player.levelup" gibi anahtarlar beklenir.
     */
    static String problem(String key) {
        String k = normalize(key);
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-' || c == '/' || c == ':';
            if (!ok) {
                return "geçersiz karakter içeriyor (yalnızca küçük harf, rakam, nokta, alt çizgi ve tire kullanın)";
            }
        }
        if (k.indexOf('.') < 0) {
            return "noktalı bir ses anahtarı olmalı (örn. block.chest.open); ENTITY_PLAYER_LEVELUP gibi enum adı kullanılamaz";
        }
        if (k.startsWith(".") || k.endsWith(".") || k.startsWith(":") || k.endsWith(":")) {
            return "geçersiz biçimde (başında/sonunda nokta veya iki nokta olamaz)";
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SoundSpec)) {
            return false;
        }
        SoundSpec s = (SoundSpec) o;
        return key.equals(s.key) && Float.compare(volume, s.volume) == 0 && Float.compare(pitch, s.pitch) == 0;
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 31 + Float.floatToIntBits(volume) * 7 + Float.floatToIntBits(pitch);
    }

    @Override
    public String toString() {
        return key + "@" + volume + "/" + pitch;
    }
}
