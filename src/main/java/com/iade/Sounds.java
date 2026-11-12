package com.iade;

import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Menu / iade / bildirim sesleri. Sesler config.yml'deki "sesler" bolumunden okunur.
 * <p>
 * Surum uyumu: {@code Sound} enum'u KULLANILMAZ (1.21'de enum'dan arayuze donusturuldu, eski derleme yeni
 * sunucuda calismazdi). Bunun yerine {@code Player.playSound(Location, String, float, float)} cagrilir; ses
 * Minecraft'in kendi ses anahtariyla (ornegin "block.chest.open") calinir, anahtar yoksa istemci sessiz kalir.
 */
public final class Sounds {

    public static final String MENU_AC = "menu-ac";
    public static final String SAYFA = "sayfa";
    public static final String IADE_ONAY = "iade-onay";
    public static final String IADE_AL = "iade-al";
    public static final String HATA = "hata";
    public static final String BILDIRIM = "bildirim";

    /** Ayarlanabilir olaylar (config.yml: sesler.<olay>). */
    public static final String[] EVENTS = {MENU_AC, SAYFA, IADE_ONAY, IADE_AL, HATA, BILDIRIM};

    /** Olay, varsayilan ses, seviye, perde. config.yml ile birebir ayni olmalidir. */
    private static final String[][] DEFAULTS = {
            {MENU_AC, "block.chest.open", "0.6", "1.0"},
            {SAYFA, "ui.button.click", "0.5", "1.2"},
            {IADE_ONAY, "entity.player.levelup", "0.7", "1.2"},
            {IADE_AL, "entity.experience_orb.pickup", "0.8", "1.0"},
            {HATA, "entity.villager.no", "0.7", "1.0"},
            {BILDIRIM, "block.note_block.pling", "0.8", "1.4"}
    };

    private Sounds() {
    }

    private static String[] row(String event) {
        for (String[] r : DEFAULTS) {
            if (r[0].equals(event)) {
                return r;
            }
        }
        return new String[]{event, "", "1.0", "1.0"};
    }

    static String defaultKey(String event) {
        return row(event)[1];
    }

    static double defaultVolume(String event) {
        return Double.parseDouble(row(event)[2]);
    }

    static double defaultPitch(String event) {
        return Double.parseDouble(row(event)[3]);
    }

    /** Sesi (ayarlarda acik ve tanimliysa) oyuncunun bulundugu konumda yalnizca ona calar. */
    public static void play(Iade plugin, Player player, String event) {
        if (player == null) {
            return;
        }
        Settings s = plugin.getSettings();
        if (s == null || !s.seslerAktif) {
            return;
        }
        SoundSpec spec = s.sesler.get(event);
        if (spec == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), spec.key, spec.volume, spec.pitch);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Ses calinamadi: " + spec.key, t);
        }
    }
}
