package com.iade;

import org.bukkit.Bukkit;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/** Iade onaylandiginda Discord webhook'una embed gonderir. Arka plan thread'inde calisir. */
public final class DiscordWebhook {

    private DiscordWebhook() {
    }

    public static void sendRefundLog(final Iade plugin, String url, String victim, String staff,
                                     String killer, String reason, long deathTime) {
        if (Text.isBlank(url) || !url.trim().startsWith("http")) {
            return;
        }
        final String endpoint = url.trim();
        final String json = buildJson(plugin, victim, staff, killer, reason, deathTime);
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    post(plugin, endpoint, json);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Webhook zamanlanamadi", t);
        }
    }

    private static String buildJson(Iade plugin, String victim, String staff, String killer,
                                    String reason, long deathTime) {
        String date = plugin.getSettings().formatFull(deathTime);
        StringBuilder sb = new StringBuilder(768);
        sb.append("{\"username\":\"Iade\",");
        sb.append("\"allowed_mentions\":{\"parse\":[]},");
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"\\ud83d\\udce6 \\u0130ade \\u0130\\u015flemi Ger\\u00e7ekle\\u015ftirildi\",");
        sb.append("\"color\":5763719,");
        sb.append("\"fields\":[");
        field(sb, "\ud83d\udc80 Hileye \u00d6len Oyuncu", victim, true);
        sb.append(',');
        field(sb, "\ud83d\udee1\ufe0f \u0130ade Veren Yetkili", staff, true);
        sb.append(',');
        field(sb, "\ud83c\udfaf Katil (Hileci)", killer, true);
        sb.append(',');
        field(sb, "\ud83d\udcdd Ban Sebebi", reason, false);
        sb.append(',');
        field(sb, "\ud83d\udd52 \u00d6l\u00fcm Tarihi", date, false);
        sb.append("],");
        sb.append("\"footer\":{\"text\":\"Iade\"}");
        sb.append("}]}");
        return sb.toString();
    }

    private static void field(StringBuilder sb, String name, String value, boolean inline) {
        String v = Text.isBlank(value) ? "-" : Text.truncate(value, 1000);
        sb.append("{\"name\":\"").append(esc(name)).append("\",\"value\":\"").append(esc(v))
                .append("\",\"inline\":").append(inline).append('}');
    }

    /** JSON metin kacisi; Turkce karakterler ve emojiler \\uXXXX olarak yazilir. */
    private static String esc(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static void post(Iade plugin, String endpoint, String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "Iade");
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning("Discord webhook basarisiz (HTTP " + code + ")");
            }
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                in.close();
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Discord webhook gonderilemedi: " + t.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
