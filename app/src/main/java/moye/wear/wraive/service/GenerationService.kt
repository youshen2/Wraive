package moye.wear.wraive.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import moye.wear.wraive.MainActivity
import moye.wear.wraive.R
import java.util.concurrent.atomic.AtomicInteger

class GenerationService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.generation_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.generating_reply))
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "generation"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "moye.wear.wraive.STOP_GENERATION_SERVICE"
        private const val EXTRA_TITLE = "title"
        private val activeCount = AtomicInteger(0)

        fun begin(context: Context, title: String) {
            activeCount.incrementAndGet()
            ContextCompat.startForegroundService(
                context,
                Intent(context, GenerationService::class.java).putExtra(EXTRA_TITLE, title)
            )
        }

        fun end(context: Context) {
            if (activeCount.decrementAndGet() <= 0) {
                activeCount.set(0)
                context.startService(
                    Intent(context, GenerationService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
