package com.iade;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite katmani. Tek baglanti kullanilir ve tum metotlar senkronizedir; eklenti ayrica
 * butun yazma islerini tek thread'li bir executor uzerinden yaptirir.
 * Sunucuyla gelen sqlite-jdbc surucusu kullanilir (Spigot/Paper/Purpur 1.16+ hepsinde mevcut).
 */
public final class Database {

    private final Iade plugin;
    private Connection conn;

    public Database(Iade plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean open() {
        try {
            File dir = plugin.getDataFolder();
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("Veri klasoru olusturulamadi: " + dir);
            }
            File file = new File(dir, "veriler.db");
            migrateOldFolder(file);

            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

            Statement s = conn.createStatement();
            try {
                s.execute("PRAGMA journal_mode = WAL;");
                s.execute("PRAGMA synchronous = NORMAL;");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS olumler ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "uuid TEXT NOT NULL,"
                        + "katil TEXT NOT NULL,"
                        + "sebep TEXT NOT NULL,"
                        + "tarih LONG NOT NULL,"
                        + "banli INTEGER DEFAULT 0)");
            } finally {
                s.close();
            }

            // Eski surumlerden gelen veritabanlarini tamamlar (eksik sutunlari ekler).
            String[][] columns = {
                    {"iade_durumu", "TEXT DEFAULT 'Bekleniyor'"},
                    {"envanter", "TEXT"},
                    {"dunya", "TEXT"},
                    {"x", "REAL"},
                    {"y", "REAL"},
                    {"z", "REAL"},
                    {"yaw", "REAL"},
                    {"pitch", "REAL"},
                    {"isim", "TEXT"},
                    {"katil_uuid", "TEXT"},
                    {"ban_tarih", "INTEGER DEFAULT 0"},
                    {"ban_kaynak", "TEXT"},
                    {"ban_yetkili", "TEXT"},
                    {"ban_bitis", "INTEGER DEFAULT -1"},
                    {"ban_sure", "TEXT"},
                    {"iade_tarih", "INTEGER DEFAULT 0"},
                    {"silah", "TEXT"},
                    {"sebep_turu", "TEXT"},
                    {"mesafe", "REAL DEFAULT -1"},
                    {"ayni_ip", "INTEGER DEFAULT 0"},
                    {"ardarda", "INTEGER DEFAULT 0"}
            };
            Set<String> existing = existingColumns();
            for (String[] col : columns) {
                if (!existing.contains(col[0])) {
                    Statement a = conn.createStatement();
                    try {
                        a.executeUpdate("ALTER TABLE olumler ADD COLUMN " + col[0] + " " + col[1]);
                    } finally {
                        a.close();
                    }
                }
            }

            Statement idx = conn.createStatement();
            try {
                idx.executeUpdate("CREATE INDEX IF NOT EXISTS idx_olumler_uuid ON olumler(uuid)");
                idx.executeUpdate("CREATE INDEX IF NOT EXISTS idx_olumler_katil ON olumler(katil)");
                idx.executeUpdate("CREATE INDEX IF NOT EXISTS idx_olumler_tarih ON olumler(tarih)");
                idx.executeUpdate("CREATE TABLE IF NOT EXISTS iade_gecmisi ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "zaman INTEGER NOT NULL,"
                        + "islem TEXT NOT NULL,"
                        + "kurban_uuid TEXT,"
                        + "kurban TEXT,"
                        + "katil TEXT,"
                        + "yetkili TEXT,"
                        + "esya_sayisi INTEGER DEFAULT 0,"
                        + "olum_zamani INTEGER DEFAULT 0,"
                        + "ban_zamani INTEGER DEFAULT 0,"
                        + "ayni_ip INTEGER DEFAULT 0,"
                        + "ardarda INTEGER DEFAULT 0)");
                idx.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gecmis_zaman ON iade_gecmisi(zaman)");
            } finally {
                idx.close();
            }
            // iade_gecmisi 1.1.0'da ardarda sutunu olmadan olusturuldu
            if (!existingColumns("iade_gecmisi").contains("ardarda")) {
                Statement a = conn.createStatement();
                try {
                    a.executeUpdate("ALTER TABLE iade_gecmisi ADD COLUMN ardarda INTEGER DEFAULT 0");
                } finally {
                    a.close();
                }
            }
            return true;
        } catch (ClassNotFoundException missing) {
            plugin.getLogger().severe("Sunucuda SQLite surucusu (org.sqlite.JDBC) bulunamadi. Spigot/Paper normalde"
                    + " bunu icinde tasir; ozel/kirpilmis bir sunucu cekirdegi kullaniyorsaniz sqlite-jdbc ekleyin.");
            return false;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "SQLite baslatilamadi: " + t, t);
            return false;
        }
    }

    private Set<String> existingColumns() throws SQLException {
        return existingColumns("olumler");
    }

    private Set<String> existingColumns(String table) throws SQLException {
        Set<String> out = new HashSet<String>();
        Statement st = conn.createStatement();
        try {
            ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")");
            try {
                while (rs.next()) {
                    out.add(rs.getString("name").toLowerCase(java.util.Locale.ROOT));
                }
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
        return out;
    }

    /**
     * Daha once kurulu bir Iade surumunun veritabanini (plugins/*Iade/veriler.db) bulur ve,
     * bu eklentinin veritabani henuz yoksa bir kez kopyalar. Birden fazla aday varsa en yenisi secilir.
     */
    private void migrateOldFolder(File target) {
        try {
            if (target.exists()) {
                return;
            }
            File ownDir = plugin.getDataFolder();
            File plugins = ownDir.getParentFile();
            if (plugins == null) {
                return;
            }
            File[] dirs = plugins.listFiles();
            if (dirs == null) {
                return;
            }
            File best = null;
            for (File dir : dirs) {
                if (!dir.isDirectory() || dir.getName().equalsIgnoreCase(ownDir.getName())
                        || !dir.getName().toLowerCase().endsWith("olumliste")) {
                    continue;
                }
                File db = new File(dir, "veriler.db");
                if (db.isFile() && (best == null || db.lastModified() > best.lastModified())) {
                    best = db;
                }
            }
            if (best == null) {
                return;
            }
            Files.copy(best.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File wal = new File(best.getPath() + "-wal");
            if (wal.isFile()) {
                Files.copy(wal.toPath(), new File(target.getPath() + "-wal").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("Eski OlumListe surumunun veritabani Iade'ye tasindi.");
        } catch (Exception e) {
            plugin.getLogger().warning("Eski veritabani tasinamadi: " + e.getMessage());
        }
    }

    public synchronized List<DeathRecord> loadAll() throws SQLException {
        List<DeathRecord> out = new ArrayList<DeathRecord>();
        String q = "SELECT id, uuid, katil, katil_uuid, sebep, tarih, banli, ban_tarih, ban_kaynak, ban_yetkili, ban_bitis, ban_sure, iade_durumu, iade_tarih,"
                + " dunya, x, y, z, yaw, pitch, isim, silah, sebep_turu, mesafe, ayni_ip, ardarda FROM olumler ORDER BY id";
        Statement st = conn.createStatement();
        try {
            ResultSet rs = st.executeQuery(q);
            try {
                while (rs.next()) {
                    try {
                        UUID victim = UUID.fromString(rs.getString("uuid"));
                        UUID killerUuid = parseUuid(rs.getString("katil_uuid"));
                        DeathRecord rec = new DeathRecord(
                                rs.getInt("id"), victim, rs.getString("isim"),
                                rs.getString("katil"), killerUuid,
                                rs.getString("sebep"), rs.getLong("tarih"),
                                rs.getInt("banli") == 1, rs.getLong("ban_tarih"), rs.getString("ban_kaynak"),
                                rs.getString("iade_durumu"), rs.getLong("iade_tarih"),
                                rs.getString("dunya"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("yaw"), rs.getFloat("pitch"));
                        rec.banBanner = rs.getString("ban_yetkili");
                        long expiry = rs.getLong("ban_bitis");
                        rec.banExpiry = rs.wasNull() ? -1L : expiry;
                        rec.banDurationText = rs.getString("ban_sure");
                        rec.weapon = rs.getString("silah");
                        rec.damageCause = rs.getString("sebep_turu");
                        double dist = rs.getDouble("mesafe");
                        rec.distance = rs.wasNull() ? -1D : dist;
                        rec.sameIp = rs.getInt("ayni_ip") == 1;
                        rec.ardarda = Math.max(0, rs.getInt("ardarda"));
                        out.add(rec);
                    } catch (IllegalArgumentException bad) {
                        plugin.getLogger().warning("Bozuk kayit atlandi (id=" + rs.getInt("id") + ")");
                    }
                }
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
        return out;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Yeni kaydi ekler ve olusan id'yi dondurur. */
    public synchronized int insert(DeathRecord r, String envanterBase64) throws SQLException {
        String sql = "INSERT INTO olumler (uuid, katil, katil_uuid, sebep, tarih, banli, ban_tarih, ban_kaynak,"
                + " ban_yetkili, ban_bitis, ban_sure, iade_durumu, iade_tarih, envanter, dunya, x, y, z, yaw, pitch, isim,"
                + " silah, sebep_turu, mesafe, ayni_ip, ardarda)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            ps.setString(1, r.victimUuid.toString());
            ps.setString(2, r.killerName);
            ps.setString(3, r.killerUuid == null ? null : r.killerUuid.toString());
            ps.setString(4, r.reason);
            ps.setLong(5, r.timestamp);
            ps.setInt(6, r.banned ? 1 : 0);
            ps.setLong(7, r.banTimestamp);
            ps.setString(8, r.banSource);
            ps.setString(9, r.banBanner);
            ps.setLong(10, r.banExpiry);
            ps.setString(11, r.banDurationText);
            ps.setString(12, r.iadeDurumu);
            ps.setLong(13, r.iadeTimestamp);
            ps.setString(14, envanterBase64);
            ps.setString(15, r.dunya);
            ps.setDouble(16, r.x);
            ps.setDouble(17, r.y);
            ps.setDouble(18, r.z);
            ps.setDouble(19, r.yaw);
            ps.setDouble(20, r.pitch);
            ps.setString(21, r.victimName);
            ps.setString(22, r.weapon);
            ps.setString(23, r.damageCause);
            ps.setDouble(24, r.distance);
            ps.setInt(25, r.sameIp ? 1 : 0);
            ps.setInt(26, Math.max(0, r.ardarda));
            ps.executeUpdate();
        } finally {
            ps.close();
        }
        Statement st = conn.createStatement();
        try {
            ResultSet rs = st.executeQuery("SELECT last_insert_rowid()");
            try {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
        return -1;
    }

    public synchronized void updateBans(List<DeathRecord> records) throws SQLException {
        if (records.isEmpty()) {
            return;
        }
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE olumler SET banli = ?, sebep = ?, ban_tarih = ?, ban_kaynak = ?, ban_yetkili = ?,"
                        + " ban_bitis = ?, ban_sure = ? WHERE id = ?");
        try {
            for (DeathRecord r : records) {
                ps.setInt(1, r.banned ? 1 : 0);
                ps.setString(2, r.reason);
                ps.setLong(3, r.banTimestamp);
                ps.setString(4, r.banSource);
                ps.setString(5, r.banBanner);
                ps.setLong(6, r.banExpiry);
                ps.setString(7, r.banDurationText);
                ps.setInt(8, r.id);
                ps.addBatch();
            }
            ps.executeBatch();
        } finally {
            ps.close();
        }
    }

    public synchronized void updateIade(DeathRecord r) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE olumler SET iade_durumu = ?, iade_tarih = ? WHERE id = ?");
        try {
            ps.setString(1, r.iadeDurumu);
            ps.setLong(2, r.iadeTimestamp);
            ps.setInt(3, r.id);
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    /** Zincirdeki onceki kayitlarin "ard arda" isaretini gunceller. */
    public synchronized void updateArdarda(List<DeathRecord> records) throws SQLException {
        if (records.isEmpty()) {
            return;
        }
        PreparedStatement ps = conn.prepareStatement("UPDATE olumler SET ardarda = ? WHERE id = ?");
        try {
            for (DeathRecord r : records) {
                if (r.id < 0) {
                    continue;
                }
                ps.setInt(1, Math.max(0, r.ardarda));
                ps.setInt(2, r.id);
                ps.addBatch();
            }
            ps.executeBatch();
        } finally {
            ps.close();
        }
    }

    public synchronized void delete(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        PreparedStatement ps = conn.prepareStatement("DELETE FROM olumler WHERE id = ?");
        try {
            for (Integer id : ids) {
                ps.setInt(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        } finally {
            ps.close();
        }
    }

    public synchronized String loadInventory(int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT envanter FROM olumler WHERE id = ?");
        try {
            ps.setInt(1, id);
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
        return null;
    }

    // ------------------------------------------------------------------------------------
    // Iade gecmisi
    // ------------------------------------------------------------------------------------

    public synchronized void insertHistory(HistoryEntry e) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("INSERT INTO iade_gecmisi (zaman, islem, kurban_uuid, kurban, katil,"
                + " yetkili, esya_sayisi, olum_zamani, ban_zamani, ayni_ip, ardarda) VALUES (?,?,?,?,?,?,?,?,?,?,?)");
        try {
            ps.setLong(1, e.time);
            ps.setString(2, e.action);
            ps.setString(3, e.victimUuid);
            ps.setString(4, e.victimName);
            ps.setString(5, e.killerName);
            ps.setString(6, e.actor);
            ps.setInt(7, e.itemCount);
            ps.setLong(8, e.deathTime);
            ps.setLong(9, e.banTime);
            ps.setInt(10, e.sameIp ? 1 : 0);
            ps.setInt(11, Math.max(0, e.ardarda));
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    /** En yeni once, sayfalama icin. */
    public synchronized List<HistoryEntry> loadHistory(int offset, int limit) throws SQLException {
        List<HistoryEntry> out = new ArrayList<HistoryEntry>();
        PreparedStatement ps = conn.prepareStatement("SELECT zaman, islem, kurban_uuid, kurban, katil, yetkili, esya_sayisi,"
                + " olum_zamani, ban_zamani, ayni_ip, ardarda FROM iade_gecmisi ORDER BY zaman DESC, id DESC LIMIT ? OFFSET ?");
        try {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    out.add(new HistoryEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getInt(7), rs.getLong(8), rs.getLong(9),
                            rs.getInt(10) == 1, rs.getInt(11)));
                }
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
        return out;
    }

    public synchronized int countHistory() throws SQLException {
        Statement st = conn.createStatement();
        try {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM iade_gecmisi");
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
    }

    /** Verilen andan eski gecmis satirlarini siler; silinen satir sayisini dondurur. */
    public synchronized int deleteHistoryBefore(long cutoff) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("DELETE FROM iade_gecmisi WHERE zaman < ?");
        try {
            ps.setLong(1, cutoff);
            return ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    /** Islem sayilari ve ban tespitinden onaya kadar gecen ortalama sure. */
    public synchronized HistoryStats historyStats() throws SQLException {
        int approved = 0;
        int claimed = 0;
        int deleted = 0;
        long avg = -1L;
        Statement st = conn.createStatement();
        try {
            ResultSet rs = st.executeQuery("SELECT islem, COUNT(*) FROM iade_gecmisi GROUP BY islem");
            try {
                while (rs.next()) {
                    String action = rs.getString(1);
                    int n = rs.getInt(2);
                    if (HistoryEntry.ONAY.equals(action)) {
                        approved = n;
                    } else if (HistoryEntry.ALINDI.equals(action)) {
                        claimed = n;
                    } else if (HistoryEntry.SIL.equals(action)) {
                        deleted = n;
                    }
                }
            } finally {
                rs.close();
            }
            ResultSet ra = st.executeQuery("SELECT AVG(zaman - ban_zamani), COUNT(*) FROM iade_gecmisi"
                    + " WHERE islem = 'ONAY' AND ban_zamani > 0 AND zaman >= ban_zamani");
            try {
                if (ra.next() && ra.getInt(2) > 0) {
                    avg = Math.round(ra.getDouble(1));
                }
            } finally {
                ra.close();
            }
        } finally {
            st.close();
        }
        return new HistoryStats(approved, claimed, deleted, avg);
    }

    public synchronized void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Veritabani kapatilirken hata: " + e.getMessage());
        }
    }
}
