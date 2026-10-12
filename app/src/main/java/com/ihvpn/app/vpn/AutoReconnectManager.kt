package com.ihvpn.app.vpn

import android.content.Context
import androidx.work.*
import com.ihvpn.app.IhvpnApplication
import com.ihvpn.app.tile.IhvpnConnectionState
import java.util.concurrent.TimeUnit

/**
 * Spec: "otomatik yeniden bağlanma" + "otomatik aylık sıfırlama (abonelik tarifini baz alarak)".
 * Gerçek ağ kopması algılama (ConnectivityManager.NetworkCallback) IhvpnVpnService'e eklenip
 * kopma anında bu Worker'ı tetiklemesi gerekir — burada zamanlama/iskelet kısmı gerçek WorkManager
 * API'siyle kurulu, tetikleme noktası olarak not düşüldü.
 */
class ReconnectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val configJson = IhvpnConnectionState.lastConfigJson ?: return Result.success()
        val intent = android.content.Intent(applicationContext, IhvpnVpnService::class.java)
            .putExtra(IhvpnVpnService.EXTRA_CONFIG_JSON, configJson)
            .putExtra(IhvpnVpnService.EXTRA_PROFILE_NAME, IhvpnConnectionState.lastProfileName)
        applicationContext.startService(intent)
        return Result.success()
    }
}

class TrafficResetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IhvpnApplication
        app.profileRepository.resetAllCounters()
        return Result.success()
    }
}

object AutoReconnectManager {
    fun scheduleImmediateReconnect(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReconnectWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /** Abonelik döngüsüne göre (takvimden bağımsız) N gün sonra tetiklenecek tek seferlik sıfırlama. */
    fun scheduleSubscriptionCycleReset(context: Context, daysUntilCycleEnd: Long) {
        val request = OneTimeWorkRequestBuilder<TrafficResetWorker>()
            .setInitialDelay(daysUntilCycleEnd, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("subscription_cycle_reset", ExistingWorkPolicy.REPLACE, request)
    }
}
