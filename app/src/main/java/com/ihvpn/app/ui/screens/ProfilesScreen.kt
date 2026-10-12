package com.ihvpn.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihvpn.app.IhvpnApplication
import com.ihvpn.app.data.db.ProfileEntity
import com.ihvpn.app.data.db.SubscriptionEntity
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(application: IhvpnApplication, incomingDeepLinkUri: String?) {
    val repository = application.profileRepository
    val scope = rememberCoroutineScope()

    val profiles by repository.observeProfiles().collectAsState(initial = emptyList())
    val subscriptions by repository.observeSubscriptions().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // "İhvpn ile aç" ile gelen bir link varsa otomatik manuel profil olarak eklenir.
    LaunchedEffect(incomingDeepLinkUri) {
        incomingDeepLinkUri?.let { uri ->
            repository.addManualProfile(uri).onFailure { errorMessage = it.message }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Profil ekle")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(subscriptions) { sub ->
                SubscriptionGroup(
                    subscription = sub,
                    profiles = profiles.filter { it.subscriptionId == sub.id },
                    onSelect = { scope.launch { repository.selectProfile(it.id) } },
                    onRefresh = { scope.launch { repository.refreshSubscription(sub.id) } }
                )
            }

            val manual = profiles.filter { it.subscriptionId == null }
            if (manual.isNotEmpty()) {
                item { Text("Manuel eklenenler", modifier = Modifier.padding(16.dp)) }
                items(manual) { p ->
                    ProfileRow(p, onSelect = { scope.launch { repository.selectProfile(p.id) } })
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                scope.launch {
                    repository.addSubscription(name, url, autoUpdate = true, intervalHours = 12)
                        .onFailure { errorMessage = it.message }
                }
                showAddDialog = false
            },
            onManualLink = { rawUri ->
                scope.launch {
                    repository.addManualProfile(rawUri).onFailure { errorMessage = it.message }
                }
                showAddDialog = false
            }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Tamam") } },
            title = { Text("Hata") },
            text = { Text(msg) }
        )
    }
}

@Composable
private fun SubscriptionGroup(
    subscription: SubscriptionEntity,
    profiles: List<ProfileEntity>,
    onSelect: (ProfileEntity) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text("${subscription.name}  (${subscription.profileCount})")
            }
            TextButton(onClick = onRefresh) { Text("Güncelle") }
        }
        Text(
            "${subscription.remainingBytes?.let { it / 1_000_000_000 } ?: "—"} GB kaldı · ${subscription.remainingDays ?: "—"} gün kaldı",
            style = MaterialTheme.typography.labelSmall
        )
        if (expanded) {
            profiles.forEach { p -> ProfileRow(p, onSelect = { onSelect(p) }) }
        }
    }
}

@Composable
private fun ProfileRow(profile: ProfileEntity, onSelect: () -> Unit) {
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                Row {
                    AssistChip(onClick = {}, label = { Text(profile.protocol) })
                    if (profile.isXhttp) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text("xhttp") })
                    }
                }
            }
            if (profile.isSelected) Text("✓") else Text(profile.pingMs?.let { "$it ms" } ?: "")
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    onManualLink: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var manualLink by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil Ekle") },
        text = {
            Column {
                Text("Abonelik linki ekle", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profil Adı") })
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Abonelik URL’si") })
                Spacer(Modifier.height(12.dp))
                Text("veya panodan/tek bir link (vless://, vmess://, trojan://, ss://)", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(value = manualLink, onValueChange = { manualLink = it }, label = { Text("Link Ekle") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (manualLink.isNotBlank()) onManualLink(manualLink)
                else if (url.isNotBlank()) onConfirm(name.ifBlank { "Abonelik" }, url)
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
