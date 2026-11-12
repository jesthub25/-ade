package com.iade.ban;

import com.iade.DeathRecord;
import com.iade.DurationParser;
import com.iade.Iade;
import com.iade.Settings;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Katmanli ban ve unban kontrolu:
 *  1) Sunucu ban listesi (ana thread): Vanilla ve EssentialsX (/ban, /tempban), CMI, cogu anti-cheat
 *  2) Kurulu ban eklentilerinin API'si: LiteBans, AdvancedBan, BanManager (arka plan thread'i)
 *  3) Ban / unban komutlarini dinleme (BanListener) + dogrulama
 *  4) Giris engeli dinleme (LoginListener): LibertyBans, CMI, MaxBans... gibi API'si olmayan sistemler dahil
 *     herhangi bir ban eklentisinin KICK_BANNED sonucunu yakalar
 *  5) Periyodik tarama (sweep): anti-cheat'lerin gizli komutla verdigi banlari yakalar,
 *     erken geri alinan banlari (unban) tespit eder
 *  6) Oyuncu /olumliste yazdiginda veya yetkili /iadever yazdiginda anlik kontrol
 */
public final class BanService {

    public interface Callback {
        /**
         * Ana thread'de cagrilir.
         * @param banned    banli bulunan hedefler ve bilgisi
         * @param uncertain bir sistem hata verdigi/hazir olmadigi icin durumu belirlenemeyen hedefler
         */
        void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain);
    }

    private static final int SWEEP_BATCH = 150;
    private static final long HOUR = 3600000L;

    private final Iade plugin;
    /** Ana thread'de sorgulananlar: Vanilla, EssentialsX. */
    private volatile List<BanProvider> mainProviders = Collections.emptyList();
    /** Arka plan thread'inde sorgulananlar: LiteBans, AdvancedBan, BanManager. */
    private volatile List<BanProvider> asyncProviders = Collections.emptyList();
    private int banSweepOffset;
    private int unbanSweepOffset;

    public BanService(Iade plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Ayarlara gore saglayicilari yeniden kurar. */
    public void reload() {
        Settings s = plugin.getSettings();
        List<BanProvider> main = new ArrayList<BanProvider>();
        if (s.banVanilla) {
            main.add(new VanillaBanProvider());
        }
        if (s.banEssentialsX) {
            main.add(new EssentialsBanProvider());
        }
        List<BanProvider> async = new ArrayList<BanProvider>();
        if (s.banLiteBans) {
            async.add(new LiteBansProvider(plugin));
        }
        if (s.banAdvancedBan) {
            async.add(new AdvancedBanProvider(plugin));
        }
        if (s.banBanManager) {
            async.add(new BanManagerProvider(plugin));
        }
        mainProviders = Collections.unmodifiableList(main);
        asyncProviders = Collections.unmodifiableList(async);
    }

    /** Aktif ban sistemlerinin okunabilir listesi. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (BanProvider p : mainProviders) {
            append(sb, p);
        }
        for (BanProvider p : asyncProviders) {
            append(sb, p);
        }
        return sb.length() == 0 ? "yok" : sb.toString();
    }

    private static void append(StringBuilder sb, BanProvider p) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(p.getName());
        if (!p.getPluginName().isEmpty()) {
            sb.append(p.isActive() ? " (aktif)" : " (kurulu degil)");
        }
    }

    // ------------------------------------------------------------------------------------
    // Cekirdek sorgu
    // ------------------------------------------------------------------------------------

    /** Hedeflerin ban durumunu sorgular. Her thread'den cagrilabilir; sonuc ana thread'de dondurulur. */
    public void checkAsync(final Collection<BanTarget> targets, final Callback cb) {
        final List<BanTarget> list = new ArrayList<BanTarget>(targets);
        plugin.runMain(new Runnable() {
            @Override
            public void run() {
                mainStep(list, cb);
            }
        });
    }

    private void mainStep(List<BanTarget> targets, final Callback cb) {
        final Map<BanTarget, BanInfo> result = new LinkedHashMap<BanTarget, BanInfo>();
        final Set<BanTarget> uncertain = new HashSet<BanTarget>();
        final List<BanTarget> remaining = new ArrayList<BanTarget>();
        List<BanProvider> mains = mainProviders;

        for (BanTarget t : targets) {
            BanInfo info = null;
            boolean unknown = false;
            for (BanProvider p : mains) {
                try {
                    info = p.query(t);
                    if (info != null) {
                        break;
                    }
                } catch (Throwable ex) {
                    unknown = true;
                    plugin.getLogger().log(Level.FINE, p.getName() + " ban sorgusu belirsiz", ex);
                }
            }
            if (info != null) {
                result.put(t, info);
                continue;
            }
            if (unknown) {
                uncertain.add(t);
            }
            remaining.add(t);
        }

        final List<BanProvider> async = asyncProviders;
        if (remaining.isEmpty() || async.isEmpty()) {
            finish(cb, result, uncertain);
            return;
        }

        plugin.runBan(new Runnable() {
            @Override
            public void run() {
                for (BanTarget t : remaining) {
                    BanInfo found = null;
                    boolean unknown = false;
                    for (BanProvider p : async) {
                        try {
                            BanInfo info = p.query(t);
                            if (info != null) {
                                found = info;
                                break;
                            }
                        } catch (Throwable ex) {
                            unknown = true;
                            plugin.getLogger().log(Level.FINE, p.getName() + " ban sorgusu belirsiz", ex);
                        }
                    }
                    if (found != null) {
                        result.put(t, found);
                        uncertain.remove(t);
                    } else if (unknown) {
                        uncertain.add(t);
                    }
                }
                plugin.runMain(new Runnable() {
                    @Override
                    public void run() {
                        finish(cb, result, uncertain);
                    }
                });
            }
        });
    }

    private void finish(Callback cb, Map<BanTarget, BanInfo> result, Set<BanTarget> uncertain) {
        try {
            cb.done(result, uncertain);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Ban kontrolu sonucu islenirken hata", t);
        }
    }

    // ------------------------------------------------------------------------------------
    // Ban komutu / unban komutu
    // ------------------------------------------------------------------------------------

    /**
     * Yetkili bir ban komutu calistirdiktan sonra (2 sn sonra) sonucu dogrular ve kayitlari isaretler.
     * executor: komutu yazan (konsol dahil); temp/durationToken: gecici ban ve komuttaki sure yazisi.
     */
    public void onBanCommand(final String targetName, final String cmdReason, final String owner,
                             final String executor, final boolean temp, final String durationToken) {
        long ms = temp ? DurationParser.parseMillis(durationToken) : 0L;
        final long cmdExpiry = temp ? (ms > 0 ? System.currentTimeMillis() + ms : -1L) : 0L;
        final String durationText = temp && durationToken != null ? durationToken : "";
        later(new Runnable() {
            @Override
            public void run() {
                verifyBanCommand(targetName, cmdReason, owner, executor, cmdExpiry, durationText);
            }
        });
    }

    private void verifyBanCommand(String targetName, final String cmdReason, final String owner,
                                  final String executor, final long cmdExpiry, final String durationText) {
        List<DeathRecord> related = plugin.findUnbanned(null, targetName);
        if (related.isEmpty()) {
            return;
        }
        final BanTarget target = targetOf(related);
        checkAsync(Collections.singletonList(target), new Callback() {
            @Override
            public void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain) {
                BanInfo info = banned.get(target);
                if (info != null) {
                    // API sonucunda olmayan alanlar (sebep, banlayan, sure) komuttan tamamlanir.
                    plugin.markBanned(target.name, target.uuid,
                            info.withFallback(cmdReason, executor, cmdExpiry, durationText));
                } else if (plugin.getSettings().komutGuveni && !isVerifiable(owner)) {
                    // API ile dogrulanamayan sistem (ornegin LibertyBans): yetkilinin komutuna guvenilir.
                    plugin.markBanned(target.name, target.uuid,
                            new BanInfo(cmdReason, DeathRecord.KAYNAK_KOMUT, executor, cmdExpiry, durationText));
                }
                // Dogrulanabilir sistemde henuz banli gorunmuyorsa periyodik tarama tekrar bakar.
            }
        });
    }

    /** Yetkili bir unban komutu calistirdiktan sonra (2 sn sonra) durumu dogrular, isareti kaldirir. */
    public void onUnbanCommand(final String targetName, final String owner) {
        if (!plugin.getSettings().unbanIsaretiKaldir) {
            return;
        }
        later(new Runnable() {
            @Override
            public void run() {
                verifyUnbanCommand(targetName, owner);
            }
        });
    }

    private void verifyUnbanCommand(final String targetName, final String owner) {
        List<DeathRecord> pending = plugin.findPendingBanned(null, targetName);
        if (pending.isEmpty()) {
            return;
        }
        final BanTarget target = targetOf(pending);
        checkAsync(Collections.singletonList(target), new Callback() {
            @Override
            public void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain) {
                boolean stillBanned = banned.containsKey(target);
                if (stillBanned) {
                    return; // baska bir sistemde hala banli
                }
                if (isVerifiable(owner)) {
                    if (!uncertain.contains(target)) {
                        plugin.markUnbanned(target.name, target.uuid, false);
                    }
                } else if (plugin.getSettings().komutGuveni) {
                    plugin.markUnbanned(target.name, target.uuid, false);
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Giris engeli
    // ------------------------------------------------------------------------------------

    /**
     * Takip edilen bir katil sunucuya girerken "banli" sonucu aldi (herhangi bir ban eklentisi).
     * Her thread'den cagrilabilir.
     */
    public void onLoginBanned(final UUID uuid, final String name) {
        if (name == null) {
            return;
        }
        List<DeathRecord> related = plugin.findUnbanned(uuid, name);
        if (related.isEmpty()) {
            return;
        }
        UUID known = uuid;
        if (known == null) {
            known = targetOf(related).uuid;
        }
        final BanTarget target = new BanTarget(known, related.get(0).killerName);
        checkAsync(Collections.singletonList(target), new Callback() {
            @Override
            public void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain) {
                BanInfo info = banned.get(target);
                if (info != null) {
                    plugin.markBanned(target.name, target.uuid, info);
                } else {
                    plugin.markBanned(target.name, target.uuid, new BanInfo(null, DeathRecord.KAYNAK_GIRIS));
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Periyodik tarama
    // ------------------------------------------------------------------------------------

    /** Ana thread'de cagrilir: ban taramasi + (acik ise) unban taramasi. */
    public void sweep() {
        sweepBans();
        if (plugin.getSettings().unbanTaramasi) {
            sweepUnbans();
        }
    }

    private void sweepBans() {
        Map<String, BanTarget> unique = new LinkedHashMap<String, BanTarget>();
        for (List<DeathRecord> list : plugin.getDeathStorage().values()) {
            for (DeathRecord r : list) {
                if (!r.banned && !plugin.isExpired(r)) {
                    collect(unique, r);
                }
            }
        }
        final List<BanTarget> batch = nextBatch(unique, true);
        if (batch.isEmpty()) {
            return;
        }
        checkAsync(batch, new Callback() {
            @Override
            public void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain) {
                for (Map.Entry<BanTarget, BanInfo> e : banned.entrySet()) {
                    BanTarget t = e.getKey();
                    plugin.markBanned(t.name, t.uuid, e.getValue());
                }
            }
        });
    }

    /**
     * Banin API ile dogrulandigi ve iadesi henuz onaylanmamis kayitlar icin, ban kisa sure icinde
     * kaldirildiysa (yanlis ban duzeltmesi) isareti geri alir. Belirsiz sonuclar asla geri alma sebebi degildir.
     */
    private void sweepUnbans() {
        Settings s = plugin.getSettings();
        long now = System.currentTimeMillis();
        long window = s.unbanPenceresiSaat * HOUR;
        Map<String, BanTarget> unique = new LinkedHashMap<String, BanTarget>();
        for (List<DeathRecord> list : plugin.getDeathStorage().values()) {
            for (DeathRecord r : list) {
                if (r.banned && r.isStatus(DeathRecord.BEKLENIYOR) && r.isApiSourced()
                        && r.banTimestamp > 0 && now - r.banTimestamp <= window
                        && !plugin.isExpired(r) && isSourceActive(r.banSource)) {
                    collect(unique, r);
                }
            }
        }
        final List<BanTarget> batch = nextBatch(unique, false);
        if (batch.isEmpty()) {
            return;
        }
        checkAsync(batch, new Callback() {
            @Override
            public void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain) {
                for (BanTarget t : batch) {
                    if (!banned.containsKey(t) && !uncertain.contains(t)) {
                        plugin.markUnbanned(t.name, t.uuid, true);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Elle yenileme (menudeki "Yenile" dugmesi, /olumliste liste acilisi, kafaya tiklama)
    // ------------------------------------------------------------------------------------

    private static final int REFRESH_LIMIT = 300;

    /**
     * Listedeki tum katillerin ban durumunu hemen yeniden kontrol eder: banli olanlari isaretler, API ile
     * dogrulanmis banlari kaldirilmissa geri alir (pencere siniri yok). Bitince "after" ana thread'de calisir.
     */
    public void refreshAll(final Runnable after) {
        Map<String, BanTarget> unique = new LinkedHashMap<String, BanTarget>();
        Set<String> unbanKeys = new HashSet<String>();
        for (List<DeathRecord> list : plugin.getDeathStorage().values()) {
            for (DeathRecord r : list) {
                if (plugin.isExpired(r)) {
                    continue;
                }
                if (!r.banned) {
                    collect(unique, r);
                } else if (r.isStatus(DeathRecord.BEKLENIYOR) && r.isApiSourced() && isSourceActive(r.banSource)) {
                    collect(unique, r);
                    unbanKeys.add(r.killerName.toLowerCase(Locale.ROOT));
                }
            }
        }
        evaluate(limit(unique.values(), REFRESH_LIMIT), unbanKeys, after);
    }

    /** Tek bir katilin ban durumunu hemen yeniden kontrol eder (kafaya tiklayinca). */
    public void recheck(final BanTarget target, final Runnable after) {
        Set<String> unbanKeys = new HashSet<String>();
        for (DeathRecord r : plugin.findPendingBanned(target.uuid, target.name)) {
            if (r.isApiSourced() && isSourceActive(r.banSource)) {
                unbanKeys.add(target.key());
            }
        }
        evaluate(Collections.singletonList(target), unbanKeys, after);
    }

    private void evaluate(final List<BanTarget> batch, final Set<String> unbanKeys, final Runnable after) {
        if (batch.isEmpty()) {
            after.run();
            return;
        }
        checkAsync(batch, new Callback() {
            @Override
            public void done(Map<BanTarget, BanInfo> banned, Set<BanTarget> uncertain) {
                try {
                    for (BanTarget t : batch) {
                        BanInfo info = banned.get(t);
                        if (info != null) {
                            plugin.markBanned(t.name, t.uuid, info);
                        } else if (unbanKeys.contains(t.key()) && !uncertain.contains(t)) {
                            plugin.markUnbanned(t.name, t.uuid, true);
                        }
                    }
                } finally {
                    after.run();
                }
            }
        });
    }

    private static List<BanTarget> limit(Collection<BanTarget> all, int max) {
        List<BanTarget> out = new ArrayList<BanTarget>();
        for (BanTarget t : all) {
            if (out.size() >= max) {
                break;
            }
            out.add(t);
        }
        return out;
    }

    private static void collect(Map<String, BanTarget> unique, DeathRecord r) {
        String key = r.killerName.toLowerCase(Locale.ROOT);
        BanTarget existing = unique.get(key);
        if (existing == null || (existing.uuid == null && r.killerUuid != null)) {
            unique.put(key, new BanTarget(r.killerUuid, r.killerName));
        }
    }

    private List<BanTarget> nextBatch(Map<String, BanTarget> unique, boolean banSweep) {
        if (unique.isEmpty()) {
            return Collections.emptyList();
        }
        List<BanTarget> all = new ArrayList<BanTarget>(unique.values());
        int offset = banSweep ? banSweepOffset : unbanSweepOffset;
        if (offset >= all.size()) {
            offset = 0;
        }
        int end = Math.min(all.size(), offset + SWEEP_BATCH);
        List<BanTarget> batch = new ArrayList<BanTarget>(all.subList(offset, end));
        int next = end >= all.size() ? 0 : end;
        if (banSweep) {
            banSweepOffset = next;
        } else {
            unbanSweepOffset = next;
        }
        return batch;
    }

    // ------------------------------------------------------------------------------------

    /** Komutu sahiplenen eklentinin ban durumu API'miz ile dogrulanabiliyor mu? */
    private boolean isVerifiable(String owner) {
        if (owner == null) {
            return hasMainProvider(); // yerlesik /ban komutu sunucu ban listesine yazar
        }
        String o = owner.toLowerCase(Locale.ROOT);
        if (o.startsWith("essentials")) {
            return hasMainProvider(); // EssentialsX de sunucu ban listesini kullanir
        }
        for (BanProvider p : asyncProviders) {
            if (p.getPluginName().equalsIgnoreCase(owner) && p.isActive()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMainProvider() {
        return !mainProviders.isEmpty();
    }

    /** Banin kaynagi olan ban sistemi hala sorgulanabilir durumda mi? (kapali/kaldirilmis ise unban taramasi yapilmaz) */
    private boolean isSourceActive(String source) {
        if (source == null) {
            return false;
        }
        for (BanProvider p : mainProviders) {
            if (p.getName().equalsIgnoreCase(source) && p.isActive()) {
                return true;
            }
        }
        for (BanProvider p : asyncProviders) {
            if (p.getName().equalsIgnoreCase(source) && p.isActive()) {
                return true;
            }
        }
        return false;
    }

    private void later(Runnable r) {
        try {
            Bukkit.getScheduler().runTaskLater(plugin, r, 40L);
        } catch (Throwable ignored) {
            // eklenti kapanirken zamanlayici reddedebilir
        }
    }

    private static BanTarget targetOf(List<DeathRecord> records) {
        UUID uuid = null;
        for (DeathRecord r : records) {
            if (r.killerUuid != null) {
                uuid = r.killerUuid;
                break;
            }
        }
        return new BanTarget(uuid, records.get(0).killerName);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b;
    }
}
