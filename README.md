# Iade

**Sürüm:** 1.0.0 · **Sunucu:** Spigot / Paper / Purpur **1.20.1 ve üzeri** · **Java:** 17+ (sunucunun Java'sı)

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

Çıktı: `target/Iade-1.0.0.jar` (JDK 17 gerekir). Eklenti 1.20.1 API'sine karşı derlenir; yalnızca 1.20.1'den
bu yana değişmeden kalan çağrıları kullanır ve InventoryView gibi sürümler arasında uyumsuz sınıflar üzerinde
metot çağırmaz, bu yüzden 1.20.x ve 1.21.x sunucularında aynı jar çalışır.

## Komutlar ve yetkiler

| Komut | Alias | Yetki | Açıklama |
|---|---|---|---|
| `/olumliste` | `/iade`, `/ölümliste`, `/deathlist` | yok | Banlı katillere ait ölüm kayıtların |
| `/olumliste liste` | | `iade.liste` | **Yetkili menüsü:** listedeki tüm katiller oyuncu kafasıyla, sayfa sayfa (en yakın ban ilk slotta) |
| `/olumliste reload` | | `iade.reload` | `/olumlistereload` ile aynı |
| `/iadelerim` | `/refunds` | yok | Onaylanmış iadeleri alma menüsü |
| `/iadever <oyuncu>` | | `iade.iadever` | Kayıtları inceleme, onaylama, ölüm yerine ışınlanma |
| `/olumlistesil <oyuncu>` | `/ölümlistesil` | `iade.sil` | Oyuncunun kayıtlarını siler |
| `/olumlistebanla <oyuncu> [sebep]` | `/ölümlistebanla` | `iade.banla` | Katili elle banlı işaretler (otomatik yakalanamayan ban sistemleri için) |
| `/olumlistereload` | `/ölümlistereload` | `iade.reload` | config.yml ve messages.yml'i yeniler |

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
ban durumunu yenile ve kapat. Kafaya gelince ban sebebi, banlayan, ban süresi, öldürülen oyuncular,
iade özeti ve listeden silinme zamanı görünür. Kafaya tıklamak o oyuncunun ban durumunu yeniden kontrol eder.
Bir oyuncuyu banladıktan sonra "ölüm listesine girmiş mi?" kontrolü için: menüyü aç, oyuncuyu bul;
"Banlı değil" görünüyorsa tıkla veya `/olumlistebanla <oyuncu>` kullan.

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
