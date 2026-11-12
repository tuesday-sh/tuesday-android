package sh.tuesday.app.models

import kotlinx.serialization.Serializable

@Serializable
enum class AppBrandFlavor(val title: String, val badge: String) {
    TUESDAY_PRO("Tuesday.sh", "PRO"),
    SURETHING_HOME("SureThingNet", "HOME")
}

@Serializable
enum class PortLifecycleState(
    val key: String,
    val proLabel: String,
    val stnLabel: String,
    val severityPriority: Int
) {
    CVE_KEV("cve_kev", "CVE / KEV", "Safety / Security Alert", 1),
    EOL("eol", "EOL", "Retired / EOL", 2),
    GUESSED("guessed", "GUESSED", "Unknown / Guessed", 3),
    UPDATE("update", "UPDATE", "Update Available", 4),
    OK("ok", "OK", "Everything OK", 5),
    UNASSIGNED("unassigned", "Empty Slot", "Empty Slot", 6)
}

@Serializable
enum class InventoryScaleTier(
    val id: String,
    val label: String,
    val minDevices: Int,
    val maxDevices: Int,
    val showSummaryBadge: Boolean,
    val showPortDiagramStrip: Boolean,
    val showFilterChips: Boolean,
    val showTrustZonePills: Boolean,
    val botanicalOverlayEmoji: String
) {
    DAY_ZERO("day_zero", "Day 0", 0, 0, false, false, false, false, ""),
    PEA("pea", "Pea", 1, 2, false, false, false, false, "🌱"),
    GARDEN("garden", "Garden", 3, 7, true, false, false, false, "🪴"),
    FOREST("forest", "Forest", 8, 128, true, true, true, true, "🌲");

    companion object {
        // Backward-compatibility aliases for existing tests
        val SEED = PEA
        val SMALL_NETWORK = GARDEN
        val FLEET_RACK = FOREST

        fun forCount(count: Int): InventoryScaleTier = when {
            count <= 0 -> DAY_ZERO
            count in 1..2 -> PEA
            count in 3..7 -> GARDEN
            else -> FOREST
        }
    }
}

@Serializable
enum class StnHardwareCategory(
    val code: String,
    val portSlot: Int,
    val osiLayer: String,
    val proLabel: String,
    val stnLabel: String,
    val shortName: String,
    val description: String,
    val examples: String
) {
    C1_GATEWAY("C1", 1, "L3", "Gateway / WAN", "Wi-Fi Router / Gateway", "Gateway", "Perimeter firewall, WAN uplink, NAT, DHCP server, and default gateway.", "UDM-Pro, UXG-Pro, Firewalla, pfSense"),
    C2_SWITCH("C2", 2, "L2", "Switch Backbone", "Wired Switch Hub", "Switch", "Physical distribution plane, 802.1Q VLAN trunking, and PoE power delivery.", "USW-24-PoE, Enterprise 8 PoE, Catalyst"),
    C3_AP("C3", 3, "L1/2", "Wireless AP", "Wi-Fi Access Point", "Wi-Fi AP", "Radio-frequency access point translating wireless airwaves into wired Ethernet.", "U6-Pro, U7-Pro, Netgear AP, Eero"),
    C4_SERVER("C4", 4, "L4–7", "Compute / Server", "Home Server & Storage", "Server", "Storage (NAS), local DNS, container virtualization, and homelab workloads.", "TrueNAS, Unraid, Proxmox VE, Synology"),
    C5_CAMERA("C5", 5, "L7", "Vision / Camera", "Smart Camera", "Camera", "Surveillance video, real-time RTSP/WebRTC streams, and perimeter sensing.", "UniFi Protect G5, Owlet Cam, Reolink"),
    C6_IOT("C6", 6, "L7", "Smart Home / IoT", "Smart Home Hub", "Smart IoT", "Home automation controllers, sensor bridges, and constrained smart peripherals.", "Home Assistant, Hubitat, Hue, Ecobee"),
    C7_CLIENT("C7", 7, "L7", "Workstation / Client", "Personal Computer / Phone", "Client", "Primary trusted user devices carrying user identities, keys, and productivity data.", "MacBook Pro, ThinkPad, Linux PC, iPad"),
    C8_GUEST("C8", 8, "L8", "Guest / Human (L8)", "Guest / Visitor Device", "Guest L8", "Isolated perimeter for ephemeral human visitors, guest Wi-Fi, and user policy.", "Guest Portal, Visitor Laptop, Cast Client");

    val defaultIntakeClue: String
        get() = when (this) {
            C1_GATEWAY -> "Router / Gateway"
            C2_SWITCH -> "Network Switch"
            C3_AP -> "Wi-Fi Access Point"
            C4_SERVER -> "NAS / Home Server"
            C5_CAMERA -> "Security Camera"
            C6_IOT -> "Smart Hub / Bridge"
            C7_CLIENT -> "Workstation / Client"
            C8_GUEST -> "TV / Speaker / Media"
        }
}

@Serializable
data class NetworkSite(
    val id: String,
    val name: String,
    val devicesCount: Int,
    val role: String
)

@Serializable
data class NetworkInventoryBackup(
    val version: String = "1.0.0",
    val exportedAt: String,
    val activeNetworkId: String,
    val networks: List<NetworkSite>,
    val devices: List<RackPortDevice>,
    val mutedFeedSlugs: List<String> = emptyList(),
    val attestations: List<DeviceAttestation> = emptyList()
)

@Serializable
enum class TrustZone(val key: String, val shortCode: String, val displayLabel: String, val stnLabel: String, val slotCount: Int) {
    Z0_CORE("z0_core", "Z0", "Core Gear", "Wi-Fi & Network Gear", 16),
    Z1_TRUSTED("z1_trusted", "Z1", "Trusted", "Main Home Wi-Fi", 32),
    Z2_LAB("z2_lab", "Z2", "Homelab", "Home Storage", 24),
    Z3_IOT("z3_iot", "Z3", "Smart Home", "Smart Home Lane", 40),
    Z4_GUEST("z4_guest", "Z4", "Guest", "Guest Wi-Fi", 16)
}

@Serializable
enum class DeviceConfidenceTier {
    CONFIRMED,
    PROVISIONAL_GUESS,
    OPAQUE_UNKNOWN
}

@Serializable
enum class LifecycleLayer(
    val label: String,
    val wickDescription: String,
    val bodyDescription: String
) {
    HARDWARE("Hardware", "Release to Vendor EOL", "Purchase/Discovery to Today/Decommissioned"),
    OS("Host OS", "Release to Security EOL", "Host Install to Upgrade"),
    CONTAINER("Containers", "Publish to Deprecation/CVE", "Deployment Uptime")
}

@Serializable
enum class DevicePresenceState {
    ACTIVE,
    RETIRED,
    LOST,
    STOPPED_WORKING;

    val isOutOfService: Boolean
        get() = this != ACTIVE

    val label: String
        get() = when (this) {
            ACTIVE -> "Active in Service"
            RETIRED -> "Retired (Decommissioned)"
            LOST -> "Lost / Missing"
            STOPPED_WORKING -> "Stopped Working (Defective)"
        }

    val shortLabel: String
        get() = when (this) {
            ACTIVE -> "ACTIVE"
            RETIRED -> "RETIRED"
            LOST -> "LOST"
            STOPPED_WORKING -> "DEFECTIVE"
        }
}

@Serializable
enum class AttestationType {
    PRESENCE_AUDIT,          // Point-in-time audit adding or confirming presence
    ABSENCE_RETIRED,         // Graceful decommission / sunset
    ABSENCE_LOST,            // Unaccounted / missing in audit
    ABSENCE_STOPPED_WORKING, // Hardware failure / defect
    PRESENCE_REACTIVATED;    // Re-attested back into service

    val isAbsence: Boolean
        get() = this == ABSENCE_RETIRED || this == ABSENCE_LOST || this == ABSENCE_STOPPED_WORKING

    val resultingState: DevicePresenceState
        get() = when (this) {
            PRESENCE_AUDIT, PRESENCE_REACTIVATED -> DevicePresenceState.ACTIVE
            ABSENCE_RETIRED -> DevicePresenceState.RETIRED
            ABSENCE_LOST -> DevicePresenceState.LOST
            ABSENCE_STOPPED_WORKING -> DevicePresenceState.STOPPED_WORKING
        }

    val displayLabel: String
        get() = when (this) {
            PRESENCE_AUDIT -> "Presence Attestation"
            ABSENCE_RETIRED -> "Decommission (Retired)"
            ABSENCE_LOST -> "Absence (Lost / Missing)"
            ABSENCE_STOPPED_WORKING -> "Absence (Stopped Working)"
            PRESENCE_REACTIVATED -> "Reactivation (Return to Service)"
        }
}

@Serializable
data class DeviceAttestation(
    val id: String,
    val deviceId: String,
    val deviceLabel: String,
    val type: AttestationType,
    val presenceState: DevicePresenceState,
    val timestampMs: Long,
    val timestampIso: String,
    val auditDate: String, // Point-in-time audit date YYYY-MM-DD
    val userSignature: String, // Unique user signature e.g. "Aaron Bronow (sig_4b9a8c)"
    val environment: String,   // App environment e.g. "Tuesday Android v2026.11 (Pixel 9)"
    val auditSessionId: String? = null,
    val note: String? = null,
    val cryptographicProof: String = ""
) {
    val shortProof: String
        get() = if (cryptographicProof.length >= 12) cryptographicProof.take(12) else cryptographicProof
}

@Serializable
data class OperationalCandle(
    val layer: LifecycleLayer = LifecycleLayer.HARDWARE,
    val id: String,
    val shortLabel: String,
    val name: String,
    val portIndex: Int = 0,
    val trustZone: TrustZone = TrustZone.Z1_TRUSTED,
    val marketReleaseNorm: Float,
    val deployStartNorm: Float,
    val deployEndNorm: Float,
    val vendorEolNorm: Float,
    val isRetired: Boolean = false,
    val presenceState: DevicePresenceState = if (isRetired) DevicePresenceState.RETIRED else DevicePresenceState.ACTIVE,
    val isGuessed: Boolean = false,
    val hasCveOrRecall: Boolean = false,
    val cveStartNorm: Float? = null,
    val versionLabel: String = "",
    val releaseDateStr: String? = null,
    val deployDateStr: String? = null,
    val eolDateStr: String? = null,
    val decommissionDateStr: String? = null,
    val latestAttestation: DeviceAttestation? = null,
    val attestationCount: Int = 1
) {
    val isOutOfService: Boolean
        get() = isRetired || presenceState != DevicePresenceState.ACTIVE

    val preAdoptionAgeNorm: Float
        get() = (deployStartNorm - marketReleaseNorm).coerceAtLeast(0f)

    val remainingRunwayNorm: Float
        get() = if (isPastEol) 0f else (vendorEolNorm - deployEndNorm).coerceAtLeast(0f)

    val isPastEol: Boolean
        get() = deployEndNorm > vendorEolNorm

    val postEolOverhangNorm: Float
        get() = if (isPastEol) (deployEndNorm - vendorEolNorm).coerceAtLeast(0f) else 0f

    val isEolNearing: Boolean
        get() = !isPastEol && !isOutOfService && remainingRunwayNorm in 0.001f..0.12f
}

@Serializable
data class DeviceLifecycleCandle(
    val ver: String,
    val released: Float,
    val attested: Float,
    val ended: Float,
    val eol: Float,
    val cveStart: Float? = null
)

@Serializable
data class RackPortDevice(
    val id: String,
    val portIndex: Int,
    val portState: PortLifecycleState,
    val trustZone: TrustZone,
    val category: StnHardwareCategory = StnHardwareCategory.C6_IOT,
    val assetTag: String = "",
    val shortLabel: String,
    val name: String,
    val roomLocation: String,
    val version: String,
    val targetVersion: String? = null,
    val purchaseDate: String? = null,
    val vendorEolAt: String? = null,
    val releaseDate: String? = null,
    val decommissionDate: String? = null,
    val isRetired: Boolean = false,
    val presenceState: DevicePresenceState = if (isRetired) DevicePresenceState.RETIRED else DevicePresenceState.ACTIVE,
    val latestAttestation: DeviceAttestation? = null,
    val attestationCount: Int = 1,
    val candles: List<DeviceLifecycleCandle> = emptyList(),
    val lifecycleTier: String,
    val purls: List<String>,
    val confidenceTier: DeviceConfidenceTier = DeviceConfidenceTier.CONFIRMED,
    val candidateModels: List<String> = emptyList(),
    val excludedPurlPrefixes: List<String> = emptyList()
)

@Serializable
data class LedSlot(
    val index: Int,
    val state: PortLifecycleState,
    val trustZone: TrustZone,
    val deviceId: String? = null
)

@Serializable
data class CanonicalFeedItem(
    val id: String,
    val slug: String,
    val proName: String,
    val stnName: String,
    val purlPrefixes: List<String>
)

@Serializable
data class CuratedBulletinItem(
    val externalId: String,
    val canonicalFeedSlug: String,
    val sourceUrl: String? = null,
    val bulletinType: String,
    val isHighSev: Boolean,
    val purlBase: String,
    val proTitle: String,
    val proBody: String,
    val stnTitle: String,
    val stnBody: String,
    val aiProBullet: String,
    val aiStnBullet: String,
    val publishedAt: String
)

data class MatchedFeedView(
    val id: String,
    val slug: String,
    val name: String,
    val matchMode: DeviceConfidenceTier,
    val matchedPorts: List<String>,
    val candidateModels: List<String>,
    val isMuted: Boolean
)

data class BulletinInboxView(
    val externalId: String,
    val feedSlug: String,
    val sourceUrl: String? = null,
    val bulletinType: String,
    val isHighSev: Boolean,
    val brokeThroughMute: Boolean,
    val matchedByGuess: Boolean,
    val candidateModels: List<String>,
    val title: String,
    val body: String,
    val publishedAt: String
)

data class AssembledBulletinsPayload(
    val activeFeedCount: Int,
    val mutedFeedCount: Int,
    val provisionalFeedCount: Int,
    val mutedHighSevOverrideCount: Int,
    val provisionalHighSevCount: Int,
    val aiBullets: List<String>,
    val matchedFeeds: List<MatchedFeedView>,
    val inbox: List<BulletinInboxView>,
    val completedBulletins: List<BulletinInboxView> = emptyList()
)

@Serializable
data class PendingPushNotification(
    val id: Int,
    val bulletinExternalId: String,
    val feedSlug: String? = null,
    val bulletinType: String = "advisory",
    val isHighSev: Boolean = false,
    val brokeThroughMute: Boolean = false,
    val matchedByGuess: Boolean = false,
    val deliveryChannel: String = "tuesday_critical_bulletins",
    val deliveryStatus: String = "pending",
    val title: String,
    val body: String,
    val deepLinkUri: String,
    val deviceDeepLinkUri: String? = null,
    val monitoredDeviceId: String? = null,
    val monitoredDeviceLabel: String? = null,
    val categoryCode: String? = null
)

object BulletinsAndInventoryEngine {

    fun build128LedMatrix(
        devices: List<RackPortDevice>,
        zoneFilter: TrustZone? = null
    ): List<LedSlot> {
        val slots = mutableListOf<LedSlot>()
        val filtered = if (zoneFilter != null) {
            devices.filter { it.trustZone == zoneFilter }
        } else {
            devices
        }

        // Ordered by Category Topology (C1 Gateway -> C8 Guest), then tie-broken alphabetically
        val topologicallyOrdered = filtered.sortedWith(
            compareBy<RackPortDevice> { it.category.portSlot }
                .thenBy { it.shortLabel.lowercase() }
        )

        // Active assigned devices occupy slots 1..N
        var runningIndex = 1
        topologicallyOrdered.forEach { dev ->
            if (runningIndex <= 128) {
                slots.add(
                    LedSlot(
                        index = runningIndex++,
                        state = dev.portState,
                        trustZone = dev.trustZone,
                        deviceId = dev.id
                    )
                )
            }
        }

        // Remaining (128 - N) slots are dark UNASSIGNED slots (never artificial green OK)
        val totalCap = if (zoneFilter != null) zoneFilter.slotCount else 128
        val remainingUnassigned = (totalCap - slots.size).coerceAtLeast(0)
        repeat(remainingUnassigned) {
            slots.add(
                LedSlot(
                    index = runningIndex++,
                    state = PortLifecycleState.UNASSIGNED,
                    trustZone = zoneFilter ?: TrustZone.Z3_IOT,
                    deviceId = null
                )
            )
        }
        return slots
    }

    fun filterActionFirstDevices(
        devices: List<RackPortDevice>,
        includeOk: Boolean = false,
        zoneFilter: TrustZone? = null,
        portStateFilter: PortLifecycleState? = null
    ): List<RackPortDevice> {
        return devices
            .filter { zoneFilter == null || it.trustZone == zoneFilter }
            .filter {
                if (portStateFilter != null) {
                    it.portState == portStateFilter
                } else {
                    includeOk || it.portState != PortLifecycleState.OK
                }
            }
            .sortedWith(compareBy<RackPortDevice> { it.portState.severityPriority }.thenBy { it.trustZone.ordinal })
    }

    fun getCategoryDeviceCount(
        category: StnHardwareCategory,
        devices: List<RackPortDevice>
    ): Int = devices.count { it.category == category }

    fun computeAttestationProof(
        id: String,
        deviceId: String,
        type: AttestationType,
        auditDate: String,
        userSignature: String,
        environment: String,
        timestampMs: Long
    ): String {
        val raw = "$id:$deviceId:${type.name}:$auditDate:$userSignature:$environment:$timestampMs"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun toOperationalCandle(device: RackPortDevice, referenceNowNorm: Float = 0.72f): OperationalCandle {
        val firstCandle = device.candles.firstOrNull()
        val lastCandle = device.candles.lastOrNull()
        val isGuessed = device.portState == PortLifecycleState.GUESSED || device.version == "unverified"
        val marketRel = firstCandle?.released ?: 0.10f
        val vendorEol = lastCandle?.eol ?: 0.95f
        val deployStart = lastCandle?.attested ?: firstCandle?.attested ?: 0.40f
        val isOutOfService = device.isRetired || device.presenceState != DevicePresenceState.ACTIVE
        val deployEnd = if (isOutOfService) {
            (lastCandle?.ended ?: 0.58f).coerceAtMost(vendorEol + 0.05f)
        } else {
            referenceNowNorm
        }
        val hasCve = device.portState == PortLifecycleState.CVE_KEV || lastCandle?.cveStart != null
        val effectivePresence = if (device.isRetired && device.presenceState == DevicePresenceState.ACTIVE) {
            DevicePresenceState.RETIRED
        } else {
            device.presenceState
        }
        return OperationalCandle(
            layer = LifecycleLayer.HARDWARE,
            id = device.id,
            shortLabel = device.shortLabel,
            name = device.name,
            portIndex = device.portIndex,
            trustZone = device.trustZone,
            marketReleaseNorm = marketRel,
            deployStartNorm = deployStart,
            deployEndNorm = deployEnd,
            vendorEolNorm = vendorEol,
            isRetired = isOutOfService,
            presenceState = effectivePresence,
            isGuessed = isGuessed,
            hasCveOrRecall = hasCve,
            cveStartNorm = lastCandle?.cveStart,
            versionLabel = device.version,
            releaseDateStr = device.releaseDate ?: device.purchaseDate,
            deployDateStr = device.purchaseDate,
            eolDateStr = device.vendorEolAt,
            decommissionDateStr = device.decommissionDate,
            latestAttestation = device.latestAttestation,
            attestationCount = device.attestationCount
        )
    }

    fun sampleHostOsCandles(referenceNowNorm: Float = 0.72f): List<OperationalCandle> = listOf(
        OperationalCandle(
            layer = LifecycleLayer.OS,
            id = "os-01",
            shortLabel = "UniFi OS",
            name = "Basement Rack Gateway (UDM-Pro)",
            portIndex = 1,
            trustZone = TrustZone.Z0_CORE,
            marketReleaseNorm = 0.45f,
            deployStartNorm = 0.48f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.95f,
            versionLabel = "UniFi OS 4.0.20",
            releaseDateStr = "2024-05",
            deployDateStr = "2024-06",
            eolDateStr = "2029-06"
        ),
        OperationalCandle(
            layer = LifecycleLayer.OS,
            id = "os-02",
            shortLabel = "Linux 2.6",
            name = "Basement AP (Netgear R7000)",
            portIndex = 2,
            trustZone = TrustZone.Z0_CORE,
            marketReleaseNorm = 0.08f,
            deployStartNorm = 0.18f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.58f,
            hasCveOrRecall = true,
            versionLabel = "Linux 2.6.36.4 (OpenSSL 1.0.2u)",
            releaseDateStr = "2016-02",
            deployDateStr = "2016-09",
            eolDateStr = "2023-01"
        ),
        OperationalCandle(
            layer = LifecycleLayer.OS,
            id = "os-03",
            shortLabel = "macOS 14",
            name = "Aaron's MacBook Pro 16",
            portIndex = 5,
            trustZone = TrustZone.Z1_TRUSTED,
            marketReleaseNorm = 0.38f,
            deployStartNorm = 0.42f,
            deployEndNorm = 0.55f,
            vendorEolNorm = 0.70f,
            isRetired = true,
            versionLabel = "macOS Sonoma 14.7 (Upgraded)",
            releaseDateStr = "2023-09",
            deployDateStr = "2024-01",
            eolDateStr = "2026-09",
            decommissionDateStr = "2025-01"
        ),
        OperationalCandle(
            layer = LifecycleLayer.OS,
            id = "os-04",
            shortLabel = "Debian 12",
            name = "Homelab Hypervisor (pve-node-01)",
            portIndex = 8,
            trustZone = TrustZone.Z2_LAB,
            marketReleaseNorm = 0.35f,
            deployStartNorm = 0.52f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.88f,
            versionLabel = "Debian 12.8 (Bookworm)",
            releaseDateStr = "2023-06",
            deployDateStr = "2024-11",
            eolDateStr = "2028-06"
        ),
        OperationalCandle(
            layer = LifecycleLayer.OS,
            id = "os-05",
            shortLabel = "ESPHome",
            name = "Garage Door Relay ESP32",
            portIndex = 11,
            trustZone = TrustZone.Z3_IOT,
            marketReleaseNorm = 0.68f,
            deployStartNorm = 0.69f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.75f,
            versionLabel = "ESPHome 2026.9.0",
            releaseDateStr = "2026-09",
            deployDateStr = "2026-09",
            eolDateStr = "2026-12"
        ),
        OperationalCandle(
            layer = LifecycleLayer.OS,
            id = "os-06",
            shortLabel = "Ubuntu 22",
            name = "Unraid Storage Server (lml)",
            portIndex = 7,
            trustZone = TrustZone.Z2_LAB,
            marketReleaseNorm = 0.22f,
            deployStartNorm = 0.40f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.82f,
            versionLabel = "Ubuntu 22.04.5 LTS",
            releaseDateStr = "2022-04",
            deployDateStr = "2023-05",
            eolDateStr = "2027-04"
        )
    )

    fun sampleContainerCandles(referenceNowNorm: Float = 0.72f): List<OperationalCandle> = listOf(
        OperationalCandle(
            layer = LifecycleLayer.CONTAINER,
            id = "ctr-01",
            shortLabel = "rclone",
            name = "Rclone Sync Engine (lml)",
            portIndex = 1,
            trustZone = TrustZone.Z2_LAB,
            marketReleaseNorm = 0.40f,
            deployStartNorm = 0.52f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.85f,
            versionLabel = "ghcr.io/linuxserver/rclone:v1.68",
            releaseDateStr = "2024-01",
            deployDateStr = "2024-11",
            eolDateStr = "2028-01"
        ),
        OperationalCandle(
            layer = LifecycleLayer.CONTAINER,
            id = "ctr-02",
            shortLabel = "homeassistant",
            name = "Home Assistant Supervised",
            portIndex = 2,
            trustZone = TrustZone.Z3_IOT,
            marketReleaseNorm = 0.70f,
            deployStartNorm = 0.71f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.76f,
            versionLabel = "homeassistant/home-assistant:2026.10",
            releaseDateStr = "2026-10",
            deployDateStr = "2026-10",
            eolDateStr = "2026-11-30"
        ),
        OperationalCandle(
            layer = LifecycleLayer.CONTAINER,
            id = "ctr-03",
            shortLabel = "pihole:v5",
            name = "Local DNS Resolver (Node 1)",
            portIndex = 3,
            trustZone = TrustZone.Z0_CORE,
            marketReleaseNorm = 0.30f,
            deployStartNorm = 0.34f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.60f,
            hasCveOrRecall = true,
            versionLabel = "pihole/pihole:v5.18-legacy",
            releaseDateStr = "2023-04",
            deployDateStr = "2023-06",
            eolDateStr = "2025-06"
        ),
        OperationalCandle(
            layer = LifecycleLayer.CONTAINER,
            id = "ctr-04",
            shortLabel = "caddy",
            name = "Edge Reverse Proxy",
            portIndex = 4,
            trustZone = TrustZone.Z0_CORE,
            marketReleaseNorm = 0.22f,
            deployStartNorm = 0.26f,
            deployEndNorm = 0.48f,
            vendorEolNorm = 0.62f,
            isRetired = true,
            versionLabel = "caddy:2.6-alpine (Stopped)",
            releaseDateStr = "2022-10",
            deployDateStr = "2023-01",
            eolDateStr = "2025-02",
            decommissionDateStr = "2024-03"
        ),
        OperationalCandle(
            layer = LifecycleLayer.CONTAINER,
            id = "ctr-05",
            shortLabel = "cloudflared",
            name = "Zero Trust Tunnel Daemon",
            portIndex = 5,
            trustZone = TrustZone.Z0_CORE,
            marketReleaseNorm = 0.55f,
            deployStartNorm = 0.58f,
            deployEndNorm = referenceNowNorm,
            vendorEolNorm = 0.90f,
            versionLabel = "cloudflare/cloudflared:2026.8",
            releaseDateStr = "2024-08",
            deployDateStr = "2024-09",
            eolDateStr = "2029-01"
        )
    )

    fun sortDevicesForTimeline(devices: List<RackPortDevice>): List<RackPortDevice> =
        devices.sortedWith(compareBy<RackPortDevice> { it.trustZone.ordinal }.thenBy { it.portIndex })

    fun sortDevicesByRecentChanges(devices: List<RackPortDevice>): List<RackPortDevice> =
        devices
            .sortedWith(
                compareBy<RackPortDevice> { it.portState.severityPriority }
                    .thenByDescending { it.purchaseDate ?: "" }
            )
            .take(12)

    fun getEstimatedVersionAtCursor(device: RackPortDevice, scrubNorm: Float? = null): String {
        if (scrubNorm == null) {
            return device.version
        }
        if (device.portState == PortLifecycleState.GUESSED || device.version == "unverified") {
            return "Unverified (Est. Drift)"
        }
        val candles = device.candles
        if (candles.isEmpty()) {
            return device.version
        }
        val earliestCandle = candles.first()
        if (scrubNorm < earliestCandle.released) {
            return "Not Yet Released"
        }
        val latestCandle = candles.last()
        if (latestCandle.eol > 0f && scrubNorm > latestCandle.eol) {
            return "${latestCandle.ver} (EOL)"
        }
        for (i in candles.indices) {
            val c = candles[i]
            val nextCandle = candles.getOrNull(i + 1)
            if (nextCandle != null) {
                if (scrubNorm >= c.attested && scrubNorm < nextCandle.attested) {
                    return c.ver
                }
            } else {
                if (scrubNorm >= c.attested) {
                    return c.ver
                }
            }
        }
        return earliestCandle.ver
    }

    fun assembleBulletins(
        flavor: AppBrandFlavor,
        devices: List<RackPortDevice>,
        mutedFeedSlugs: Set<String>,
        feeds: List<CanonicalFeedItem> = defaultGoldFeeds(),
        bulletins: List<CuratedBulletinItem> = defaultGoldBulletins()
    ): AssembledBulletinsPayload {
        val isStn = flavor == AppBrandFlavor.SURETHING_HOME
        val deviceFeedLinks = mutableMapOf<String, MutableList<RackPortDevice>>()

        devices.forEach { dev ->
            if (dev.isRetired || dev.presenceState != DevicePresenceState.ACTIVE) return@forEach
            if (dev.confidenceTier == DeviceConfidenceTier.OPAQUE_UNKNOWN) return@forEach

            var matchedForDevice = feeds.filter { feed ->
                val matchesPrefix = feed.purlPrefixes.any { fp ->
                    dev.purls.any { dp ->
                        val cleanDp = dp.substringBefore("@").removeSuffix("/*")
                        val cleanFp = fp.removeSuffix("/*")
                        cleanDp.startsWith(cleanFp) || cleanFp.startsWith(cleanDp)
                    }
                }
                val excluded = feed.purlPrefixes.all { fp ->
                    val cleanFp = fp.removeSuffix("/*")
                    dev.excludedPurlPrefixes.any { ex ->
                        val cleanEx = ex.removeSuffix("/*")
                        cleanFp.startsWith(cleanEx)
                    }
                }
                matchesPrefix && !excluded
            }

            // Rule 3: Umbrella Cap — max 1 feed per PROVISIONAL_GUESS device
            if (dev.confidenceTier == DeviceConfidenceTier.PROVISIONAL_GUESS) {
                matchedForDevice = matchedForDevice.take(1)
            }

            matchedForDevice.forEach { feed ->
                deviceFeedLinks.getOrPut(feed.slug) { mutableListOf() }.add(dev)
            }
        }

        val matchedFeeds = feeds.filter { deviceFeedLinks.containsKey(it.slug) }
        val provisionalOnlySlugs = mutableSetOf<String>()

        val feedViews = matchedFeeds.map { feed ->
            val linkedDevs = deviceFeedLinks[feed.slug].orEmpty()
            val hasConfirmed = linkedDevs.any { it.confidenceTier == DeviceConfidenceTier.CONFIRMED }
            val mode = if (hasConfirmed) DeviceConfidenceTier.CONFIRMED else DeviceConfidenceTier.PROVISIONAL_GUESS
            if (!hasConfirmed) provisionalOnlySlugs.add(feed.slug)

            MatchedFeedView(
                id = feed.id,
                slug = feed.slug,
                name = if (isStn) feed.stnName else feed.proName,
                matchMode = mode,
                matchedPorts = linkedDevs.map { "#%02d %s".format(it.portIndex, it.shortLabel) },
                candidateModels = linkedDevs.flatMap { it.candidateModels }.distinct(),
                isMuted = mutedFeedSlugs.contains(feed.slug)
            )
        }

        val relevantBulletins = bulletins.filter { b ->
            val linkedDevs = deviceFeedLinks[b.canonicalFeedSlug] ?: return@filter false
            linkedDevs.any { dev ->
                val isExcluded = dev.excludedPurlPrefixes.any { ex ->
                    b.purlBase.startsWith(ex.removeSuffix("/*"))
                }
                !isExcluded && dev.purls.any { dp ->
                    val cleanDp = dp.substringBefore("@").removeSuffix("/*")
                    b.purlBase.startsWith(cleanDp) || cleanDp.startsWith(b.purlBase)
                }
            }
        }

        val unmutedConfirmedBulletins = relevantBulletins.filter { b ->
            !mutedFeedSlugs.contains(b.canonicalFeedSlug) && !provisionalOnlySlugs.contains(b.canonicalFeedSlug)
        }

        val mutedHighSevCount = relevantBulletins.count { b ->
            mutedFeedSlugs.contains(b.canonicalFeedSlug) && b.isHighSev
        }

        val provisionalHighSevCount = relevantBulletins.count { b ->
            provisionalOnlySlugs.contains(b.canonicalFeedSlug) && b.isHighSev
        }

        val aiBullets = unmutedConfirmedBulletins.map { b ->
            if (isStn) b.aiStnBullet else b.aiProBullet
        }

        val inboxItems = relevantBulletins.filter { b ->
            val isMuted = mutedFeedSlugs.contains(b.canonicalFeedSlug)
            val isProvisional = provisionalOnlySlugs.contains(b.canonicalFeedSlug)
            (!isMuted && !isProvisional) || b.isHighSev
        }.map { b ->
            val fromMuted = mutedFeedSlugs.contains(b.canonicalFeedSlug)
            val fromProvisional = provisionalOnlySlugs.contains(b.canonicalFeedSlug)
            val linkedDevs = deviceFeedLinks[b.canonicalFeedSlug].orEmpty()

            BulletinInboxView(
                externalId = b.externalId,
                feedSlug = b.canonicalFeedSlug,
                sourceUrl = b.sourceUrl,
                bulletinType = b.bulletinType,
                isHighSev = b.isHighSev,
                brokeThroughMute = fromMuted && b.isHighSev,
                matchedByGuess = fromProvisional,
                candidateModels = if (fromProvisional) linkedDevs.flatMap { it.candidateModels }.distinct() else emptyList(),
                title = if (isStn) b.stnTitle else b.proTitle,
                body = if (isStn) b.stnBody else b.proBody,
                publishedAt = b.publishedAt
            )
        }

        val (completedInboxItems, activeInboxItems) = inboxItems.partition { item ->
            val matchingBulletin = relevantBulletins.find { it.externalId == item.externalId }
            if (matchingBulletin == null) {
                false
            } else {
                val linkedDevs = deviceFeedLinks[matchingBulletin.canonicalFeedSlug].orEmpty().filter { dev ->
                    deviceMatchesBulletin(dev, matchingBulletin)
                }
                // If there are devices linked to this bulletin and all of them are remediated (PortLifecycleState.OK)
                linkedDevs.isNotEmpty() && linkedDevs.all { it.portState == PortLifecycleState.OK }
            }
        }

        val confirmedFeeds = matchedFeeds.filter { !provisionalOnlySlugs.contains(it.slug) }
        val activeFeedCount = confirmedFeeds.count { !mutedFeedSlugs.contains(it.slug) }
        val mutedFeedCount = matchedFeeds.count { mutedFeedSlugs.contains(it.slug) }

        return AssembledBulletinsPayload(
            activeFeedCount = activeFeedCount,
            mutedFeedCount = mutedFeedCount,
            provisionalFeedCount = provisionalOnlySlugs.size,
            mutedHighSevOverrideCount = mutedHighSevCount,
            provisionalHighSevCount = provisionalHighSevCount,
            aiBullets = aiBullets,
            matchedFeeds = feedViews,
            inbox = activeInboxItems,
            completedBulletins = completedInboxItems
        )
    }

    fun countBulletinsForDevice(
        device: RackPortDevice,
        bulletins: List<CuratedBulletinItem> = defaultGoldBulletins()
    ): Int {
        if (device.isRetired || device.presenceState != DevicePresenceState.ACTIVE || device.portState == PortLifecycleState.OK) return 0
        return bulletins.count { b ->
            deviceMatchesBulletin(device, b)
        }
    }

    fun findTopBulletinForDevice(
        device: RackPortDevice,
        bulletins: List<CuratedBulletinItem> = defaultGoldBulletins()
    ): CuratedBulletinItem? {
        if (device.isRetired || device.presenceState != DevicePresenceState.ACTIVE || device.portState == PortLifecycleState.OK) return null
        return bulletins.firstOrNull { b ->
            deviceMatchesBulletin(device, b)
        }
    }

    fun findBulletinsForDevice(
        device: RackPortDevice,
        bulletins: List<CuratedBulletinItem> = defaultGoldBulletins()
    ): List<CuratedBulletinItem> {
        if (device.isRetired || device.presenceState != DevicePresenceState.ACTIVE || device.portState == PortLifecycleState.OK) return emptyList()
        return bulletins.filter { b ->
            deviceMatchesBulletin(device, b)
        }
    }

    fun deviceMatchesBulletin(device: RackPortDevice, bulletin: CuratedBulletinItem): Boolean {
        if (device.isRetired || device.presenceState != DevicePresenceState.ACTIVE) return false
        val isExcluded = device.excludedPurlPrefixes.any { ex ->
            bulletin.purlBase.startsWith(ex.removeSuffix("/*"))
        }
        return !isExcluded && device.purls.any { dp ->
            val cleanDp = dp.substringBefore("@").removeSuffix("/*")
            bulletin.purlBase.startsWith(cleanDp) || cleanDp.startsWith(bulletin.purlBase)
        }
    }

    fun defaultRackPortDevices(): List<RackPortDevice> = listOf(
        RackPortDevice(
            id = "0192d9f0-0008-7000-8000-000000000108",
            portIndex = 1,
            portState = PortLifecycleState.OK,
            trustZone = TrustZone.Z0_CORE,
            category = StnHardwareCategory.C1_GATEWAY,
            assetTag = "STN-GW-01",
            shortLabel = "UDM-Pro",
            name = "UniFi Dream Machine Pro",
            roomLocation = "Basement Rack U02",
            version = "4.0.21",
            targetVersion = "4.0.21",
            releaseDate = "2020-02-15",
            purchaseDate = "2024-02-01",
            vendorEolAt = "2030-06-01",
            candles = listOf(
                DeviceLifecycleCandle("3.2.12", 0.05f, 0.14f, 0.50f, 0.70f, null),
                DeviceLifecycleCandle("4.0.21", 0.46f, 0.50f, 0.96f, 0.99f, null)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/ui/unifi-os@4.0.21"),
        ),
        RackPortDevice(
            id = "0192d9f0-0005-7000-8000-000000000105",
            portIndex = 2,
            portState = PortLifecycleState.EOL,
            trustZone = TrustZone.Z0_CORE,
            category = StnHardwareCategory.C3_AP,
            assetTag = "STN-AP-00",
            shortLabel = "R7000 AP",
            name = "Basement Netgear R7000",
            roomLocation = "Basement Workbench",
            version = "1.0.11.136",
            targetVersion = null,
            releaseDate = "2013-10-01",
            purchaseDate = "2016-09-01",
            vendorEolAt = "2025-12-31",
            candles = listOf(
                DeviceLifecycleCandle("1.0.11.136", 0.08f, 0.18f, 0.96f, 0.65f, null)
            ),
            lifecycleTier = "Legacy (EOL)",
            purls = listOf("pkg:generic/netgear/r7000@1.0.11.136"),
        ),
        RackPortDevice(
            id = "0192d9f0-0009-7000-8000-000000000109",
            portIndex = 3,
            portState = PortLifecycleState.OK,
            trustZone = TrustZone.Z0_CORE,
            category = StnHardwareCategory.C3_AP,
            assetTag = "STN-AP-01",
            shortLabel = "U7-Pro",
            name = "Living Room Ceiling UniFi U7 Pro",
            roomLocation = "1F Living Room",
            version = "7.0.66",
            targetVersion = "7.0.66",
            releaseDate = "2024-01-09",
            purchaseDate = "2025-03-10",
            vendorEolAt = "2032-03-10",
            candles = listOf(
                DeviceLifecycleCandle("7.0.59", 0.10f, 0.16f, 0.58f, 0.85f, null),
                DeviceLifecycleCandle("7.0.66", 0.54f, 0.58f, 0.96f, 0.99f, null)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/ui/u7-pro@7.0.66"),
        ),
        RackPortDevice(
            id = "0192d9f0-0004-7000-8000-000000000104",
            portIndex = 4,
            portState = PortLifecycleState.OK,
            trustZone = TrustZone.Z1_TRUSTED,
            category = StnHardwareCategory.C2_SWITCH,
            assetTag = "STN-SW-01",
            shortLabel = "Switch 2",
            name = "Living Room Nintendo Switch 2",
            roomLocation = "1F Media Console",
            version = "19.0.1",
            targetVersion = "19.0.1",
            releaseDate = "2025-06-01",
            purchaseDate = "2025-06-15",
            vendorEolAt = "2032-06-15",
            isRetired = true,
            decommissionDate = "2025-10-01",
            presenceState = DevicePresenceState.RETIRED,
            candles = listOf(
                DeviceLifecycleCandle("19.0.0", 0.15f, 0.25f, 0.50f, 0.90f, null),
                DeviceLifecycleCandle("19.0.1", 0.48f, 0.50f, 0.62f, 0.99f, null)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/nintendo/horizon-os@19.0.1"),
        ),
        RackPortDevice(
            id = "0192d9f0-0010-7000-8000-000000000110",
            portIndex = 5,
            portState = PortLifecycleState.UPDATE,
            trustZone = TrustZone.Z1_TRUSTED,
            category = StnHardwareCategory.C7_CLIENT,
            assetTag = "STN-CL-02",
            shortLabel = "MacBook M4",
            name = "Aaron's MacBook Pro 16 (M4 Max)",
            roomLocation = "2F Office Desk",
            version = "15.3.1",
            targetVersion = "15.3.2",
            releaseDate = "2024-11-01",
            purchaseDate = "2025-01-20",
            vendorEolAt = "2027-04-30",
            candles = listOf(
                DeviceLifecycleCandle("15.2.0", 0.12f, 0.18f, 0.52f, 0.75f, null),
                DeviceLifecycleCandle("15.3.1", 0.48f, 0.52f, 0.90f, 0.78f, null)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/apple/macbook-pro-16"),
        ),
        RackPortDevice(
            id = "0192d9f0-0001-7000-8000-000000000101",
            portIndex = 6,
            portState = PortLifecycleState.GUESSED,
            trustZone = TrustZone.Z2_LAB,
            category = StnHardwareCategory.C4_SERVER,
            assetTag = "STN-LAB-09",
            shortLabel = "Unknown Pi",
            name = "Unidentified Raspberry Pi 5 Node",
            roomLocation = "Basement Rack Shelf",
            version = "unverified",
            targetVersion = null,
            releaseDate = "2023-10-23",
            purchaseDate = null,
            vendorEolAt = null,
            candles = listOf(
                DeviceLifecycleCandle("unverified", 0.60f, 0.65f, 0.96f, 0.99f, null)
            ),
            lifecycleTier = "Unverified",
            purls = listOf("pkg:generic/unknown/*"),
            confidenceTier = DeviceConfidenceTier.OPAQUE_UNKNOWN,
        ),
        RackPortDevice(
            id = "0192d9f0-0002-7000-8000-000000000102",
            portIndex = 7,
            portState = PortLifecycleState.OK,
            trustZone = TrustZone.Z2_LAB,
            category = StnHardwareCategory.C4_SERVER,
            assetTag = "STN-NAS-01",
            shortLabel = "unraid-lml",
            name = "Unraid Storage Server (lml)",
            roomLocation = "Basement Rack U06",
            version = "7.0.0",
            targetVersion = "7.0.0",
            releaseDate = "2020-04-10",
            purchaseDate = "2024-11-15",
            vendorEolAt = "2031-11-15",
            candles = listOf(
                DeviceLifecycleCandle("6.12.14", 0.08f, 0.15f, 0.62f, 0.85f, null),
                DeviceLifecycleCandle("7.0.0", 0.58f, 0.62f, 0.96f, 0.99f, null)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/limetech/unraid@7.0.0"),
        ),
        RackPortDevice(
            id = "0192d9f0-0011-7000-8000-000000000111",
            portIndex = 8,
            portState = PortLifecycleState.UPDATE,
            trustZone = TrustZone.Z2_LAB,
            category = StnHardwareCategory.C4_SERVER,
            assetTag = "STN-LAB-02",
            shortLabel = "Proxmox VE",
            name = "Homelab Hypervisor (pve-node-01)",
            roomLocation = "Basement Rack U04",
            version = "8.3.1",
            targetVersion = "8.3.2",
            releaseDate = "2024-02-01",
            purchaseDate = "2025-02-01",
            vendorEolAt = "2030-02-01",
            candles = listOf(
                DeviceLifecycleCandle("8.2.4", 0.08f, 0.14f, 0.46f, 0.78f, null),
                DeviceLifecycleCandle("8.3.1", 0.44f, 0.46f, 0.90f, 0.99f, null)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:deb/debian/proxmox-ve@8.3.1"),
        ),
        RackPortDevice(
            id = "0192d9f0-0006-7000-8000-000000000106",
            portIndex = 9,
            portState = PortLifecycleState.CVE_KEV,
            trustZone = TrustZone.Z3_IOT,
            category = StnHardwareCategory.C5_CAMERA,
            assetTag = "STN-CAM-01",
            shortLabel = "Baby Cam",
            name = "Nursery Baby Monitor",
            roomLocation = "2F Nursery",
            version = "1.0.4",
            targetVersion = "2.1.0",
            releaseDate = "2022-03-01",
            purchaseDate = "2025-01-12",
            vendorEolAt = "2026-10-15",
            candles = listOf(
                DeviceLifecycleCandle("1.0.2", 0.05f, 0.12f, 0.45f, 0.55f, null),
                DeviceLifecycleCandle("1.0.4", 0.40f, 0.45f, 0.96f, 0.74f, 0.62f)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/owlet/cam-2@1.0.4"),
        ),
        RackPortDevice(
            id = "0192d9f0-0007-7000-8000-000000000107",
            portIndex = 10,
            portState = PortLifecycleState.CVE_KEV,
            trustZone = TrustZone.Z3_IOT,
            category = StnHardwareCategory.C6_IOT,
            assetTag = "STN-IOT-01",
            shortLabel = "LG OLED",
            name = "Living Room LG OLED TV",
            roomLocation = "1F Living Room",
            version = "8.1.0",
            targetVersion = "8.3.0",
            releaseDate = "2021-03-01",
            purchaseDate = "2022-11-28",
            vendorEolAt = "2027-11-28",
            candles = listOf(
                DeviceLifecycleCandle("8.0.4", 0.04f, 0.10f, 0.38f, 0.60f, null),
                DeviceLifecycleCandle("8.1.0", 0.32f, 0.38f, 0.96f, 0.78f, 0.72f)
            ),
            lifecycleTier = "Vintage",
            purls = listOf("pkg:generic/lg/webos@8.1.0"),
        ),
        RackPortDevice(
            id = "0192d9f0-0003-7000-8000-000000000103",
            portIndex = 11,
            portState = PortLifecycleState.UPDATE,
            trustZone = TrustZone.Z3_IOT,
            category = StnHardwareCategory.C6_IOT,
            assetTag = "STN-IOT-04",
            shortLabel = "Garage ESP",
            name = "Garage Door Relay ESP32",
            roomLocation = "Garage Ceiling",
            version = "2026.9.0",
            targetVersion = "2026.10.2",
            releaseDate = "2018-09-01",
            purchaseDate = "2024-03-10",
            vendorEolAt = "2029-03-10",
            isRetired = true,
            decommissionDate = "2025-08-20",
            presenceState = DevicePresenceState.STOPPED_WORKING,
            candles = listOf(
                DeviceLifecycleCandle("2026.8.2", 0.10f, 0.18f, 0.48f, 0.75f, null),
                DeviceLifecycleCandle("2026.9.0", 0.45f, 0.48f, 0.60f, 0.99f, null)
            ),
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/esphome/firmware@2026.9.0"),
        ),
        RackPortDevice(
            id = "0192d9f0-0012-7000-8000-000000000112",
            portIndex = 12,
            portState = PortLifecycleState.GUESSED,
            trustZone = TrustZone.Z3_IOT,
            category = StnHardwareCategory.C6_IOT,
            assetTag = "STN-IOT-09",
            shortLabel = "Thermostat",
            name = "Hallway Smart Thermostat",
            roomLocation = "1F Hallway",
            version = "unverified",
            targetVersion = null,
            releaseDate = "2022-05-15",
            purchaseDate = null,
            vendorEolAt = null,
            candles = listOf(
                DeviceLifecycleCandle("unverified", 0.55f, 0.62f, 0.96f, 0.99f, null)
            ),
            lifecycleTier = "Provisional Guess",
            purls = listOf("pkg:generic/ecobee/*"),
            confidenceTier = DeviceConfidenceTier.PROVISIONAL_GUESS,
            candidateModels = listOf("SmartThermostat Premium", "Ecobee3 Lite"),
        )
    )

    fun defaultGoldFeeds(): List<CanonicalFeedItem> = listOf(
        CanonicalFeedItem(
            id = "f-01",
            slug = "ubiquiti-advisories",
            proName = "Ubiquiti UniFi Security & Firmware",
            stnName = "UniFi Wi-Fi, Switch & Gateway Updates",
            purlPrefixes = listOf("pkg:generic/ui/", "pkg:generic/ubiquiti/", "pkg:generic/ubiquiti-unifi/", "pkg:deb/ubiquiti/")
        ),
        CanonicalFeedItem(
            id = "f-02",
            slug = "cpsc-safety",
            proName = "CPSC Consumer Safety & Power Recalls",
            stnName = "CPSC Home, Nursery & Battery Safety Recalls",
            purlPrefixes = listOf("pkg:generic/owlet/", "pkg:generic/anker/", "pkg:generic/ecoflow/", "pkg:generic/baseus/", "pkg:generic/jackery/", "pkg:generic/ugreen/", "pkg:generic/mophie/")
        ),
        CanonicalFeedItem(
            id = "f-03",
            slug = "vendor-eol-ledger",
            proName = "Netgear PSIRT, Firmware & EOL Ledger",
            stnName = "Netgear Nighthawk, Orbi & Switch Lifespan Notices",
            purlPrefixes = listOf("pkg:generic/netgear/")
        ),
        CanonicalFeedItem(
            id = "f-04",
            slug = "apple-proxmox-upstream",
            proName = "Apple macOS/iOS & Proxmox VE Releases",
            stnName = "Apple Devices & Home Server Software",
            purlPrefixes = listOf("pkg:generic/apple/", "pkg:generic/apple-ios/", "pkg:generic/apple-ipados/", "pkg:generic/apple-macos/", "pkg:deb/debian/proxmox-ve", "pkg:generic/proxmox/")
        ),
        CanonicalFeedItem(
            id = "f-05",
            slug = "unraid-os-releases",
            proName = "Unraid OS Stable Release Channel",
            stnName = "Unraid Storage Server Updates",
            purlPrefixes = listOf("pkg:generic/limetech/unraid")
        ),
        CanonicalFeedItem(
            id = "f-12",
            slug = "nintendo-switch-updates",
            proName = "Nintendo Switch & Horizon OS System Updates",
            stnName = "Nintendo Switch Console Updates",
            purlPrefixes = listOf("pkg:generic/nintendo/horizon-os")
        ),
        CanonicalFeedItem(
            id = "f-06",
            slug = "lg-esphome-iot",
            proName = "LG webOS TV & ESPHome IoT Feed",
            stnName = "LG Smart TV & Garage Smart Relay Feed",
            purlPrefixes = listOf("pkg:generic/lg/webos", "pkg:generic/lg-webos/", "pkg:generic/esphome/", "pkg:generic/espressif/", "pkg:generic/nodemcu/", "pkg:generic/ai-thinker/", "pkg:generic/wemos/", "pkg:generic/m5stack/", "pkg:generic/quinled/")
        ),
        CanonicalFeedItem(
            id = "f-07",
            slug = "ecobee-umbrella",
            proName = "Ecobee Thermostat Family (OUI Umbrella)",
            stnName = "Ecobee Thermostat Safety & Updates",
            purlPrefixes = listOf("pkg:generic/ecobee/")
        ),
        CanonicalFeedItem(
            id = "f-08",
            slug = "synology-dsm-advisories",
            proName = "Synology DSM Security Advisories",
            stnName = "Synology Home NAS Updates",
            purlPrefixes = listOf("pkg:generic/synology/", "pkg:generic/synology/dsm", "pkg:generic/synology-dsm/")
        ),
        CanonicalFeedItem(
            id = "f-09",
            slug = "cisco-ios-xe-enterprise",
            proName = "Cisco Catalyst IOS-XE Enterprise Advisories",
            stnName = "Cisco Enterprise Routers & Switches",
            purlPrefixes = listOf("pkg:generic/cisco/", "pkg:generic/cisco/ios-xe")
        ),
        CanonicalFeedItem(
            id = "f-10",
            slug = "smart-home-zigbee-matter-iot",
            proName = "Home Assistant OS, Raspberry Pi, SMLIGHT & Matter Hubs",
            stnName = "Smart Home Hub, Lighting, Audio & Camera Updates",
            purlPrefixes = listOf("pkg:generic/home-assistant/", "pkg:generic/raspberry-pi/", "pkg:generic/smlight/", "pkg:generic/sonoff/", "pkg:generic/philips-hue/", "pkg:generic/lutron/", "pkg:generic/sonos/", "pkg:generic/reolink/", "pkg:generic/wyze/", "pkg:generic/shelly/", "pkg:generic/aqara/", "pkg:generic/brother/", "pkg:generic/samsung-galaxy/", "pkg:generic/google-pixel/")
        ),
        CanonicalFeedItem(
            id = "f-11",
            slug = "isp-docsis-fiber-managed",
            proName = "US ISP Broadband, DOCSIS 3.1 & Fiber ONT Provisioning",
            stnName = "Internet Provider Modem & Gateway Auto-Updates",
            purlPrefixes = listOf("pkg:generic/xfinity/", "pkg:generic/technicolor/", "pkg:generic/at-t/", "pkg:generic/verizon/", "pkg:generic/spectrum/", "pkg:generic/t-mobile/", "pkg:generic/spacex-starlink/", "pkg:generic/starlink/", "pkg:generic/arris/", "pkg:generic/motorola/", "pkg:generic/hitron/", "pkg:generic/calix/", "pkg:generic/nokia/", "pkg:generic/nokia-fiber/", "pkg:generic/arcadyan-astoria/")
        )
    )

    fun defaultGoldBulletins(): List<CuratedBulletinItem> = listOf(
        CuratedBulletinItem(
            externalId = "CPSC-26-104",
            canonicalFeedSlug = "cpsc-safety",
            bulletinType = "recall",
            isHighSev = true,
            purlBase = "pkg:generic/owlet/cam-2",
            proTitle = "CPSC-26-104: Owlet Cam 2 Thermal Power Cord Recall",
            proBody = "Published CPSC physical hazard notice matching S1 serial 44A819002106. Inspect USB-C power cable and request free replacement kit.",
            stnTitle = "Safety Recall: Nursery Baby Monitor Power Cord",
            stnBody = "The U.S. Consumer Product Safety Commission issued a recall for the power cord on your Nursery Baby Monitor. Tap to request a free replacement cord.",
            aiProBullet = "1 Physical Recall (Port #09): CPSC-26-104 thermal cord recall on Nursery Baby Cam.",
            aiStnBullet = "Safety Recall (Nursery Baby Monitor): Check the power cord and claim a free replacement kit.",
            publishedAt = "2h ago"
        ),
        CuratedBulletinItem(
            externalId = "CVE-2026-88901",
            canonicalFeedSlug = "ecobee-umbrella",
            bulletinType = "cve",
            isHighSev = true,
            purlBase = "pkg:generic/ecobee/smart-thermostat",
            proTitle = "CVE-2026-88901: Ecobee HomeKit Local Pairing PIN Bypass",
            proBody = "High-severity LAN pairing bypass affecting Ecobee SmartThermostat family. Matched via OUI 44:61:32 on Port #12 (72% confidence) — confirm exact SKU.",
            stnTitle = "Possible Security Update: Hallway Smart Thermostat",
            stnBody = "We think Port #12 is an Ecobee Thermostat. Confirm your exact model to verify if this security advisory applies to your home.",
            aiProBullet = "1 Confirmed Ecobee CVE (Port #12): HomeKit LAN pairing bypass.",
            aiStnBullet = "Thermostat Security Fix (Hallway Ecobee): Update firmware to fix HomeKit pairing bug.",
            publishedAt = "3h ago"
        ),
        CuratedBulletinItem(
            externalId = "CVE-2026-21099",
            canonicalFeedSlug = "lg-esphome-iot",
            bulletinType = "cisa_kev",
            isHighSev = true,
            purlBase = "pkg:generic/lg/webos",
            proTitle = "CVE-2026-21099 (CISA KEV): LG webOS LAN Auth Bypass",
            proBody = "Active exploitation reported in CISA KEV catalog affecting webOS < 8.3.0. Port #10 is running 8.1.0; patch 8.3.0 is available upstream.",
            stnTitle = "Critical Security Fix Needed: Living Room LG OLED TV",
            stnBody = "A critical security flaw affects LG TVs running version 8.1.0. Update your TV software to version 8.3.0 in Settings -> Support.",
            aiProBullet = "1 CISA KEV Exploit (Port #10): LG webOS 8.1.0 auth bypass; patch 8.3.0 ready.",
            aiStnBullet = "Critical TV Update (Living Room LG TV): Install software update 8.3.0 to fix a security warning.",
            publishedAt = "5h ago"
        ),
        CuratedBulletinItem(
            externalId = "CVE-2026-50881",
            canonicalFeedSlug = "ubiquiti-advisories",
            sourceUrl = "https://community.ui.com/releases/Security-Advisory-Bulletin-064-064/84811c09-4cf4-42ab-bd61-cc994445963b",
            bulletinType = "cve",
            isHighSev = true,
            purlBase = "pkg:generic/ui/unifi-os",
            proTitle = "CVE-2026-50881: UniFi OS 4.0.20 Remote Management Advisory",
            proBody = "0-day advisory affecting unifi-core@4.0.20 (currently running on Port #01). Workaround: disable Direct Remote Connection until 4.0.21 ships.",
            stnTitle = "Security Workaround: Main Rack UniFi Gateway",
            stnBody = "Your router is on the newest version (4.0.20), but Ubiquiti recommends turning off Direct Remote Connection until 4.0.21 arrives.",
            aiProBullet = "1 Zero-Day Workaround (Port #01): UniFi OS 4.0.20 requires Direct Remote Connection disabled.",
            aiStnBullet = "Router Safety Tip (Main UniFi Gateway): Turn off Direct Remote Connection until version 4.0.21 releases.",
            publishedAt = "9h ago"
        ),
        CuratedBulletinItem(
            externalId = "EOL-netgear-r7000",
            canonicalFeedSlug = "vendor-eol-ledger",
            bulletinType = "eol",
            isHighSev = true,
            purlBase = "pkg:generic/netgear/r7000",
            proTitle = "CISA BOD 23-01 / Netgear EOL: R7000 Passed Support Cutoff",
            proBody = "Hardware on Port #02 reached End-of-Support (2025-12-31) with unpatched openssl@1.0.2u. Decommission from Z0 Core.",
            stnTitle = "Retired Device Notice: Basement Netgear R7000",
            stnBody = "Netgear ended security updates for this 2016 Wi-Fi router. We recommend replacing it with a supported access point.",
            aiProBullet = "1 End-of-Life Notice (Port #02): Netgear R7000 passed vendor EOL cutoff.",
            aiStnBullet = "Retired Router (Basement Netgear R7000): Ended manufacturer support; plan a replacement.",
            publishedAt = "1d ago"
        ),
        CuratedBulletinItem(
            externalId = "RECALL-apple-mbp16-battery",
            canonicalFeedSlug = "apple-proxmox-upstream",
            bulletinType = "recall",
            isHighSev = true,
            purlBase = "pkg:generic/apple/macbook-pro-16",
            proTitle = "Apple Service Advisory: MacBook Pro 16 Battery & Thermal Safety Notice",
            proBody = "Voluntary Apple battery safety recall applicable to select MacBook Pro 16 enclosures. Check hardware serial on Apple Support to confirm eligibility for free battery replacement.",
            stnTitle = "Safety Recall: MacBook Pro 16 Battery Service Notice",
            stnBody = "Apple issued a battery inspection advisory for MacBook Pro 16 models. Tap to check your serial number with Apple for a free battery service.",
            aiProBullet = "1 Safety Recall (Port #05): MacBook Pro 16 battery thermal service advisory.",
            aiStnBullet = "Battery Safety Recall: Check your MacBook Pro 16 serial number for free battery service.",
            publishedAt = "2d ago",
            sourceUrl = "https://support.apple.com/service-programs"
        ),
        CuratedBulletinItem(
            externalId = "VULN-apple-silicon-m-series",
            canonicalFeedSlug = "apple-proxmox-upstream",
            bulletinType = "cve",
            isHighSev = true,
            purlBase = "pkg:generic/apple/macbook-pro-16",
            proTitle = "Hardware Advisory: Apple Silicon M-Series Data Memory-Dependent Prefetcher (GoFetch)",
            proBody = "Microarchitectural side-channel vulnerability in Apple Silicon (M1/M2/M3/M4) CPU cores. Mitigation: enable runtime pointer authentication and audit cryptographic key isolations.",
            stnTitle = "Security Advisory: Apple Silicon Chip Defense",
            stnBody = "Researchers identified a chip-level security advisory affecting Apple Silicon processors. Update macOS to ensure security mitigations are enabled.",
            aiProBullet = "1 Silicon Advisory (Port #05): Apple Silicon M-series prefetcher microarchitecture defense.",
            aiStnBullet = "Apple Chip Security Alert: Ensure your MacBook Pro has the latest macOS updates applied.",
            publishedAt = "3d ago"
        ),
        CuratedBulletinItem(
            externalId = "EOL-apple-vintage-hardware",
            canonicalFeedSlug = "apple-proxmox-upstream",
            bulletinType = "eol",
            isHighSev = false,
            purlBase = "pkg:generic/apple/macbook-pro-16",
            proTitle = "Apple Hardware Lifecycle: Support Vintage Window Transition",
            proBody = "Hardware support window tracking. Apple considers products Vintage 5 years after distribution ceases; authorized parts and battery service transition to limited availability.",
            stnTitle = "Hardware Support Status: MacBook Pro Service Window",
            stnBody = "Track your laptop's official Apple service lifespan. Apple provides hardware repairs and parts for up to 5–7 years from release.",
            aiProBullet = "1 Lifecycle Notice (Port #05): MacBook Pro 16 manufacturer support runway tracking.",
            aiStnBullet = "Hardware Lifespan: Apple manufacturer service window tracking active.",
            publishedAt = "5d ago"
        ),
        CuratedBulletinItem(
            externalId = "REL-esphome-2026.10.2",
            canonicalFeedSlug = "lg-esphome-iot",
            bulletinType = "release",
            isHighSev = false,
            purlBase = "pkg:generic/esphome/firmware",
            proTitle = "ESPHome 2026.10.2 Minor Release (esp-idf@5.3.1)",
            proBody = "Minor Wi-Fi reconnect improvement for ESP32 relays on Port #11 (currently running 2026.9.0). No CVEs associated.",
            stnTitle = "Minor Feature Update: Garage Door Relay ESP32",
            stnBody = "ESPHome 2026.10.2 is available with minor Wi-Fi stability improvements for your garage relay.",
            aiProBullet = "1 IoT Release (Port #11): ESPHome 2026.10.2 minor update ready.",
            aiStnBullet = "1 Smart Home Update: Minor ESPHome 2026.10.2 update for Garage Door Relay.",
            publishedAt = "2d ago"
        ),
        CuratedBulletinItem(
            externalId = "REL-ecobee-4.8.7.220",
            canonicalFeedSlug = "ecobee-umbrella",
            bulletinType = "release",
            isHighSev = false,
            purlBase = "pkg:generic/ecobee/smart-thermostat",
            proTitle = "Ecobee Firmware 4.8.7.220 Routine Radar Tuning",
            proBody = "Routine occupancy radar tuning for SmartThermostat Premium (suppressed while Port #12 is an unconfirmed guess).",
            stnTitle = "Routine Thermostat Update (4.8.7.220)",
            stnBody = "Minor room sensor update for Ecobee SmartThermostat.",
            aiProBullet = "1 Thermostat Release (Port #12): Ecobee 4.8.7.220 available.",
            aiStnBullet = "1 Thermostat Update: Routine sensor tuning for Hallway Ecobee.",
            publishedAt = "4d ago"
        ),
        CuratedBulletinItem(
            externalId = "EOL-cisco-ws-c2960-8tc-l",
            canonicalFeedSlug = "cisco-ios-xe-enterprise",
            bulletinType = "eol",
            isHighSev = true,
            purlBase = "pkg:generic/cisco/ws-c2960-8tc-l",
            proTitle = "Cisco PSIRT / EOL: Catalyst 2960-8TC-L Passed Support Cutoff & Smart Install Advisory",
            proBody = "Cisco Catalyst WS-C2960-8TC-L reached End-of-Vulnerability-Support. Disable Cisco Smart Install (`no vstack` on TCP/4786) and isolate management VLAN.",
            stnTitle = "Retired Switch Security Notice: Cisco Catalyst 2960-8TC-L",
            stnBody = "Cisco no longer releases security updates for your Catalyst 2960 8-port switch. Keep it on a private home VLAN and disable Smart Install.",
            aiProBullet = "1 Switch EOL & Hardening Notice: Cisco WS-C2960-8TC-L requires `no vstack` hardening.",
            aiStnBullet = "Retired Switch Notice (Cisco Catalyst 2960): End of manufacturer security updates.",
            publishedAt = "1h ago"
        ),
        CuratedBulletinItem(
            externalId = "CVE-2024-10443-synology-ds220-plus",
            canonicalFeedSlug = "synology-dsm-advisories",
            bulletinType = "cve",
            isHighSev = true,
            purlBase = "pkg:generic/synology/ds220-plus",
            proTitle = "Synology-SA-24:20 (CVE-2024-10443): DiskStation DS220+ DSM / Synology Photos RCE",
            proBody = "Critical zero-click command injection affecting Synology Photos and DSM on DiskStation DS220+. Upgrade DSM to 7.2.2-72806-1 and Synology Photos to 1.7.0-0795.",
            stnTitle = "Critical Security Update: Synology DiskStation NAS (DS220+)",
            stnBody = "Synology fixed a critical security flaw in Synology Photos and DSM on the DS220+ series. Open DSM Control Panel -> Update & Restore to install the latest update.",
            aiProBullet = "1 NAS Critical CVE: Synology DS220+ DSM 7.2.2-72806-1 security patch required.",
            aiStnBullet = "Critical NAS Update (Synology DS220+): Install the latest DSM and Synology Photos security fix.",
            publishedAt = "1h ago"
        ),
        CuratedBulletinItem(
            externalId = "REL-synology-dsm-7.2.2-routine",
            canonicalFeedSlug = "synology-dsm-advisories",
            bulletinType = "release",
            isHighSev = false,
            purlBase = "pkg:generic/synology/ds220-plus",
            proTitle = "Synology DSM 7.2.2-72806 Update 2 Storage Pool SMART Telemetry",
            proBody = "Routine Btrfs scrub scheduling and NVMe cache telemetry update for DiskStation DS220+ (suppressed while device is an unconfirmed guess).",
            stnTitle = "Routine Storage Update: Synology DS220+",
            stnBody = "Minor drive health reporting update for Synology DiskStation DS220+.",
            aiProBullet = "1 Routine NAS Release: Synology DS220+ DSM 7.2.2 Update 2.",
            aiStnBullet = "1 Routine NAS Update: Minor drive health update for Synology DS220+.",
            publishedAt = "3h ago"
        ),
        CuratedBulletinItem(
            externalId = "CVE-2026-39104-esp8266-sdk",
            canonicalFeedSlug = "lg-esphome-iot",
            bulletinType = "cve",
            isHighSev = true,
            purlBase = "pkg:generic/espressif/esp8266mod",
            proTitle = "CVE-2026-39104: Espressif ESP8266MOD / NodeMCU WPA2 Beacon Buffer Overflow",
            proBody = "High-severity Wi-Fi beacon frame overflow in legacy ESP8266 NONOS/RTOS SDK on NodeMCU / AI-Thinker ESP8266MOD boards. Reflash OTA with ESPHome 2026.10.2+.",
            stnTitle = "Security Update Needed: ESP8266 / NodeMCU Smart Home Board",
            stnBody = "A Wi-Fi security flaw affects older firmware on your ESP8266 / NodeMCU board. Update it from your ESPHome or Home Assistant dashboard.",
            aiProBullet = "1 IoT High-Sev CVE: ESP8266MOD / NodeMCU WPA2 beacon overflow; reflash ESPHome 2026.10.2+.",
            aiStnBullet = "Smart Home Board Security Fix (ESP8266 / NodeMCU): Update wireless firmware via Home Assistant.",
            publishedAt = "30m ago"
        ),
        CuratedBulletinItem(
            externalId = "REL-sonoff-zbdongle-e-7.4.4",
            canonicalFeedSlug = "smart-home-zigbee-matter-iot",
            bulletinType = "release",
            isHighSev = false,
            purlBase = "pkg:generic/sonoff/zbdongle-e",
            proTitle = "SONOFF ZBDongle-E EmberZNet 7.4.4.0 Zigbee Coordinator Firmware",
            proBody = "Recommended Silicon Labs EFR32MG21 EmberZNet 7.4.4.0 NCP firmware for SONOFF ZBDongle-E improving ZHA / Zigbee2MQTT mesh routing table stability.",
            stnTitle = "New Coordinator Update: SONOFF Zigbee 3.0 USB Dongle Plus-E",
            stnBody = "Firmware 7.4.4.0 is available for your SONOFF Zigbee USB stick with improved smart home sensor range and reliability.",
            aiProBullet = "1 Zigbee Coordinator Release: SONOFF ZBDongle-E EmberZNet 7.4.4.0 available.",
            aiStnBullet = "1 Smart Home Dongle Update: Firmware 7.4.4.0 available for SONOFF Zigbee USB Dongle.",
            publishedAt = "2h ago"
        ),
        CuratedBulletinItem(
            externalId = "REL-docsis-cm1100-mb8600",
            canonicalFeedSlug = "isp-docsis-fiber-managed",
            bulletinType = "release",
            isHighSev = false,
            purlBase = "pkg:generic/motorola/mb8600",
            proTitle = "DOCSIS 3.1 OFDM Profile Update: Motorola MB8600 / MB7621 & Netgear CM1100",
            proBody = "ISP-pushed DOCSIS 3.1 downstream OFDM profile update (8600-19.3.18). Firmware is provisioned automatically via coaxial TFTP by your cable provider.",
            stnTitle = "Cable Modem Update: Motorola SURFboard / MB Series",
            stnBody = "Your internet provider automatically delivers software updates to your Motorola cable modem overnight. No manual download is required.",
            aiProBullet = "1 DOCSIS Modem Lifecycle Notice: Motorola MB8600 / MB7621 ISP-provisioned update.",
            aiStnBullet = "1 Cable Modem Notice (Motorola): Updates are managed automatically by your internet provider.",
            publishedAt = "4h ago"
        )
    )
}
