package com.ihvpn.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Görüntüleme alanları (name/protocol/address/network) düz metin tutulur —
 * bunlar liste ekranında şifre çözmeden gösterilmesi gereken meta verilerdir.
 *
 * `encryptedUriBase64` + `ivBase64` ise gerçek bağlantı sırrını (UUID/şifre dahil
 * tam vless://vmess:// linkini) Android Keystore ile şifrelenmiş halde tutar.
 * Şifre çözme işlemi sadece SecureProfileStore üzerinden, bağlanma anında yapılır.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subscriptionId: String?,          // null ise manuel eklenmiş
    val name: String,
    val protocol: String,                 // ProtocolType.name
    val address: String,
    val port: Int,
    val network: String,
    val security: String,
    val isXhttp: Boolean,
    val encryptedUriBase64: String,
    val ivBase64: String,
    val isSelected: Boolean = false,
    val pingMs: Int? = null,
    val lastValidatedEpochMs: Long? = null,
    val isValid: Boolean? = null,         // profil kaydetmeden önce doğrulama testinin sonucu
    val totalDownBytes: Long = 0L,
    val totalUpBytes: Long = 0L,
    val sortOrder: Int = 0
)
