package com.ihvpn.app.model

/**
 * VPN bağlantı durumu.
 * Bu durum SADECE gerçek VpnService/Xray-core geri bildirimiyle değişmelidir.
 * UI katmanı hiçbir zaman sahte "Bağlandı" durumu üretmemelidir.
 */
enum class ConnectionState {
    DISCONNECTED,   // Bağlantı kesildi
    CONNECTING,     // Bağlanıyor
    CONNECTED,      // Bağlandı
    ERROR           // Hata (örn. xhttp desteklenmiyor, izin reddedildi vb.)
}

enum class ProtocolType(val label: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    UNKNOWN("Bilinmiyor")
}

/**
 * Tek bir sunucu/profil kaydı.
 * ping/downloadSpeed/uploadSpeed/totalDown/totalUp alanları yalnızca
 * gerçek ölçüm sonuçlarıyla doldurulmalı; ölçüm yoksa null bırakılmalı,
 * UI'da "—" gösterilmeli.
 */
data class VpnProfile(
    val id: String,
    val name: String,
    val protocol: ProtocolType,
    val serverAddress: String,
    val isSelected: Boolean = false,
    val supportsXhttp: Boolean = false,
    val pingMs: Int? = null,               // null = henüz ölçülmedi
    val downloadSpeedBps: Long? = null,
    val uploadSpeedBps: Long? = null,
    val totalDownBytes: Long = 0L,
    val totalUpBytes: Long = 0L
)

/**
 * Ana ekranda gösterilen canlı bağlantı istatistikleri.
 * Bunlar VpnService/Xray-core'dan gelen gerçek verilerle güncellenmelidir.
 */
data class ConnectionStats(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val activeProfileName: String? = null,
    val pingMs: Int? = null,
    val downloadSpeedBps: Long? = null,
    val uploadSpeedBps: Long? = null,
    val totalDownBytes: Long = 0L,
    val totalUpBytes: Long = 0L,
    val errorMessage: String? = null
)
