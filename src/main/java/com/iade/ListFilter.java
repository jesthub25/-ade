package com.iade;

import java.util.Locale;

/** /olumliste liste menusundaki filtre. */
public enum ListFilter {
    TUMU, BANLI, BANLISIZ;

    /** config.yml degerini cozer; taninmazsa varsayilani dondurur. */
    public static ListFilter parse(String text, ListFilter def) {
        if (text == null) {
            return def;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.equals("tumu") || t.equals("hepsi") || t.equals("all")) {
            return TUMU;
        }
        if (t.equals("banli") || t.equals("banned")) {
            return BANLI;
        }
        if (t.equals("banlisiz") || t.equals("banli-degil") || t.equals("unbanned")) {
            return BANLISIZ;
        }
        return def;
    }

    public static boolean isValid(String text) {
        if (text == null) {
            return false;
        }
        ListFilter probe = parse(text, null);
        return probe != null;
    }

    public ListFilter next() {
        ListFilter[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
