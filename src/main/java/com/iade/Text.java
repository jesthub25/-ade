package com.iade;

/**
 * Kucuk metin yardimcilari. Bukkit'in ChatColor sinifina bagimli degildir,
 * boylece tum surumlerde ayni sekilde calisir.
 */
public final class Text {

    private static final String CODES = "0123456789aAbBcCdDeEfFkKlLmMnNoOrR";

    private Text() {
    }

    /** '&' renk kodlarini '§' ile degistirir. '§' zaten yazilmissa dokunmaz. */
    public static String color(String s) {
        if (s == null) {
            return "";
        }
        char[] b = s.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && CODES.indexOf(b[i + 1]) > -1) {
                b[i] = '\u00a7';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    /** %anahtar% -> deger degisimi. kv = anahtar1, deger1, anahtar2, deger2 ... */
    public static String replace(String s, String... kv) {
        if (s == null) {
            return "";
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace(kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return s;
    }

    /** Eski surumlerde envanter basligi en fazla 32 karakter olabilir. */
    public static String title(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= 32) {
            return s;
        }
        String cut = s.substring(0, 32);
        if (cut.endsWith("\u00a7")) {
            cut = cut.substring(0, 31);
        }
        return cut;
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * Sureyi kisa yazar: "2g 5s" (gun varsa gun+saat), "3s 20dk" (saat varsa saat+dakika), "12dk".
     * Bir dakikadan kisa sureler icin lessThanMinute dondurulur. Birim yazilari ayarlardan gelir.
     */
    public static String duration(long millis, String day, String hour, String minute, String lessThanMinute) {
        if (millis < 60000L) {
            return lessThanMinute;
        }
        long min = millis / 60000L;
        long d = min / 1440L;
        long h = (min % 1440L) / 60L;
        long mi = min % 60L;
        if (d > 0) {
            return d + day + " " + h + hour;
        }
        if (h > 0) {
            return h + hour + " " + mi + minute;
        }
        return mi + minute;
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
