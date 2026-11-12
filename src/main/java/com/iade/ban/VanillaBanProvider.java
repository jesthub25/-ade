package com.iade.ban;

/**
 * Vanilla / Bukkit ban listesi (banned-players.json). Ana thread'de cagrilir.
 * CMI (varsayilan) ve cogu anti-cheat de bu listeyi kullanir.
 */
public final class VanillaBanProvider implements BanProvider {

    @Override
    public String getName() {
        return "Vanilla";
    }

    @Override
    public String getPluginName() {
        return "";
    }

    @Override
    public boolean requiresMainThread() {
        return true;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public BanInfo query(BanTarget t) {
        return BukkitBanLists.lookup(t, getName());
    }
}
