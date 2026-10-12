package com.ihvpn.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.ihvpn.app.ui.nav.IhvpnNavHost
import com.ihvpn.app.ui.theme.IhvpnTheme

/**
 * FragmentActivity'den türetildi çünkü androidx.biometric.BiometricPrompt
 * bir FragmentActivity/AppCompatActivity gerektirir (App Lock için).
 */
class MainActivity : FragmentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingVpnStart?.invoke(result.resultCode == RESULT_OK)
        pendingVpnStart = null
    }

    /** Bir bağlanma isteği VPN izni bekliyorsa burada tutulur; izin sonucu gelince çağrılır. */
    var pendingVpnStart: ((granted: Boolean) -> Unit)? = null

    /** Ana ekranın erişip kullanabilmesi için: izin akışını başlatan gerçek fonksiyon. */
    fun requestVpnPermissionThenRun(onResult: (granted: Boolean) -> Unit) {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            onResult(true) // izin zaten verilmiş
        } else {
            pendingVpnStart = onResult
            vpnPermissionLauncher.launch(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deepLinkUri = intent?.data?.toString()
        val requestedVpnPermissionFromTile = intent?.getBooleanExtra("request_vpn_permission", false) == true

        setContent {
            IhvpnTheme {
                IhvpnNavHost(
                    application = application as IhvpnApplication,
                    activity = this,
                    incomingDeepLinkUri = deepLinkUri,
                    autoRequestVpnPermission = requestedVpnPermissionFromTile
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // "İhvpn ile aç" ile uygulama zaten açıkken yeni bir link geldiğinde de
        // IhvpnNavHost'un bunu yakalaması için burada bir paylaşılan durum/callback
        // (örn. bir SharedFlow) tetiklenmelidir. Basitlik için activity recreate ile
        // ilk onCreate akışına güveniliyor; gerçek projede bunu bir StateFlow ile değiştir.
    }
}
