package com.europad.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Keeps the input pipeline alive when the screen dims or the app is backgrounded mid-session
 * (T2.8): a PARTIAL_WAKE_LOCK keeps the CPU ticking so the sender coroutine never stalls, and
 * the foreground notification keeps Android from killing us under Doze/low-memory pressure.
 * The activity's FLAG_KEEP_SCREEN_ON is the screen-side half; this is the app-side half.
 *
 * It also holds a **low-latency Wi-Fi lock**, which is the single biggest latency win available on a
 * 2.4 GHz link — see [acquireWifiLock].
 */
class SessionService : Service() {

    companion object {
        private const val CHANNEL_ID = "europad-session"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, SessionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EuroPad session", NotificationManager.IMPORTANCE_LOW),
            )
        }

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EuroPad")
            .setContentText("Controller session active — keepalive running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EuroPad::Session")
            .apply { acquire() }

        acquireWifiLock()
    }

    /**
     * Pins the Wi-Fi radio into low-latency mode for the whole session.
     *
     * A CPU wake lock was never enough. Android's Wi-Fi power-save (PSM) parks the radio between
     * packets and only wakes it on the AP's beacon, which on a 2.4 GHz link means an idle-to-active
     * transition of tens of milliseconds and a jittery floor even while frames are flowing at 120 Hz —
     * the phone would send on time and the datagram would sit in the radio waiting for a beacon slot.
     * That is the "smooth in the app, laggy in the game" gap, and no amount of tuning on the send side
     * can close it.
     *
     * WIFI_MODE_FULL_LOW_LATENCY (API 29, and minSdk is 29) disables power-save *and* asks the driver
     * for a latency-optimised state — the same mode Android grants to games. It only engages while the
     * app is in the foreground and the screen is on, which is exactly a driving session, and it
     * degrades to plain "stay associated" behaviour otherwise, so there is no battery trap in holding
     * it for the life of the service.
     *
     * Reference-counted is off deliberately: acquire/release are paired with onCreate/onDestroy, and a
     * ref-counted lock that leaks one acquire would hold the radio hot forever.
     */
    private fun acquireWifiLock() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "EuroPad::LowLatency")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } catch (_: Exception) {
            // Some OEM stacks reject the low-latency mode outright. HIGH_PERF is the older, cruder
            // request (power-save off, nothing about latency) and is better than leaving PSM on.
            wifiLock = try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EuroPad::HighPerf")
                    ?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        wakeLock = null
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        wifiLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
