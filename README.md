<p align="center"><img src="docs/icon.svg" width="128" alt="Punk Store"></p>

# Punk Store

Telefonda uygulama ararken F-Droid'e, Play Store'a ve Steam'e ayrı ayrı girmekten sıkıldım, hepsini tek yerde toplayan bir mağaza yazdım. Görünüşü Steam'in mobil uygulamasından geliyor, çünkü o arayüzü seviyorum.

Valve ile bir bağlantısı yok, kendi keyfime yaptığım bir hayran projesi. Lisansı GPL-3.0.

## Neler var

**Üç kaynak, tek arama.** F-Droid'deki açık kaynak uygulamalar, Google Play (Aurora Store'daki gibi anonim ya da kendi Google hesabınla) ve Steam mağazası aynı yerde. Yazdıkça arıyor, en alakalı sonuç en üstte çıkıyor.

**Steam gibi kütüphane.** Son oynadığın uygulama en üstte büyük kartla duruyor, altında son açtıkların var. Kurulu, kurulu olmayan, güncellemesi bekleyen gibi filtreler, açılıp kapanan gruplar, ızgara ya da liste görünümü var. Bir öğeye uzun basınca aç, güncelle, sabitle, koleksiyona ekle, kaldır gibi seçenekler çıkıyor. Telefonunda kurulu olup mağazada bulunmayan uygulamalar da kütüphanede görünüyor.

**Steam hesabı.** Steam ile giriş yapınca oyunların, oynama sürelerin ve başarımların uygulamaya geliyor, oyunlar kütüphanede ayrı bir filtrede duruyor. Giriş Steam'in kendi sayfasında yapılıyor, şifren uygulamaya hiç gelmiyor. Giriş yapmak istemezsen profil adını yazman yetiyor. O zaman sadece profilinde görünen oyunlar geliyor, çünkü Steam tam listeyi girişsiz göstermiyor.

**İndirmeler.** Aynı anda iki uygulama iniyor, diğerleri sırada bekliyor. İstediğin an duraklatıp sonra kaldığı yerden devam ettirebilirsin; internet kopsa ya da uygulama kapansa bile indirme baştan başlamıyor. Bağlantı giderse birkaç kez kendisi tekrar deniyor. İnen dosyanın boyutu ve SHA-256 değeri kontrol ediliyor. Bildirimde hız ve kalan süre görünüyor, duraklat ve iptal düğmeleri de orada.

**Kurulum.** Normal Android kurucusu, sistemin kendi kurulum ekranı ya da root ile sessiz kurulum. Play'deki bölünmüş APK'lardan sadece telefonun işlemcisine uyanlar iniyor.

**Uygulama sayfası.** Ekran görüntüleri, kullanıcı yorumları (Play ve Steam), gizlilik raporu (reklam ve izleyiciler, Exodus bağlantısı). Steam oyunlarında platformlar, sistem gereksinimleri, SteamDB bilgileri ve tahmini satış rakamları da var.

**Ufak tefek şeyler.** İstek listesi, koleksiyonlar, sabitlenenler, Tinder tarzı kaydırmalı bir Keşfet ekranı, seviye ve başarımlar, güncelleme bildirimleri, yedekle ve geri yükle.

## Temalar

- **Steam (varsayılan):** bugünkü Steam mobil uygulamasının renkleri.
- **Steam 2013:** karbon siyahı zemin, camsı yeşil düğmeler.
- **Steam 2006:** eski Steam'in zeytin yeşili, kabartmalı kenarlı görünüşü.
- **Material You:** telefonun kendi renkleri, Steam görünüşünü sevmeyenler için.

## Derleme

Kotlin ve Jetpack Compose ile yazıldı, Android 8.0 (API 26) ve üstünde çalışıyor.

```sh
gradle assembleRelease
```

APK `app/build/outputs/apk/release/` klasörüne çıkıyor.

## Nereden ne geliyor

- **F-Droid:** resmi depodaki `index-v2.json`
- **Google Play:** Aurora OSS'nin [gplayapi](https://gitlab.com/AuroraOSS/gplayapi) kütüphanesi
- **Steam mağazası:** Steam'in herkese açık mağaza uçları (API anahtarı gerekmiyor)
- **Steam profili:** steamcommunity.com profil sayfaları

## Teşekkürler

[F-Droid](https://f-droid.org) ve [Aurora Store](https://auroraoss.com) olmasa bu uygulama olmazdı. Logo fontu Russo One (OFL).
