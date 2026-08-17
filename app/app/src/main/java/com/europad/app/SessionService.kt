package com.europad.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Keeps the input pipeline alive when the screen dims or the app is backgrounded mid-session
 * (T2.8): a PARTIAL_WAKE_LOCK keeps the CPU ticking so the sender coroutine never stalls, and
 * the foreground notification keeps Android from killing us under Doze/low-memory pressure.
 * The activity's FLAG_KEEP_SCREEN_ON is the screen-side half; this is the app-side half.
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
