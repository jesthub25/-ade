package com.iade.ban;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * EssentialsX ban sistemi (/ban, /tempban, /banip). EssentialsX banlari sunucunun ban listesine
 * yazar; bu saglayici EssentialsX kuruluysa o listeyi okur ve banlari "EssentialsX" kaynagiyla
 * isaretler. Gecici banlarin suresi dolunca liste kaydi kendiliginden kalkar.
 * IP banlari liste uzerinden kurbanin ismine baglanamaz; onlari giris dinleme (LoginListener) yakalar.
 * Ana thread'de cagrilir. EssentialsX kurulu degilse null (banli degil) dondurur.
 */
public final class EssentialsBanProvider implements BanProvider {

    @Override
    public String getName() {
        return "EssentialsX";
    }

    @Override
    public String getPluginName() {
        return "Essentials"; // EssentialsX'in plugin.yml adi
    }

    @Override
    public boolean requiresMainThread() {
        return true;
    }

    @Override
    public boolean isActive() {
        Plugin p = find();
        return p != null && p.isEnabled();
    }

    @Override
    public BanInfo query(BanTarget t) {
        if (!isActive()) {
            return null; // kurulu degil: bu sistemde banli degil
        }
        return BukkitBanLists.lookup(t, getName());
    }

    private static Plugin find() {
        PluginManager pm = Bukkit.getPluginManager();
        if (pm == null) {
            return null;
        }
        Plugin p = pm.getPlugin("Essentials");
        if (p == null) {
            p = pm.getPlugin("EssentialsX");
        }
        return p;
    }
}
