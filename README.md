# İhvpn — proje iskeleti (gerçek kod, henüz derlenmedi)

Bu paket, konuşma boyunca netleşen tüm spec maddelerini karşılayan **gerçek Kotlin/Compose
kaynak kodudur** — mockup değil. Ancak şu ortamda (bu sohbet arayüzünde) ne Android SDK'sı
ne Google'ın Maven sunucularına ağ erişimi olduğu için **hiç derlenmedi/test edilmedi**.
Android Studio'da ilk `Gradle Sync` ve `Build` senin makinende gerçekleşecek.

## Nasıl açılır

**Önce şunu bil:** `app/build.gradle.kts`, Xray-core'u `app/libs/libv2ray.aar` adında
yerel bir dosyadan bekliyor (`implementation(files("libs/libv2ray.aar"))`). Bu dosya
Maven/JitPack'ten inmiyor — çünkü 2dust/AndroidLibXrayLite bir Maven paketi değil, bir
**Go kaynak kodu deposu**; gomobile ile kendin (ya da CI) derlemen gerekiyor. Dosya
projede yoksa Gradle Sync "file not found" hatası verir, bu normal — aşağıdaki iki
yoldan biriyle doldurman lazım:

**Yol A — en kolay: CI'nin derlediği hazır .aar'ı indir**
1. Bu projeyi GitHub'a push et (aşağıdaki "GitHub'da otomatik derleme" bölümüne bak).
2. Actions sekmesinde iş bitince **Artifacts**'tan `libv2ray-aar` dosyasını indir.
3. İndirdiğin `libv2ray.aar`'ı `app/libs/` klasörüne koy.
4. Android Studio'da Gradle Sync'i tekrar çalıştır.

**Yol B — yerelde kendin derle (Go + gomobile gerekir)**
```
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
git clone --depth 1 --branch v26.8.20 https://github.com/2dust/AndroidLibXrayLite.git
cd AndroidLibXrayLite && go mod tidy
gomobile bind -androidapi 24 -ldflags="-s -w" -o libv2ray.aar ./
cp libv2ray.aar ../ihvpn/app/libs/
```
Bu adım Xray-core'un tamamını derlediği için ilk seferde 15-30 dakika sürebilir.

Sonra:
1. Android Studio (Koala veya üzeri) ile `ihvpn` klasörünü aç.
2. Gradle Sync'i bekle.
3. Bir cihaz/emülatörde çalıştır.

## Neler gerçek ve çalışacak şekilde yazıldı
- **ProfileUriParser** — vless://, vmess://, trojan://, ss:// linklerini gerçekten ayrıştırır
  (xhttp/reality/grpc/ws parametreleri dahil).
- **SubscriptionFetcher** — Hiddify tarzı abonelik linkini indirir, base64/düz metni otomatik
  algılar, `subscription-userinfo` header'ından kalan GB/gün bilgisini çıkarır.
- **XrayConfigBuilder** — ayrıştırılmış profilden gerçek bir Xray-core `config.json` üretir
  (xhttp/grpc/ws/httpupgrade/reality stream ayarları dahil).
- **XrayCoreController** — AndroidLibXrayLite'ın gerçek `V2RayPoint` API'sini sarmalar
  (RunLoop/StopLoop/QueryStats/MeasureOutboundDelay).
- **IhvpnVpnService** — gerçek `VpnService`: TUN kurulumu, kill switch (`setBlocking`),
  DNS sızıntı koruması (tünel içi DNS), IPv6 sızıntı koruması (IPv6 route eklenmiyor),
  split-tunneling iskeleti, bildirimden "Bağlantıyı Kes" aksiyonu, `onRevoke()` ile temiz kapanış.
- **SecureProfileStore** — Android Keystore tabanlı gerçek AES-256-GCM şifreleme (profil sırları).
- **TrafficStatsMonitor / PingTester** — gerçek `TrafficStats` ve TCP-connect tabanlı ölçüm;
  hiçbir yerde sahte sayı üretilmiyor (bağlı değilken UI "—" gösterir).
- **Room veritabanı** (profiller + abonelikler), **AppLockManager** (biyometrik),
  **BackupManager** (JSON dışa/içe aktarma), **IhvpnTileService** (Quick Settings),
  **AutoReconnectManager** (WorkManager).
- 4 ekran (Ana Sayfa/Profiller/Ayarlar/Loglar) gerçek Compose kodu, repository'ye bağlı.

## Xray-core entegrasyonu hakkında — 11 Ekim'de düzeltildi
Önceki sürümde `XrayCoreController` tamamen **yanlış/eski** bir API'ye göre yazılmıştı
(`V2RayPoint` / `V2RayVPNServiceSupportsSet` — kütüphanenin yıllar önce terk edilmiş
mimarisi). Gerçek GitHub kaynağına (`libv2ray_main.go`, `libv2ray_android.go`,
2dust/AndroidLibXrayLite main branch) bakılarak güncel, doğru API'ye
(`CoreController` / `CoreCallbackHandler`) göre yeniden yazıldı. Önemli mimari fark:

- Artık ayrı bir `tun2socks` köprüsüne **gerek yok** — `StartLoop(configContent, tunFd)`
  TUN'un ham dosya tanımlayıcısını doğrudan Xray-core'a veriyor, paket okuma/yazma
  Xray-core'un kendi içinde hallediliyor.
- Eski `protect(fd)` callback'i kaldırılmış. Bunun yerine `IhvpnVpnService`,
  `Builder.addDisallowedApplication(packageName)` ile kendi paketini VPN'in dışında
  tutuyor — Xray-core bu uygulamanın içinde (aynı process) çalıştığı için onun
  soketleri de otomatik olarak tünele hiç girmiyor. **Bu kısım benim en güçlü
  çıkarımım; kütüphane kaynağında bunu açıkça doğrulayan bir yorum bulamadım.**
  Gerçek cihazda test ederken bağlantı sonsuz döngüye girer ya da hiç veri akmazsa,
  önce burayı tekrar araştırmamız gerekecek.

Detaylı imzalar ve kaynaklar için `XrayCoreController.kt`'nin başındaki yorumu oku.

## Henüz eksik / senin tamamlaman gereken kısımlar (dürüstçe)
1. **QR kod tarama ekranı**: `zxing-android-embedded` bağımlılığı eklendi ama tarama
   ekranını başlatan `ActivityResultContract` kodu henüz yazılmadı.
3. **Pano/İhvpn ile aç akışının UI'sı**: deep link/clipboard verisi `ProfilesScreen`'e
   ulaşıyor ama "6 seçenekli + butonu" ayrı bir bottom sheet olarak henüz tasarlanmadı.
4. **BackupManager**'ın dosya seçici (SAF — `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`)
   entegrasyonu yok; şu an sadece JSON string üretme/okuma mantığı var.
5. **Gradle wrapper jar'ı** bu ortamda indirilemedi (ağ kısıtı) — Android Studio'yu ilk
   açışında otomatik tamamlayacaktır, ya da `gradle wrapper` komutunu bilgisayarında çalıştır.
6. **Launcher ikonu** placeholder — gerçek marka ikonuyla değiştirilmeli.
7. **Toplu ping/hız testi ekranı (spec madde)**: `PingTester.batchPing` fonksiyonu hazır,
   ama Profiller ekranında "tümünü test et" butonu henüz bağlanmadı.
8. **Loglar ekranı**: gerçek bir `LogBus` (SharedFlow) ile `XrayCoreController`'ın
   `onStatus` callback'i henüz birleştirilmedi — şu an placeholder metin var.

## GitHub'da otomatik derleme (Android Studio'suz)
`.github/workflows/android-build.yml` eklendi — bu repoyu GitHub'a push edince
otomatik olarak çalışır, hiçbir yerel Android Studio/SDK kurulumu gerekmez:

1. Bu klasörü yeni bir GitHub reposuna push et:
   ```
   cd ihvpn
   git init
   git add .
   git commit -m "İlk sürüm"
   git branch -M main
   git remote add origin https://github.com/<kullanici-adin>/ihvpn.git
   git push -u origin main
   ```
2. GitHub'da repo sayfasında **Actions** sekmesine git — "Android CI" iş akışı
   otomatik başlayacak (push olduğu an tetiklenir).
3. İş bitince (CI, Xray-core'u gomobile ile baştan derlediği için **ilk çalıştırmada
   yaklaşık 20-35 dakika** sürer — bu normal, Gradle'ın değil Go/Xray-core derlemesinin
   süresi) **Artifacts** bölümünden `ihvpn-debug-apk` dosyasını indir — içinde kurulabilir
   bir `.apk` var. Aynı bölümde `libv2ray-aar` de var — yerelde Android Studio ile
   derlemek istersen bunu indirip `app/libs/libv2ray.aar` olarak koyabilirsin.
4. **Release (imzasız) APK** için bir etiket (tag) push et:
   ```
   git tag v0.1.3
   git push origin v0.1.3
   ```
   Bu durumda ek olarak `ihvpn-release-apk-unsigned` artifact'i de üretilir —
   imzasız olduğu için cihaza kurmadan önce kendi keystore'unla imzalaman gerekir
   (`jarsigner`/`apksigner` veya Android Studio'nun "Generate Signed Bundle/APK" aracı).

**Not:** Workflow, `gradlew` dosyası repoda olmadığı için CI üzerinde
`gradle wrapper` komutuyla kendi üretiyor — bu yüzden ilk commit'te `gradlew`
dosyası eksik olsa da iş akışı çalışır. İstersen kendi makinende
`gradle wrapper --gradle-version 8.9` çalıştırıp `gradlew`/`gradlew.bat`
dosyalarını da repoya ekleyebilirsin, böylece yerel derleme de wrapper ile tutarlı olur.

## Test önceliği
Spec'teki 12 maddelik "gerçek bağlantı şartı" listesinden önce şunları doğrula:
1. VPN izni diyaloğu gerçekten çıkıyor mu (`MainActivity.requestVpnPermissionThenRun`)
2. `establish()` başarılı oluyor mu (Logcat'te `IhvpnVpnService` etiketini izle)
3. Bir VLESS profiliyle gerçekten internet erişimi var mı — ve İhvpn'in kendi
   paketi dışlandıktan sonra sonsuz döngü/bağlantı kopması olmuyor mu (yukarıdaki
   "protect" notuna bak — burada sorun çıkarsa önce o kısmı tekrar araştırmalıyız)

İyi çalışmalar — İshak.
