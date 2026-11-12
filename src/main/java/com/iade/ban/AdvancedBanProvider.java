package com.iade.ban;

import com.iade.Iade;

import java.lang.reflect.Method;

/**
 * AdvancedBan: PunishmentManager.get().getBan(uuid) / isBanned(uuid).
 * AdvancedBan UUID'leri tiresiz saklar; ikisi de denenir.
 */
public final class AdvancedBanProvider extends ReflectiveProvider {

    private Object manager;
    private Method getBan;
    private Method isBanned;

    public AdvancedBanProvider(Iade plugin) {
        super(plugin, "AdvancedBan");
    }

    @Override
    public String getName() {
        return "AdvancedBan";
    }

    @Override
    protected boolean init(ClassLoader loader) throws Exception {
        Class<?> c = Class.forName("me.leoko.advancedban.manager.PunishmentManager", true, loader);
        Object instance = c.getMethod("get").invoke(null);
        if (instance == null) {
            return false;
        }
        Method gb = null;
        Method ib = null;
        try {
            gb = c.getMethod("getBan", String.class);
        } catch (NoSuchMethodException ignored) {
            // opsiyonel
        }
        try {
            ib = c.getMethod("isBanned", String.class);
        } catch (NoSuchMethodException ignored) {
            // opsiyonel
        }
        if (gb == null && ib == null) {
            throw new NoSuchMethodException("getBan/isBanned bulunamadi");
        }
        getBan = gb;
        isBanned = ib;
        manager = instance;
        return true;
    }

    @Override
    protected BanInfo doQuery(BanTarget t) throws Exception {
        if (t.uuid == null) {
            return null;
        }
        String plain = t.uuid.toString().replace("-", "");
        String dashed = t.uuid.toString();

        if (getBan != null) {
            Object ban = getBan.invoke(manager, plain);
            if (ban == null) {
                ban = getBan.invoke(manager, dashed);
            }
            if (ban != null) {
                return detailsOf(ban);
            }
            return null;
        }

        Object r = isBanned.invoke(manager, plain);
        if (!(r instanceof Boolean) || !((Boolean) r).booleanValue()) {
            r = isBanned.invoke(manager, dashed);
        }
        if (r instanceof Boolean && ((Boolean) r).booleanValue()) {
            return new BanInfo(null, getName());
        }
        return null;
    }

    /** Sebep, banlayan (operator) ve bitis zamani; okunamayan alanlar bos/bilinmiyor kalir. */
    private BanInfo detailsOf(Object punishment) {
        String reason = null;
        String operator = null;
        long expiry = -1L;
        try {
            Object r = punishment.getClass().getMethod("getReason").invoke(punishment);
            if (r != null && !"none".equalsIgnoreCase(r.toString().trim())) {
                reason = r.toString();
            }
        } catch (Throwable ignored) {
            // opsiyonel
        }
        try {
            Object o = punishment.getClass().getMethod("getOperator").invoke(punishment);
            if (o != null) {
                operator = o.toString();
            }
        } catch (Throwable ignored) {
            // opsiyonel
        }
        try {
            Object e = punishment.getClass().getMethod("getEnd").invoke(punishment);
            if (e instanceof Number) {
                long end = ((Number) e).longValue();
                expiry = end <= 0 ? 0L : end;
            }
        } catch (Throwable ignored) {
            // opsiyonel
        }
        return new BanInfo(reason, getName(), operator, expiry);
    }
}
