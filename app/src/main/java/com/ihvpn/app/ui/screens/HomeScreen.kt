package com.ihvpn.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihvpn.app.IhvpnApplication
import com.ihvpn.app.MainActivity
import com.ihvpn.app.data.db.ProfileEntity
import com.ihvpn.app.model.ConnectionState
import com.ihvpn.app.parser.ProfileUriParser
import com.ihvpn.app.parser.XrayConfigBuilder
import com.ihvpn.app.tile.IhvpnConnectionState
import com.ihvpn.app.vpn.IhvpnVpnService
import com.ihvpn.app.vpn.TrafficStatsMonitor
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(application: IhvpnApplication, activity: MainActivity) {
    val repository = application.profileRepository
    val scope = rememberCoroutineScope()

    val profiles by repository.observeProfiles().collectAsState(initial = emptyList())
    val selected: ProfileEntity? = profiles.firstOrNull { it.isSelected }

    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var downSpeed by remember { mutableStateOf<Long?>(null) }
    var upSpeed by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            TrafficStatsMonitor().liveSpeedFlow().collect { sample ->
                downSpeed = sample.downloadBytesPerSec
                upSpeed = sample.uploadBytesPerSec
            }
        } else {
            downSpeed = null
            upSpeed = null
        }
    }

    fun startConnection(profile: ProfileEntity) {
        connectionState = ConnectionState.CONNECTING
        scope.launch {
            val rawUri = repository.decryptUri(profile)
            val parsed = ProfileUriParser.parse(rawUri).getOrNull()
            if (parsed == null) {
                connectionState = ConnectionState.ERROR
                return@launch
            }
            val configJson = XrayConfigBuilder.build(parsed)

            activity.requestVpnPermissionThenRun { granted ->
                if (!granted) {
                    connectionState = ConnectionState.DISCONNECTED
                    return@requestVpnPermissionThenRun
                }
                IhvpnConnectionState.lastConfigJson = configJson
                IhvpnConnectionState.lastProfileName = profile.name
                IhvpnConnectionState.isConnected = true

                val intent = Intent(activity, IhvpnVpnService::class.java)
                    .putExtra(IhvpnVpnService.EXTRA_CONFIG_JSON, configJson)
                    .putExtra(IhvpnVpnService.EXTRA_PROFILE_NAME, profile.name)
                    .putExtra(IhvpnVpnService.EXTRA_KILL_SWITCH, true)
                    .putExtra(IhvpnVpnService.EXTRA_DNS_LEAK_PROTECTION, true)
                    .putExtra(IhvpnVpnService.EXTRA_BLOCK_IPV6, true)
                activity.startService(intent)
                connectionState = ConnectionState.CONNECTED
            }
        }
    }

    fun disconnect() {
        val intent = Intent(activity, IhvpnVpnService::class.java)
            .setAction(com.ihvpn.app.vpn.VpnNotificationHelper.ACTION_DISCONNECT)
        activity.startService(intent)
        IhvpnConnectionState.isConnected = false
        connectionState = ConnectionState.DISCONNECTED
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("İhvpn", style = MaterialTheme.typography.headlineLarge)
        Text("v0.1.3 · İshak Çiçekliyüz", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(28.dp))

        FilledIconButton(
            onClick = {
                if (connectionState == ConnectionState.CONNECTED) {
                    disconnect()
                } else if (selected != null) {
                    startConnection(selected)
                }
            },
            enabled = selected != null || connectionState == ConnectionState.CONNECTED,
            modifier = Modifier.size(140.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Bağlan/Kes", modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when (connectionState) {
                ConnectionState.CONNECTED -> "Bağlandı"
                ConnectionState.CONNECTING -> "Bağlanıyor"
                ConnectionState.ERROR -> "Hata: profil geçersiz"
                ConnectionState.DISCONNECTED -> "Bağlı değil"
            }
        )
        Text(selected?.name ?: "Profil seçilmedi", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("İndirme", downSpeed?.let { formatSpeed(it) } ?: "—")
            StatCard("Yükleme", upSpeed?.let { formatSpeed(it) } ?: "—")
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    ElevatedCard(modifier = Modifier.padding(4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    val kbps = bytesPerSec / 1024.0
    return if (kbps < 1024) "%.1f KB/s".format(kbps) else "%.1f MB/s".format(kbps / 1024.0)
}
