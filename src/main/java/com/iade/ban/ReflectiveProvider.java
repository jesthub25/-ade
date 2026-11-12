package com.iade.ban;

import com.iade.Iade;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;

/**
 * Ban eklentilerine derleme zamani bagimlilik OLMADAN baglanir (reflection). Boylece ban
 * eklentisi kurulu degilse veya API'si degismisse bu eklenti calismaya devam eder, sadece o
 * sistemi atlar ve bir kez uyari yazar.
 *
 * Sorgu sonucu uc durumludur:
 *  - BanInfo        : oyuncu bu sistemde banli
 *  - null           : oyuncu kesin olarak banli degil (veya eklenti kurulu degil)
 *  - exception      : belirlenemedi (API hazir degil, UUID yok, hata). Cagiran "belirsiz" sayar.
 */
abstract class ReflectiveProvider implements BanProvider {

    protected final Iade plugin;
    private final String pluginName;
    private volatile boolean ready;
    private volatile boolean broken;
    private volatile boolean logged;

    ReflectiveProvider(Iade plugin, String pluginName) {
        this.plugin = plugin;
        this.pluginName = pluginName;
    }

    @Override
    public final String getPluginName() {
        return pluginName;
    }

    @Override
    public final boolean requiresMainThread() {
        return false;
    }

    @Override
    public final boolean isActive() {
        return isInstalled() && ensureReady();
    }

    /**
     * API sinif ve metotlarini bulur. API henuz hazir degilse false dondurur (sonra tekrar denenir);
     * API uyumsuzsa exception firlatir (sistem kapatilir).
     */
    protected abstract boolean init(ClassLoader pluginLoader) throws Exception;

    protected abstract BanInfo doQuery(BanTarget target) throws Exception;

    private boolean isInstalled() {
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        return p != null && p.isEnabled();
    }

    private synchronized boolean ensureReady() {
        if (ready) {
            return true;
        }
        if (broken) {
            return false;
        }
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        if (p == null || !p.isEnabled()) {
            return false;
        }
        try {
            ready = init(p.getClass().getClassLoader());
        } catch (Throwable t) {
            broken = true;
            warnOnce("API baglantisi kurulamadi, bu ban sistemi atlanacak", t);
        }
        return ready;
    }

    @Override
    public final BanInfo query(BanTarget target) throws Exception {
        if (!isInstalled()) {
            return null; // kurulu degil: bu sistemde kesinlikle banli degil
        }
        if (target.uuid == null) {
            throw new IllegalStateException("UUID bilinmiyor");
        }
        if (!ensureReady()) {
            throw new IllegalStateException(getName() + " API hazir degil");
        }
        try {
            return doQuery(target);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            warnOnce("sorgu hatasi", cause);
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException(cause);
        } catch (Exception e) {
            warnOnce("sorgu hatasi", e);
            throw e;
        }
    }

    protected final void warnOnce(String message, Throwable t) {
        if (!logged) {
            logged = true;
            plugin.getLogger().warning("[" + getName() + "] " + message + ": " + t);
        }
    }
}
