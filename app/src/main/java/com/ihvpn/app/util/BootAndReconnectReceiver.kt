package com.ihvpn.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Cihaz yeniden başladığında Android VPN izni kalıcı olsa da (Always-on VPN dışında)
 * servisi otomatik başlatmaz; kullanıcı "otomatik yeniden bağlan" ayarını açtıysa
 * burada son seçili profille bağlantıyı yeniden kurmak için WorkManager'a bir
 * OneTimeWorkRequest kuyruklanmalı (bkz. AutoReconnectManager — bu proje iskeletinde
 * ayrı bir dosya olarak eklenmesi önerilir).
 */
class BootAndReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // TODO: DataStore/SharedPreferences'tan "otomatik yeniden bağlan" tercihini oku,
        // açıksa AutoReconnectManager.scheduleImmediateReconnect(context) çağır.
    }
}
