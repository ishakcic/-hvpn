package com.ihvpn.app.backup

import com.ihvpn.app.data.db.AppDatabase
import com.ihvpn.app.data.db.SubscriptionEntity
import com.ihvpn.app.parser.ProfileUriParser
import com.ihvpn.app.repository.ProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Spec madde: yedekleme/dışa aktarma. Şifreli alanları (encryptedUriBase64) DEĞİL,
 * her profilin çözülmüş ham linkini (vless://...) dışa aktarır — böylece yedek dosyası
 * başka bir cihaza/kişiye taşınabilir bir "profil listesi" olur, ama diskte dururken
 * İhvpn'in kendi veritabanı yine şifreli kalır (SecureProfileStore).
 *
 * Kullanıcı bu JSON dosyasını kendi sorumluluğunda saklamalı; içinde düz metin
 * bağlantı sırları vardır.
 */
@Serializable
data class BackupSubscription(val name: String, val url: String, val autoUpdate: Boolean, val updateIntervalHours: Int)

@Serializable
data class BackupManualProfile(val rawUri: String)

@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAtEpochMs: Long,
    val subscriptions: List<BackupSubscription>,
    val manualProfiles: List<BackupManualProfile>
)

class BackupManager(
    private val db: AppDatabase,
    private val repository: ProfileRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(): String {
        val subs = db.subscriptionDao().getAllOnce().map {
            BackupSubscription(it.name, it.url, it.autoUpdate, it.updateIntervalHours)
        }
        val manualEntities = db.profileDao().getManualOnce()
        val manualProfiles = manualEntities.map { entity ->
            BackupManualProfile(rawUri = repository.decryptUri(entity))
        }

        val backup = BackupFile(
            exportedAtEpochMs = System.currentTimeMillis(),
            subscriptions = subs,
            manualProfiles = manualProfiles
        )
        return json.encodeToString(backup)
    }

    suspend fun import(jsonText: String): Result<Int> = runCatching {
        val backup = json.decodeFromString(BackupFile.serializer(), jsonText)
        var imported = 0

        backup.subscriptions.forEach { sub ->
            repository.addSubscription(sub.name, sub.url, sub.autoUpdate, sub.updateIntervalHours)
                .onSuccess { imported += it }
        }
        backup.manualProfiles.forEach { profile ->
            ProfileUriParser.parse(profile.rawUri).onSuccess {
                repository.addManualProfile(profile.rawUri)
                imported += 1
            }
        }
        imported
    }
}
