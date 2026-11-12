package com.iade;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * config.yml degerleri. Her yuklemede yeni bir nesne uretilir (degismez).
 *
 * Config bagimliligi kurallari:
 *  - Anahtar dosyada yoksa varsayilan kullanilir (sessizce). Kod varsayilanlari config.yml ile birebir aynidir.
 *  - Anahtar var ama turu yanlissa (ornegin sayi yerine yazi) varsayilan kullanilir ve UYARI uretilir.
 *  - Bilinmeyen anahtar (yazim hatasi) UYARI uretir.
 *  - Liste yerine tek deger yazilmissa tek ogeli liste kabul edilir.
 */
public final class Settings {

    public final Set<String> aktifDunyalar;
    public final boolean keepInventoryAtla;
    public final String guiBaslik;
    public final String kagitAdi;
    public final List<String> loreMesajlari;
    public final String webhookUrl;

    public final int silinmeSuresiSaat;
    public final int banliSaklamaSaat;
    public final int iadeAlmaSaat;
    public final int alindiSaklamaSaat;
    public final int temizlikAraligiDakika;

    public final String listeBaslik;
    public final ListFilter listeFiltre;
    public final boolean listeYenidenEskiye;
    public final int listeKurbanSiniri;

    public final boolean banVanilla;
    public final boolean banEssentialsX;
    public final boolean banLiteBans;
    public final boolean banAdvancedBan;
    public final boolean banBanManager;
    public final boolean komutDinleme;
    public final boolean komutGuveni;
    public final boolean unbanIsaretiKaldir;
    public final boolean unbanTaramasi;
    public final int unbanPenceresiSaat;
    public final boolean girisDinleme;
    public final boolean taramaAktif;
    public final int taramaAraligiSaniye;
    public final Set<String> banKomutlari;
    public final Set<String> unbanKomutlari;

    /** Ayarlarda bulunan, ama eklentinin calismasini engellemeyen sorunlar (yukleme/reload sirasinda gosterilir). */
    public final List<String> uyarilar;

    private final DateTimeFormatter shortFmt;
    private final DateTimeFormatter fullFmt;

    public Settings(FileConfiguration c) {
        List<String> warn = new ArrayList<String>();

        this.aktifDunyalar = lowerSet(readList(c, "aktif-dunyalar", warn));
        this.keepInventoryAtla = readBool(c, "keepinventory-atla", true, warn);
        this.guiBaslik = readString(c, "gui-baslik", "&8\u00bb &c\u00d6l\u00fcm Listeniz", warn);
        this.kagitAdi = readString(c, "kagit-adi", "&e&lHileci Bildirimi: %katil%", warn);
        this.loreMesajlari = Collections.unmodifiableList(readList(c, "lore-mesajlari", warn));
        String hook = readString(c, "webhook-url", "", warn);
        this.webhookUrl = hook;

        this.silinmeSuresiSaat = readInt(c, "silinme-suresi-saat", 48, 1, Integer.MAX_VALUE, warn);
        this.banliSaklamaSaat = readInt(c, "banli-saklama-suresi-saat", 48, 1, Integer.MAX_VALUE, warn);
        this.iadeAlmaSaat = readInt(c, "iade-alma-suresi-saat", 48, 1, Integer.MAX_VALUE, warn);
        this.alindiSaklamaSaat = readInt(c, "alindi-saklama-suresi-saat", 24, 1, Integer.MAX_VALUE, warn);
        this.temizlikAraligiDakika = readInt(c, "temizlik-araligi-dakika", 5, 1, Integer.MAX_VALUE, warn);

        this.listeBaslik = readString(c, "liste-gui.baslik", "&8\u00bb &c\u00d6l\u00fcm Listesi &7%sayfa%/%toplam%", warn);
        String filterText = readString(c, "liste-gui.varsayilan-filtre", "tumu", warn);
        if (!ListFilter.isValid(filterText)) {
            warn.add("liste-gui.varsayilan-filtre ge\u00e7ersiz ('" + filterText + "'), 'tumu' kullan\u0131l\u0131yor "
                    + "(se\u00e7enekler: tumu, banli, banlisiz)");
        }
        this.listeFiltre = ListFilter.parse(filterText, ListFilter.TUMU);
        this.listeYenidenEskiye = readBool(c, "liste-gui.yeniden-eskiye", true, warn);
        this.listeKurbanSiniri = readInt(c, "liste-gui.kurban-gosterim-siniri", 5, 0, 20, warn);

        this.banVanilla = readBool(c, "ban-sistemi.vanilla", true, warn);
        this.banEssentialsX = readBool(c, "ban-sistemi.essentialsx", true, warn);
        this.banLiteBans = readBool(c, "ban-sistemi.litebans", true, warn);
        this.banAdvancedBan = readBool(c, "ban-sistemi.advancedban", true, warn);
        this.banBanManager = readBool(c, "ban-sistemi.banmanager", true, warn);
        this.komutDinleme = readBool(c, "ban-sistemi.komut-dinleme", true, warn);
        this.girisDinleme = readBool(c, "ban-sistemi.giris-dinleme", true, warn);
        this.komutGuveni = readBool(c, "ban-sistemi.komut-guveni", true, warn);
        this.unbanIsaretiKaldir = readBool(c, "ban-sistemi.unban-isareti-kaldir", true, warn);
        this.unbanTaramasi = readBool(c, "ban-sistemi.unban-taramasi", true, warn);
        this.unbanPenceresiSaat = readInt(c, "ban-sistemi.unban-taramasi-penceresi-saat", 72, 1, Integer.MAX_VALUE, warn);
        this.taramaAktif = readBool(c, "ban-sistemi.tarama-aktif", true, warn);
        this.taramaAraligiSaniye = readInt(c, "ban-sistemi.tarama-araligi-saniye", 60, 10, Integer.MAX_VALUE, warn);
        this.banKomutlari = lowerSet(readList(c, "ban-sistemi.ban-komutlari", warn));
        this.unbanKomutlari = lowerSet(readList(c, "ban-sistemi.unban-komutlari", warn));

        if (!Text.isBlank(hook) && !hook.trim().startsWith("http")) {
            warn.add("webhook-url 'http' ile ba\u015flam\u0131yor, webhook g\u00f6nderilmeyecek");
        }
        if (aktifDunyalar.isEmpty()) {
            warn.add("aktif-dunyalar bo\u015f: hi\u00e7bir d\u00fcnyada \u00f6l\u00fcm kayd\u0131 al\u0131nmayacak");
        }
        if (komutDinleme && banKomutlari.isEmpty()) {
            warn.add("ban-sistemi.ban-komutlari bo\u015f: ban komutlar\u0131 dinlenmeyecek (api/tarama/giri\u015f yine \u00e7al\u0131\u015f\u0131r)");
        }

        ZoneId zone;
        String z = readString(c, "saat-dilimi", "Europe/Istanbul", warn);
        try {
            zone = Text.isBlank(z) ? ZoneId.systemDefault() : ZoneId.of(z.trim());
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
            warn.add("saat-dilimi ge\u00e7ersiz ('" + z + "'), sunucu saati kullan\u0131l\u0131yor");
        }
        this.shortFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT).withZone(zone);
        this.fullFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ROOT).withZone(zone);

        warn.addAll(unknownKeys(c, "config.yml"));
        this.uyarilar = Collections.unmodifiableList(warn);
    }

    // ------------------------------------------------------------------------------------
    // Tur dogrulamali okuyucular
    // ------------------------------------------------------------------------------------

    static int readInt(FileConfiguration c, String key, int def, int min, int max, List<String> warn) {
        if (!c.isSet(key)) {
            return def;
        }
        Object raw = c.get(key);
        Integer parsed = toInt(raw);
        if (parsed == null) {
            warn.add(key + " bir tam say\u0131 olmal\u0131 (bulunan: '" + raw + "'), varsay\u0131lan " + def + " kullan\u0131l\u0131yor");
            return def;
        }
        int v = parsed;
        if (v < min) {
            warn.add(key + " en az " + min + " olmal\u0131 (bulunan: " + v + "), varsay\u0131lan " + def + " kullan\u0131l\u0131yor");
            return def;
        }
        if (v > max) {
            warn.add(key + " en fazla " + max + " olabilir (bulunan: " + v + "), " + max + " kullan\u0131l\u0131yor");
            return max;
        }
        return v;
    }

    /** 48, 48.0 ve tirnakli "48" kabul edilir (niyet acik); 1.5, "abc", liste vb. kabul edilmez. */
    private static Integer toInt(Object raw) {
        try {
            if (raw instanceof Number) {
                double d = ((Number) raw).doubleValue();
                if (d != Math.rint(d) || d > Integer.MAX_VALUE || d < Integer.MIN_VALUE) {
                    return null;
                }
                return (int) d;
            }
            if (raw instanceof String) {
                return Integer.valueOf(((String) raw).trim());
            }
        } catch (NumberFormatException ignored) {
            // asagidaki null
        }
        return null;
    }

    /** true/false ve tirnakli "true"/"false" kabul edilir. */
    static boolean readBool(FileConfiguration c, String key, boolean def, List<String> warn) {
        if (!c.isSet(key)) {
            return def;
        }
        Object raw = c.get(key);
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof String) {
            String t = ((String) raw).trim();
            if (t.equalsIgnoreCase("true")) {
                return true;
            }
            if (t.equalsIgnoreCase("false")) {
                return false;
            }
        }
        warn.add(key + " true veya false olmal\u0131 (bulunan: '" + raw + "'), varsay\u0131lan " + def + " kullan\u0131l\u0131yor");
        return def;
    }

    static String readString(FileConfiguration c, String key, String def, List<String> warn) {
        if (!c.isSet(key)) {
            return def;
        }
        Object v = c.get(key);
        if (v instanceof List || v instanceof org.bukkit.configuration.ConfigurationSection) {
            warn.add(key + " tek bir yaz\u0131 olmal\u0131, varsay\u0131lan kullan\u0131l\u0131yor");
            return def;
        }
        return String.valueOf(v);
    }

    /** Liste okur. Dosyada yoksa jar icindeki varsayilan liste; tek deger yazilmissa tek ogeli (virgulle bolunmus) liste. */
    static List<String> readList(FileConfiguration c, String key, List<String> warn) {
        if (!c.isSet(key)) {
            return new ArrayList<String>(c.getStringList(key)); // jar varsayilani (yoksa bos)
        }
        if (c.isList(key)) {
            return new ArrayList<String>(c.getStringList(key));
        }
        Object v = c.get(key);
        if (v instanceof String) {
            List<String> out = new ArrayList<String>();
            for (String part : ((String) v).split(",")) {
                if (!part.trim().isEmpty()) {
                    out.add(part.trim());
                }
            }
            warn.add(key + " bir liste olmal\u0131 (tek de\u011fer bulundu, tek \u00f6geli liste kabul edildi)");
            return out;
        }
        warn.add(key + " bir liste olmal\u0131 (bulunan: '" + v + "'), varsay\u0131lan kullan\u0131l\u0131yor");
        Configuration defaults = c.getDefaults();
        return defaults == null ? new ArrayList<String>() : new ArrayList<String>(defaults.getStringList(key));
    }

    /** Dosyada olup jar varsayilanlarinda bulunmayan anahtarlar (yazim hatasi olabilir). */
    public static List<String> unknownKeys(FileConfiguration c, String fileName) {
        List<String> out = new ArrayList<String>();
        Configuration defaults = c.getDefaults();
        if (defaults == null) {
            return out;
        }
        Set<String> known = defaults.getKeys(true);
        for (String key : c.getKeys(true)) {
            if (!known.contains(key)) {
                out.add(fileName + ": bilinmeyen anahtar '" + key + "' (yaz\u0131m hatas\u0131 olabilir, kullan\u0131lmayacak)");
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------

    public boolean isWorldActive(String worldName) {
        return worldName != null && aktifDunyalar.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public String formatShort(long millis) {
        return shortFmt.format(Instant.ofEpochMilli(millis));
    }

    public String formatFull(long millis) {
        return fullFmt.format(Instant.ofEpochMilli(millis));
    }

    private static Set<String> lowerSet(List<String> in) {
        Set<String> out = new LinkedHashSet<String>();
        if (in != null) {
            for (String s : in) {
                if (s != null && !s.trim().isEmpty()) {
                    out.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Collections.unmodifiableSet(out);
    }
}
