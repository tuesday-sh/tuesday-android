package sh.tuesday.app.models

import kotlinx.serialization.Serializable

@Serializable
enum class AppBrandFlavor(val title: String, val badge: String) {
    TUESDAY_PRO("Tuesday.sh", "PRO • RACK"),
    SURETHING_HOME("SureThingNet", "HOME • CALM")
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
    C8_GUEST("C8", 8, "L8", "Guest / Human (L8)", "Guest / Visitor Device", "Guest L8", "Isolated perimeter for ephemeral human visitors, guest Wi-Fi, and user policy.", "Guest Portal, Visitor Laptop, Cast Client")
}

@Serializable
data class NetworkSite(
    val id: String,
    val name: String,
    val devicesCount: Int,
    val role: String
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
data class RackPortDevice(
    val id: String,
    val portIndex: Int,
    val portState: PortLifecycleState,
    val trustZone: TrustZone,
    val category: StnHardwareCategory = StnHardwareCategory.C6_IOT,
    val assetTag: String,
    val shortLabel: String,
    val name: String,
    val roomLocation: String,
    val version: String,
    val targetVersion: String? = null,
    val lifecycleTier: String,
    val purls: List<String>,
    val confidenceTier: DeviceConfidenceTier = DeviceConfidenceTier.CONFIRMED,
    val candidateModels: List<String> = emptyList(),
    val excludedPurlPrefixes: List<String> = emptyList(),
    val proHeadline: String,
    val stnHeadline: String,
    val s0Identity: String,
    val s1Hardware: String,
    val s2Software: String,
    val s3Lifecycle: String,
    val s4Advisories: String,
    val s5Topology: String,
    val s6Remediation: String
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
    val inbox: List<BulletinInboxView>
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

        // Active assigned devices occupy slots 1..N
        var runningIndex = 1
        filtered.forEach { dev ->
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
            inbox = inboxItems
        )
    }

    fun defaultRackPortDevices(): List<RackPortDevice> = listOf(
        RackPortDevice(
            id = "0192d9f0-0008-7000-8000-000000000108",
            portIndex = 1,
            portState = PortLifecycleState.CVE_KEV,
            trustZone = TrustZone.Z0_CORE,
            category = StnHardwareCategory.C1_GATEWAY,
            assetTag = "STN-GW-01",
            shortLabel = "UDM-Pro",
            name = "Main Rack UniFi Dream Machine Pro",
            roomLocation = "Basement Rack U02",
            version = "4.0.20",
            targetVersion = "4.0.20",
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/ui/unifi-os@4.0.20"),
            proHeadline = "CVE-2026-50881 (0-Day • No Patch): Disable Direct Remote Connection",
            stnHeadline = "Safety Workaround: Turn off Direct Remote Connection in UniFi settings",
            s0Identity = "STN-GW-01 • Basement Rack U02 • Z0 Core",
            s1Hardware = "Ubiquiti UDM-Pro • purchased 2022-11 (26 mo remaining)",
            s2Software = "pkg:generic/ui/unifi-os@4.0.20 (Latest)",
            s3Lifecycle = "Active Support • Zero-Day Workaround Required",
            s4Advisories = "CVE-2026-50881 (CVSS 8.2, EPSS 0.34) • Unpatched 0-day",
            s5Topology = "Z0 Core Gateway • WAN1 + 8 GbE RJ45 + 2 10G SFP+",
            s6Remediation = "Disable Direct Remote Connection until UniFi OS 4.0.21 ships."
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
            lifecycleTier = "Legacy (EOL)",
            purls = listOf("pkg:generic/netgear/r7000@1.0.11.136"),
            proHeadline = "CISA BOD 23-01 • Vendor EOL (2025-12-31) • Decommission from Z0 Core",
            stnHeadline = "Retired Hardware (Age 9.5 yrs): Plan to replace with a modern Wi-Fi 7 access point",
            s0Identity = "STN-AP-00 • Basement Workbench • Z0 Core",
            s1Hardware = "Netgear Nighthawk R7000 • 2016-05 (PAST EOL 2025-12-31)",
            s2Software = "pkg:generic/netgear/r7000@1.0.11.136 + openssl@1.0.2u",
            s3Lifecycle = "Legacy / Obsolete (>8 yrs) • Zero vendor patches",
            s4Advisories = "CISA BOD 23-01 EOL Directive • Unpatched OpenSSL 1.0.2u",
            s5Topology = "Z0 Core AP • Recommend isolation or replacement",
            s6Remediation = "Replace with UniFi U7 Pro or move to isolated lab VLAN."
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
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/ui/u7-pro@7.0.66"),
            proHeadline = "pkg:generic/ui/u7-pro@7.0.66 • 0 CVEs • Lifecycle Active (54 mo left)",
            stnHeadline = "Up to date (7.0.66) • 4.5 years of manufacturer support remaining",
            s0Identity = "STN-AP-01 • 1F Living Room • Z0 Core",
            s1Hardware = "Ubiquiti U7-Pro • Wi-Fi 7 Tri-Band AP",
            s2Software = "pkg:generic/ui/u7-pro@7.0.66",
            s3Lifecycle = "Active Support (54 mo remaining)",
            s4Advisories = "0 Active CVEs / KEVs",
            s5Topology = "Z0 Core Access Point",
            s6Remediation = "No action required."
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
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/nintendo/horizon-os@19.0.1"),
            proHeadline = "pkg:generic/nintendo/horizon-os@19.0.1 • Enrolled via Plug & Tag",
            stnHeadline = "Up to date (19.0.1) • Warranty receipt saved",
            s0Identity = "STN-SW-01 • 1F Media Console • Z1 Trusted",
            s1Hardware = "Nintendo Switch 2 (BEE-001)",
            s2Software = "pkg:generic/nintendo/horizon-os@19.0.1",
            s3Lifecycle = "Active Support",
            s4Advisories = "0 Active CVEs",
            s5Topology = "Z1 Trusted Docked Ethernet",
            s6Remediation = "No action required."
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
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/apple/macos@15.3.1"),
            proHeadline = "Update Published: pkg:generic/apple/macos@15.3.1 → 15.3.2",
            stnHeadline = "Software Update Ready: macOS 15.3.2 is available to install",
            s0Identity = "STN-CL-02 • 2F Office Desk • Z1 Trusted",
            s1Hardware = "Apple MacBook Pro 16 (M4 Max)",
            s2Software = "pkg:generic/apple/macos@15.3.1 (15.3.2 available)",
            s3Lifecycle = "Active Support (60 mo remaining)",
            s4Advisories = "Routine maintenance update available",
            s5Topology = "Z1 Trusted Workstation",
            s6Remediation = "Install macOS 15.3.2 during Tuesday patch window."
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
            lifecycleTier = "Unverified",
            purls = listOf("pkg:generic/unknown/*"),
            confidenceTier = DeviceConfidenceTier.OPAQUE_UNKNOWN,
            proHeadline = "Opaque Unknown (42% MAC OUI D8:3A:DD): 0 Feeds Attached — Tag Model",
            stnHeadline = "Unidentified Device: Snap a photo of the label so we can track updates",
            s0Identity = "STN-LAB-09 • Basement Rack Shelf • Z2 Lab",
            s1Hardware = "Raspberry Pi Foundation OUI (D8:3A:DD) • Confidence 42%",
            s2Software = "Unverified OS / PURL",
            s3Lifecycle = "Unknown until identified",
            s4Advisories = "0 Feeds Attached (Opaque Unknown <50% confidence)",
            s5Topology = "Z2 Lab Switch Port 6",
            s6Remediation = "Use Plug & Tag camera or enroll tuesday-agent to verify OS."
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
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/limetech/unraid@7.0.0"),
            proHeadline = "pkg:generic/limetech/unraid@7.0.0 • Chainguard Golden Digests Match",
            stnHeadline = "Up to date (Unraid 7.0.0) • All 14 apps healthy",
            s0Identity = "STN-NAS-01 • Basement Rack U06 • Z2 Lab",
            s1Hardware = "Custom 2U Rackmount NAS (lml)",
            s2Software = "pkg:generic/limetech/unraid@7.0.0",
            s3Lifecycle = "Active Support",
            s4Advisories = "0 Active CVEs",
            s5Topology = "Z2 Lab 10GbE Trunk",
            s6Remediation = "No action required."
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
            lifecycleTier = "Active",
            purls = listOf("pkg:deb/debian/proxmox-ve@8.3.1"),
            proHeadline = "Update Published: pkg:deb/debian/proxmox-ve@8.3.1 → 8.3.2",
            stnHeadline = "Update Available: Proxmox Server 8.3.2 ready for Tuesday window",
            s0Identity = "STN-LAB-02 • Basement Rack U04 • Z2 Lab",
            s1Hardware = "Minisforum MS-01 (pve-node-01)",
            s2Software = "pkg:deb/debian/proxmox-ve@8.3.1 (8.3.2 available)",
            s3Lifecycle = "Active Support",
            s4Advisories = "QEMU maintenance update queued",
            s5Topology = "Z2 Lab Hypervisor Trunk",
            s6Remediation = "Apply apt dist-upgrade during Tuesday window."
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
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/owlet/cam-2@1.0.4"),
            proHeadline = "CPSC Recall #26-104 (Thermal Power Cord) + Firmware 1.0.4 → 2.1.0",
            stnHeadline = "Safety Recall & Firmware Fix: Check power cord and update to 2.1.0",
            s0Identity = "STN-CAM-01 • 2F Nursery • Z3 IoT",
            s1Hardware = "Owlet Cam 2 • S/N 44A819002106",
            s2Software = "pkg:generic/owlet/cam-2@1.0.4 (2.1.0 available)",
            s3Lifecycle = "Active Support • Physical Safety Recall",
            s4Advisories = "CPSC Recall #26-104 + RTSP Auth Bypass CVE",
            s5Topology = "Z3 IoT Wi-Fi VLAN 30",
            s6Remediation = "Order free replacement USB-C cord and update firmware to 2.1.0."
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
            lifecycleTier = "Vintage",
            purls = listOf("pkg:generic/lg/webos@8.1.0"),
            proHeadline = "CISA KEV CVE-2026-21099 (EPSS 0.91): Patch 8.1.0 → 8.3.0 Available",
            stnHeadline = "Security Update Ready: Update LG TV software from 8.1.0 to 8.3.0",
            s0Identity = "STN-IOT-01 • 1F Living Room • Z3 IoT",
            s1Hardware = "LG OLED65C3PUA • Vintage Tier",
            s2Software = "pkg:generic/lg/webos@8.1.0 (8.3.0 available)",
            s3Lifecycle = "Vintage Support (8 mo remaining)",
            s4Advisories = "CISA KEV CVE-2026-21099 (CVSS 9.1, EPSS 0.91)",
            s5Topology = "Z3 IoT Media VLAN",
            s6Remediation = "Update TV firmware to 8.3.0 in Settings -> Support -> Software Update."
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
            lifecycleTier = "Active",
            purls = listOf("pkg:generic/esphome/firmware@2026.9.0"),
            proHeadline = "Update Published: pkg:generic/esphome/firmware@2026.9.0 → 2026.10.2",
            stnHeadline = "Minor Feature Update: ESPHome 2026.10.2 available in Home Assistant",
            s0Identity = "STN-IOT-04 • Garage Ceiling • Z3 IoT",
            s1Hardware = "ESP32-WROOM-32D Relay Node",
            s2Software = "pkg:generic/esphome/firmware@2026.9.0 (2026.10.2 available)",
            s3Lifecycle = "Active Support",
            s4Advisories = "0 Active CVEs • Minor Wi-Fi reconnect fix",
            s5Topology = "Z3 IoT Wi-Fi",
            s6Remediation = "Trigger OTA update via Home Assistant ESPHome dashboard."
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
            lifecycleTier = "Provisional Guess",
            purls = listOf("pkg:generic/ecobee/*"),
            confidenceTier = DeviceConfidenceTier.PROVISIONAL_GUESS,
            candidateModels = listOf("SmartThermostat Premium", "Ecobee3 Lite"),
            proHeadline = "Best Guess (72% OUI 44:61:32 + _hap._tcp): Ecobee Family • Confirm Model",
            stnHeadline = "Likely Ecobee Thermostat (72% Match): Confirm exact model on Bulletins tab",
            s0Identity = "STN-IOT-09 • 1F Hallway • Z3 IoT",
            s1Hardware = "Ecobee Inc. OUI (44:61:32) + HomeKit mDNS • 72% Guess",
            s2Software = "pkg:generic/ecobee/* (Provisional Umbrella PURL)",
            s3Lifecycle = "Provisional Guardrail Mode (High-Sev Alerts Only)",
            s4Advisories = "CVE-2026-88901 HomeKit Pairing Bypass (Confirm Model to Verify)",
            s5Topology = "Z3 IoT Wi-Fi",
            s6Remediation = "Tap Confirm Model on Bulletins tab to lock exact hardware SKU."
        )
    )

    fun defaultGoldFeeds(): List<CanonicalFeedItem> = listOf(
        CanonicalFeedItem(
            id = "f-01",
            slug = "ubiquiti-advisories",
            proName = "Ubiquiti UniFi Security & Firmware",
            stnName = "UniFi Wi-Fi & Router Updates",
            purlPrefixes = listOf("pkg:generic/ui/unifi-os", "pkg:generic/ui/u7-pro")
        ),
        CanonicalFeedItem(
            id = "f-02",
            slug = "cpsc-safety",
            proName = "CPSC Consumer Recalls & IoT Advisories",
            stnName = "U.S. Safety Recalls & Nursery Alerts",
            purlPrefixes = listOf("pkg:generic/owlet/cam-2")
        ),
        CanonicalFeedItem(
            id = "f-03",
            slug = "vendor-eol-ledger",
            proName = "CISA BOD 23-01 & Vendor EOL Ledger",
            stnName = "Retired Device & End-of-Support Notices",
            purlPrefixes = listOf("pkg:generic/netgear/r7000")
        ),
        CanonicalFeedItem(
            id = "f-04",
            slug = "apple-proxmox-upstream",
            proName = "Apple macOS & Debian Proxmox Releases",
            stnName = "MacBook & Home Server Software Updates",
            purlPrefixes = listOf("pkg:generic/apple/macos", "pkg:deb/debian/proxmox-ve")
        ),
        CanonicalFeedItem(
            id = "f-05",
            slug = "unraid-nintendo-stable",
            proName = "Unraid OS & Nintendo Horizon OS",
            stnName = "Media Server & Game Console Updates",
            purlPrefixes = listOf("pkg:generic/limetech/unraid", "pkg:generic/nintendo/horizon-os")
        ),
        CanonicalFeedItem(
            id = "f-06",
            slug = "lg-esphome-iot",
            proName = "LG webOS TV & ESPHome Firmware",
            stnName = "Smart TV & Garage Sensor Updates",
            purlPrefixes = listOf("pkg:generic/lg/webos", "pkg:generic/esphome/firmware")
        ),
        CanonicalFeedItem(
            id = "f-07",
            slug = "ecobee-umbrella",
            proName = "Ecobee SmartThermostat Family Advisories",
            stnName = "Ecobee Thermostat Security & Recalls",
            purlPrefixes = listOf("pkg:generic/ecobee/*")
        ),
        CanonicalFeedItem(
            id = "f-08",
            slug = "synology-dsm",
            proName = "Synology DSM Advisories",
            stnName = "Synology NAS Updates",
            purlPrefixes = listOf("pkg:generic/synology/dsm")
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
            externalId = "REL-macos-15.3.2-pve-8.3.2",
            canonicalFeedSlug = "apple-proxmox-upstream",
            bulletinType = "release",
            isHighSev = false,
            purlBase = "pkg:generic/apple/macos",
            proTitle = "Upstream Releases: macOS 15.3.2 & Proxmox VE 8.3.2 Available",
            proBody = "Routine maintenance releases published for Port #05 (MacBook Pro M4: 15.3.1 -> 15.3.2) and Port #08 (pve-node-01: 8.3.1 -> 8.3.2).",
            stnTitle = "New Software Releases: MacBook Pro & Home Server",
            stnBody = "Routine stability updates are ready for Aaron's MacBook Pro (macOS 15.3.2) and your Proxmox Home Server (8.3.2).",
            aiProBullet = "2 Routine Releases (Ports #05, #08): macOS 15.3.2 and Proxmox VE 8.3.2 available.",
            aiStnBullet = "2 Routine Software Updates: New maintenance releases for MacBook Pro and Home Server.",
            publishedAt = "1d ago"
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
        )
    )
}
