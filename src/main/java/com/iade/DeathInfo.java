package com.iade;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Olum aninda toplanan ek bilgiler (silah, olum turu, mesafe, ayni IP) ve bunlarin metne cevrilmesi.
 * <p>
 * Surum uyumu: yalnizca kararli Bukkit cagrilari kullanilir. Enchantment / Sound / InventoryView
 * metotlarina dokunulmaz; buyu sayisi {@code getEnchantments().size()} ile, olum turu enum adiyla
 * ({@code DamageCause.name()}) okunur. Boylece 1.20.1 ile derlenen jar 1.21.x'te de calisir.
 */
public final class DeathInfo {

    /** Silah kaydinda alanlari ayiran karakter (gorunur isimlerde kullanilamaz, yakalarken temizlenir). */
    private static final char SEP = '\t';
    private static final int MAX_NAME = 48;
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+(%[0-9A-Za-z_.\\-]+)?$");

    private DeathInfo() {
    }

    // ------------------------------------------------------------------------------------
    // Yakalama (ana thread)
    // ------------------------------------------------------------------------------------

    /** Katilin ana elindeki esya: "TUR \t ozel ad \t buyu sayisi". Elde esya yoksa "AIR". Okunamazsa null. */
    public static String captureWeapon(Player killer) {
        try {
            ItemStack hand = killer.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == null) {
                return "AIR";
            }
            String type = hand.getType().name();
            if (isAir(type)) {
                return "AIR";
            }
            String name = "";
            if (hand.hasItemMeta()) {
                ItemMeta meta = hand.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    name = clean(meta.getDisplayName());
                }
            }
            int enchants = 0;
            try {
                enchants = hand.getEnchantments().size();
            } catch (Throwable ignored) {
                // buyu sayisi okunamazsa 0 kalir
            }
            return type + SEP + name + SEP + enchants;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Kurbanin son hasar nedeni (DamageCause adi, orn. ENTITY_ATTACK, PROJECTILE). Yoksa null. */
    public static String captureCause(Player victim) {
        try {
            EntityDamageEvent last = victim.getLastDamageCause();
            if (last == null || last.getCause() == null) {
                return null;
            }
            return last.getCause().name();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Katil ile kurban arasindaki mesafe (blok). Farkli dunyadaysalar veya okunamazsa -1. */
    public static double captureDistance(Player killer, Player victim) {
        try {
            Location a = killer.getLocation();
            Location b = victim.getLocation();
            if (a == null || b == null || a.getWorld() == null || b.getWorld() == null
                    || !a.getWorld().getName().equals(b.getWorld().getName())) {
                return -1D;
            }
            double d = a.distance(b);
            return (Double.isNaN(d) || Double.isInfinite(d)) ? -1D : d;
        } catch (Throwable t) {
            return -1D;
        }
    }

    // ------------------------------------------------------------------------------------
    // Ayni IP
    // ------------------------------------------------------------------------------------

    /** Oyuncunun baglanti adresi (kanonik yazim) ya da bilinmiyorsa null. IP hicbir yerde saklanmaz. */
    public static String addressOf(Player p) {
        try {
            InetSocketAddress sa = p.getAddress();
            if (sa == null) {
                return null;
            }
            InetAddress ia = sa.getAddress();
            if (ia == null) {
                return null;
            }
            return stripScope(ia.getHostAddress());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Kurban ve katil ayni (ve yoksayilanlar listesinde olmayan) adresten mi baglanmis? */
    public static boolean sameIp(Player victim, Player killer, Set<String> ignored) {
        String a = addressOf(victim);
        if (a == null || a.isEmpty()) {
            return false;
        }
        String b = addressOf(killer);
        if (b == null || !a.equals(b)) {
            return false;
        }
        return !ignored.contains(a);
    }

    /**
     * Ayar listesindeki bir adresi kanonik yaziya cevirir (IPv6 tam yazim, kucuk harf). IPv4/IPv6 sabiti
     * degilse null doner. Alan adlari asla cozumlenmez (DNS sorgusu yapilmaz).
     */
    static String canonicalAddress(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("[") && t.endsWith("]") && t.length() > 2) {
            t = t.substring(1, t.length() - 1);
        }
        if (t.isEmpty()) {
            return null;
        }
        boolean literal = IPV4.matcher(t).matches() || (t.indexOf(':') >= 0 && IPV6.matcher(t).matches());
        if (!literal) {
            return null;
        }
        try {
            return stripScope(InetAddress.getByName(t).getHostAddress());
        } catch (Throwable e) {
            return null;
        }
    }

    private static String stripScope(String host) {
        int pct = host.indexOf('%');
        String h = pct >= 0 ? host.substring(0, pct) : host;
        return h.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------------------
    // Metne cevirme (menuler, bildirimler)
    // ------------------------------------------------------------------------------------

    /** Silah kaydini okunur metne cevirir; kayit yoksa null. */
    public static String weaponText(Messages m, String raw) {
        if (Text.isBlank(raw)) {
            return null;
        }
        String[] parts = raw.split(String.valueOf(SEP), -1);
        String type = parts[0];
        if (isAir(type)) {
            return m.get("silah-el");
        }
        StringBuilder sb = new StringBuilder(pretty(type));
        if (parts.length > 1 && !parts[1].isEmpty()) {
            sb.append(' ').append(m.get("silah-ozel-ad", "%ad%", parts[1]));
        }
        if (parts.length > 2) {
            int n = 0;
            try {
                n = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException ignored) {
                // bozuk deger: buyu yazilmaz
            }
            if (n > 0) {
                sb.append(' ').append(m.get("silah-buyu", "%buyu%", String.valueOf(n)));
            }
        }
        return sb.toString();
    }

    /** DamageCause adini Turkce ada cevirir (messages.yml: tur-<ad>); bilinmeyen tur duzenlenerek yazilir. */
    public static String causeText(Messages m, String raw) {
        if (Text.isBlank(raw)) {
            return null;
        }
        String key = "tur-" + raw.trim().toLowerCase(Locale.ROOT);
        if (m.has(key)) {
            return m.get(key);
        }
        return pretty(raw.trim());
    }

    /** "12.3" biciminde (nokta ondalik) mesafe; bilinmiyorsa null. */
    public static String distanceText(double d) {
        if (d < 0 || Double.isNaN(d)) {
            return null;
        }
        return String.format(Locale.ROOT, "%.1f", d);
    }

    /** DIAMOND_SWORD -> "Diamond Sword" */
    static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder(enumName.length());
        boolean upper = true;
        for (int i = 0; i < enumName.length(); i++) {
            char c = enumName.charAt(i);
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }
        return sb.toString();
    }

    private static boolean isAir(String type) {
        return type.equals("AIR") || type.endsWith("_AIR");
    }

    /** Renk kodlarini, sekme ve satir sonlarini temizler; uzunlugu sinirlar. */
    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++; // renk kodunun ikinci karakterini de atla
                continue;
            }
            sb.append(c < 0x20 ? ' ' : c);
        }
        return Text.truncate(sb.toString().trim(), MAX_NAME);
    }
}
