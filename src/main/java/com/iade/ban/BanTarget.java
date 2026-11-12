package com.iade.ban;

import java.util.Locale;
import java.util.UUID;

/** Ban sorgusu yapilacak oyuncu. uuid bilinmiyorsa (eski kayitlar) null olabilir. */
public final class BanTarget {

    public final UUID uuid;
    public final String name;

    public BanTarget(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public String key() {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BanTarget && key().equals(((BanTarget) o).key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }
}
