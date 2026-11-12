package com.iade;

import com.iade.ban.BanInfo;
import com.iade.ban.BanService;
import com.iade.ban.BanTarget;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Iade - hilecilere olen oyuncularin envanterini kaydeden ve katil banlaninca
 * yetkili onayiyla iade eden eklenti. Spigot / Paper / Purpur 1.16 - 1.21.
 */
public class Iade extends JavaPlugin {

    private static final String[] COMMANDS = {
            "olumliste", "olumlistesil", "iadelerim", "iadever", "olumlistebanla", "olumlistereload"
    };
    private static final String DEFAULT_BAN_REASON = "Yasakland\u0131";
    private static final String PENDING_REASON = "Bekleniyor...";

    private volatile Settings settings;
    private volatile Messages messages;
    private Database database;
    private BanService banService;
    private Menus menus;
    private ExecutorService dbExecutor;
    private ExecutorService banExecutor;
    private BukkitTask cleanupTask;
    private BukkitTask sweepTask;
    /** Dosya okuma sirasinda uretilen uyarilar (ornegin UTF-8 olmayan dosya). */
    private final List<String> fileWarnings = new java.util.concurrent.CopyOnWriteArrayList<String>();

    /** Kurban UUID'si -> kayitlari. Tum listeler thread-safe'tir. */
    private final Map<UUID, List<DeathRecord>> deathStorage = new ConcurrentHashMap<UUID, List<DeathRecord>>();

    // ------------------------------------------------------------------------------------
    // Yasam dongusu
    // ------------------------------------------------------------------------------------

    @Override
    public void onEnable() {
        loadFiles();

        dbExecutor = Executors.newSingleThreadExecutor(new NamedFactory("Iade-DB"));
        banExecutor = Executors.newSingleThreadExecutor(new NamedFactory("Iade-Ban"));

        database = new Database(this);
        if (!database.open()) {
            getLogger().severe("Veritabani acilamadi, eklenti kapatiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadRecords();

        banService = new BanService(this);
        menus = new Menus(this);

        CommandManager commands = new CommandManager(this);
        for (String name : COMMANDS) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) {
                getLogger().warning("Komut plugin.yml'de bulunamadi: " + name);
                continue;
            }
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(menus, this);
        pm.registerEvents(new BanListener(this), this);
        pm.registerEvents(new LoginListener(this), this);

        scheduleTasks();

        getLogger().info("Iade v" + getDescription().getVersion() + " aktif.");
        getLogger().info("Ban sistemleri: " + banService.describe());
        for (String warning : configWarnings(settings)) {
            getLogger().warning("Ayar uyar\u0131s\u0131: " + warning);
        }
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                    getLogger().warning("Bekleyen veritabani islemleri zaman asimina ugradi.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (banExecutor != null) {
            banExecutor.shutdownNow();
        }
        if (database != null) {
            database.close();
        }
    }

    /** reloadAll() sonucu. ok=false ise hicbir sey degismemistir (eski ayarlar korunur). */
    public static final class ReloadResult {
        public final boolean ok;
        public final String file;
        public final String error;
        public final List<String> warnings;

        ReloadResult(boolean ok, String file, String error, List<String> warnings) {
            this.ok = ok;
            this.file = file;
            this.error = error;
            this.warnings = warnings;
        }
    }

    /**
     * config.yml ve messages.yml dosyalarini yeniden yukler. Dosyalardan biri okunamazsa (YAML hatasi)
     * eski ayarlar aynen korunur. Basariliysa ban sistemleri, zamanlayicilar ve saklama sureleri yeni
     * ayarlara gore hemen yenilenir (suresi dolan kayitlar bir sonraki dakikada silinir).
     */
    public ReloadResult reloadAll() {
        fileWarnings.clear();
        YamlConfiguration cfg;
        YamlConfiguration msg;
        try {
            cfg = loadYamlStrict("config.yml");
        } catch (Exception e) {
            return new ReloadResult(false, "config.yml", shorten(e), new ArrayList<String>());
        }
        try {
            msg = loadYamlStrict("messages.yml");
        } catch (Exception e) {
            return new ReloadResult(false, "messages.yml", shorten(e), new ArrayList<String>());
        }

        Settings newSettings = new Settings(cfg);
        this.settings = newSettings;
        this.messages = new Messages(msg);
        if (banService != null) {
            banService.reload();
        }
        scheduleTasks();
        runDb(new Runnable() {
            @Override
            public void run() {
                cleanOldRecords();
            }
        });
        return new ReloadResult(true, null, null, configWarnings(newSettings));
    }

    private static String shorten(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            m = e.getClass().getSimpleName();
        }
        int nl = m.indexOf('\n');
        if (nl > 0) {
            m = m.substring(0, nl);
        }
        return Text.truncate(m, 140);
    }

    /** Ayar dosyasindaki, eklentinin calismasini engellemeyen ama duzeltilmesi gereken sorunlar. */
    public List<String> configWarnings(Settings s) {
        List<String> out = new ArrayList<String>(fileWarnings);
        out.addAll(s.uyarilar);
        out.addAll(getMessages().unknownKeys());
        for (String world : s.aktifDunyalar) {
            try {
                if (Bukkit.getWorld(world) == null) {
                    out.add("aktif-dunyalar: '" + world + "' ad\u0131nda y\u00fckl\u00fc bir d\u00fcnya yok");
                }
            } catch (Throwable ignored) {
                // dunya listesi okunamazsa uyari atlanir
            }
        }
        return out;
    }

    private void loadFiles() {
        fileWarnings.clear();
        this.settings = new Settings(loadYamlLenient("config.yml"));
        this.messages = new Messages(loadYamlLenient("messages.yml"));
    }

    /** Acilista: dosya bozuksa hata yazilir ve jar icindeki varsayilanlar kullanilir. */
    private YamlConfiguration loadYamlLenient(String name) {
        try {
            return loadYamlStrict(name);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, name + " okunamad\u0131, varsay\u0131lanlar kullan\u0131lacak: " + shorten(e));
            YamlConfiguration fallback = new YamlConfiguration();
            attachDefaults(fallback, name);
            return fallback;
        }
    }

    /**
     * YAML dosyasini UTF-8 olarak okur (Windows Not Defteri'nin BOM'u ve CRLF satir sonlari sorun cikarmaz);
     * eksik anahtarlar jar icindeki varsayilanlardan tamamlanir. UTF-8 olmayan dosyalar icin uyari uretilir.
     */
    private YamlConfiguration loadYamlStrict(String name) throws Exception {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        if (text.indexOf('\uFFFD') >= 0) {
            fileWarnings.add(name + " UTF-8 olarak kaydedilmemi\u015f (T\u00fcrk\u00e7e karakterler bozuk g\u00f6r\u00fcnebilir); dosyay\u0131 UTF-8 kodlamas\u0131yla kaydedin");
        }
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(text);
        attachDefaults(cfg, name);
        return cfg;
    }

    private void attachDefaults(YamlConfiguration cfg, String name) {
        InputStream in = getResource(name);
        if (in != null) {
            Reader dr = null;
            try {
                dr = new InputStreamReader(in, StandardCharsets.UTF_8);
                cfg.setDefaults(YamlConfiguration.loadConfiguration(dr));
            } finally {
                closeQuietly(dr);
            }
        }
    }

    private static void closeQuietly(Reader r) {
        if (r != null) {
            try {
                r.close();
            } catch (IOException ignored) {
                // yok say
            }
        }
    }

    private void loadRecords() {
        try {
            List<DeathRecord> all = database.loadAll();
            for (DeathRecord r : all) {
                addToStorage(r);
            }
            getLogger().info(all.size() + " olum kaydi yuklendi.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Olum kayitlari yuklenemedi", t);
        }
    }

    private void scheduleTasks() {
        cancelTasks();
        try {
            // Eski kayit temizligi: ilk calisma 1 dk sonra, sonra "temizlik-araligi-dakika" ayarinda (isi DB thread'i yapar).
            long cleanupPeriod = (long) settings.temizlikAraligiDakika * 1200L;
            cleanupTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    runDb(new Runnable() {
                        @Override
                        public void run() {
                            cleanOldRecords();
                        }
                    });
                }
            }, 1200L, cleanupPeriod);

            Settings s = settings;
            if (s.taramaAktif && banService != null) {
                long period = (long) s.taramaAraligiSaniye * 20L;
                sweepTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            banService.sweep();
                        } catch (Throwable t) {
                            getLogger().log(Level.WARNING, "Ban taramasi hatasi", t);
                        }
                    }
                }, 200L, period);
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Zamanlayicilar kurulamadi", t);
        }
    }

    private void cancelTasks() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    /** DB thread'inde calisir. Suresi dolan kayitlari bellekten ve veritabanindan siler. */
    private void cleanOldRecords() {
        Settings s = getSettings();
        long now = System.currentTimeMillis();
        List<Integer> ids = new ArrayList<Integer>();
        for (List<DeathRecord> list : deathStorage.values()) {
            List<DeathRecord> expired = new ArrayList<DeathRecord>();
            for (DeathRecord r : list) {
                if (r.expiresAt(s) < now) {
                    expired.add(r);
                }
            }
            if (!expired.isEmpty()) {
                list.removeAll(expired);
                for (DeathRecord r : expired) {
                    ids.add(r.id);
                }
            }
        }
        if (!ids.isEmpty()) {
            try {
                database.delete(ids);
                getLogger().info(ids.size() + " suresi dolmus olum kaydi silindi.");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Eski kayitlar silinemedi", t);
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Erisimciler
    // ------------------------------------------------------------------------------------

    public Settings getSettings() {
        return settings;
    }

    public Messages getMessages() {
        return messages;
    }

    public BanService getBanService() {
        return banService;
    }

    public Menus getMenus() {
        return menus;
    }

    public Map<UUID, List<DeathRecord>> getDeathStorage() {
        return deathStorage;
    }

    // ------------------------------------------------------------------------------------
    // Thread yardimcilari
    // ------------------------------------------------------------------------------------

    /** Veritabani isini tek thread'li DB kuyruguna ekler. */
    public void runDb(final Runnable task) {
        if (dbExecutor == null) {
            return;
        }
        try {
            dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        getLogger().log(Level.WARNING, "Veritabani isleminde hata", t);
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            // kapanis sirasinda
        }
    }

    /** Ban eklentisi sorgularini ayri bir arka plan thread'inde calistirir. */
    public void runBan(final Runnable task) {
        if (banExecutor == null) {
            return;
        }
        try {
            banExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        getLogger().log(Level.WARNING, "Ban sorgusunda hata", t);
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            // kapanis sirasinda
        }
    }

    /** Ana thread'de calistirir (zaten ana thread'deyse hemen). */
    public void runMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(this, task);
        } catch (Throwable ignored) {
            // eklenti kapanirken zamanlayici reddedebilir
        }
    }

    // ------------------------------------------------------------------------------------
    // Kayit deposu
    // ------------------------------------------------------------------------------------

    private void addToStorage(DeathRecord r) {
        List<DeathRecord> list = deathStorage.get(r.victimUuid);
        if (list == null) {
            List<DeathRecord> fresh = new CopyOnWriteArrayList<DeathRecord>();
            list = deathStorage.putIfAbsent(r.victimUuid, fresh);
            if (list == null) {
                list = fresh;
            }
        }
        list.add(r);
    }

    /** Yeni olumu kaydeder: envanter arka planda serilestirilip veritabanina yazilir. */
    public void recordDeath(final DeathRecord record, final ItemStack[] contents) {
        runDb(new Runnable() {
            @Override
            public void run() {
                String data = null;
                try {
                    data = ItemSerializer.toBase64(contents);
                } catch (Throwable t) {
                    getLogger().log(Level.WARNING, "Envanter serilestirilemedi (" + record.victimName + ")", t);
                }
                try {
                    record.id = database.insert(record, data);
                    addToStorage(record);
                } catch (Throwable t) {
                    getLogger().log(Level.WARNING, "Olum kaydi veritabanina yazilamadi", t);
                }
            }
        });
    }

    public boolean isTracked(DeathRecord rec) {
        List<DeathRecord> list = deathStorage.get(rec.victimUuid);
        return list != null && list.contains(rec);
    }

    /** Kaydin saklama suresi dolmus mu? (temizlik gorevi silmeden once de yok sayilir) */
    public boolean isExpired(DeathRecord rec) {
        return rec.isExpired(getSettings(), System.currentTimeMillis());
    }

    /** Kayit hala listede mi ve suresi dolmamis mi? Iade onayi/alma islemleri bunu kontrol eder. */
    public boolean isLive(DeathRecord rec) {
        return isTracked(rec) && !isExpired(rec);
    }

    public boolean hasBannedRecords(UUID victim) {
        List<DeathRecord> list = deathStorage.get(victim);
        if (list == null) {
            return false;
        }
        for (DeathRecord r : list) {
            if (r.banned && !isExpired(r)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasClaimable(UUID victim) {
        List<DeathRecord> list = deathStorage.get(victim);
        if (list == null) {
            return false;
        }
        for (DeathRecord r : list) {
            if (r.banned && r.isStatus(DeathRecord.VERILDI) && !isExpired(r)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------
    // Ban durumu islemleri
    // ------------------------------------------------------------------------------------

    private static boolean matches(DeathRecord r, String name, UUID uuid) {
        if (uuid != null && uuid.equals(r.killerUuid)) {
            return true;
        }
        return name != null && r.killerName.equalsIgnoreCase(name);
    }

    /** Katile ait, henuz banli isaretlenmemis kayitlar. */
    public List<DeathRecord> findUnbanned(UUID uuid, String name) {
        List<DeathRecord> out = new ArrayList<DeathRecord>();
        for (List<DeathRecord> list : deathStorage.values()) {
            for (DeathRecord r : list) {
                if (!r.banned && matches(r, name, uuid) && !isExpired(r)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    /** Katile ait, banli isaretli ve iadesi henuz onaylanmamis kayitlar. */
    public List<DeathRecord> findPendingBanned(UUID uuid, String name) {
        List<DeathRecord> out = new ArrayList<DeathRecord>();
        for (List<DeathRecord> list : deathStorage.values()) {
            for (DeathRecord r : list) {
                if (r.banned && r.isStatus(DeathRecord.BEKLENIYOR) && matches(r, name, uuid) && !isExpired(r)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    /**
     * Katile ait (suresi dolmamis) tum kayitlari banli olarak isaretler: sebep, banlayan, sure ve kaynak
     * kaydedilir. Her thread'den cagrilabilir.
     */
    public int markBanned(String name, UUID uuid, BanInfo info) {
        String finalReason = Text.isBlank(info.reason) ? DEFAULT_BAN_REASON : info.reason.trim();
        long now = System.currentTimeMillis();
        List<DeathRecord> changed = new ArrayList<DeathRecord>();
        for (List<DeathRecord> list : deathStorage.values()) {
            for (DeathRecord r : list) {
                if (r.banned || !matches(r, name, uuid) || r.isExpired(getSettings(), now)) {
                    continue;
                }
                r.reason = finalReason;
                r.banTimestamp = now;
                r.banSource = info.source;
                r.banBanner = info.banner.isEmpty() ? null : info.banner;
                r.banExpiry = info.expiry;
                r.banDurationText = info.durationText.isEmpty() ? null : info.durationText;
                r.banned = true;
                changed.add(r);
            }
        }
        saveBans(changed);
        return changed.size();
    }

    /**
     * Banli isareti kaldirir (sadece iadesi onaylanmamis kayitlarda).
     * onlyApiSourced: true ise yalnizca ban sistemi API'siyle dogrulanmis kayitlara dokunur.
     */
    public int markUnbanned(String name, UUID uuid, boolean onlyApiSourced) {
        List<DeathRecord> changed = new ArrayList<DeathRecord>();
        for (List<DeathRecord> list : deathStorage.values()) {
            for (DeathRecord r : list) {
                if (!r.banned || !r.isStatus(DeathRecord.BEKLENIYOR) || !matches(r, name, uuid)) {
                    continue;
                }
                if (onlyApiSourced && !r.isApiSourced()) {
                    continue;
                }
                r.banned = false;
                r.banTimestamp = 0L;
                r.banSource = null;
                r.banBanner = null;
                r.banExpiry = -1L;
                r.banDurationText = null;
                r.reason = PENDING_REASON;
                changed.add(r);
            }
        }
        saveBans(changed);
        return changed.size();
    }

    private void saveBans(final List<DeathRecord> changed) {
        if (changed.isEmpty()) {
            return;
        }
        runDb(new Runnable() {
            @Override
            public void run() {
                try {
                    database.updateBans(changed);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Ban durumu kaydedilemedi", e);
                }
            }
        });
    }

    /**
     * Bir oyuncunun (kurban) kayitlarindaki henuz banli olmayan katilleri hemen sorgular, sonra
     * "after" calistirilir (ana thread). Komut ve menu acilmadan once guncel durumu saglar.
     */
    public void refreshBansFor(UUID victim, final Runnable after) {
        List<DeathRecord> recs = deathStorage.get(victim);
        if (recs == null || recs.isEmpty() || banService == null) {
            after.run();
            return;
        }
        Map<String, BanTarget> unique = new LinkedHashMap<String, BanTarget>();
        for (DeathRecord r : recs) {
            if (r.banned || isExpired(r)) {
                continue;
            }
            String key = r.killerName.toLowerCase(Locale.ROOT);
            BanTarget existing = unique.get(key);
            if (existing == null || (existing.uuid == null && r.killerUuid != null)) {
                unique.put(key, new BanTarget(r.killerUuid, r.killerName));
            }
        }
        if (unique.isEmpty()) {
            after.run();
            return;
        }
        banService.checkAsync(unique.values(), new BanService.Callback() {
            @Override
            public void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain) {
                for (Map.Entry<BanTarget, BanInfo> e : banned.entrySet()) {
                    BanTarget t = e.getKey();
                    markBanned(t.name, t.uuid, e.getValue());
                }
                after.run();
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Iade durumu islemleri
    // ------------------------------------------------------------------------------------

    /** Yetkili iadeyi onayladi: durum "Verildi" olur. */
    public void markApproved(DeathRecord rec) {
        rec.iadeDurumu = DeathRecord.VERILDI;
        rec.iadeTimestamp = System.currentTimeMillis();
        saveIade(rec);
    }

    /** Oyuncu iadeyi aldi: durum "Alindi" olur. */
    public void markTaken(DeathRecord rec) {
        rec.iadeDurumu = DeathRecord.ALINDI;
        rec.iadeTimestamp = System.currentTimeMillis();
        saveIade(rec);
    }

    private void saveIade(final DeathRecord rec) {
        runDb(new Runnable() {
            @Override
            public void run() {
                try {
                    database.updateIade(rec);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Iade durumu kaydedilemedi", e);
                }
            }
        });
    }

    /** Envanter verisini DB thread'inde okur, sonucu ana thread'de callback'e verir (null olabilir). */
    public void loadInventoryAsync(final DeathRecord rec, final Consumer<String> callback) {
        runDb(new Runnable() {
            @Override
            public void run() {
                String data = null;
                try {
                    data = database.loadInventory(rec.id);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Envanter okunamadi (id=" + rec.id + ")", e);
                }
                final String result = data;
                runMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.accept(result);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Oyuncu ismi cozumleme / silme
    // ------------------------------------------------------------------------------------

    /**
     * Isimden kurban UUID'si bulur: once cevrimici oyuncular, sonra kayitlardaki isimler,
     * en son sunucunun bildigi cevrimdisi oyuncular (ag istegi yapmaz).
     */
    public UUID resolveVictim(String name) {
        if (name == null) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (Map.Entry<UUID, List<DeathRecord>> e : deathStorage.entrySet()) {
            for (DeathRecord r : e.getValue()) {
                if (r.victimName != null && r.victimName.equalsIgnoreCase(name)) {
                    return e.getKey();
                }
            }
        }
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                return op.getUniqueId();
            }
        }
        return null;
    }

    /**
     * Oyuncunun kayitlarini siler. Kurban olarak kaydi yoksa, o isimdeki katile ait kayitlari siler.
     * Silinen kayit sayisini dondurur.
     */
    public int deleteRecordsFor(String name) {
        List<DeathRecord> toRemove = new ArrayList<DeathRecord>();
        UUID victim = resolveVictim(name);
        if (victim != null) {
            List<DeathRecord> list = deathStorage.get(victim);
            if (list != null) {
                toRemove.addAll(list);
                list.removeAll(toRemove);
            }
        }
        if (toRemove.isEmpty()) {
            for (List<DeathRecord> list : deathStorage.values()) {
                List<DeathRecord> hit = new ArrayList<DeathRecord>();
                for (DeathRecord r : list) {
                    if (r.killerName.equalsIgnoreCase(name)
                            || (r.victimName != null && r.victimName.equalsIgnoreCase(name))) {
                        hit.add(r);
                    }
                }
                if (!hit.isEmpty()) {
                    list.removeAll(hit);
                    toRemove.addAll(hit);
                }
            }
        }
        if (toRemove.isEmpty()) {
            return 0;
        }
        final List<Integer> ids = new ArrayList<Integer>();
        for (DeathRecord r : toRemove) {
            ids.add(r.id);
        }
        runDb(new Runnable() {
            @Override
            public void run() {
                try {
                    database.delete(ids);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Kayitlar silinemedi", e);
                }
            }
        });
        return toRemove.size();
    }

    // ------------------------------------------------------------------------------------
    // Tab tamamlama icin isimler
    // ------------------------------------------------------------------------------------

    public List<String> victimNames(String prefix) {
        Set<String> names = new LinkedHashSet<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        for (List<DeathRecord> list : deathStorage.values()) {
            for (DeathRecord r : list) {
                if (r.victimName != null) {
                    names.add(r.victimName);
                }
            }
        }
        return filter(names, prefix);
    }

    public List<String> killerNames(String prefix) {
        Set<String> names = new LinkedHashSet<String>();
        for (List<DeathRecord> list : deathStorage.values()) {
            for (DeathRecord r : list) {
                names.add(r.killerName);
            }
        }
        return filter(names, prefix);
    }

    private static List<String> filter(Collection<String> names, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String n : names) {
            if (n.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(n);
                if (out.size() >= 40) {
                    break;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------

    private static final class NamedFactory implements ThreadFactory {
        private final String name;

        NamedFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        }
    }
}
