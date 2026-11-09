package sh.tuesday.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import sh.tuesday.app.MainActivity
import sh.tuesday.app.models.PendingPushNotification

class TuesdayNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_CRITICAL_BULLETINS = "tuesday_critical_bulletins"
        const val CHANNEL_LIFECYCLE_DIGESTS = "tuesday_lifecycle_digests"
        const val EXTRA_NOTIFICATION_DELIVERY_ID = "notification_delivery_id"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL_BULLETINS,
                "Critical Hardware & CVE Bulletins",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-severity CVEs, End-of-Life cutoffs, and physical safety recalls for your enrolled hardware."
                enableVibration(true)
            }

            val digestChannel = NotificationChannel(
                CHANNEL_LIFECYCLE_DIGESTS,
                "Firmware & Lifecycle Digests",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Routine firmware releases and maintenance windows for confirmed hardware."
            }

            manager.createNotificationChannel(criticalChannel)
            manager.createNotificationChannel(digestChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showPushNotification(notif: PendingPushNotification): Boolean {
        if (!hasNotificationPermission()) return false

        val primaryIntent = Intent(Intent.ACTION_VIEW, Uri.parse(notif.deepLinkUri), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_DELIVERY_ID, notif.id)
        }

        val primaryPendingIntent = PendingIntent.getActivity(
            context,
            notif.id * 10 + 1,
            primaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = if (notif.isHighSev) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val parts = mutableListOf<String>()
        if (!notif.monitoredDeviceLabel.isNullOrBlank()) {
            parts.add(notif.monitoredDeviceLabel)
        }
        if (notif.matchedByGuess) parts.add("Provisional Guess")
        if (notif.brokeThroughMute) parts.add("Critical Override")
        val subText = parts.joinToString(" • ")

        val builder = NotificationCompat.Builder(context, notif.deliveryChannel)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(notif.title)
            .setContentText(notif.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notif.body))
            .setPriority(priority)
            .setContentIntent(primaryPendingIntent)
            .setAutoCancel(true)

        if (subText.isNotEmpty()) {
            builder.setSubText(subText)
        }

        val deviceUri = notif.deviceDeepLinkUri
        if (!deviceUri.isNullOrBlank()) {
            val deviceIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deviceUri), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NOTIFICATION_DELIVERY_ID, notif.id)
            }
            val devicePendingIntent = PendingIntent.getActivity(
                context,
                notif.id * 10 + 2,
                deviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val actionLabel = if (notif.matchedByGuess) "Verify Sticker" else "Inspect Device"
            builder.addAction(
                android.R.drawable.ic_menu_info_details,
                actionLabel,
                devicePendingIntent
            )
        }

        return try {
            NotificationManagerCompat.from(context).notify(notif.id, builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
