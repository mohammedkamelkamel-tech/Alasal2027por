package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R

/**
 * Handles local offline notifications for new invoices, recorded collections,
 * and scheduled meter reading reminders with sound / silent channel control.
 */
object AlasalNotificationService {

    const val CHANNEL_ID_SOUND = "alasal_alerts_sound"
    const val CHANNEL_ID_SILENT = "alasal_alerts_silent"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val soundChannel = NotificationChannel(
            CHANNEL_ID_SOUND,
            "تنبيهات محطة العسل (بصوت)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات الفواتير والتحصيلات ومواعيد القراءة مع الصوت"
            enableVibration(true)
        }

        val silentChannel = NotificationChannel(
            CHANNEL_ID_SILENT,
            "تنبيهات محطة العسل (صامتة)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "إشعارات الفواتير والتحصيلات ومواعيد القراءة بدون صوت"
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(soundChannel)
        manager.createNotificationChannel(silentChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun dispatchNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        prefs: NotificationAndPrintPrefs
    ): Boolean {
        if (!prefs.notificationsEnabled) return false
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val channelId = if (prefs.soundEnabled) CHANNEL_ID_SOUND else CHANNEL_ID_SILENT
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(
                if (prefs.soundEnabled) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
            )

        if (!prefs.soundEnabled) {
            builder.setSilent(true)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        manager.notify(notificationId, builder.build())
        return true
    }
}
