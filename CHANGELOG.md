# Punk Store — sürüm notları

## 0.23 (2026-10-05)
- **Steam oyunları gelmiyordu**: Steam, oyun listesi sayfasını (`/games`, XML dahil) artık yalnızca giriş yapmış kullanıcılara gösteriyor (girişsiz istek giriş sayfasına yönleniyor). Çözüm: **Steam ile giriş yap** (Steam'in kendi sayfası, WebView; şifre uygulamaya gelmez, yalnızca oturum çerezi saklanır) → tam oyun listesi + oynama süreleri + başarımlar. Girişsiz yedek: profildeki "en çok oynanan" ve son etkinlik oyunları; toplam oyun/arkadaş/rozet sayısı profil sayfasından. Neden boş geldiği artık açıkça yazılıyor.
- Steam oyunları Punk Store **kütüphanesine** eklenir ("Steam" filtresi, oynama süresi, "Oynama süresi" sıralaması); liste diske kaydedilir.
- **Steam hesabı sayfası** Steam mobil hesap sayfası gibi: degrade profil başlığı + çerçeveli kare avatar + "Profili görüntüle", Oyun/Seviye/Saat kutuları, büyük mavi düğme, "OYUNLARIM" listesi (Steam arama satırı gibi başlık görselli), Hesabı değiştir / Çıkış yap satırları; oyun başarımları sayfası başlık görselli.
- **Ayarlar (Steam temalarında)** baştan: büyük kalın satırlar, gri aralıklı bölüm başlıkları, mavi tikli seçimler, kayan mavi anahtarlar, koyu giriş kutuları (SteamKit.kt).
- Saat sayıları dil biçiminden bağımsız okunur (1,234.5 / 1.234,5).
- İkon: poşet %5 küçültüldü.

- **Yeni indirme motoru** (`Downloads.kt`): kuyruk (aynı anda 2), `.part` + HTTP Range ile kaldığı yerden devam, duraklat / sürdür / iptal, ağ hatasında 4 kez üstel beklemeli yeniden deneme, boş alan denetimi, boyut + SHA-256 doğrulaması (F-Droid hex, Play base64), kurulum sonucu PackageInstaller'dan okunur (iptal / imza çakışması / yetersiz alan anlaşılır mesajla), yarım kalan indirmeler sonraki açılışta "Duraklatıldı" olarak geri gelir, kurulum iptal edilirse APK silinmez (yeniden indirmeden tekrar dene).
- Bildirim: uygulama başına ilerleme, hız, kalan süre; **Duraklat** ve **İptal** düğmeleri.
- İndir düğmesi: içi soldan sağa dolar, durum yazısı kayarak değişir (Sırada → %42 → Doğrulanıyor → Kuruluyor → ✓ Kuruldu), dokununca duraklat/devam, hata olursa kırmızı "Tekrar dene". Altında "12,3 / 45,6 MB · 2,1 MB/s · 15 sn kaldı" satırı.
- İndirmeler sayfası: kuyruk kartları, tümünü duraklat / sürdür, bitenleri temizle.
- **Kütüphane Steam gibi** (`Library.kt`): "Son oynanan" büyük kartı + son oynananlar şeridi, sayılı filtre çipleri (Tümü / Kurulu / Kurulu değil / Güncellemeler / İndirilenler / Sabitlenen / Oynanan / Oyunlar / F-Droid / Play / Steam / Cihaz / koleksiyonlar), açılır-kapanır gruplar (İndirilenler, Güncelleme bekleyenler, Kurulu, Kurulu değil), ızgara ↔ liste görünümü, 5 sıralama (son oynanan, A-Z, en çok açılan, son güncellenen, boyut), tümünü güncelle, uzun basınca menü (aç, yükle/güncelle, sabitle, koleksiyon, kütüphaneden çıkar, kaldır, uygulama bilgisi). Katalogda olmayan kurulu uygulamalar da "Cihaz" olarak listelenir (kendi ikonlarıyla). Punk Store'dan kurulan her şey kütüphaneye otomatik eklenir.
- Animasyonlar: sekmeler arası yöne göre kayma, uygulama sayfası sağdan kayarak açılır, basınca yaylanan düğme/kartlar, liste öğeleri yer değiştirirken akar.
- İkon: poşet yukarı alındı (üstteki boşluk giderildi, dikeyde ortalı).

## 0.16 (2026-10-05)
- Arama: yazdıkça canlı (180 ms), Play ve Steam paralel, ilerleme çubuğu; yeni sıralama (Rank.kt: tam ad > önek > kelime > içerir); Play'de bulunamazsa bilinen/olası paket adlarıyla doğrudan sorgu (WhatsApp vb.).
- İndirme: Play split APK'ları cihaz ABI'sine göre süzülür (-113 hatası).
- Detay: düğmeler 2 sütunlu düzgün ızgara; gerçek Windows/Apple/Linux/Android logoları (Windows yalnızca Steam); incelemeler sayfanın en altında; Steam Topluluk Pazarı + fiyat karşılaştırma bağlantıları.
- Hata kutusu: koyu zeminde beyaz yazı, 6 sn sonra kendiliğinden kapanır, OK düğmesi (takılı kalma düzeltildi).
- Açılış animasyonu cihazdaki ikon paketini kullanmaz (yalnızca katalog görselleri). İkon çarkı %24 küçüldü.

## 0.15 (2026-10-05)
- Menü/Profil tekrarları kaldırıldı: MENÜ yalnızca Steam/Ayarlar/Bilgi/Yenile; Başarımlar tek yerde; Profil'den istek listesi/ayarlar satırları çıktı; kısayol satırlarından Keşfet/İndirmeler tekrarları çıktı.
- Rozetler + Başarımlar tek sayfa ("Rozetler ve Başarımlar", seviye rozeti dahil).
- Yorumlar: yüklenemezse hata + "Tekrar dene"; gerçekten boşsa bölüm gizlenir.
- ROOT/oturum kurulumunda INSTALL_FAILED_NO_MATCHING_ABIS: F-Droid'de cihaz ABI'sine uyan sürüm seçilir; Google Play isteği gerçek cihaz ABI'siyle yapılır.
- Steam: platform simgeleri (Windows/macOS/Linux, Play'de aynı adlı oyun varsa Android), SteamSpy ile tahmini sahip sayısı/brüt gelir/ortalama oynama.
- Gizlilik raporu (reklam/izleyici + Exodus Privacy bağlantısı).

## 0.14 (2026-10-05)
- ÇÖKME düzeltildi: Play uygulaması sayfası (ör. İstanbulkart) `"%90 · …".format()` biçim hatasıyla çöküyordu; Steam değerlendirme özeti de aynı hatayı taşıyordu.
- Bildirimde indirilen uygulamanın ikonu ve adı (büyük ikon), alt satırda "Punk Store".
- İndirme çubuğu: dalgalı yalnızca Material temasında; Steam temalarında düz mavi çubuk + akan ışık.
- Açılış: varsayılan Android ikonlu kurulumlar elendi, katalog kapakları karıştırıldı. İkon: kalın, büyük 8 dişli çark, "P" kaldırıldı.
- Profil: eski 8 rozet kaldırıldı → Başarımlar özeti; uzun özellik listesi kaldırıldı, kısayollar ikonlu olarak Mağaza/Kütüphane/Güncellemeler'e dağıtıldı; menü ikonlu ve kısa.
- Detay: kullanıcı yorumları (Steam/Play), SteamDB bölümü (şu an oynayan, fiyat geçmişi, grafikler), sistem gereksinimleri, diller, Metacritic, fragman, daha çok görsel (kaydırmalı galeri), tam genişlik istek listesi düğmesi, ikonlu eylem düğmeleri, Windows simgesi (Steam).
- Keşfet: kategori sekmeleri (Android/Steam/Oyunlar/…), yatay/dikey görseller bulanık arka planla sığar, dokununca sonraki görsel, Steam oyunları desteği.
- Ayarlar: dışa/içe aktarma (ayarlar dahil). Arama kutusunda uygulama sayısı kaldırıldı. Alt boşluklar.

## 0.13 — modern Steam paleti gerçek piksellerden; yeni ikon denemesi
## 0.12 — ön plan indirme servisi, dalgalı çubuk, hızlı açılış, özellik gruplama
## 0.11 — Russo One logo, Steam mağazası entegrasyonu, 20+ yeni özellik
## 0.10 — temalara özel panel/parlaklık/karbon doku, akıllı kapak
## 0.9 — 33 başarım, Steam hesabı (Web API), açılış animasyonu
## 0.8 — bilgi sayfası, yeni ikon · 0.7 — güncelleme denetimi, filtre, Keşfet, kurulum yöntemleri, temalar
## 0.6 — Steam mobil arayüzü yeniden · 0.5 — ikon · 0.4 — Google girişi · 0.3 — istek listesi/profil
