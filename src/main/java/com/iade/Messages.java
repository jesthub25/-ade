package com.iade;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** messages.yml okuyucu. Eksik anahtarlar jar icindeki varsayilanlardan tamamlanir. */
public final class Messages {

    private final FileConfiguration cfg;

    public Messages(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    /**
     * Mesaji dondurur. Once sablon renklendirilir, sonra degerler yerlestirilir;
     * boylece oyuncu/sebep metinleri icindeki '&' kodlari renk olarak yorumlanmaz.
     */
    public String get(String key, String... kv) {
        String template = cfg.isList(key) ? null : cfg.getString(key);
        if (template == null) {
            return "\u00a7c[mesaj eksik: " + key + "]";
        }
        return Text.replace(Text.color(template.replace("%prefix%", prefix())), kv);
    }

    public List<String> list(String key, String... kv) {
        List<String> raw;
        if (cfg.isString(key)) {
            raw = new ArrayList<String>();   // liste yerine tek satir yazilmissa tek satirlik liste say
            raw.add(cfg.getString(key));
        } else {
            raw = cfg.getStringList(key);
        }
        List<String> out = new ArrayList<String>();
        if (raw != null) {
            for (String s : raw) {
                out.add(Text.replace(Text.color(s.replace("%prefix%", prefix())), kv));
            }
        }
        return out;
    }

    /** messages.yml'de olup jar varsayilanlarinda bulunmayan anahtarlar (yazim hatasi olabilir). */
    public List<String> unknownKeys() {
        return Settings.unknownKeys(cfg, "messages.yml");
    }

    private String prefix() {
        String p = cfg.getString("prefix", "");
        return p == null ? "" : p;
    }
}
