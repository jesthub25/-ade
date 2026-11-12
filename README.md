# Iade

**Sürüm:** 1.1.0 · **Sunucu:** Spigot / Paper / Purpur **1.20.1 ve üzeri** · **Java:** 17+ (sunucunun Java'sı)

Bir oyuncu başka bir oyuncu tarafından öldürülünce envanteri kaydedilir. Katil banlanırsa kurban
`/olumliste` ile kaydı görür, yetkili `/iadever` ile onaylar, kurban `/iadelerim` ile eşyalarını
shulker kutusu olarak geri alır.

## Kurulum

[Releases](../../releases) sayfasından `Iade-<sürüm>.jar` dosyasını indirip sunucunun `plugins/` klasörüne atın.
İlk açılışta `plugins/Iade/` altında `config.yml`, `messages.yml` ve `veriler.db` oluşur.

## Derleme

```
mvn clean package
```

Çıktı: `target/Iade-1.1.0.jar` (JDK 17 gerekir). Eklenti 1.20.1 API'sine karşı derlenir; yalnızca 1.20.1'den
bu yana değişmeden kalan çağrıları kullanır ve InventoryView gibi sürümler arasında uyumsuz sınıflar üzerinde
metot çağırmaz, bu yüzden 1.20.x ve 1.21.x sunucularında aynı jar çalışır.

## Komutlar ve yetkiler

| Komut | Alias | Yetki | Açıklama |
|---|---|---|---|
| `/olumliste` | `/iade`, `/ölümliste`, `/deathlist` | yok | Banlı katillere ait ölüm kayıtların |
| `/olumliste liste` | | `iade.liste` | **Yetkili menüsü:** listedeki tüm katiller oyuncu kafasıyla, sayfa sayfa (en yakın ban ilk slotta) |
| `/olumliste gecmis` | | `iade.liste` | **İade geçmişi:** onaylanan, alınan ve silinen iadelerin kaydı (en yeni önce) |
| `/olumliste reload` | | `iade.reload` | `/olumlistereload` ile aynı |
| `/iadelerim` | `/refunds` | yok | Onaylanmış iadeleri alma menüsü |
| `/iadever <oyuncu>` | | `iade.iadever` | Kayıtları inceleme, onaylama, ölüm yerine ışınlanma |
| `/olumlistesil <oyuncu>` | `/ölümlistesil` | `iade.sil` | Oyuncunun kayıtlarını siler |
| `/olumlistebanla <oyuncu> [sebep]` | `/ölümlistebanla` | `iade.banla` | Katili elle banlı işaretler (otomatik yakalanamayan ban sistemleri için) |
| `/olumlistereload` | `/ölümlistereload` | `iade.reload` | config.yml ve messages.yml'i yeniler |

`iade.bildirim` yetkisi (varsayılan op), takip edilen bir katil banlandığında sohbette bildirim alır.
`iade.*` hepsini kapsar. Varsayılan: sadece op. Diğer yetkililere LuckPerms ile verin.

## Ban kontrolü (katmanlı)

1. **API:** Vanilla/Bukkit ban listesi (1.20.3 ve sonrasında UUID tabanlı profil listesi dahil), **EssentialsX**
   (`/ban`, `/tempban`, `/banip`), LiteBans, AdvancedBan, BanManager. Kurulu olmayan atlanır.
   EssentialsX banları sunucunun ban listesine yazar; eklenti bu listeyi "EssentialsX" kaynağıyla okur.
   EssentialsX IP banları isme bağlanamaz, onları giriş dinleme yakalar.
2. **Komut dinleme:** ban ve unban komutları; komutun var olması ve kullananın yetkisi doğrulanır.
3. **Giriş dinleme:** takip edilen katil girişte "banlı" sonucu alırsa (LibertyBans, CMI, MaxBans dahil herhangi bir sistem) işaretlenir.
4. **Periyodik tarama:** anti-cheat'lerin arka planda verdiği banları yakalar (varsayılan 60 sn).
5. **Anlık kontrol:** `/olumliste`, `/iadelerim` ve `/iadever` önce ban durumunu tazeler.

**Unban kontrolü:** `/unban`, `/pardon` vb. komutlar ve (API ile doğrulanmış banlar için) arka plan taraması,
iadesi henüz onaylanmamış kayıtların işaretini geri alır. Onaylanmış iade asla geri alınmaz.
Bir ban sistemi hata verirse durum "belirsiz" sayılır ve işaret asla geri alınmaz.

## Yetkili menüsü (`/olumliste liste`)

54 slot: üst 45 slot kafa, alt satırda önceki/sonraki sayfa, filtre (Tümü / Banlılar / Banlı değil),
ban durumunu yenile, **istatistikler**, **iade geçmişi** ve kapat. Kafaya gelince ban sebebi, banlayan, ban süresi,
öldürülen oyuncular, son öldürmenin silahı / türü / mesafesi, iade özeti, aynı IP uyarısı ve listeden silinme zamanı görünür.
Kafaya tıklamak o oyuncunun ban durumunu yeniden kontrol eder.
Bir oyuncuyu banladıktan sonra "ölüm listesine girmiş mi?" kontrolü için: menüyü aç, oyuncuyu bul;
"Banlı değil" görünüyorsa tıkla veya `/olumlistebanla <oyuncu>` kullan.

## Yeni özellikler (1.1.0)

| Özellik | Ne yapar | Ayar bölümü |
|---|---|---|
| **Giriş hatırlatması** | Oyuncu girince onaylanmış ama alınmamış iadesi varsa adedi ve kalan süreyi yazar; mesaj tıklanınca `/iadelerim` açılır | `giris-hatirlatma` |
| **Yetkili bildirimi** | Takip edilen katil banlanınca `iade.bildirim` yetkisi olanlara (ve konsola) kurban sayısıyla haber verir, tıklanınca `/olumliste liste` yazılır; ayrıca Discord embed'i | `yetkili-bildirimi` |
| **İade geçmişi** | Onay / teslim alma / silme işlemleri veritabanına yazılır; `/olumliste gecmis` veya menüdeki düğmeyle sayfa sayfa görülür; eski satırlar temizlenir | `gecmis` |
| **Aynı IP koruması** | Kurban ve katil aynı IP'deyse kayıt işaretlenir (varsayılan) veya hiç kaydedilmez. IP adresi **saklanmaz**, yalnızca "aynı IP" işareti tutulur | `ip-korumasi` |
| **Sesler** | Menü açma, sayfa, iade onay, iade alma, hata ve bildirim sesleri; her biri ayarlanır veya kapatılır | `sesler` |
| **Ölüm bilgisi** | Katilin silahı (adı ve büyü sayısı), ölüm türü ve mesafe kaydedilir; yetkili menülerinde görünür | `olum-bilgisi` |
| **İstatistikler** | Toplam kayıt, banlı katiller, farklı kurbanlar, en çok kurbanı olan 3 katil, geçmiş toplamları ve ortalama onay süresi | yetkili menüsündeki düğme |

Notlar:

- **Sesler** `Sound` enum'u yerine Minecraft ses anahtarıyla (`block.chest.open` gibi, noktalı yazım) çalınır; böylece
  1.20.1 ile derlenen jar 1.21.x'te de çalışır. Enum adı yazılırsa (`ENTITY_PLAYER_LEVELUP`) konsola uyarı yazılır.
- **Tıklanabilir mesajlar** sunucu çekirdeği desteklemiyorsa otomatik olarak düz yazıya döner.
- **Aynı IP** proxy / yerel ağ arkasında herkes aynı adresle görünüyorsa `yoksayilan-adresler` listesine o adresi ekleyin
  (varsayılan: `127.0.0.1`, `::1`). Bu liste alan adı çözümlemez; yalnızca IPv4/IPv6 adresi kabul eder.
- Eski kayıtlar (1.0.0'dan kalanlar) silah / tür / mesafe bilgisi taşımaz; bu satırlar menüde görünmez. Veritabanı şeması
  ilk açılışta otomatik güncellenir.

## Saklama süreleri (config.yml, hepsi `/olumlistereload` ile uygulanır)

| Ayar | Varsayılan | Anlamı |
|---|---|---|
| `silinme-suresi-saat` | 48 | Banlanmamış kayıt, ölümden itibaren |
| `banli-saklama-suresi-saat` | 48 | Banlı, iadesi onaylanmamış kayıt, banlanmadan itibaren |
| `iade-alma-suresi-saat` | 48 | Onaylanmış ama alınmamış iade, onaydan itibaren |
| `alindi-saklama-suresi-saat` | 24 | Alınmış kayıt |
| `temizlik-araligi-dakika` | 5 | Silme görevinin aralığı (dolan kayıt menülerde zaten görünmez) |

## Ayarlar

`config.yml` tüm davranışı, `messages.yml` tüm metinleri belirler (`&` ve `§` renk kodları çalışır).
Hatalı değerler (sayı yerine yazı gibi) güvenli varsayılana döner ve konsola uyarı yazılır; bozuk YAML dosyasında
`/olumlistereload` eski ayarları korur. Tüm ayarlar sunucuyu yeniden başlatmadan `/olumlistereload` ile uygulanır.

## Eski veriler

Önceki bir OlumListe sürümünün `plugins/*OlumListe/veriler.db` dosyası ilk açılışta
otomatik taşınır, şema da güncellenir. `config.yml` ve `messages.yml` taşınmaz; değerleri elle kopyalayın.
Eski sürümdeki yetki adları yerine artık `iade.*` kullanılır; LuckPerms gibi bir yetki eklentisi kullanıyorsanız güncelleyin.

## Notlar

- Folia desteklenmez.
- Veritabanı için sunucuyla gelen `sqlite-jdbc` kullanılır (ek kurulum gerekmez).
