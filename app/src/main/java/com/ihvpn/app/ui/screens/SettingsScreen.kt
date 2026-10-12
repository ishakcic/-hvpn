package com.ihvpn.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ihvpn.app.IhvpnApplication
import com.ihvpn.app.MainActivity
import kotlinx.coroutines.launch

private const val PREFS_NAME = "ihvpn_settings"

@Composable
fun SettingsScreen(application: IhvpnApplication, activity: MainActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var killSwitch by remember { mutableStateOf(prefs.getBoolean("kill_switch", true)) }
    var dnsProtection by remember { mutableStateOf(prefs.getBoolean("dns_protection", true)) }
    var autoReconnect by remember { mutableStateOf(prefs.getBoolean("auto_reconnect", false)) }
    var appLock by remember { mutableStateOf(prefs.getBoolean("app_lock", false)) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    fun save(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoringBatteryOpt = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        SettingSwitchRow("Kill switch", killSwitch) { killSwitch = it; save("kill_switch", it) }
        SettingSwitchRow("DNS sızıntı koruması", dnsProtection) { dnsProtection = it; save("dns_protection", it) }
        SettingSwitchRow("Otomatik yeniden bağlan", autoReconnect) { autoReconnect = it; save("auto_reconnect", it) }
        SettingSwitchRow("Uygulama kilidi", appLock) { enabled ->
            if (enabled && !com.ihvpn.app.security.AppLockManager.isBiometricAvailable(activity)) {
                return@SettingSwitchRow // cihazda biyometri/PIN yoksa açılmasın
            }
            appLock = enabled; save("app_lock", enabled)
        }

        if (!ignoringBatteryOpt) {
            Spacer(Modifier.height(12.dp))
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Bağlantı kararlılığı için pil optimizasyonundan muaf tutun", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                    }) { Text("İzin ver") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { showResetConfirm = true }) { Text("Trafik sayaçlarını sıfırla") }

        Spacer(Modifier.height(16.dp))
        ElevatedCard(onClick = { aboutExpanded = !aboutExpanded }) {
            Column(Modifier.padding(12.dp)) {
                Text("Hakkında")
                if (aboutExpanded) {
                    Spacer(Modifier.height(8.dp))
                    Text("İhvpn", style = MaterialTheme.typography.titleMedium)
                    Text("Sürüm 0.1.3", style = MaterialTheme.typography.bodySmall)
                    Text("Yapımcı: İshak Çiçekliyüz", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Sayaçları sıfırla") },
            text = { Text("Tüm profillerin toplam indirme/yükleme sayaçları sıfırlanacak. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { application.profileRepository.resetAllCounters() }
                    showResetConfirm = false
                }) { Text("Sıfırla") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
