package com.iade.ban;

import com.iade.Iade;

import java.lang.reflect.Method;
import java.util.UUID;

/** BanManager: BmAPI.isBanned(UUID) ve BmAPI.getCurrentBan(UUID) (statik metotlar). */
public final class BanManagerProvider extends ReflectiveProvider {

    private static final String[] API_CLASSES = {
            "me.confuser.banmanager.common.api.BmAPI",
            "me.confuser.banmanager.BmAPI"
    };

    private Method isBanned;
    private Method getCurrentBan;

    public BanManagerProvider(Iade plugin) {
        super(plugin, "BanManager");
    }

    @Override
    public String getName() {
        return "BanManager";
    }

    @Override
    protected boolean init(ClassLoader loader) throws Exception {
        Class<?> c = null;
        for (String name : API_CLASSES) {
            try {
                c = Class.forName(name, true, loader);
                break;
            } catch (ClassNotFoundException ignored) {
                // siradaki adayi dene
            }
        }
        if (c == null) {
            throw new ClassNotFoundException("BmAPI sinifi bulunamadi");
        }
        isBanned = c.getMethod("isBanned", UUID.class);
        try {
            getCurrentBan = c.getMethod("getCurrentBan", UUID.class);
        } catch (NoSuchMethodException e) {
            getCurrentBan = null;
        }
        return true;
    }

    @Override
    protected BanInfo doQuery(BanTarget t) throws Exception {
        if (t.uuid == null) {
            return null;
        }
        Object r = isBanned.invoke(null, t.uuid);
        if (!(r instanceof Boolean) || !((Boolean) r).booleanValue()) {
            return null;
        }
        return detailsOf(t.uuid);
    }

    /** Sebep, banlayan (actor) ve bitis zamani; okunamayan alanlar bos/bilinmiyor kalir. */
    private BanInfo detailsOf(UUID uuid) {
        if (getCurrentBan == null) {
            return new BanInfo(null, getName());
        }
        String reason = null;
        String actor = null;
        long expiry = -1L;
        try {
            Object ban = getCurrentBan.invoke(null, uuid);
            if (ban != null) {
                try {
                    Object r = ban.getClass().getMethod("getReason").invoke(ban);
                    reason = r == null ? null : r.toString();
                } catch (Throwable ignored) {
                    // opsiyonel
                }
                try {
                    Object a = ban.getClass().getMethod("getActor").invoke(ban);
                    if (a != null) {
                        Object n = a.getClass().getMethod("getName").invoke(a);
                        actor = n == null ? null : n.toString();
                    }
                } catch (Throwable ignored) {
                    // opsiyonel
                }
                try {
                    Object e = ban.getClass().getMethod("getExpires").invoke(ban);
                    if (e instanceof Number) {
                        long seconds = ((Number) e).longValue();
                        expiry = seconds <= 0 ? 0L : seconds * 1000L; // BanManager saniye cinsinden tutar
                    }
                } catch (Throwable ignored) {
                    // opsiyonel
                }
            }
        } catch (Throwable ignored) {
            // yalnizca ban bilgisi, ayrinti yok
        }
        return new BanInfo(reason, getName(), actor, expiry);
    }
}
