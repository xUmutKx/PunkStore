# Punk Store

F-Droid + Google Play (Aurora tarzı anonim/Google girişli) + Steam mağazası tek Android uygulamasında. Steam mobil arayüzünden esinlenmiştir (hayran çalışması, Valve ile bağlantısı yoktur). GPL-3.0.

- Paket: `com.punkstore.app` · Kotlin + Jetpack Compose · minSdk 26
- Derleme: `export PATH=/root/tools/gradle-8.9/bin:$PATH; gradle assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease --offline --no-daemon`
- Çıktı: `app/build/outputs/apk/release/app-release.apk` (debug anahtarıyla imzalı)

## Ekranlar
| Sekme | İçerik |
|---|---|
| Mağaza | Öne çıkanlar, Steam bölümleri (indirimdekiler/çok satanlar/yeni/yakında), Play listeleri, kategoriler, yeni çıkanlar, kısayollar |
| Keşfet | Tinder tarzı kartlar (sağa = beğen → istek listesi, sola = geç); Hepsi/Android/Steam/Oyunlar/F-Droid/Play/kategori sekmeleri; kart görselleri dokununca değişir; zevk puanı |
| Kütüphane | Kapak ızgarası, arama, sıralama, favoriler önde; Koleksiyonlar/Favoriler/Son bakılanlar/Paylaş/Yedek kısayolları |
| Güncellemeler | Denetle, tümünü güncelle, yoksay, Punk Store kendi güncellemesi; İndirmeler/Depolama/Temizlik/Kurulu bilgisi kısayolları |
| Profil | Seviye/XP, kapak teması, fotoğraf, durum, vitrin, başarım özeti, istatistikler, Steam hesabı |
| Ayarlar | Tasarım (Steam modern / 2013 / 2006 / Material You), dil, kurulum yöntemi (oturum/sistem/root), indirme, güncelleme, Google girişi, dispenser, yedek |

## Kaynaklar
- **F-Droid:** `index-v2.json` (afiş = featureGraphic, ekran görüntüleri: phone + 7" + 10", antiFeatures → reklam/izleyici)
- **Google Play:** gplayapi (anonim dispenser ya da Google hesabı → AAS belirteci); yorumlar ReviewsHelper
- **Steam mağazası (anahtarsız):** `featuredcategories`, `storesearch`, `appdetails`, `appreviews`, `GetNumberOfCurrentPlayers`; SteamDB bağlantıları
- **Steam hesabı (kullanıcının kendi Web API anahtarı):** sahip olunan oyunlar, oyun başarımları (şema + yüzde)

## Özellikler (dağıtılmış, ayrı sayfa yok)
İstek listesi · Kütüphaneye ekle · Favoriler · Koleksiyonlar · Son bakılanlar · Günün uygulaması · Rastgele · Steam'de ara · Paket adıyla aç · Filtre hazırları · İstatistikler · Depolama · Temizlik önerileri · Kurulu uygulama bilgisi + APK yedekle · Yedekle/geri yükle (ayarlar dahil) · Kütüphaneyi paylaş · Arama geçmişi · Yazı boyutu · Titreşim · Gün serisi · 38 başarım · İndirme ön plan servisi + ikonlu bildirimler.

## Dosya haritası (`app/src/main/java/com/punkstore`)
`Store.kt` (durum) · `Repo.kt` F-Droid · `PlayRepo.kt` Play · `SteamStoreApi.kt` · `SteamLink.kt` · `SteamUi.kt` (mağaza/kütüphane/detay) · `Discover.kt` · `Profile.kt` · `Achievements.kt` · `Features.kt` (alt sayfalar) · `Filters.kt` · `Installer.kt` (oturum/sistem/root) · `DownloadService.kt` · `Updater.kt` · `About.kt`/`Splash.kt`/`Logo.kt` · `Theme.kt` (paletler) · `Components.kt`.

## Temalar
- **Modern**: kullanıcının Steam mobil ekran görüntülerinden örneklenen gerçek renkler (#1F2127 çubuk, #2A2C34 panel, #1A9FFF mavi, #121A24 mağaza sayfası, #3B4650 satın alma).
- **2013**: karbon siyahı + camsı yeşil · **2006**: steam.styles değerleri (GreenBG 76,88,68 · Maize 196,181,80), kabartmalı kenarlar.
