package com.iade;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * config.yml icinde tek bir degeri, dosyanin geri kalanina (yorumlar, bosluklar, satir sonlari) DOKUNMADAN
 * degistirir. Bukkit'in saveConfig()'i yorumlari bozabilecegi icin satir bazli duzenleme yapilir.
 *
 * Guvenlik: sonuc once bellekte YAML olarak okunup degerin gercekten istenen deger oldugu dogrulanir;
 * ancak sonra gecici dosyaya yazilip yerine tasinir. Herhangi bir adim basarisiz olursa orijinal dosya
 * hic degismez. Desteklenen yollar: "bolum.anahtar" (iki seviye) ve "anahtar".
 */
public final class ConfigWriter {

    private ConfigWriter() {
    }

    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_\\-]+)(\\s*:\\s*)(.*)$");

    /** @return null: basarili; aksi halde hata aciklamasi (dosya degismemistir). */
    public static synchronized String set(File file, String path, Object value) {
        try {
            if (file == null || !file.isFile()) {
                return "config.yml bulunamadı";
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') {
                text = text.substring(1);
            }
            String eol = text.contains("\r\n") ? "\r\n" : "\n";
            String[] raw = text.split("\r?\n", -1);
            List<String> lines = new ArrayList<String>(raw.length);
            for (String l : raw) {
                lines.add(l);
            }
            String rendered = render(value);
            String[] parts = path.split("\\.");
            String problem;
            if (parts.length == 1) {
                problem = setTopLevel(lines, parts[0], rendered);
            } else if (parts.length == 2) {
                problem = setNested(lines, parts[0], parts[1], rendered);
            } else {
                return "desteklenmeyen yol: " + path;
            }
            if (problem != null) {
                return problem;
            }
            String result = String.join(eol, lines);

            // Dogrulama: yeni metin gecerli YAML olmali ve deger istenen deger olmali.
            YamlConfiguration check = new YamlConfiguration();
            try {
                check.loadFromString(result);
            } catch (Exception e) {
                return "oluşan YAML geçersiz: " + e.getMessage();
            }
            Object got = check.get(path);
            if (got == null || !String.valueOf(got).equals(String.valueOf(value))) {
                return "değer doğrulanamadı (" + got + " != " + value + ")";
            }

            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.write(tmp.toPath(), result.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        } catch (IOException e) {
            return String.valueOf(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } catch (RuntimeException e) {
            return String.valueOf(e);
        }
    }

    /** Degeri YAML yazimina cevirir: true/false, sayi veya cift tirnakli yazi. */
    static String render(Object value) {
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return String.valueOf(value);
        }
        if (value instanceof Double || value instanceof Float) {
            return String.valueOf(value);
        }
        String s = String.valueOf(value);
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    private static boolean isBlankOrComment(String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith("#");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static String setTopLevel(List<String> lines, String key, String rendered) {
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (isBlankOrComment(l) || indentOf(l) != 0) {
                continue;
            }
            Matcher m = KEY_LINE.matcher(l);
            if (m.matches() && m.group(2).equals(key)) {
                lines.set(i, replaceValue(l, m, rendered));
                return null;
            }
        }
        appendBlock(lines, false, key + ": " + rendered);
        return null;
    }

    private static String setNested(List<String> lines, String section, String key, String rendered) {
        int sectionAt = -1;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (isBlankOrComment(l) || indentOf(l) != 0) {
                continue;
            }
            Matcher m = KEY_LINE.matcher(l);
            if (m.matches() && m.group(2).equals(section)) {
                String rest = stripComment(m.group(4)).trim();
                if (!rest.isEmpty()) {
                    return "'" + section + "' bölümü düzenlenemiyor (satır içi değer)";
                }
                sectionAt = i;
                break;
            }
        }
        if (sectionAt < 0) {
            appendBlock(lines, true, section + ":", "  " + key + ": " + rendered);
            return null;
        }
        // bolumun sonu: sonraki girintisiz, yorum olmayan satir
        int end = lines.size();
        for (int j = sectionAt + 1; j < lines.size(); j++) {
            String l = lines.get(j);
            if (!isBlankOrComment(l) && indentOf(l) == 0) {
                end = j;
                break;
            }
        }
        int childIndent = -1;
        int lastContent = sectionAt;
        for (int j = sectionAt + 1; j < end; j++) {
            String l = lines.get(j);
            if (isBlankOrComment(l)) {
                continue;
            }
            lastContent = j;
            if (childIndent < 0) {
                childIndent = indentOf(l);
            }
        }
        if (childIndent < 0) {
            childIndent = 2;
        }
        for (int j = sectionAt + 1; j < end; j++) {
            String l = lines.get(j);
            if (isBlankOrComment(l) || indentOf(l) != childIndent) {
                continue;
            }
            Matcher m = KEY_LINE.matcher(l);
            if (m.matches() && m.group(2).equals(key)) {
                lines.set(j, replaceValue(l, m, rendered));
                return null;
            }
        }
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < childIndent; i++) {
            pad.append(' ');
        }
        lines.add(lastContent + 1, pad + key + ": " + rendered);
        return null;
    }

    /** Satirdaki degeri degistirir; satir sonundaki yorumu (ve sutunu) korur. */
    private static String replaceValue(String line, Matcher m, String rendered) {
        String rest = m.group(4);
        int valueLen = valueLength(rest);
        String after = rest.substring(valueLen);
        String prefix = m.group(1) + m.group(2) + m.group(3) + rendered;
        int hash = after.indexOf('#');
        if (hash < 0) {
            return prefix;
        }
        String comment = after.substring(hash);
        int oldCommentCol = line.length() - after.length() + hash;
        int pad = Math.max(1, oldCommentCol - prefix.length());
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        return sb.append(comment).toString();
    }

    /** "deger   # yorum" icindeki degerin uzunlugu (tirnaklari dikkate alir; sondaki bosluklar dahil degil). */
    private static int valueLength(String rest) {
        if (rest.isEmpty()) {
            return 0;
        }
        char first = rest.charAt(0);
        if (first == '"' || first == '\'') {
            for (int i = 1; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (first == '"' && c == '\\') {
                    i++;
                } else if (c == first) {
                    if (first == '\'' && i + 1 < rest.length() && rest.charAt(i + 1) == '\'') {
                        i++;
                        continue;
                    }
                    return i + 1;
                }
            }
            return rest.length();
        }
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            if (rest.charAt(i) == '#' && (i == 0 || rest.charAt(i - 1) == ' ' || rest.charAt(i - 1) == '\t')) {
                cut = i;
                break;
            }
        }
        while (cut > 0 && (rest.charAt(cut - 1) == ' ' || rest.charAt(cut - 1) == '\t')) {
            cut--;
        }
        return cut;
    }

    private static String stripComment(String rest) {
        return rest.substring(0, valueLength(rest));
    }

    /** Dosyanin sonuna satir(lar) ekler; dosyanin sonundaki satir sonu korunur. */
    private static void appendBlock(List<String> lines, boolean blankBefore, String... added) {
        boolean trailingNewline = !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty();
        if (trailingNewline) {
            lines.remove(lines.size() - 1);
        }
        if (blankBefore && !lines.isEmpty()) {
            lines.add("");
        }
        for (String l : added) {
            lines.add(l);
        }
        if (trailingNewline) {
            lines.add("");
        }
    }
}
