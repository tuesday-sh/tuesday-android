package sh.tuesday.app.services

import android.content.Context
import sh.tuesday.app.BuildConfig
import java.util.UUID

/**
 * Provides a single app-scoped, non-PII client device identifier persisted in
 * private SharedPreferences (`tuesday_prefs_v1` / `client_device_id`).
 *
 * Debug builds use the `test-android-` prefix so `tuesday-server` automatically
 * excludes local development installs from production activation alerts.
 */
object ClientDeviceIdProvider {
    private const val PREFS_NAME = "tuesday_prefs_v1"
    private const val KEY_CLIENT_DEVICE_ID = "client_device_id"

    @Synchronized
    fun getOrCreate(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CLIENT_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val prefix = if (BuildConfig.DEBUG) "test-android-" else "android-"
        val generated = prefix + UUID.randomUUID().toString().take(8)
        prefs.edit().putString(KEY_CLIENT_DEVICE_ID, generated).apply()
        return generated
    }
}
