package com.ihvpn.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Gerçek log akışı için: XrayCoreController.Callbacks.onStatus çıktısını
 * bir SharedFlow<String> (örn. LogBus objesinde) toplayıp burada collectAsState
 * ile göstermek önerilir. Bu ekran o bağlantının UI ucudur; kaynak taraf
 * (bkz. IhvpnVpnService.startXray -> onStatus) not düşülerek bırakıldı.
 */
@Composable
fun LogsScreen() {
    val placeholderLogs = listOf(
        "Servis başlatılmayı bekliyor…"
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Loglar", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(placeholderLogs) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
