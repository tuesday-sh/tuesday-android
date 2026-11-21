package sh.tuesday.app.models

/**
 * Canonical server-issued notification record returned by `GET /api/v1/notifications/feed`
 * and mutated via `PATCH /api/v1/notifications/:id`.
 */
data class SiteNotificationItem(
    val id: Int,
    val networkSiteId: Int,
    val ruleId: String,
    val triggerType: String,
    val dedupKey: String,
    val surfaces: List<String>,
    val priority: String,
    val status: String,
    val pushStatus: String,
    val title: String,
    val body: String,
    val ctaLabel: String? = null,
    val deepLinkUri: String? = null,
    val monitoredDeviceId: String? = null,
    val monitoredDeviceLabel: String? = null,
    val snoozedUntil: String? = null,
    val expiresAt: String? = null,
    val seenAt: String? = null,
    val resolvedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun targetsSurface(surface: String): Boolean = surfaces.any { it.equals(surface, ignoreCase = true) }
    val isUnresolved: Boolean get() = status in setOf("active", "seen", "snoozed")
}

data class SiteNotificationFeedResponse(
    val status: String,
    val networkSiteId: Int,
    val syncedAt: String,
    val count: Int,
    val notifications: List<SiteNotificationItem>
)
