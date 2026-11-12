package com.iade;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ban komutlarindaki sure yazimini ("7d", "1h30m", "2w", "1mo") milisaniyeye cevirir.
 * Birimler: y (yil), mo (ay), w (hafta), d (gun), h (saat), m (dakika), s (saniye).
 */
public final class DurationParser {

    private static final Pattern WHOLE = Pattern.compile("^(\\d+(?:y|mo|w|d|h|m|s))+$");
    private static final Pattern PART = Pattern.compile("(\\d+)(y|mo|w|d|h|m|s)");

    private DurationParser() {
    }

    /** Taninmayan yazim icin -1 dondurur. */
    public static long parseMillis(String token) {
        if (token == null) {
            return -1L;
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (!WHOLE.matcher(t).matches()) {
            return -1L;
        }
        long total = 0L;
        Matcher m = PART.matcher(t);
        while (m.find()) {
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return -1L;
            }
            total += n * unit(m.group(2));
            if (total < 0 || total > 100L * 365L * 86400000L) {
                return -1L; // tasma / mantiksiz uzunluk
            }
        }
        return total > 0 ? total : -1L;
    }

    private static long unit(String u) {
        switch (u) {
            case "y":
                return 365L * 86400000L;
            case "mo":
                return 30L * 86400000L;
            case "w":
                return 7L * 86400000L;
            case "d":
                return 86400000L;
            case "h":
                return 3600000L;
            case "m":
                return 60000L;
            default:
                return 1000L;
        }
    }
}
