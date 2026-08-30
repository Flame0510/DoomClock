package com.flame.doomclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "doomclock_alarm"
        private const val NOTIFICATION_ID = 0x00DC
    }

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "Alarm"
        val fullScreen = intent.getBooleanExtra("fullScreen", true)
        val targetPackage = intent.getStringExtra("targetPackage")
        val targetLabel = intent.getStringExtra("targetLabel")

        // Intent to the full-screen alarm activity
        val fullScreenIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("label", label)
            putExtra("fullScreen", fullScreen)
            putExtra("targetPackage", targetPackage)
            putExtra("targetLabel", targetLabel)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm, context)

        // Remove the persistent "next alarm" status-bar notification
        nm.cancel(1100)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setPriority(Notification.PRIORITY_MAX)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Press to stop the alarm")
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(contentPendingIntent, true)
        } else {
            // pre-Q: high priority + heads up
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_MAX)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        // Su Android 14+ serve USE_FULL_SCREEN_INTENT; se non concesso, mostra comunque heads-up
        val showFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            nm.canUseFullScreenIntent()

        try {
            if (showFullScreen) {
                nm.notify(NOTIFICATION_ID, builder.build())
            } else {
                // Heads-up notification
                nm.notify(NOTIFICATION_ID, builder.build())
            }
        } catch (_: SecurityException) {
            // fallback: heads-up
            nm.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createChannel(nm: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DoomClock Alarm",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Alarm notifications"
                setBypassDnd(true)
                setShowBadge(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
