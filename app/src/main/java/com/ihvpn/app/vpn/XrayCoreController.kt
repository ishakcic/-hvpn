package com.ihvpn.app.vpn

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * com.github.2dust:AndroidLibXrayLite'ın GERÇEK (doğrulanmış) Kotlin sarmalayıcısı.
 *
 * Bu sürüm, kütüphanenin GitHub'daki güncel kaynağından (libv2ray_main.go,
 * libv2ray_android.go — 2dust/AndroidLibXrayLite, main branch, Ekim 2026) birebir
 * doğrulanarak yazıldı. Önceki sürümde kullandığım `V2RayPoint` / `Libv2ray.newV2RayPoint`
 * / `V2RayVPNServiceSupportsSet` API'si TAMAMEN YANLIŞTI — bu, kütüphanenin yıllar önce
 * terk edilmiş eski mimarisiydi. Gerçek, güncel API şu:
 *
 *   func InitCoreEnv(envPath string, key string)
 *   func NewCoreController(s CoreCallbackHandler) *CoreController
 *   func (x *CoreController) StartLoop(configContent string, tunFd int32) (err error)
 *   func (x *CoreController) StopLoop() error
 *   func (x *CoreController) QueryStats(tag string, direct string) int64
 *   func (x *CoreController) MeasureDelay(url string) (int64, error)
 *   func MeasureOutboundDelay(ConfigureFileContent string, url string) (int64, error)
 *   type CoreController struct { CallbackHandler CoreCallbackHandler; IsRunning bool; ... }
 *   type CoreCallbackHandler interface {
 *       Startup() int
 *       Shutdown() int
 *       OnEmitStatus(int, string) int
 *   }
 *
 * ÖNEMLİ MİMARİ FARK — artık ayrı bir "Protect(fd)" callback'i YOK:
 * StartLoop artık TUN dosya tanımlayıcısını (tunFd) doğrudan alıyor ve Xray-core
 * paketleri TUN'dan okuyup yazmayı kendi içinde hallediyor (ayrı bir tun2socks
 * katmanına GEREK YOK — önceki sürümde söylediğim "kritik eksik" artık geçersiz).
 * Peki Xray-core'un DIŞARI açtığı soketler tekrar VPN tüneline düşüp sonsuz
 * döngü oluşturmasın diye nasıl korunuyor? Kütüphanenin güncel kaynağında
 * buna özel bir Kotlin callback'i YOK. En olası ve mantıklı açıklama — ki
 * IhvpnVpnService.kt'de zaten uyguluyorum — İhvpn'in kendi paketini
 * `Builder.addDisallowedApplication(packageName)` ile VPN'in dışında tutması:
 * Xray-core bu uygulamanın İÇİNDE (aynı process, aynı UID) çalıştığı için,
 * uygulamanın kendisi tünelin dışında tutulduğunda onun açtığı TÜM soketler
 * (Xray-core'un dışarı bağlantıları dahil) otomatik olarak tünele hiç girmiyor.
 * Bunu %100 kaynak koduyla doğrulayamadım (kütüphanede buna dair açık bir yorum
 * yok) — bu benim en güçlü çıkarımım, kesin bir dokümantasyon referansı değil.
 * Eğer gerçek cihazda bağlantı kurulduktan sonra sonsuz döngü/bağlantı kopması
 * gözlemlersen, bu konuyu tekrar araştırmam gerekecek.
 *
 * Referans: https://github.com/2dust/AndroidLibXrayLite (paket adı: libv2ray)
 */
class XrayCoreController(
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onStartup()
        fun onShutdown()
        fun onStatus(code: Long, message: String?)
    }

    private var controller: CoreController? = null

    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long { callbacks.onStartup(); return 0L }
        override fun shutdown(): Long { callbacks.onShutdown(); return 0L }
        override fun onEmitStatus(code: Long, message: String?): Long {
            callbacks.onStatus(code, message)
            return 0L
        }
    }

    /** geoip.dat/geosite.dat'ın bulunduğu dizin + (opsiyonel) XUDP taban anahtarı. */
    fun initCoreEnv(assetDirPath: String, xudpBaseKey: String = "") {
        Libv2ray.initCoreEnv(assetDirPath, xudpBaseKey)
    }

    /**
     * Tüneli başlatır. tunFd, VpnService.Builder.establish() ile açılan
     * ParcelFileDescriptor'ın ham fd'sidir (pfd.fd) — TUN kullanılmıyorsa 0 verilebilir.
     */
    fun start(configJson: String, tunFd: Int): Boolean {
        val c = Libv2ray.newCoreController(callbackHandler)
        controller = c
        return try {
            c.startLoop(configJson, tunFd)
            true
        } catch (e: Exception) {
            callbacks.onStatus(-1L, "Xray başlatılamadı: ${e.message}")
            controller = null
            false
        }
    }

    fun stop() {
        try {
            controller?.stopLoop()
        } catch (e: Exception) {
            callbacks.onStatus(-1L, "Xray durdurulurken hata: ${e.message}")
        } finally {
            controller = null
        }
    }

    fun isRunning(): Boolean = controller?.isRunning ?: false

    /** Ana Sayfa'daki gerçek indirme/yükleme sayaçları için. tag genelde "proxy" olur. */
    fun queryStats(tag: String = "proxy"): TrafficSnapshot {
        val c = controller ?: return TrafficSnapshot(0, 0)
        val up = c.queryStats(tag, "uplink")
        val down = c.queryStats(tag, "downlink")
        return TrafficSnapshot(uplinkBytes = up, downlinkBytes = down)
    }

    /** Çalışan tünel üzerinden gerçek gecikme ölçümü. */
    fun measureDelay(testUrl: String = ""): Long =
        runCatching { controller?.measureDelay(testUrl) }.getOrNull() ?: -1L

    /**
     * Profil kaydetmeden önce arka planda gerçek gecikme testi (spec madde 11) —
     * ayrı, geçici bir Xray instance'ı açıp kapatarak test eder, mevcut tüneli etkilemez.
     */
    fun measureOutboundDelay(configJson: String, testUrl: String = ""): Long =
        runCatching { Libv2ray.measureOutboundDelay(configJson, testUrl) }.getOrDefault(-1L)
}

data class TrafficSnapshot(val uplinkBytes: Long, val downlinkBytes: Long)
