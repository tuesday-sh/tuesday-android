package sh.tuesday.app.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import sh.tuesday.app.models.PendingPushNotification
import java.util.concurrent.TimeUnit

class TuesdayFirebaseMessagingService : FirebaseMessagingService() {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs = applicationContext.getSharedPreferences("tuesday_prefs", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        val deviceId = ClientDeviceIdProvider.getOrCreate(applicationContext)
        val payload = JSONObject().apply {
            put("fcm_token", token)
            put("platform", "android")
            put("client_device_id", deviceId)
        }
        val req = Request.Builder()
            .url("https://api.tuesday.sh/api/v1/notifications/register_token")
            .addHeader("X-Tuesday-Device-ID", deviceId)
            .addHeader("X-Tuesday-Client-Platform", "android")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            httpClient.newCall(req).execute().close()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "Tuesday.sh Alert"
        val body = message.notification?.body ?: data["body"] ?: return
        val notifId = data["notificationId"]?.toIntOrNull() ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val priority = data["priority"] ?: "normal"
        val isHighSev = priority.equals("critical", ignoreCase = true) || priority.equals("high", ignoreCase = true)

        val pending = PendingPushNotification(
            id = notifId,
            bulletinExternalId = data["ruleId"] ?: "fcm-$notifId",
            feedSlug = null,
            bulletinType = "advisory",
            isHighSev = isHighSev,
            brokeThroughMute = isHighSev,
            matchedByGuess = false,
            deliveryChannel = if (isHighSev) {
                TuesdayNotificationManager.CHANNEL_CRITICAL_BULLETINS
            } else {
                TuesdayNotificationManager.CHANNEL_LIFECYCLE_DIGESTS
            },
            deliveryStatus = "sent_fcm",
            title = title,
            body = body,
            deepLinkUri = data["deepLinkUri"]?.takeIf { it.isNotBlank() } ?: "tuesday://bulletins",
            deviceDeepLinkUri = null,
            monitoredDeviceId = null,
            monitoredDeviceLabel = null,
            categoryCode = null
        )

        TuesdayNotificationManager(applicationContext).showPushNotification(pending)
    }
}
