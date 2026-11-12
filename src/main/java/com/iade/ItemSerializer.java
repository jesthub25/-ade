package com.iade;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Envanteri Base64'e cevirir. Format eski surumle birebir aynidir; boylece mevcut
 * veriler.db dosyasindaki envanterler okunmaya devam eder. Bukkit'in serilestirmesi
 * surum bilgisi tasidigi icin esyalar yeni surumlerde otomatik yukseltilir.
 */
public final class ItemSerializer {

    private static final int MAX_ITEMS = 4096;

    private ItemSerializer() {
    }

    public static String toBase64(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
        try {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                out.writeObject(item);
            }
        } finally {
            out.close();
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static ItemStack[] fromBase64(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IOException("Gecersiz Base64 verisi", e);
        }
        BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(raw));
        try {
            int len = in.readInt();
            if (len < 0 || len > MAX_ITEMS) {
                throw new IOException("Gecersiz envanter boyutu: " + len);
            }
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Sinif turu bulunamadi", e);
        } finally {
            in.close();
        }
    }
}
