package com.iade;

import com.iade.ban.BanTarget;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Butun menuler. Menuler baslik yerine InventoryHolder sinifiyla taninir; tiklama ve surukleme
 * her zaman iptal edilir (menuden esya cikarilamaz / menuye esya konamaz).
 * Tiklama islemleri ana thread'de, veritabani okumalari arka planda yapilir.
 */
public final class Menus implements Listener {

    enum Type {
        DEATH_LIST, CLAIM, ADMIN_LIST, ADMIN_DETAIL, STAFF_LIST
    }

    static final class MenuHolder implements InventoryHolder {
        final Type type;
        final UUID owner;
        final UUID victim;
        final List<DeathRecord> records;
        final DeathRecord record;
        final int page;
        final List<KillerEntry> entries;
        final ListFilter filter;
        Inventory inventory;

        MenuHolder(Type type, UUID owner, UUID victim, List<DeathRecord> records, DeathRecord record, int page,
                   List<KillerEntry> entries, ListFilter filter) {
            this.type = type;
            this.owner = owner;
            this.victim = victim;
            this.records = records;
            this.record = record;
            this.page = page;
            this.entries = entries;
            this.filter = filter;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // --- Oyuncu / iade menuleri (kenarlikli, 28 kayit) ---
    private static final int PAGE_SIZE = 28;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] SLOT_INDEX = new int[54];

    static {
        Arrays.fill(SLOT_INDEX, -1);
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            SLOT_INDEX[CONTENT_SLOTS[i]] = i;
        }
    }

    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_APPROVE = 45;
    private static final int SLOT_TELEPORT = 49;
    private static final int SLOT_DETAIL_CLOSE = 53;

    // --- Yetkili menusu: ust 5 satir (45 slot) kafa, alt satir dugmeler ---
    private static final int STAFF_PAGE_SIZE = 45;
    private static final int STAFF_PREV = 45;
    private static final int STAFF_FILTER = 48;
    private static final int STAFF_CLOSE = 49;
    private static final int STAFF_REFRESH = 50;
    private static final int STAFF_NEXT = 53;

    private final Iade plugin;
    /** Oyuncunun su an acik olan yetkili menusu (arka plan kontrolu bitince yeniden acilsin mi diye). */
    private final Map<UUID, MenuHolder> staffOpen = new ConcurrentHashMap<UUID, MenuHolder>();
    /** Ban kontrolu suren yetkililer (cift tiklamayi engeller). */
    private final Set<UUID> checking = ConcurrentHashMap.newKeySet();

    public Menus(Iade plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------------------
    // Acma: oyuncu / iade menuleri
    // ------------------------------------------------------------------------------------

    public void openDeathList(Player player, int page) {
        openList(player, Type.DEATH_LIST, player.getUniqueId(), page);
    }

    public void openClaim(Player player, int page) {
        openList(player, Type.CLAIM, player.getUniqueId(), page);
    }

    public void openAdminList(Player admin, UUID victim, int page) {
        openList(admin, Type.ADMIN_LIST, victim, page);
    }

    private void openList(Player viewer, Type type, UUID victim, int requestedPage) {
        Settings s = plugin.getSettings();
        Messages m = plugin.getMessages();

        List<DeathRecord> list = collect(type, victim);
        int total = totalPages(list.size(), PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, total - 1));

        String title;
        if (type == Type.CLAIM) {
            title = m.get("iade-al-gui-baslik");
        } else if (type == Type.ADMIN_LIST) {
            title = m.get("iadever-list-gui-baslik", "%oyuncu%",
                    victimName(victim, list.isEmpty() ? null : list.get(0)));
        } else {
            title = Text.color(s.guiBaslik);
        }

        MenuHolder holder = new MenuHolder(type, viewer.getUniqueId(), victim, list, null, page, null, null);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.title(title));
        holder.inventory = inv;

        ItemStack glass = glass();
        for (int i = 0; i < 54; i++) {
            if (SLOT_INDEX[i] < 0) {
                inv.setItem(i, glass);
            }
        }

        if (list.isEmpty()) {
            inv.setItem(31, simple(Material.BARRIER, m.get("kayit-yok-baslik"), m.list("kayit-yok-lore")));
        } else {
            int from = page * PAGE_SIZE;
            int to = Math.min(list.size(), from + PAGE_SIZE);
            for (int i = from; i < to; i++) {
                inv.setItem(CONTENT_SLOTS[i - from], buildEntry(type, list.get(i), victim));
            }
        }

        inv.setItem(SLOT_CLOSE, simple(Material.BARRIER, m.get("kapat-buton"), m.list("kapat-lore")));
        String pageStr = String.valueOf(page + 1);
        String totalStr = String.valueOf(total);
        if (page > 0) {
            inv.setItem(SLOT_PREV, simple(Material.ARROW, m.get("onceki-sayfa"),
                    m.list("sayfa-lore", "%sayfa%", pageStr, "%toplam%", totalStr)));
        }
        if (page < total - 1) {
            inv.setItem(SLOT_NEXT, simple(Material.ARROW, m.get("sonraki-sayfa"),
                    m.list("sayfa-lore", "%sayfa%", pageStr, "%toplam%", totalStr)));
        }

        viewer.openInventory(inv);
    }

    private void openDetail(final Player admin, final UUID victim, final DeathRecord rec) {
        plugin.loadInventoryAsync(rec, new Consumer<String>() {
            @Override
            public void accept(String data) {
                if (admin.isOnline()) {
                    openDetailMenu(admin, victim, rec, data);
                }
            }
        });
    }

    private void openDetailMenu(Player admin, UUID victim, DeathRecord rec, String data) {
        Messages m = plugin.getMessages();
        MenuHolder holder = new MenuHolder(Type.ADMIN_DETAIL, admin.getUniqueId(), victim, null, rec, 0, null, null);
        String title = m.get("iade-detay-gui-baslik", "%oyuncu%", victimName(victim, rec));
        Inventory inv = Bukkit.createInventory(holder, 54, Text.title(title));
        holder.inventory = inv;

        // Kayitli envanter: 0-35 canta, 36-39 zirh, 40 sol el
        try {
            ItemStack[] items = ItemSerializer.fromBase64(data);
            for (int i = 0; i < items.length && i <= 40; i++) {
                if (items[i] != null) {
                    inv.setItem(i, items[i]);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Kayitli envanter okunamadi (id=" + rec.id + ")", t);
        }

        ItemStack glass = glass();
        for (int i = 41; i < 54; i++) {
            inv.setItem(i, glass);
        }
        inv.setItem(SLOT_APPROVE, simple(Material.SHULKER_BOX, m.get("shulker-ver-buton"), m.list("shulker-ver-lore")));
        if (rec.dunya != null) {
            inv.setItem(SLOT_TELEPORT, simple(Material.COMPASS, m.get("isinlan-buton"),
                    m.list("isinlan-lore", "%dunya%", rec.dunya,
                            "%x%", String.valueOf((int) rec.x),
                            "%y%", String.valueOf((int) rec.y),
                            "%z%", String.valueOf((int) rec.z))));
        } else {
            inv.setItem(SLOT_TELEPORT, simple(Material.BARRIER, m.get("konum-kaydi-yok"), new ArrayList<String>()));
        }
        inv.setItem(SLOT_DETAIL_CLOSE, simple(Material.BARRIER, m.get("kapat-buton"), m.list("kapat-lore")));

        admin.openInventory(inv);
    }

    // ------------------------------------------------------------------------------------
    // Acma: yetkili menusu (/olumliste liste)
    // ------------------------------------------------------------------------------------

    /**
     * Listedeki tum katilleri oyuncu kafasiyla gosterir. Ilk slotlar en yakin ban yiyen oyunculardir;
     * sonra henuz banli isaretlenmemis takip edilen katiller gelir. Kafanin uzerine gelince ban sebebi,
     * banlayan, sure ve silinme zamani gorulur.
     */
    public void openStaffList(Player viewer, ListFilter filter, int requestedPage) {
        Settings s = plugin.getSettings();
        Messages m = plugin.getMessages();

        List<KillerEntry> entries = KillerEntry.build(plugin, filter, s.listeYenidenEskiye);
        int total = totalPages(entries.size(), STAFF_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, total - 1));
        String pageStr = String.valueOf(page + 1);
        String totalStr = String.valueOf(total);

        String title = Text.title(Text.replace(Text.color(s.listeBaslik),
                "%sayfa%", pageStr, "%toplam%", totalStr,
                "%filtre%", filterName(filter), "%adet%", String.valueOf(entries.size())));

        MenuHolder holder = new MenuHolder(Type.STAFF_LIST, viewer.getUniqueId(), null, null, null, page, entries, filter);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inv;

        // Slot 0'dan baslayarak: en yakin ban en solda / en ustte
        int from = page * STAFF_PAGE_SIZE;
        int to = Math.min(entries.size(), from + STAFF_PAGE_SIZE);
        for (int i = from; i < to; i++) {
            inv.setItem(i - from, buildStaffHead(entries.get(i)));
        }
        if (entries.isEmpty()) {
            inv.setItem(22, simple(Material.BARRIER, m.get("liste-bos-baslik"), m.list("liste-bos-lore")));
        }

        ItemStack glass = glass();
        for (int i = STAFF_PAGE_SIZE; i < 54; i++) {
            inv.setItem(i, glass);
        }
        if (page > 0) {
            inv.setItem(STAFF_PREV, simple(Material.ARROW, m.get("onceki-sayfa"),
                    m.list("sayfa-lore", "%sayfa%", pageStr, "%toplam%", totalStr)));
        }
        if (page < total - 1) {
            inv.setItem(STAFF_NEXT, simple(Material.ARROW, m.get("sonraki-sayfa"),
                    m.list("sayfa-lore", "%sayfa%", pageStr, "%toplam%", totalStr)));
        }
        inv.setItem(STAFF_FILTER, simple(Material.HOPPER, m.get("filtre-buton", "%filtre%", filterName(filter)),
                m.list("filtre-lore", "%adet%", String.valueOf(entries.size()),
                        "%sonraki%", filterName(filter.next()))));
        inv.setItem(STAFF_CLOSE, simple(Material.BARRIER, m.get("kapat-buton"), m.list("kapat-lore")));
        inv.setItem(STAFF_REFRESH, simple(Material.CLOCK, m.get("yenile-buton"), m.list("yenile-lore")));

        viewer.openInventory(inv);
        staffOpen.put(viewer.getUniqueId(), holder);
    }

    private ItemStack buildStaffHead(KillerEntry e) {
        Settings s = plugin.getSettings();
        Messages m = plugin.getMessages();
        long now = System.currentTimeMillis();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta im = head.getItemMeta();
        if (!(im instanceof SkullMeta)) {
            return head;
        }
        SkullMeta sm = (SkullMeta) im;
        if (e.uuid != null) {
            try {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(e.uuid));
            } catch (Throwable ignored) {
                // kafa dokusu ayarlanamazsa duz kafa gosterilir
            }
        }

        String unknown = m.get("bilinmiyor");
        sm.setDisplayName(m.get(e.banned ? "liste-kafa-baslik-banli" : "liste-kafa-baslik-banlisiz",
                "%oyuncu%", e.name));
        String[] values = {
                "%oyuncu%", e.name,
                "%sebep%", orElse(Text.truncate(e.reason, 90), unknown),
                "%banlayan%", orElse(e.banner, unknown),
                "%sure%", durationText(e, now),
                "%ban_tarihi%", e.banned ? s.formatShort(e.banTime) : unknown,
                "%kaynak%", sourceName(e.source),
                "%olum_sayisi%", String.valueOf(e.kills),
                "%kurbanlar%", victimsText(e),
                "%son_olum%", s.formatShort(e.lastDeath),
                "%bekleyen%", String.valueOf(e.pending),
                "%onayli%", String.valueOf(e.approved),
                "%alinan%", String.valueOf(e.taken),
                "%silinme%", s.formatShort(e.removeAt),
                "%silinme_kalan%", remaining(e.removeAt - now)
        };
        sm.setLore(m.list(e.banned ? "liste-kafa-lore-banli" : "liste-kafa-lore-banlisiz", values));
        head.setItemMeta(sm);
        return head;
    }

    private String filterName(ListFilter filter) {
        Messages m = plugin.getMessages();
        switch (filter) {
            case BANLI:
                return m.get("filtre-banli");
            case BANLISIZ:
                return m.get("filtre-banlisiz");
            default:
                return m.get("filtre-tumu");
        }
    }

    private String durationText(KillerEntry e, long now) {
        Messages m = plugin.getMessages();
        Settings s = plugin.getSettings();
        if (!e.banned) {
            return m.get("bilinmiyor");
        }
        if (e.banExpiry == 0L) {
            return m.get("ban-sure-kalici");
        }
        if (e.banExpiry > 0L) {
            String until = s.formatShort(e.banExpiry);
            if (e.banExpiry > now) {
                return m.get("ban-sure-gecici", "%bitis%", until, "%kalan%", remaining(e.banExpiry - now));
            }
            return m.get("ban-sure-doldu", "%bitis%", until);
        }
        if (!Text.isBlank(e.durationText)) {
            return m.get("ban-sure-metin", "%sure%", e.durationText);
        }
        return m.get("ban-sure-bilinmiyor");
    }

    /** "2g 5s", "3s 20dk", "12dk" bicimi; birimler messages.yml'den gelir. */
    String remaining(long millis) {
        Messages m = plugin.getMessages();
        return Text.duration(millis, m.get("birim-gun"), m.get("birim-saat"), m.get("birim-dakika"),
                m.get("kalan-az"));
    }

    private String sourceName(String source) {
        Messages m = plugin.getMessages();
        if (Text.isBlank(source)) {
            return m.get("bilinmiyor");
        }
        if (DeathRecord.KAYNAK_KOMUT.equals(source)) {
            return m.get("kaynak-komut");
        }
        if (DeathRecord.KAYNAK_GIRIS.equals(source)) {
            return m.get("kaynak-giris");
        }
        if (DeathRecord.KAYNAK_MANUEL.equals(source)) {
            return m.get("kaynak-manuel");
        }
        return source;
    }

    private String victimsText(KillerEntry e) {
        int limit = plugin.getSettings().listeKurbanSiniri;
        if (limit <= 0 || e.victims.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(limit, e.victims.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(e.victims.get(i));
        }
        int rest = e.victims.size() - shown;
        if (rest > 0) {
            sb.append(' ').append(plugin.getMessages().get("kurban-fazla", "%adet%", String.valueOf(rest)));
        }
        return sb.toString();
    }

    private static String orElse(String value, String fallback) {
        return Text.isBlank(value) ? fallback : value;
    }

    // ------------------------------------------------------------------------------------
    // Liste hazirlama
    // ------------------------------------------------------------------------------------

    private List<DeathRecord> collect(Type type, UUID victim) {
        List<DeathRecord> out = new ArrayList<DeathRecord>();
        List<DeathRecord> src = plugin.getDeathStorage().get(victim);
        if (src != null) {
            for (DeathRecord r : src) {
                if (!r.banned || plugin.isExpired(r)) {
                    continue;
                }
                if (type == Type.CLAIM && !r.isStatus(DeathRecord.VERILDI)) {
                    continue;
                }
                out.add(r);
            }
        }
        Collections.sort(out, new Comparator<DeathRecord>() {
            @Override
            public int compare(DeathRecord a, DeathRecord b) {
                return Long.compare(b.timestamp, a.timestamp);
            }
        });
        return out;
    }

    private static int totalPages(int size, int perPage) {
        return Math.max(1, (size + perPage - 1) / perPage);
    }

    private ItemStack buildEntry(Type type, DeathRecord r, UUID victim) {
        Settings s = plugin.getSettings();
        Messages m = plugin.getMessages();
        String date = s.formatShort(r.timestamp);

        if (type == Type.CLAIM) {
            return simple(Material.PURPLE_SHULKER_BOX, m.get("iade-shulker-adi", "%tarih%", date),
                    m.list("claim-item-lore", "%katil%", r.killerName, "%sebep%", r.reason));
        }

        if (type == Type.ADMIN_LIST) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta im = head.getItemMeta();
            if (im instanceof SkullMeta) {
                SkullMeta sm = (SkullMeta) im;
                try {
                    sm.setOwningPlayer(Bukkit.getOfflinePlayer(victim));
                } catch (Throwable ignored) {
                    // kafa dokusu ayarlanamazsa duz kafa gosterilir
                }
                sm.setDisplayName(m.get("admin-kafa-baslik", "%tarih%", date));
                sm.setLore(m.list("admin-kafa-lore", "%katil%", r.killerName, "%sebep%", r.reason,
                        "%iade%", statusText(r)));
                head.setItemMeta(sm);
            }
            return head;
        }

        // DEATH_LIST: kagit
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.replace(Text.color(s.kagitAdi), "%katil%", r.killerName));
            List<String> lore = new ArrayList<String>();
            for (String line : s.loreMesajlari) {
                lore.add(Text.replace(Text.color(line), "%sebep%", r.reason, "%tarih%", date,
                        "%iade%", statusText(r), "%katil%", r.killerName));
            }
            meta.setLore(lore);
            paper.setItemMeta(meta);
        }
        return paper;
    }

    private String statusText(DeathRecord r) {
        Messages m = plugin.getMessages();
        if (r.isStatus(DeathRecord.VERILDI)) {
            return m.get("iade-verildi-text");
        }
        if (r.isStatus(DeathRecord.ALINDI)) {
            return m.get("iade-alindi-text");
        }
        return m.get("iade-bekleniyor-text");
    }

    private String victimName(UUID victim, DeathRecord rec) {
        if (rec != null && rec.victimName != null) {
            return rec.victimName;
        }
        Player online = Bukkit.getPlayer(victim);
        if (online != null) {
            return online.getName();
        }
        try {
            String n = Bukkit.getOfflinePlayer(victim).getName();
            if (n != null) {
                return n;
            }
        } catch (Throwable ignored) {
            // yok say
        }
        return "?";
    }

    private static ItemStack glass() {
        return simple(Material.BLACK_STAINED_GLASS_PANE, " ", null);
    }

    private static ItemStack simple(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------------------------------
    // Tiklama / surukleme / kapatma
    // ------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MenuHolder holder = (MenuHolder) top.getHolder();
        if (!player.getUniqueId().equals(holder.owner)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) {
            return;
        }
        try {
            if (holder.type == Type.ADMIN_DETAIL) {
                handleDetailClick(player, holder, raw);
            } else if (holder.type == Type.STAFF_LIST) {
                handleStaffClick(player, holder, raw);
            } else {
                handleListClick(player, holder, raw);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Menu tiklamasi islenirken hata", t);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof MenuHolder) {
            staffOpen.remove(event.getPlayer().getUniqueId(), top.getHolder());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        staffOpen.remove(id);
        checking.remove(id);
    }

    private void handleListClick(Player player, MenuHolder holder, int raw) {
        if (raw == SLOT_CLOSE) {
            closeLater(player);
            return;
        }
        int total = totalPages(holder.records.size(), PAGE_SIZE);
        if (raw == SLOT_PREV && holder.page > 0) {
            reopenLater(player, holder, holder.page - 1);
            return;
        }
        if (raw == SLOT_NEXT && holder.page < total - 1) {
            reopenLater(player, holder, holder.page + 1);
            return;
        }
        int idx = SLOT_INDEX[raw];
        if (idx < 0) {
            return;
        }
        int recordIndex = holder.page * PAGE_SIZE + idx;
        if (recordIndex >= holder.records.size()) {
            return;
        }
        DeathRecord rec = holder.records.get(recordIndex);
        if (holder.type == Type.CLAIM) {
            claim(player, rec);
        } else if (holder.type == Type.ADMIN_LIST) {
            openDetail(player, holder.victim, rec);
        }
    }

    private void handleDetailClick(Player player, MenuHolder holder, int raw) {
        if (raw == SLOT_APPROVE) {
            approve(player, holder);
        } else if (raw == SLOT_TELEPORT) {
            teleport(player, holder.record);
        } else if (raw == SLOT_DETAIL_CLOSE) {
            closeLater(player);
        }
    }

    private void handleStaffClick(final Player player, final MenuHolder holder, int raw) {
        if (!player.hasPermission(CommandManager.PERM_LISTE)) {
            player.sendMessage(plugin.getMessages().get("no-permission"));
            closeLater(player);
            return;
        }
        int total = totalPages(holder.entries.size(), STAFF_PAGE_SIZE);
        if (raw == STAFF_CLOSE) {
            closeLater(player);
        } else if (raw == STAFF_PREV && holder.page > 0) {
            reopenStaffLater(player, holder.filter, holder.page - 1);
        } else if (raw == STAFF_NEXT && holder.page < total - 1) {
            reopenStaffLater(player, holder.filter, holder.page + 1);
        } else if (raw == STAFF_FILTER) {
            reopenStaffLater(player, holder.filter.next(), 0);
        } else if (raw == STAFF_REFRESH) {
            if (checking.add(player.getUniqueId())) {
                player.sendMessage(plugin.getMessages().get("liste-yenileniyor"));
                runCheck(player, holder, new CheckStarter() {
                    @Override
                    public void start(Runnable after) {
                        plugin.getBanService().refreshAll(after);
                    }
                });
            }
        } else if (raw >= 0 && raw < STAFF_PAGE_SIZE) {
            int index = holder.page * STAFF_PAGE_SIZE + raw;
            if (index < holder.entries.size() && checking.add(player.getUniqueId())) {
                KillerEntry entry = holder.entries.get(index);
                final BanTarget target = new BanTarget(entry.uuid, entry.name);
                runCheck(player, holder, new CheckStarter() {
                    @Override
                    public void start(Runnable after) {
                        plugin.getBanService().recheck(target, after);
                    }
                });
            }
        }
    }

    private interface CheckStarter {
        void start(Runnable after);
    }

    /** Ban kontrolunu baslatir; bitince (menu hala aciksa) ayni sayfayi guncel verilerle yeniden acar. */
    private void runCheck(final Player player, final MenuHolder holder, CheckStarter starter) {
        final UUID id = player.getUniqueId();
        Runnable after = new Runnable() {
            @Override
            public void run() {
                checking.remove(id);
                if (player.isOnline() && staffOpen.get(id) == holder) {
                    openStaffList(player, holder.filter, holder.page);
                }
            }
        };
        try {
            starter.start(after);
        } catch (Throwable t) {
            checking.remove(id);
            plugin.getLogger().log(Level.WARNING, "Ban kontrolu baslatilamadi", t);
        }
    }

    // ------------------------------------------------------------------------------------
    // Yetkili islemleri
    // ------------------------------------------------------------------------------------

    private void approve(Player admin, MenuHolder holder) {
        Messages m = plugin.getMessages();
        DeathRecord rec = holder.record;
        if (!plugin.isLive(rec) || !rec.banned) {
            admin.sendMessage(m.get("refund-not-found"));
            closeLater(admin);
            return;
        }
        if (!rec.isStatus(DeathRecord.BEKLENIYOR)) {
            admin.sendMessage(m.get("refund-already-given"));
            return;
        }
        plugin.markApproved(rec);
        admin.sendMessage(m.get("refund-approved"));

        String victimName = victimName(holder.victim, rec);
        DiscordWebhook.sendRefundLog(plugin, plugin.getSettings().webhookUrl, victimName, admin.getName(),
                rec.killerName, rec.reason, rec.timestamp);

        Player target = Bukkit.getPlayer(holder.victim);
        if (target != null && target.isOnline()) {
            target.sendMessage(m.get("victim-notified"));
        }
        closeLater(admin);
    }

    private void teleport(Player admin, DeathRecord rec) {
        Messages m = plugin.getMessages();
        World world = rec.dunya == null ? null : Bukkit.getWorld(rec.dunya);
        if (world == null) {
            admin.sendMessage(m.get("teleport-fail"));
            return;
        }
        admin.teleport(new Location(world, rec.x, rec.y, rec.z, rec.yaw, rec.pitch));
        admin.sendMessage(m.get("teleport-success"));
        closeLater(admin);
    }

    // ------------------------------------------------------------------------------------
    // Oyuncu: iadeyi alma
    // ------------------------------------------------------------------------------------

    private void claim(final Player player, final DeathRecord rec) {
        if (!rec.banned || !rec.isStatus(DeathRecord.VERILDI)) {
            return;
        }
        if (!rec.busy.compareAndSet(false, true)) {
            return; // cift tiklama
        }
        plugin.loadInventoryAsync(rec, new Consumer<String>() {
            @Override
            public void accept(String data) {
                try {
                    finishClaim(player, rec, data);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Iade verilirken hata", t);
                } finally {
                    rec.busy.set(false);
                }
            }
        });
    }

    private void finishClaim(Player player, DeathRecord rec, String data) {
        Messages m = plugin.getMessages();
        if (!player.isOnline() || !rec.isStatus(DeathRecord.VERILDI) || !plugin.isLive(rec)) {
            return;
        }

        List<ItemStack> give;
        try {
            give = buildRefundItems(rec, data);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Iade esyalari okunamadi (id=" + rec.id + ")", t);
            player.sendMessage(m.get("iade-hata"));
            return;
        }

        if (give.isEmpty()) {
            player.sendMessage(m.get("iade-esya-yok"));
            plugin.markTaken(rec);
            closeLater(player);
            return;
        }

        if (freeSlots(player) < give.size()) {
            player.sendMessage(m.get("iade-envanter-dolu", "%adet%", String.valueOf(give.size())));
            return;
        }

        // Once durumu kaydet: ortada bir hata olsa bile iade iki kez verilmez.
        plugin.markTaken(rec);
        for (ItemStack stack : give) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        player.sendMessage(m.get("refund-success"));
        closeLater(player);
    }

    /**
     * Kayitli esyalari 27'ser slotluk shulker kutularina koyar. Zaten shulker kutusu olan esyalar
     * (ic ice shulker olmaz) oldugu gibi ayrica verilir. Dondurulen her oge envanterde bir slot tutar.
     */
    private List<ItemStack> buildRefundItems(DeathRecord rec, String data) throws Exception {
        List<ItemStack> loose = new ArrayList<ItemStack>();
        List<ItemStack> shulkers = new ArrayList<ItemStack>();
        for (ItemStack it : ItemSerializer.fromBase64(data)) {
            if (it == null || it.getType() == Material.AIR) {
                continue;
            }
            if (it.getType().name().endsWith("SHULKER_BOX")) {
                shulkers.add(it);
            } else {
                loose.add(it);
            }
        }

        String boxName = plugin.getMessages().get("iade-shulker-adi", "%tarih%",
                plugin.getSettings().formatShort(rec.timestamp));
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (int i = 0; i < loose.size(); i += 27) {
            ItemStack box = new ItemStack(Material.SHULKER_BOX);
            ItemMeta meta = box.getItemMeta();
            if (meta instanceof BlockStateMeta) {
                BlockStateMeta bsm = (BlockStateMeta) meta;
                BlockState state = bsm.getBlockState();
                if (state instanceof ShulkerBox) {
                    ShulkerBox shulker = (ShulkerBox) state;
                    Inventory inv = shulker.getInventory();
                    int end = Math.min(loose.size(), i + 27);
                    for (int j = i; j < end; j++) {
                        inv.setItem(j - i, loose.get(j));
                    }
                    bsm.setBlockState(shulker);
                }
                bsm.setDisplayName(boxName);
                box.setItemMeta(bsm);
            }
            out.add(box);
        }
        out.addAll(shulkers);
        return out;
    }

    private static int freeSlots(Player player) {
        int free = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it == null || it.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    // ------------------------------------------------------------------------------------
    // Yardimcilar: envanter islemlerini bir sonraki tike erteler
    // ------------------------------------------------------------------------------------

    private void closeLater(final Player player) {
        later(new Runnable() {
            @Override
            public void run() {
                player.closeInventory();
            }
        });
    }

    private void reopenLater(final Player player, final MenuHolder holder, final int page) {
        later(new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    openList(player, holder.type, holder.victim, page);
                }
            }
        });
    }

    private void reopenStaffLater(final Player player, final ListFilter filter, final int page) {
        later(new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    openStaffList(player, filter, page);
                }
            }
        });
    }

    private void later(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Gorev zamanlanamadi", t);
        }
    }
}
