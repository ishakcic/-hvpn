package com.ihvpn.app.repository

import com.ihvpn.app.data.db.AppDatabase
import com.ihvpn.app.data.db.ProfileEntity
import com.ihvpn.app.data.db.SubscriptionEntity
import com.ihvpn.app.parser.ParsedProfile
import com.ihvpn.app.parser.ProfileUriParser
import com.ihvpn.app.parser.SubscriptionFetcher
import com.ihvpn.app.security.SecureProfileStore
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProfileRepository(
    private val db: AppDatabase,
    private val fetcher: SubscriptionFetcher = SubscriptionFetcher()
) {
    fun observeProfiles(): Flow<List<ProfileEntity>> = db.profileDao().observeAll()
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>> = db.subscriptionDao().observeAll()

    /** Tek bir link (vless/vmess/trojan/ss) manuel eklenirken çağrılır. */
    suspend fun addManualProfile(rawUri: String): Result<Unit> = runCatching {
        val parsed = ProfileUriParser.parse(rawUri).getOrThrow()
        db.profileDao().upsert(parsed.toEntity(subscriptionId = null))
    }

    /** "Abonelik linki ekle" akışı: URL'i indirir, tüm profilleri (örn. 91 tanesini) kaydeder. */
    suspend fun addSubscription(name: String, url: String, autoUpdate: Boolean, intervalHours: Int): Result<Int> = runCatching {
        val subscription = SubscriptionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url,
            autoUpdate = autoUpdate,
            updateIntervalHours = intervalHours
        )
        val result = refreshSubscriptionInternal(subscription)
        result.getOrThrow()
    }

    /** Var olan bir aboneliği yeniden çeker (manuel "güncelle" ya da otomatik döngü). */
    suspend fun refreshSubscription(subscriptionId: String): Result<Int> = runCatching {
        val existing = db.subscriptionDao().getById(subscriptionId)
            ?: throw IllegalArgumentException("Abonelik bulunamadı: $subscriptionId")
        refreshSubscriptionInternal(existing).getOrThrow()
    }

    private suspend fun refreshSubscriptionInternal(subscription: SubscriptionEntity): Result<Int> = runCatching {
        val fetchResult = fetcher.fetch(subscription.url).getOrThrow()

        val updated = subscription.copy(
            lastUpdatedEpochMs = System.currentTimeMillis(),
            remainingBytes = fetchResult.userInfo?.remainingBytes,
            remainingDays = fetchResult.userInfo?.remainingDays,
            profileCount = fetchResult.profiles.size
        )
        db.subscriptionDao().upsert(updated)

        db.profileDao().deleteForSubscription(subscription.id)
        val entities = fetchResult.profiles.mapIndexed { index, parsed ->
            parsed.toEntity(subscriptionId = subscription.id, order = index)
        }
        db.profileDao().upsertAll(entities)

        if (fetchResult.skippedInvalid > 0) {
            // Çağıran taraf (ViewModel) bunu kullanıcıya "N profil ayrıştırılamadı" olarak gösterebilir.
        }
        fetchResult.profiles.size
    }

    suspend fun selectProfile(id: String) {
        db.profileDao().clearSelection()
        db.profileDao().select(id)
    }

    /** Bağlanma anında gerçek URI'yi (Xray config üretmek için) şifresi çözülmüş olarak döndürür. */
    suspend fun decryptUri(profile: ProfileEntity): String =
        SecureProfileStore.decrypt(profile.encryptedUriBase64, profile.ivBase64)

    suspend fun resetAllCounters() = db.profileDao().resetAllCounters()
    suspend fun resetCountersForSubscription(id: String) = db.profileDao().resetCountersForSubscription(id)
    suspend fun deleteProfile(profile: ProfileEntity) = db.profileDao().delete(profile)

    private fun ParsedProfile.toEntity(subscriptionId: String?, order: Int = 0): ProfileEntity {
        val payload = SecureProfileStore.encrypt(rawUri)
        return ProfileEntity(
            subscriptionId = subscriptionId,
            name = name,
            protocol = protocol.name,
            address = address,
            port = port,
            network = network,
            security = security,
            isXhttp = isXhttp,
            encryptedUriBase64 = payload.cipherTextBase64,
            ivBase64 = payload.ivBase64,
            sortOrder = order
        )
    }
}
