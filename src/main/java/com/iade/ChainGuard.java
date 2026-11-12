package com.iade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ard arda oldurme korumasi: ayni katil, pencere icinde ayni kurbani (veya kapsam "herkes" ise kimi olursa)
 * esik kez oldurmusse zincir supheli sayilir. Zincir iki kaynaktan hesaplanir ve yinelenenler elenir:
 * kalici kayitlar (sunucu yeniden basladiktan sonra da gecerli) ve henuz veritabanina yazilmamis /
 * "engelle" modunda kaydedilmeyen olumler icin bellekteki kisa omurlu liste.
 * Yalnizca ana thread'den (olum olayi) cagrilir.
 */
final class ChainGuard {

    /** Bellekteki bir olum: rec null ise olum "engelle" modunda kaydedilmedi (yalnizca sayilir). */
    private static final class Seen {
        final UUID victim;
        final long time;
        final DeathRecord rec;

        Seen(UUID victim, long time, DeathRecord rec) {
            this.victim = victim;
            this.time = time;
            this.rec = rec;
        }
    }

    /** Degerlendirme sonucu. */
    static final class Result {
        /** Yeni olum dahil zincir uzunlugu. */
        final int size;
        /** Zincirdeki, kayitli onceki olumler (yeni olum haric). */
        final List<DeathRecord> earlier;

        Result(int size, List<DeathRecord> earlier) {
            this.size = size;
            this.earlier = earlier;
        }
    }

    private final Iade plugin;
    private final Map<UUID, ArrayDeque<Seen>> recent = new ConcurrentHashMap<UUID, ArrayDeque<Seen>>();

    ChainGuard(Iade plugin) {
        this.plugin = plugin;
    }

    /** Yeni bir olum eklenirse zincir ne olurdu? (Henuz hicbir seyi degistirmez; yalnizca bellegi budar.) */
    Result evaluate(Settings s, UUID killer, UUID victim, long now) {
        long window = s.ardardaPencereDakika * 60000L;
        Set<DeathRecord> found = Collections.newSetFromMap(new IdentityHashMap<DeathRecord, Boolean>());
        int ghosts = 0;

        Map<UUID, List<DeathRecord>> storage = plugin.getDeathStorage();
        if (s.ardardaTumKurbanlar) {
            for (List<DeathRecord> list : storage.values()) {
                collect(list, killer, now, window, found);
            }
        } else {
            collect(storage.get(victim), killer, now, window, found);
        }

        ArrayDeque<Seen> deque = recent.get(killer);
        if (deque != null) {
            synchronized (deque) {
                prune(deque, now, window);
                for (Seen e : deque) {
                    if (!s.ardardaTumKurbanlar && !e.victim.equals(victim)) {
                        continue;
                    }
                    if (e.rec != null) {
                        found.add(e.rec);
                    } else {
                        ghosts++;
                    }
                }
            }
        }
        List<DeathRecord> earlier = new ArrayList<DeathRecord>(found);
        return new Result(earlier.size() + ghosts + 1, earlier);
    }

    /** Olumu belleke ekler (rec null: kaydedilmeyen olum). */
    void track(UUID killer, UUID victim, long now, DeathRecord rec) {
        ArrayDeque<Seen> deque = recent.get(killer);
        if (deque == null) {
            ArrayDeque<Seen> created = new ArrayDeque<Seen>();
            deque = recent.putIfAbsent(killer, created);
            if (deque == null) {
                deque = created;
            }
        }
        long window = plugin.getSettings().ardardaPencereDakika * 60000L;
        synchronized (deque) {
            deque.addLast(new Seen(victim, now, rec));
            prune(deque, now, window);
        }
        sweep(now, window);
    }

    /** Suresi dolan butun girisleri temizler (bellek buyumesin). */
    private void sweep(long now, long window) {
        if (recent.size() < 64) {
            return;
        }
        for (Iterator<Map.Entry<UUID, ArrayDeque<Seen>>> it = recent.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ArrayDeque<Seen>> e = it.next();
            ArrayDeque<Seen> d = e.getValue();
            synchronized (d) {
                prune(d, now, window);
                if (d.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    private static void prune(ArrayDeque<Seen> deque, long now, long window) {
        while (!deque.isEmpty() && now - deque.peekFirst().time > window) {
            deque.removeFirst();
        }
    }

    private static void collect(List<DeathRecord> list, UUID killer, long now, long window, Set<DeathRecord> out) {
        if (list == null) {
            return;
        }
        for (DeathRecord r : list) {
            if (r.killerUuid != null && r.killerUuid.equals(killer) && now - r.timestamp <= window) {
                out.add(r);
            }
        }
    }

    /** Bellegi tamamen bosaltir (eklenti kapanirken / testlerde). */
    void clear() {
        recent.clear();
    }
}
