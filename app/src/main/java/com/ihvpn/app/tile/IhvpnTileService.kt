package com.ihvpn.app.tile

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ihvpn.app.vpn.IhvpnVpnService
import com.ihvpn.app.vpn.VpnNotificationHelper

/**
 * Hızlı ayarlar panelinden tek dokunuşla bağlan/kes.
 * Android kısıtı: VPN izni daha önce verilmemişse (VpnService.prepare() != null),
 * tile arka plandan izin diyaloğu açamaz — bu durumda ana uygulamayı açmak zorundayız.
 */
class IhvpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile(isConnected = IhvpnConnectionState.isConnected)
    }

    override fun onClick() {
        super.onClick()
        if (IhvpnConnectionState.isConnected) {
            startService(Intent(this, IhvpnVpnService::class.java).setAction(VpnNotificationHelper.ACTION_DISCONNECT))
            updateTile(isConnected = false)
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            // İzin daha önce verilmemiş: tile'dan doğrudan bağlanamayız, ana uygulamayı açmalıyız.
            val openApp = Intent(this, com.ihvpn.app.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("request_vpn_permission", true)
            startActivityAndCollapse(openApp)
            return
        }

        // İzin zaten var: seçili profille doğrudan bağlan (config'i MainActivity/Repository hazırlar,
        // burada basitlik için son bilinen config'in Application katmanında tutulduğu varsayılır).
        val lastConfig = IhvpnConnectionState.lastConfigJson
        if (lastConfig != null) {
            val svcIntent = Intent(this, IhvpnVpnService::class.java)
                .putExtra(IhvpnVpnService.EXTRA_CONFIG_JSON, lastConfig)
                .putExtra(IhvpnVpnService.EXTRA_PROFILE_NAME, IhvpnConnectionState.lastProfileName)
            startService(svcIntent)
            updateTile(isConnected = true)
        } else {
            startActivityAndCollapse(Intent(this, com.ihvpn.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun updateTile(isConnected: Boolean) {
        qsTile?.apply {
            state = if (isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isConnected) "İhvpn: Bağlı" else "İhvpn: Bağlı değil"
            updateTile()
        }
    }
}

/**
 * Basit süreç-içi durum köprüsü. Gerçek projede bunun yerine bir DataStore/Repository
 * StateFlow'u (VpnRepository) kullanılmalı; burada tile ile servis arasında hafif bir
 * paylaşım göstermek için minimal tutuldu.
 */
object IhvpnConnectionState {
    var isConnected: Boolean = false
    var lastConfigJson: String? = null
    var lastProfileName: String = ""
}
