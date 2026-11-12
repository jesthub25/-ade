package com.iade.ban;

import com.iade.Iade;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * LiteBans: litebans.api.Database.get().isPlayerBanned(UUID, String ip) ile ban durumu,
 * prepareStatement ile ban sebebi sorgulanir. LiteBans'in kendi komutlarini ve
 * baska eklentilerin gizli komutla verdigi banlari da yakalar.
 */
public final class LiteBansProvider extends ReflectiveProvider {

    private Object db;
    private Method isPlayerBanned;
    private Method prepareStatement;

    public LiteBansProvider(Iade plugin) {
        super(plugin, "LiteBans");
    }

    @Override
    public String getName() {
        return "LiteBans";
    }

    @Override
    protected boolean init(ClassLoader loader) throws Exception {
        Class<?> c = Class.forName("litebans.api.Database", true, loader);
        Object instance = c.getMethod("get").invoke(null);
        if (instance == null) {
            return false; // LiteBans veritabani henuz hazir degil, sonra tekrar denenecek
        }
        isPlayerBanned = c.getMethod("isPlayerBanned", UUID.class, String.class);
        try {
            prepareStatement = c.getMethod("prepareStatement", String.class);
        } catch (NoSuchMethodException e) {
            prepareStatement = null; // sebep okunamaz ama ban durumu yine bilinir
        }
        db = instance;
        return true;
    }

    @Override
    protected BanInfo doQuery(BanTarget t) throws Exception {
        if (t.uuid == null) {
            return null;
        }
        Object r = isPlayerBanned.invoke(db, t.uuid, null);
        if (!(r instanceof Boolean) || !((Boolean) r).booleanValue()) {
            return null;
        }
        return fetchDetails(t.uuid);
    }

    /** Sebep, banlayan ve bitis zamanini okur; sutun adlari uymazsa sadece sebebi, o da olmazsa bos bilgiyi dondurur. */
    private BanInfo fetchDetails(UUID uuid) {
        if (prepareStatement == null) {
            return new BanInfo(null, getName());
        }
        try {
            PreparedStatement ps = (PreparedStatement) prepareStatement.invoke(db,
                    "SELECT reason, banned_by_name, until FROM {bans} WHERE uuid=? AND active=? ORDER BY time DESC LIMIT 1");
            try {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, true);
                ResultSet rs = ps.executeQuery();
                try {
                    if (rs.next()) {
                        long until = rs.getLong(3);
                        return new BanInfo(rs.getString(1), getName(), rs.getString(2), until > 0 ? until : 0L);
                    }
                } finally {
                    rs.close();
                }
            } finally {
                ps.close();
            }
        } catch (Throwable ignored) {
            // sutunlar farkliysa asagidaki yalnizca-sebep sorgusu denenir
        }
        return new BanInfo(fetchReason(uuid), getName());
    }

    private String fetchReason(UUID uuid) {
        if (prepareStatement == null) {
            return null;
        }
        try {
            PreparedStatement ps = (PreparedStatement) prepareStatement.invoke(db,
                    "SELECT reason FROM {bans} WHERE uuid=? AND active=? ORDER BY time DESC LIMIT 1");
            try {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, true);
                ResultSet rs = ps.executeQuery();
                try {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                } finally {
                    rs.close();
                }
            } finally {
                ps.close();
            }
        } catch (Throwable ignored) {
            // Sebep okunamazsa komuttaki veya varsayilan sebep kullanilir.
        }
        return null;
    }
}
