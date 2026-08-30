package com.flame.doomclock

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    private const val REQUEST_CODE = 0x00DC
    private const val CHANNEL_ID = "doomclock_alarm"

    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
    }

    fun scheduleAlarm(context: Context, triggerAtMs: Long, label: String, fullScreen: Boolean, targetPackage: String?, targetLabel: String?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("label", label)
            putExtra("fullScreen", fullScreen)
            putExtra("targetPackage", targetPackage)
            putExtra("targetLabel", targetLabel)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }

        // Persistent "next alarm" notification -> shows the alarm icon in the status bar
        showNextAlarmNotification(context, triggerAtMs, label)
    }

    fun cancelAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        pi.cancel()

        // Remove the "next alarm" status-bar notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1100)
    }

    private fun showNextAlarmNotification(context: Context, triggerAtMs: Long, label: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }

        val time = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT
        ).format(java.util.Date(triggerAtMs))

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Next alarm")
            .setContentText("$label · $time")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()

        nm.notify(1100, notification)
    }
}
