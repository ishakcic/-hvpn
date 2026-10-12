package com.ihvpn.app.parser

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Hiddify panelinin "subscription-userinfo" header formatını temsil eder. */
data class SubscriptionUserInfo(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expireEpochSeconds: Long?
) {
    val remainingBytes: Long get() = (totalBytes - uploadBytes - downloadBytes).coerceAtLeast(0)
    val remainingDays: Long? get() = expireEpochSeconds?.let {
        ((it - System.currentTimeMillis() / 1000) / 86400).coerceAtLeast(0)
    }
}

data class SubscriptionFetchResult(
    val profiles: List<ParsedProfile>,
    val userInfo: SubscriptionUserInfo?,
    val skippedInvalid: Int
)

class SubscriptionFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    /**
     * Bir Hiddify/aboneli link indirir. Panel, User-Agent'a göre düz metin
     * (satır satır vless://, vmess:// ... linkleri) ya da base64 kodlanmış
     * aynı içeriği dönebilir — ikisini de otomatik algılar.
     */
    fun fetch(url: String): Result<SubscriptionFetchResult> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "İhvpn/0.1.3 (Android; Xray-core)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Abonelik indirilemedi: HTTP ${response.code}")
            }
            val bodyText = response.body?.string().orEmpty()
            val userInfoHeader = response.header("subscription-userinfo")
            val decodedBody = decodeIfBase64(bodyText)

            var skipped = 0
            val profiles = decodedBody.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    ProfileUriParser.parse(line).getOrElse { skipped++; null }
                }

            SubscriptionFetchResult(
                profiles = profiles,
                userInfo = userInfoHeader?.let(::parseUserInfoHeader),
                skippedInvalid = skipped
            )
        }
    }

    private fun decodeIfBase64(raw: String): String {
        val looksLikeUriList = raw.contains("://")
        if (looksLikeUriList) return raw
        return runCatching {
            String(Base64.decode(raw.trim(), Base64.DEFAULT))
        }.getOrDefault(raw)
    }

    /** Örnek: "upload=301000000; download=21090000000; total=1048576000000; expire=1780000000" */
    private fun parseUserInfoHeader(header: String): SubscriptionUserInfo {
        val map = header.split(";")
            .mapNotNull {
                val parts = it.trim().split("=")
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap()

        return SubscriptionUserInfo(
            uploadBytes = map["upload"]?.toLongOrNull() ?: 0L,
            downloadBytes = map["download"]?.toLongOrNull() ?: 0L,
            totalBytes = map["total"]?.toLongOrNull() ?: 0L,
            expireEpochSeconds = map["expire"]?.toLongOrNull()
        )
    }
}
