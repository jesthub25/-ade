package com.iade;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Tiklanabilir sohbet mesaji. Bungee chat sinifi yalnizca ic sinifta kullanilir; bu sinif yuklenemez veya
 * cagri basarisiz olursa (cok eski/cok yeni bir sunucu cekirdegi) mesaj duz metin olarak gonderilir, yani
 * hicbir kosulda mesaj kaybolmaz ve hata firlatilmaz.
 */
final class Chat {

    private Chat() {
    }

    /**
     * @param hint      tiklanabilir halde metnin sonuna eklenen ipucu ("(Tiklayin)"); duz metinde eklenmez, bos olabilir
     * @param command   tiklaninca kullanilacak komut ("/iadelerim"); bos ise mesaj tiklanamaz
     * @param run       true: komutu dogrudan calistirir, false: sohbet satirina yazar (oyuncu Enter'a basar)
     * @param clickable ayarlardaki "tiklanabilir" degeri
     */
    static void send(Player player, String text, String hint, String command, boolean run, boolean clickable) {
        if (clickable && !Text.isBlank(command)) {
            try {
                Clickable.send(player, hint == null ? text : text + hint, command, run);
                return;
            } catch (Throwable t) {
                // duz metne don
            }
        }
        player.sendMessage(text);
    }

    private static final class Clickable {
        static void send(Player player, String text, String command, boolean run) {
            BaseComponent[] parts = TextComponent.fromLegacyText(text);
            ClickEvent click = new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND,
                    command);
            for (BaseComponent part : parts) {
                part.setClickEvent(click);
            }
            player.spigot().sendMessage(parts);
        }
    }
}
