package com.deeprows.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

/**
 * Android VPN entry point.
 *
 * IMPORTANT: this service does not pretend to be a working VPN by itself. A real
 * tunnel requires a WireGuard gateway and a short-lived client configuration
 * obtained from the Deeprows backend. Once that configuration is available, the
 * WireGuard tunnel engine should be started here.
 */
class DeeprowsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Deliberately fail closed until a real WireGuard session is supplied.
        // Do not create a TUN interface and claim that traffic is protected.
        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Deeprows VPN")
            .setContentText("VPN service is ready")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Deeprows VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_DISCONNECT = "com.deeprows.browser.VPN_DISCONNECT"
        private const val CHANNEL_ID = "deeprows_vpn"
        private const val NOTIFICATION_ID = 2201
    }
}
