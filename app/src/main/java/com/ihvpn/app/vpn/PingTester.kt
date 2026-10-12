package com.ihvpn.app.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sunucuya gerçek bir TCP el sıkışma süresi ölçer (Xray başlatmadan hızlı ön kontrol için).
 * Gerçek proxy üzerinden gecikme (asıl doğrulama) XrayCoreController.measureDelay ile yapılır;
 * bu sınıf sadece "sunucuya ulaşılabiliyor mu, kabaca ne kadar sürüyor" sorusuna hızlı yanıt verir.
 */
object PingTester {
    suspend fun tcpPingMs(host: String, port: Int, timeoutMs: Int = 4000): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            ((System.nanoTime() - start) / 1_000_000L).toInt()
        }.getOrNull()
    }

    /** Profiller ekranındaki "tümünü test et" için — sırayla değil, çağıran taraf paralel çalıştırmalı. */
    suspend fun batchPing(targets: List<Pair<String, Pair<String, Int>>>): Map<String, Int?> =
        withContext(Dispatchers.IO) {
            targets.associate { (id, hostPort) ->
                id to tcpPingMs(hostPort.first, hostPort.second)
            }
        }
}
