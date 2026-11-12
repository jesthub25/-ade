package com.iade;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.iade.ban.BanInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CommandManager implements CommandExecutor, TabCompleter {

    static final String PERM_LISTE = "iade.liste";
    static final String PERM_IADEVER = "iade.iadever";
    static final String PERM_SIL = "iade.sil";
    static final String PERM_BANLA = "iade.banla";
    static final String PERM_RELOAD = "iade.reload";

    private final Iade plugin;

    public CommandManager(Iade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "olumliste":
                return olumListe(sender, args);
            case "iadelerim":
                return iadelerim(sender);
            case "iadever":
                return iadeVer(sender, args);
            case "olumlistesil":
                return olumListeSil(sender, args);
            case "olumlistebanla":
                return olumListeBanla(sender, args);
            case "olumlistereload":
                return reload(sender);
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------------------------

    /** /olumliste (kendi listen), /olumliste liste (yetkili menusu), /olumliste reload. */
    private boolean olumListe(CommandSender sender, String[] args) {
        Messages m = plugin.getMessages();
        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("liste") || sub.equals("list")) {
                return staffList(sender);
            }
            if (sub.equals("gecmis") || sub.equals("ge\u00e7mi\u015f") || sub.equals("history")) {
                return history(sender);
            }
            if (sub.equals("reload")) {
                return reload(sender);
            }
            sender.sendMessage(m.get("usage-olumliste"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(m.get("only-players"));
            return true;
        }
        final Player player = (Player) sender;
        player.sendMessage(m.get("opening"));
        plugin.refreshBansFor(player.getUniqueId(), new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.getMenus().openDeathList(player, 0);
                }
            }
        });
        return true;
    }

    /** Yetkili menusu: listedeki tum katiller (kafa), en yakin ban en solda. */
    private boolean staffList(CommandSender sender) {
        Messages m = plugin.getMessages();
        if (!sender.hasPermission(PERM_LISTE)) {
            sender.sendMessage(m.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(m.get("only-players"));
            return true;
        }
        final Player player = (Player) sender;
        player.sendMessage(m.get("liste-aciliyor"));
        plugin.getBanService().refreshAll(new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.getMenus().openStaffList(player, plugin.getSettings().listeFiltre, 0);
                }
            }
        });
        return true;
    }

    /** Iade gecmisi (yetkili): onaylanan, alinan ve silinen iadelerin kaydi. */
    private boolean history(CommandSender sender) {
        Messages m = plugin.getMessages();
        if (!sender.hasPermission(PERM_LISTE)) {
            sender.sendMessage(m.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(m.get("only-players"));
            return true;
        }
        if (!plugin.getSettings().gecmisAktif) {
            sender.sendMessage(m.get("gecmis-kapali"));
            return true;
        }
        plugin.getMenus().openHistory((Player) sender, 0);
        return true;
    }

    private boolean iadelerim(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessages().get("only-players"));
            return true;
        }
        final Player player = (Player) sender;
        plugin.refreshBansFor(player.getUniqueId(), new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (plugin.hasClaimable(player.getUniqueId())) {
                    plugin.getMenus().openClaim(player, 0);
                } else {
                    player.sendMessage(plugin.getMessages().get("no-refund"));
                }
            }
        });
        return true;
    }

    private boolean iadeVer(CommandSender sender, String[] args) {
        Messages m = plugin.getMessages();
        if (!sender.hasPermission(PERM_IADEVER)) {
            sender.sendMessage(m.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(m.get("only-players"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(m.get("usage-iadever"));
            return true;
        }
        final Player admin = (Player) sender;
        final UUID victim = plugin.resolveVictim(args[0]);
        if (victim == null) {
            admin.sendMessage(m.get("player-not-found"));
            return true;
        }
        plugin.refreshBansFor(victim, new Runnable() {
            @Override
            public void run() {
                if (!admin.isOnline()) {
                    return;
                }
                if (plugin.hasBannedRecords(victim)) {
                    plugin.getMenus().openAdminList(admin, victim, 0);
                } else {
                    admin.sendMessage(plugin.getMessages().get("refund-not-found"));
                }
            }
        });
        return true;
    }

    private boolean olumListeSil(CommandSender sender, String[] args) {
        Messages m = plugin.getMessages();
        if (!sender.hasPermission(PERM_SIL)) {
            sender.sendMessage(m.get("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(m.get("usage-sil"));
            return true;
        }
        int removed = plugin.deleteRecordsFor(args[0], sender.getName());
        if (removed > 0) {
            sender.sendMessage(m.get("sil-success", "%oyuncu%", args[0], "%adet%", String.valueOf(removed)));
        } else {
            sender.sendMessage(m.get("sil-not-found", "%oyuncu%", args[0]));
        }
        return true;
    }

    private boolean olumListeBanla(CommandSender sender, String[] args) {
        Messages m = plugin.getMessages();
        if (!sender.hasPermission(PERM_BANLA)) {
            sender.sendMessage(m.get("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(m.get("usage-banla"));
            return true;
        }
        StringBuilder reason = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (reason.length() > 0) {
                reason.append(' ');
            }
            reason.append(args[i]);
        }
        String text = reason.length() == 0 ? m.get("manuel-ban-sebebi") : reason.toString();
        int count = plugin.markBanned(args[0], null,
                new BanInfo(text, DeathRecord.KAYNAK_MANUEL, sender.getName(), -1L));
        if (count > 0) {
            sender.sendMessage(m.get("banla-success", "%oyuncu%", args[0], "%adet%", String.valueOf(count)));
        } else {
            sender.sendMessage(m.get("banla-not-found", "%oyuncu%", args[0]));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission(PERM_RELOAD)) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }
        Iade.ReloadResult result = plugin.reloadAll();
        Messages m = plugin.getMessages();
        if (!result.ok) {
            sender.sendMessage(m.get("reload-fail", "%dosya%", result.file, "%hata%", result.error));
            return true;
        }
        sender.sendMessage(m.get("reload-success", "%sistemler%", plugin.getBanService().describe()));
        for (String warning : result.warnings) {
            sender.sendMessage(m.get("reload-warning", "%uyari%", warning));
        }
        return true;
    }

    // ------------------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (name.equals("olumliste")) {
            List<String> subs = new ArrayList<String>();
            if (sender.hasPermission(PERM_LISTE) && "liste".startsWith(prefix)) {
                subs.add("liste");
            }
            if (sender.hasPermission(PERM_LISTE) && "gecmis".startsWith(prefix)) {
                subs.add("gecmis");
            }
            if (sender.hasPermission(PERM_RELOAD) && "reload".startsWith(prefix)) {
                subs.add("reload");
            }
            return subs;
        }
        if (name.equals("iadever") && sender.hasPermission(PERM_IADEVER)) {
            return plugin.victimNames(args[0]);
        }
        if (name.equals("olumlistesil") && sender.hasPermission(PERM_SIL)) {
            List<String> out = new ArrayList<String>(plugin.victimNames(args[0]));
            for (String k : plugin.killerNames(args[0])) {
                if (!out.contains(k)) {
                    out.add(k);
                }
            }
            return out;
        }
        if (name.equals("olumlistebanla") && sender.hasPermission(PERM_BANLA)) {
            return plugin.killerNames(args[0]);
        }
        return Collections.emptyList();
    }
}
