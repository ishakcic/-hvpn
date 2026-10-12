package com.ihvpn.app.vpn

import android.net.TrafficStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow

/**
 * Sahte hız göstermemek için: gerçek anlık indirme/yükleme hızını
 * android.net.TrafficStats'ın UID bazlı sayaçlarından (iki ölçüm arasındaki fark / geçen süre) hesaplar.
 */
class TrafficStatsMonitor(private val uid: Int = android.os.Process.myUid()) {

    fun liveSpeedFlow(intervalMs: Long = 1000L): Flow<SpeedSample> = flow {
        var lastRx = TrafficStats.getUidRxBytes(uid)
        var lastTx = TrafficStats.getUidTxBytes(uid)
        var lastTime = System.currentTimeMillis()

        while (true) {
            delay(intervalMs)
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            val now = System.currentTimeMillis()
            val elapsedSec = ((now - lastTime).coerceAtLeast(1)) / 1000.0

            val downBps = if (rx >= lastRx) ((rx - lastRx) / elapsedSec).toLong() else 0L
            val upBps = if (tx >= lastTx) ((tx - lastTx) / elapsedSec).toLong() else 0L

            emit(SpeedSample(downloadBytesPerSec = downBps, uploadBytesPerSec = upBps, totalDown = rx, totalUp = tx))

            lastRx = rx; lastTx = tx; lastTime = now
        }
    }
}

data class SpeedSample(
    val downloadBytesPerSec: Long,
    val uploadBytesPerSec: Long,
    val totalDown: Long,
    val totalUp: Long
)
