package sh.tuesday.app.models

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@Serializable
enum class PackageManagerType(val rawValue: String, val displayName: String) {
    APT("apt", "APT (Debian/Ubuntu)"),
    APK("apk", "APK (Alpine/Unraid)"),
    BREW("brew", "Homebrew (macOS)"),
    YUM("yum", "YUM/DNF (RHEL)"),
    DOCKER("docker", "Docker Containers"),
    HACS("hacs", "Home Assistant HACS"),
    FIRMWARE("firmware", "Device Firmware / OTA");

    companion object {
        fun fromRaw(raw: String): PackageManagerType =
            entries.find { it.rawValue.equals(raw, ignoreCase = true) } ?: FIRMWARE
    }
}

@Serializable
enum class NodeHealthStatus(val displayTitle: String) {
    HEALTHY("Healthy"),
    WARNING("Warning"),
    CRITICAL("Critical")
}

@Serializable
enum class InventoryConfidenceState(val rawValue: String) {
    ENROLLED("ENROLLED"),
    VERIFIED_MATCH("VERIFIED MATCH"),
    INCOMPLETE_OR_GUESSED("INCOMPLETE / GUESSED")
}

@Serializable
data class PackageUpdate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val currentVersion: String,
    val availableVersion: String,
    val isSecurityCritical: Boolean = false,
    val advisoryNote: String? = null
)

@Serializable
data class DiscoveredDeviceMetadata(
    val manufacturer: String? = null,
    val modelNumber: String? = null,
    val deviceCategory: String? = null,
    val serialNumber: String? = null,
    val macAddress: String? = null,
    val barcodeOrQr: String? = null,
    val confidenceScore: Int? = null,
    val discoveryNotes: String? = null
)

@Serializable
data class MonitoredDevice(
    val id: String = UUID.randomUUID().toString(),
    val hostname: String,
    val osDetails: String,
    val agentVersion: String = "0.2.1",
    val packageManager: PackageManagerType,
    val haEntityId: String = "sensor.tuesday_" + hostname.lowercase().replace("[.\\s-]".toRegex(), "_"),
    val lastCheckInEpochMs: Long = System.currentTimeMillis(),
    val pendingUpdates: List<PackageUpdate> = emptyList(),
    val healthScore: Double = calculateHealthScore(pendingUpdates, lastCheckInEpochMs),
    val status: NodeHealthStatus = evaluateStatus(healthScore, pendingUpdates, lastCheckInEpochMs),
    val inventoryState: InventoryConfidenceState? = null,
    val discoveredMetadata: DiscoveredDeviceMetadata? = null
) {
    companion object {
        fun calculateHealthScore(
            pendingUpdates: List<PackageUpdate>,
            lastCheckInEpochMs: Long,
            nowEpochMs: Long = System.currentTimeMillis()
        ): Double {
            var score = 100.0
            val criticalCount = pendingUpdates.count { it.isSecurityCritical }
            val regularCount = pendingUpdates.size - criticalCount

            score -= criticalCount * 30.0
            score -= regularCount * 8.0

            val hoursSinceCheckIn = max(0.0, (nowEpochMs - lastCheckInEpochMs) / 3600000.0)
            if (hoursSinceCheckIn > 72.0) {
                score -= 45.0
            } else if (hoursSinceCheckIn > 24.0) {
                score -= 20.0
            }

            return min(100.0, max(5.0, score))
        }

        fun evaluateStatus(
            healthScore: Double,
            pendingUpdates: List<PackageUpdate>,
            lastCheckInEpochMs: Long,
            nowEpochMs: Long = System.currentTimeMillis()
        ): NodeHealthStatus {
            val hoursSinceCheckIn = (nowEpochMs - lastCheckInEpochMs) / 3600000.0

            // 1. Critical Rules: security-critical CVE, stale > 72h, or score < 40
            if (pendingUpdates.any { it.isSecurityCritical } || hoursSinceCheckIn > 72.0 || healthScore < 40.0) {
                return NodeHealthStatus.CRITICAL
            }

            // 2. Warning Rules: outdated packages, stale > 24h, or score < 75
            if (pendingUpdates.isNotEmpty() || hoursSinceCheckIn > 24.0 || healthScore < 75.0) {
                return NodeHealthStatus.WARNING
            }

            // 3. Healthy
            return NodeHealthStatus.HEALTHY
        }
    }
}
