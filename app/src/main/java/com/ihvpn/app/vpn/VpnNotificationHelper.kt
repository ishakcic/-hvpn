package com.ihvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ihvpn.app.MainActivity

object VpnNotificationHelper {
    const val CHANNEL_ID = "ihvpn_vpn_status"
    const val NOTIFICATION_ID = 4201
    const val ACTION_DISCONNECT = "com.ihvpn.app.action.DISCONNECT"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "VPN Bağlantı Durumu", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun buildConnectedNotification(context: Context, profileName: String): Notification {
        ensureChannel(context)

        val openAppIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            context, 0,
            Intent(context, IhvpnVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("İhvpn — Bağlandı")
            .setContentText(profileName)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Bağlantıyı Kes", disconnectIntent)
            .build()
    }
}
