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
}
