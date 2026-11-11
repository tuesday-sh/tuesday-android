package sh.tuesday.app

import org.junit.Assert.*
import org.junit.Test
import sh.tuesday.app.models.*
import sh.tuesday.app.services.GeminiDiscoveryService

class MonitoredDeviceTest {

    @Test
    fun testHealthyDeviceEvaluation() {
        val now = 1700000000000L
        val device = MonitoredDevice(
            hostname = "aaronpro18",
            osDetails = "macOS 15.7.9",
            packageManager = PackageManagerType.BREW,
            lastCheckInEpochMs = now - 10 * 60 * 1000,
            pendingUpdates = emptyList(),
            healthScore = 100.0,
            status = NodeHealthStatus.HEALTHY
        )

        assertEquals(NodeHealthStatus.HEALTHY, device.status)
        assertEquals(100.0, device.healthScore, 0.01)
        assertEquals("sensor.tuesday_aaronpro18", device.haEntityId)
    }

    @Test
    fun testWarningDeviceEvaluationWithOutdatedPackages() {
        val now = 1700000000000L
        val updates = listOf(
            PackageUpdate(name = "esphome", currentVersion = "2026.9.1", availableVersion = "2026.10.0")
        )
        val score = MonitoredDevice.calculateHealthScore(updates, now - 5 * 60 * 1000, now)
        val status = MonitoredDevice.evaluateStatus(score, updates, now - 5 * 60 * 1000, now)

        assertEquals(NodeHealthStatus.WARNING, status)
        assertEquals(92.0, score, 0.01)
    }

    @Test
    fun testCriticalDeviceEvaluationWithSecurityCriticalUpdate() {
        val now = 1700000000000L
        val updates = listOf(
            PackageUpdate(
                name = "openssl",
                currentVersion = "3.0.13-1",
                availableVersion = "3.0.13-4",
                isSecurityCritical = true
            )
        )
        val score = MonitoredDevice.calculateHealthScore(updates, now, now)
        val status = MonitoredDevice.evaluateStatus(score, updates, now, now)

        assertEquals(NodeHealthStatus.CRITICAL, status)
        assertEquals(70.0, score, 0.01)
    }

    @Test
    fun testProgressiveGeminiDiscoveryHeuristicNarrowing() {
        val initialIdentifiers = ExtractedDeviceIdentifiers()
        val initialResult = GeminiDiscoveryService.fallbackHeuristic(
            accumulatedIdentifiers = initialIdentifiers,
            userClueNotes = emptyList(),
            imageCount = 1,
            usedLiveApi = false
        )

        assertFalse(initialResult.candidates.isEmpty())
        assertTrue(initialResult.nextNarrowingQuestion.contains("Barcode") || initialResult.nextNarrowingQuestion.contains("MAC"))

        // Add Model & MAC clue
        initialIdentifiers.modelNumber = "UCG-Ultra"
        initialIdentifiers.macAddress = "74:83:C2:9E:11:04"

        val refinedResult = GeminiDiscoveryService.fallbackHeuristic(
            accumulatedIdentifiers = initialIdentifiers,
            userClueNotes = listOf("Model=UCG-Ultra"),
            imageCount = 1,
            usedLiveApi = false
        )

        val topMatch = refinedResult.candidates.first()
        assertTrue(topMatch.confidencePercent >= 90)
        assertEquals("UCG-Ultra", topMatch.modelNumber)
    }

    @Test
    fun test128LedMatrixAndActionFirstFiltering() {
        val devices = BulletinsAndInventoryEngine.defaultRackPortDevices()
        val matrix = BulletinsAndInventoryEngine.build128LedMatrix(devices, zoneFilter = null)

        assertEquals(128, matrix.size)
        assertEquals(3, matrix.count { it.state == PortLifecycleState.OK })
        assertEquals(116, matrix.count { it.state == PortLifecycleState.UNASSIGNED })
        assertEquals(3, matrix.count { it.state == PortLifecycleState.CVE_KEV })
        assertEquals(1, matrix.count { it.state == PortLifecycleState.EOL })
        assertEquals(2, matrix.count { it.state == PortLifecycleState.GUESSED })
        assertEquals(3, matrix.count { it.state == PortLifecycleState.UPDATE })

        val actionList = BulletinsAndInventoryEngine.filterActionFirstDevices(devices, includeOk = false)
        assertEquals(9, actionList.size)
        assertTrue(actionList.none { it.portState == PortLifecycleState.OK })
        assertEquals(PortLifecycleState.CVE_KEV, actionList.first().portState)

        val allList = BulletinsAndInventoryEngine.filterActionFirstDevices(devices, includeOk = true)
        assertEquals(12, allList.size)

        val cveOnly = BulletinsAndInventoryEngine.filterActionFirstDevices(
            devices,
            includeOk = false,
            portStateFilter = PortLifecycleState.CVE_KEV
        )
        assertEquals(3, cveOnly.size)
    }

    @Test
    fun testAdaptiveInventoryScaleTiersAndStnCategories() {
        assertEquals(InventoryScaleTier.DAY_ZERO, InventoryScaleTier.forCount(0))
        assertEquals(InventoryScaleTier.SEED, InventoryScaleTier.forCount(1))
        assertEquals(InventoryScaleTier.SMALL_NETWORK, InventoryScaleTier.forCount(3))
        assertEquals(InventoryScaleTier.SMALL_NETWORK, InventoryScaleTier.forCount(7))
        assertEquals(InventoryScaleTier.FLEET_RACK, InventoryScaleTier.forCount(8))
        assertEquals(InventoryScaleTier.FLEET_RACK, InventoryScaleTier.forCount(12))

        val devices = BulletinsAndInventoryEngine.defaultRackPortDevices()
        val totalAcrossC1ToC8 = StnHardwareCategory.entries.sumOf { cat ->
            BulletinsAndInventoryEngine.getCategoryDeviceCount(cat, devices)
        }
        assertEquals(12, totalAcrossC1ToC8)
        assertTrue(BulletinsAndInventoryEngine.getCategoryDeviceCount(StnHardwareCategory.C1_GATEWAY, devices) >= 1)
        assertTrue(BulletinsAndInventoryEngine.getCategoryDeviceCount(StnHardwareCategory.C4_SERVER, devices) >= 1)
    }

    @Test
    fun testBulletinsAssemblyWithMuteOverrideAndBestGuessRules() {
        val devices = BulletinsAndInventoryEngine.defaultRackPortDevices()
        val payload = BulletinsAndInventoryEngine.assembleBulletins(
            flavor = AppBrandFlavor.TUESDAY_PRO,
            devices = devices,
            mutedFeedSlugs = setOf("lg-esphome-iot")
        )

        // 7 matched feeds (5 active confirmed, 1 muted confirmed, 1 provisional best-guess; Synology unmatched)
        assertEquals(7, payload.matchedFeeds.size)
        assertEquals(5, payload.activeFeedCount)
        assertEquals(1, payload.mutedFeedCount)
        assertEquals(1, payload.provisionalFeedCount)
        assertEquals(1, payload.mutedHighSevOverrideCount)
        assertEquals(1, payload.provisionalHighSevCount)

        // Top-level AI summary excludes muted feeds and provisional guess feeds
        assertEquals(4, payload.aiBullets.size)

        // Inbox has 6 items: 4 unmuted confirmed + 1 muted high-sev breakthrough + 1 provisional high-sev
        assertEquals(6, payload.inbox.size)
        val lgBreakthrough = payload.inbox.find { it.externalId == "CVE-2026-21099" }
        assertNotNull(lgBreakthrough)
        assertTrue(lgBreakthrough!!.brokeThroughMute)

        val ecobeeGuess = payload.inbox.find { it.externalId == "CVE-2026-88901" }
        assertNotNull(ecobeeGuess)
        assertTrue(ecobeeGuess!!.matchedByGuess)
        assertTrue(ecobeeGuess.candidateModels.contains("SmartThermostat Premium"))

        // Routine releases from muted or provisional feeds are suppressed
        assertNull(payload.inbox.find { it.externalId == "REL-esphome-2026.10.2" })
        assertNull(payload.inbox.find { it.externalId == "REL-ecobee-4.8.7.220" })

        // Out-of-service devices (#04 Switch 2 RETIRED, #11 Garage ESP STOPPED_WORKING) are excluded from matchedPorts
        val unraidFeed = payload.matchedFeeds.find { it.slug == "unraid-nintendo-stable" }
        assertNotNull(unraidFeed)
        assertEquals(listOf("#07 unraid-lml"), unraidFeed!!.matchedPorts)

        val lgEspFeed = payload.matchedFeeds.find { it.slug == "lg-esphome-iot" }
        assertNotNull(lgEspFeed)
        assertEquals(listOf("#10 LG OLED"), lgEspFeed!!.matchedPorts)
    }

    @Test
    fun testTimelineSortingScrubberVersionAndRecentChanges() {
        val devices = BulletinsAndInventoryEngine.defaultRackPortDevices()

        // 1. Timeline sort: Z0_CORE -> Z4_GUEST, then portIndex ascending
        val timelineSorted = BulletinsAndInventoryEngine.sortDevicesForTimeline(devices)
        assertEquals(12, timelineSorted.size)
        assertEquals(TrustZone.Z0_CORE, timelineSorted.first().trustZone)
        assertEquals(1, timelineSorted.first().portIndex)
        assertEquals(TrustZone.Z3_IOT, timelineSorted.last().trustZone)

        // 2. Dynamic scrubber version interpolation
        val udmPro = devices.first { it.portIndex == 1 }
        assertEquals("3.2.12", BulletinsAndInventoryEngine.getEstimatedVersionAtCursor(udmPro, 0.25f))
        assertEquals("4.0.20", BulletinsAndInventoryEngine.getEstimatedVersionAtCursor(udmPro, 0.65f))
        assertEquals("4.0.20", BulletinsAndInventoryEngine.getEstimatedVersionAtCursor(udmPro, null))

        // 3. Recent changes sort: max 12 items, highest severity first, then newest purchaseDate
        val recentChanges = BulletinsAndInventoryEngine.sortDevicesByRecentChanges(devices)
        assertEquals(12, recentChanges.size)
        assertEquals(PortLifecycleState.CVE_KEV, recentChanges.first().portState)
        // Among the 3 CVE_KEV devices (port 1: 2024-02-01, port 9: 2025-01-12, port 10: 2022-11-28), port 9 has the most recent purchaseDate
        assertEquals(9, recentChanges.first().portIndex)

        // 4. C8 Human diagram recommendation indicator
        assertTrue(sh.tuesday.app.ui.tabs.hasActiveCheckupRecommendation(devices))
        val allOkDevices = devices.map { it.copy(portState = PortLifecycleState.OK, confidenceTier = DeviceConfidenceTier.CONFIRMED) }
        assertFalse(sh.tuesday.app.ui.tabs.hasActiveCheckupRecommendation(allOkDevices, welcomeComplete = true))
    }

    @Test
    fun testOperationalTenureCandlestickGrammar() {
        val devices = BulletinsAndInventoryEngine.defaultRackPortDevices()

        // 1. UDM-Pro: Pre-adoption age, operational tenure, remaining runway
        val udmPro = devices.first { it.portIndex == 1 }
        val udmCandle = BulletinsAndInventoryEngine.toOperationalCandle(udmPro, referenceNowNorm = 0.72f)
        assertEquals(sh.tuesday.app.models.LifecycleLayer.HARDWARE, udmCandle.layer)
        assertEquals(0.05f, udmCandle.marketReleaseNorm)
        assertEquals(0.50f, udmCandle.deployStartNorm)
        assertEquals(0.72f, udmCandle.deployEndNorm)
        assertEquals(0.99f, udmCandle.vendorEolNorm)
        // Left wick: pre-adoption age (0.50 - 0.05 = 0.45)
        assertEquals(0.45f, udmCandle.preAdoptionAgeNorm, 0.001f)
        // Right wick: remaining runway (0.99 - 0.72 = 0.27)
        assertEquals(0.27f, udmCandle.remainingRunwayNorm, 0.001f)
        assertFalse(udmCandle.isPastEol)
        assertFalse(udmCandle.isRetired)

        // 2. Netgear R7000: Post-EOL Overhang (Zombie hardware running past EOL)
        val r7000 = devices.first { it.portIndex == 2 }
        val r7000Candle = BulletinsAndInventoryEngine.toOperationalCandle(r7000, referenceNowNorm = 0.72f)
        assertTrue(r7000Candle.isPastEol)
        assertEquals(0.07f, r7000Candle.postEolOverhangNorm, 0.001f)
        assertEquals(0f, r7000Candle.remainingRunwayNorm, 0.001f)

        // 3. Retired gear: Hollow outline
        val retiredUdm = BulletinsAndInventoryEngine.toOperationalCandle(udmPro.copy(isRetired = true), referenceNowNorm = 0.72f)
        assertTrue(retiredUdm.isRetired)

        // 4. EOL nearing (Amber)
        val nearEolCandle = sh.tuesday.app.models.OperationalCandle(
            layer = sh.tuesday.app.models.LifecycleLayer.HARDWARE,
            id = "near-eol",
            shortLabel = "Expiring",
            name = "Expiring Gateway",
            marketReleaseNorm = 0.20f,
            deployStartNorm = 0.40f,
            deployEndNorm = 0.70f,
            vendorEolNorm = 0.75f // remaining runway = 0.05f (< 0.12f threshold)
        )
        assertTrue(nearEolCandle.isEolNearing)
        assertEquals(0.05f, nearEolCandle.remainingRunwayNorm, 0.001f)

        // 5. Multi-layer scaling: Host OS and Containers
        val osCandles = BulletinsAndInventoryEngine.sampleHostOsCandles(referenceNowNorm = 0.72f)
        assertEquals(6, osCandles.size)
        assertTrue(osCandles.any { it.isPastEol }) // Netgear Linux 2.6
        assertTrue(osCandles.any { it.isRetired }) // macOS Sonoma upgraded

        val containerCandles = BulletinsAndInventoryEngine.sampleContainerCandles(referenceNowNorm = 0.72f)
        assertEquals(5, containerCandles.size)
        assertTrue(containerCandles.any { it.isPastEol }) // pihole legacy
        assertTrue(containerCandles.any { it.isRetired }) // caddy alpine
    }

    @Test
    fun testUnifiedInventorySeedsDriveTimelineStates() {
        val allDevices = BulletinsAndInventoryEngine.defaultRackPortDevices()
        assertEquals(12, allDevices.size)

        // 1. Reset (0): 0 inventory items -> 0 timeline candles
        val zeroSeed = allDevices.take(0)
        assertEquals(0, zeroSeed.size)

        // 2. Pea (1): 1 inventory item -> 1 timeline candle (#01 UDM-Pro)
        val peaSeed = allDevices.take(1)
        assertEquals(1, peaSeed.size)
        val udm = peaSeed.first()
        assertEquals(1, udm.portIndex)
        assertEquals("2020-02-15", udm.releaseDate)
        assertEquals("2024-02-01", udm.purchaseDate)
        assertFalse(udm.isRetired)

        // 3. Garden (7): 7 inventory items -> includes Active (#03, #07), Zombie Overhang (#02), Retired (#04), and Runway Low (#05)
        val gardenSeed = allDevices.take(7)
        assertEquals(7, gardenSeed.size)
        val r7000Candle = BulletinsAndInventoryEngine.toOperationalCandle(gardenSeed.first { it.portIndex == 2 }, 0.72f)
        val retiredSwitchCandle = BulletinsAndInventoryEngine.toOperationalCandle(gardenSeed.first { it.portIndex == 4 }, 0.72f)
        val macRunwayCandle = BulletinsAndInventoryEngine.toOperationalCandle(gardenSeed.first { it.portIndex == 5 }, 0.72f)
        assertTrue(r7000Candle.isPastEol)
        assertTrue(retiredSwitchCandle.isRetired)
        assertEquals(sh.tuesday.app.models.DevicePresenceState.RETIRED, retiredSwitchCandle.presenceState)
        assertTrue(macRunwayCandle.isEolNearing)

        // 4. Forest (12): 12 inventory items -> also includes Stopped Working (#11) and Baby Cam Nearing EOL (#09)
        val stoppedEspCandle = BulletinsAndInventoryEngine.toOperationalCandle(allDevices.first { it.portIndex == 11 }, 0.72f)
        val camCandle = BulletinsAndInventoryEngine.toOperationalCandle(allDevices.first { it.portIndex == 9 }, 0.72f)
        assertTrue(stoppedEspCandle.isRetired)
        assertEquals(sh.tuesday.app.models.DevicePresenceState.STOPPED_WORKING, stoppedEspCandle.presenceState)
        assertTrue(camCandle.isEolNearing)
    }

    @Test
    fun testVisionDebugGoldAlignmentAndDeepLink() {
        val destOn = sh.tuesday.app.navigation.DeepLinkDestination.fromUri("tuesday://dev/vision-debug")
        assertTrue(destOn is sh.tuesday.app.navigation.DeepLinkDestination.VisionDebug)
        val visionDest = destOn as sh.tuesday.app.navigation.DeepLinkDestination.VisionDebug
        assertTrue(visionDest.enabled)
        assertTrue(visionDest.openWizard)

        val res = GeminiDiscoveryService.fallbackHeuristic(
            accumulatedIdentifiers = ExtractedDeviceIdentifiers(),
            userClueNotes = emptyList(),
            imageCount = 1,
            usedLiveApi = false,
            fallbackErrorReason = "SocketTimeoutException: test"
        )
        assertEquals(3, res.candidates.size)
        val alignedCand = res.candidates[0]
        val unalignedCand = res.candidates[2]
        assertTrue(alignedCand.goldAlignment?.aligned == true)
        assertEquals("ubiquiti-advisories", alignedCand.goldAlignment?.matchedFeedSlug)
        assertFalse(unalignedCand.goldAlignment?.aligned == true)
        assertEquals("UNALIGNED RAW MATCH", unalignedCand.goldAlignment?.statusLabel)
        assertNotNull(res.debugLogText)
        assertTrue(res.debugLogText!!.contains("[✓ GOLD ALIGNED]"))
        assertTrue(res.debugLogText!!.contains("[⚠ UNALIGNED RAW]"))
    }

    @Test
    fun testEnrolledInventoryBulletinsAndNotificationSyncDeepLink() {
        val syncDest = sh.tuesday.app.navigation.DeepLinkDestination.fromUri("tuesday://sync-notifications")
        assertTrue(syncDest is sh.tuesday.app.navigation.DeepLinkDestination.SyncNotifications)
        assertTrue((syncDest as sh.tuesday.app.navigation.DeepLinkDestination.SyncNotifications).forceResend)

        val enrolledLabDevices = listOf(
            RackPortDevice(
                id = "dev-cisco",
                portIndex = 20,
                category = StnHardwareCategory.C2_SWITCH,
                portState = PortLifecycleState.EOL,
                trustZone = TrustZone.Z0_CORE,
                assetTag = "STN-LAB-20",
                shortLabel = "WS-C2960",
                name = "Cisco WS-C2960-8TC-L",
                roomLocation = "Lab",
                version = "15.0(2)SE11",
                targetVersion = "EOL",
                lifecycleTier = "End of Life",
                purls = listOf("pkg:generic/cisco/ws-c2960-8tc-l@1.0.0"),
                confidenceTier = DeviceConfidenceTier.CONFIRMED,
            ),
            RackPortDevice(
                id = "dev-synology",
                portIndex = 21,
                category = StnHardwareCategory.C4_SERVER,
                portState = PortLifecycleState.GUESSED,
                trustZone = TrustZone.Z2_LAB,
                assetTag = "STN-LAB-21",
                shortLabel = "DS220+",
                name = "Synology DS220+",
                roomLocation = "Lab",
                version = "unverified",
                targetVersion = "7.2.2",
                lifecycleTier = "Provisional Guess",
                purls = listOf("pkg:generic/synology/ds220-plus@1.0.0"),
                confidenceTier = DeviceConfidenceTier.PROVISIONAL_GUESS,
                candidateModels = listOf("DS220+", "DS218+", "DS720+"),
            ),
            RackPortDevice(
                id = "dev-esp",
                portIndex = 22,
                category = StnHardwareCategory.C6_IOT,
                portState = PortLifecycleState.CVE_KEV,
                trustZone = TrustZone.Z3_IOT,
                assetTag = "STN-LAB-22",
                shortLabel = "ESP8266MOD",
                name = "ESP8266MOD",
                roomLocation = "Lab",
                version = "2026.9.0",
                targetVersion = "2026.10.2",
                lifecycleTier = "Active",
                purls = listOf("pkg:generic/espressif/esp8266mod@1.0.0"),
                confidenceTier = DeviceConfidenceTier.CONFIRMED,
            ),
            RackPortDevice(
                id = "dev-sonoff",
                portIndex = 23,
                category = StnHardwareCategory.C6_IOT,
                portState = PortLifecycleState.UPDATE,
                trustZone = TrustZone.Z3_IOT,
                assetTag = "STN-LAB-23",
                shortLabel = "ZBDongle-E",
                name = "SONOFF ZBDongle-E",
                roomLocation = "Lab",
                version = "7.4.1",
                targetVersion = "7.4.4",
                lifecycleTier = "Active",
                purls = listOf("pkg:generic/sonoff/zbdongle-e@1.0.0"),
                confidenceTier = DeviceConfidenceTier.CONFIRMED,
            )
        )

        val payload = BulletinsAndInventoryEngine.assembleBulletins(
            flavor = AppBrandFlavor.TUESDAY_PRO,
            devices = enrolledLabDevices,
            mutedFeedSlugs = setOf("lg-esphome-iot")
        )

        // Cisco EOL (confirmed), Synology CVE (guess), ESP8266 CVE (muted breakthrough), Sonoff Release (confirmed)
        // Synology routine release is suppressed because dev-synology is PROVISIONAL_GUESS!
        assertNotNull(payload.inbox.find { it.externalId == "EOL-cisco-ws-c2960-8tc-l" })
        val synologyCve = payload.inbox.find { it.externalId == "CVE-2024-10443-synology-ds220-plus" }
        assertNotNull(synologyCve)
        assertTrue(synologyCve!!.matchedByGuess)
        assertNull(payload.inbox.find { it.externalId == "REL-synology-dsm-7.2.2-routine" })

        val espCve = payload.inbox.find { it.externalId == "CVE-2026-39104-esp8266-sdk" }
        assertNotNull(espCve)
        assertTrue(espCve!!.brokeThroughMute)

        assertNotNull(payload.inbox.find { it.externalId == "REL-sonoff-zbdongle-e-7.4.4" })
    }

    @Test
    fun testDeviceAttestationLifecycleModelAndSeals() {
        val userSig = "Aaron Bronow (sig_4b9a8c)"
        val env = "Tuesday Android v2026.11 (Pixel 9 • net-default)"
        val auditDate = "2026-11-11"
        val timestamp = 1794412800000L

        // 1. Compute Deterministic Proof Hash
        val proof1 = BulletinsAndInventoryEngine.computeAttestationProof(
            id = "att-test-01",
            deviceId = "dev-udm",
            type = sh.tuesday.app.models.AttestationType.PRESENCE_AUDIT,
            auditDate = auditDate,
            userSignature = userSig,
            environment = env,
            timestampMs = timestamp
        )
        val proof2 = BulletinsAndInventoryEngine.computeAttestationProof(
            id = "att-test-01",
            deviceId = "dev-udm",
            type = sh.tuesday.app.models.AttestationType.PRESENCE_AUDIT,
            auditDate = auditDate,
            userSignature = userSig,
            environment = env,
            timestampMs = timestamp
        )
        // Proofs must be deterministic
        assertEquals(proof1, proof2)
        assertEquals(64, proof1.length) // Full SHA-256 hex string

        // 2. State Mapping for Lifecycle Transitions
        assertEquals(sh.tuesday.app.models.DevicePresenceState.ACTIVE, sh.tuesday.app.models.AttestationType.PRESENCE_AUDIT.resultingState)
        assertEquals(sh.tuesday.app.models.DevicePresenceState.ACTIVE, sh.tuesday.app.models.AttestationType.PRESENCE_REACTIVATED.resultingState)
        assertEquals(sh.tuesday.app.models.DevicePresenceState.RETIRED, sh.tuesday.app.models.AttestationType.ABSENCE_RETIRED.resultingState)
        assertEquals(sh.tuesday.app.models.DevicePresenceState.LOST, sh.tuesday.app.models.AttestationType.ABSENCE_LOST.resultingState)
        assertEquals(sh.tuesday.app.models.DevicePresenceState.STOPPED_WORKING, sh.tuesday.app.models.AttestationType.ABSENCE_STOPPED_WORKING.resultingState)

        // 3. Absence flags
        assertFalse(sh.tuesday.app.models.AttestationType.PRESENCE_AUDIT.isAbsence)
        assertTrue(sh.tuesday.app.models.AttestationType.ABSENCE_RETIRED.isAbsence)
        assertTrue(sh.tuesday.app.models.AttestationType.ABSENCE_LOST.isAbsence)
        assertTrue(sh.tuesday.app.models.AttestationType.ABSENCE_STOPPED_WORKING.isAbsence)

        // 4. DeviceAttestation instance and shortProof
        val att = sh.tuesday.app.models.DeviceAttestation(
            id = "att-test-01",
            deviceId = "dev-udm",
            deviceLabel = "UDM-Pro",
            type = sh.tuesday.app.models.AttestationType.ABSENCE_STOPPED_WORKING,
            presenceState = sh.tuesday.app.models.DevicePresenceState.STOPPED_WORKING,
            timestampMs = timestamp,
            timestampIso = "2026-11-11T12:00:00Z",
            auditDate = auditDate,
            userSignature = userSig,
            environment = env,
            auditSessionId = "AUDIT-2026-Q4",
            note = "Blown capacitor on power rail",
            cryptographicProof = proof1
        )
        assertEquals(proof1.take(12), att.shortProof)
        assertEquals(sh.tuesday.app.models.DevicePresenceState.STOPPED_WORKING, att.presenceState)
    }
}
