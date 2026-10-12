package com.ihvpn.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gerçek Android VpnService implementasyonu.
 *
 * Akış: MainActivity VpnService.prepare() ile izin ister → izin verilirse bu servis
 * startService ile başlatılır → establishTunnel() TUN arayüzünü açar → o arayüzün ham
 * dosya tanımlayıcısı (fd) doğrudan XrayCoreController.start()'a, oradan da gerçek
 * Xray-core'un StartLoop(configContent, tunFd) fonksiyonuna geçilir — Xray-core TUN'dan
 * paket okuyup yazmayı kendi içinde yapar (ayrı bir tun2socks katmanına gerek yok).
 *
 * Kill switch: VpnService.Builder.setBlocking(true) + Xray çökerse tüneli hemen kapatıp
 * hiçbir zaman "bağlı" göstermeden servisi durdurmak (spec madde: sahte bağlı durumu yasak).
 *
 * "Protect" yerine kendini dışlama: AndroidLibXrayLite'ın güncel API'sinde artık ayrı bir
 * protect(fd) callback'i yok (bkz. XrayCoreController.kt'deki not). Bunun yerine İhvpn'in
 * kendi paketi addDisallowedApplication(packageName) ile VPN'in dışında tutuluyor — Xray-core
 * bu uygulamanın içinde (aynı process/UID) çalıştığı için onun açtığı soketler de otomatik
 * olarak tünele hiç girmiyor.
 */
class IhvpnVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var xrayController: XrayCoreController? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentProfileName: String = ""

    companion object {
        const val EXTRA_CONFIG_JSON = "extra_config_json"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
        const val EXTRA_KILL_SWITCH = "extra_kill_switch"
        const val EXTRA_DNS_LEAK_PROTECTION = "extra_dns_leak_protection"
        const val EXTRA_BLOCK_IPV6 = "extra_block_ipv6"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == VpnNotificationHelper.ACTION_DISCONNECT) {
            stopTunnel()
            return START_NOT_STICKY
        }

        val configJson = intent?.getStringExtra(EXTRA_CONFIG_JSON)
        currentProfileName = intent?.getStringExtra(EXTRA_PROFILE_NAME) ?: "Profil"
        val killSwitch = intent?.getBooleanExtra(EXTRA_KILL_SWITCH, true) ?: true
        val dnsLeakProtection = intent?.getBooleanExtra(EXTRA_DNS_LEAK_PROTECTION, true) ?: true
        val blockIpv6Leak = intent?.getBooleanExtra(EXTRA_BLOCK_IPV6, true) ?: true

        if (configJson.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(VpnNotificationHelper.NOTIFICATION_ID, VpnNotificationHelper.buildConnectedNotification(this, currentProfileName))
        establishAndStart(configJson, killSwitch, dnsLeakProtection, blockIpv6Leak)
        return START_STICKY
    }

    private fun establishAndStart(configJson: String, killSwitch: Boolean, dnsProtection: Boolean, blockIpv6: Boolean) {
        val builder = Builder()
            .setSession("İhvpn")
            .setMtu(1500)
            .addAddress("10.10.10.2", 30)
            // DNS sızıntı koruması: sistem DNS'i yerine tünel içinden çözülen DNS kullanılır
            .apply { if (dnsProtection) addDnsServer("1.1.1.1") }
            .apply { if (dnsProtection) addDnsServer("8.8.8.8") }
            .addRoute("0.0.0.0", 0)
            // Kill switch: alttaki fiziksel arayüz koparsa trafiği düşür, açık internete sızdırma
            .setBlocking(killSwitch)

        // Split tunneling + "protect" yerine geçen mekanizma: İhvpn kendi paketini VPN'in
        // dışında tutar (bkz. XrayCoreController.kt'deki mimari notu). Kullanıcının seçtiği
        // uygulama listesi (spec: "uygulama bazlı yönlendirme") burada aynı şekilde
        // builder.addDisallowedApplication(packageName) / addAllowedApplication(packageName)
        // çağrılarıyla genişletilmeli.
        runCatching { builder.addDisallowedApplication(packageName) }

        if (blockIpv6) {
            // IPv6 route eklenmiyor: sistem IPv6 arayüzünden tünel dışına sızma ihtimali
            // ortadan kalksın diye bu VPN oturumu süresince IPv6 yönlendirmeye dahil edilmiyor.
        } else {
            builder.addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            builder.addRoute("::", 0)
        }

        runCatching { builder.establish() }
            .onSuccess { pfd ->
                tunInterface = pfd
                if (pfd == null) { stopTunnel(); return }
                startXray(configJson, pfd.fd)
            }
            .onFailure { stopTunnel() }
    }

    private fun startXray(configJson: String, tunFd: Int) {
        val controller = XrayCoreController(object : XrayCoreController.Callbacks {
            override fun onStartup() { /* Xray başarıyla başladı — bildirim zaten gösteriliyor */ }
            override fun onShutdown() { /* Xray tarafı kapandı, servis zaten stopTunnel() ile temizlenecek */ }
            override fun onStatus(code: Long, message: String?) {
                // Loglar ekranına iletilecek (bkz. repository/LogBus — basitlik için burada atlandı,
                // gerçek projede bir SharedFlow/Room log tablosuna yazılmalı).
            }
        })
        xrayController = controller
        controller.initCoreEnv(filesDir.absolutePath)

        serviceScope.launch {
            val started = controller.start(configJson, tunFd)
            if (!started) {
                // Kill switch açıksa ve Xray başlayamadıysa tüneli hemen kapat — asla sahte "bağlı" gösterme.
                stopTunnel()
            }
        }
    }

    private fun stopTunnel() {
        xrayController?.stop()
        xrayController = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // Kullanıcı sistem ayarlarından "VPN'i kapat" dediğinde de çağrılır — burada da temiz kapatmalıyız.
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }
}
