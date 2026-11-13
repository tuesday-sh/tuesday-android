package sh.tuesday.app

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.tuesday.app.domain.BarcodeIdentifierParser
import sh.tuesday.app.domain.EntitlementGatePolicy
import sh.tuesday.app.domain.ManualEnrollmentResolver
import sh.tuesday.app.domain.RhythmPostureEvaluator
import sh.tuesday.app.domain.TimelineStatusEvaluator
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.AttestationType
import sh.tuesday.app.models.BulletinInboxView
import sh.tuesday.app.models.DeviceAttestation
import sh.tuesday.app.models.DevicePresenceState
import sh.tuesday.app.models.ExtractedDeviceIdentifiers
import sh.tuesday.app.models.InventoryConfidenceState
import sh.tuesday.app.models.NetworkSite
import sh.tuesday.app.models.OperationalCandle
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.models.StnHardwareCategory
import sh.tuesday.app.models.TrustZone
import sh.tuesday.app.services.GoldClueItem
import sh.tuesday.app.services.GoldClueKind

class DomainUiLogicTest {

    private fun makeDevice(
        id: String,
        name: String,
        state: PortLifecycleState,
        category: StnHardwareCategory = StnHardwareCategory.C1_GATEWAY
    ): RackPortDevice = RackPortDevice(
        id = id,
        portIndex = 0,
        portState = state,
        trustZone = TrustZone.Z0_CORE,
        category = category,
        assetTag = "INTERNAL-TAG-01",
        shortLabel = name.take(8).uppercase(),
        name = name,
        roomLocation = "Network Closet",
        version = "4.0.6",
        lifecycleTier = "Active",
        purls = listOf("pkg:hardware/ubiquiti/udm-pro@4.0.6")
    )

    private fun makeBulletin(
        id: String,
        type: String,
        title: String
    ): BulletinInboxView = BulletinInboxView(
        externalId = id,
        feedSlug = "ubiquiti-unifi",
        sourceUrl = "https://tuesday.sh/b/$id",
        bulletinType = type,
        isHighSev = true,
        brokeThroughMute = false,
        matchedByGuess = false,
        candidateModels = emptyList(),
        title = title,
        body = "Summary for $title",
        publishedAt = "2026-11-10T10:00:00Z"
    )

    private fun makeAttestation(
        deviceId: String,
        auditDate: String
    ): DeviceAttestation = DeviceAttestation(
        id = "att-$deviceId",
        deviceId = deviceId,
        deviceLabel = deviceId,
        type = AttestationType.PRESENCE_AUDIT,
        presenceState = DevicePresenceState.ACTIVE,
        timestampMs = 1762819200000L,
        timestampIso = "${auditDate}T12:00:00Z",
        auditDate = auditDate,
        userSignature = "Test Auditor",
        environment = "JUnit"
    )

    private fun makeCandle(
        id: String = "dev-1",
        deployEndNorm: Float = 0.72f,
        vendorEolNorm: Float = 0.95f,
        presenceState: DevicePresenceState = DevicePresenceState.ACTIVE,
        hasCveOrRecall: Boolean = false,
        latestAttestation: DeviceAttestation? = null
    ): OperationalCandle = OperationalCandle(
        id = id,
        shortLabel = "UDM-PRO",
        name = "UniFi Dream Machine Pro",
        marketReleaseNorm = 0.20f,
        deployStartNorm = 0.35f,
        deployEndNorm = deployEndNorm,
        vendorEolNorm = vendorEolNorm,
        isRetired = presenceState == DevicePresenceState.RETIRED,
        presenceState = presenceState,
        hasCveOrRecall = hasCveOrRecall,
        latestAttestation = latestAttestation
    )

    @Test
    fun rhythmPostureEvaluatorHandlesDayZeroHealthyAndActiveFindingsAcrossFlavors() {
        // 1. Day-0 empty fleet with incomplete welcome -> active recommendation for R2 only
        assertTrue(RhythmPostureEvaluator.hasActiveRecommendation(emptyList(), welcomeComplete = false))
        val dayZeroPro = RhythmPostureEvaluator.evaluatePosture(
            flavor = AppBrandFlavor.TUESDAY_PRO,
            devices = emptyList(),
            bulletins = emptyList(),
            welcomeComplete = false
        )
        assertEquals(4, dayZeroPro.size)
        assertNull(dayZeroPro[0].action) // r1 suppressed on Day-0
        assertNotNull(dayZeroPro[1].action) // r2 Welcome Bulletin
        assertNull(dayZeroPro[2].action) // r3 suppressed on Day-0
        assertNull(dayZeroPro[3].action) // r4 suppressed on Day-0

        // 2. All-OK fleet with welcomeComplete = true -> zero recommendations
        val okFleet = listOf(makeDevice("d1", "UDM-Pro", PortLifecycleState.OK))
        assertFalse(RhythmPostureEvaluator.hasActiveRecommendation(okFleet, welcomeComplete = true))
        val healthyItems = RhythmPostureEvaluator.evaluatePosture(
            flavor = AppBrandFlavor.SURETHING_HOME,
            devices = okFleet,
            bulletins = emptyList(),
            welcomeComplete = true
        )
        assertTrue(healthyItems.all { it.action == null && it.recommendation == null })

        // 3. Mixed fleet with GUESSED + CVE_KEV + EOL and RECALL + CISA_KEV bulletins across flavors
        val mixedFleet = listOf(
            makeDevice("d1", "Mystery Plug", PortLifecycleState.GUESSED, StnHardwareCategory.C6_IOT),
            makeDevice("d2", "EdgeRouter X", PortLifecycleState.CVE_KEV, StnHardwareCategory.C1_GATEWAY),
            makeDevice("d3", "AirPort Extreme", PortLifecycleState.EOL, StnHardwareCategory.C3_AP)
        )
        val bulletins = listOf(
            makeBulletin("b1", "RECALL", "Smart Plug Thermal Recall"),
            makeBulletin("b2", "CISA_KEV", "CVE-2026-1001 Actively Exploited")
        )
        assertTrue(RhythmPostureEvaluator.hasActiveRecommendation(mixedFleet, welcomeComplete = true))

        val proPosture = RhythmPostureEvaluator.evaluatePosture(
            flavor = AppBrandFlavor.TUESDAY_PRO,
            devices = mixedFleet,
            bulletins = bulletins,
            welcomeComplete = true
        )
        val homePosture = RhythmPostureEvaluator.evaluatePosture(
            flavor = AppBrandFlavor.SURETHING_HOME,
            devices = mixedFleet,
            bulletins = bulletins,
            welcomeComplete = true
        )

        assertTrue(proPosture.all { it.action != null && it.recommendation != null })
        assertTrue(proPosture[0].title.contains("NEW HARDWARE INTAKE"))
        assertTrue(homePosture[0].title.contains("New Devices & Recognition"))
        assertTrue(proPosture[3].recommendation!!.contains("Guest VLAN"))
        assertTrue(homePosture[3].recommendation!!.contains("unplugging"))

        val shareReport = RhythmPostureEvaluator.formatShareReport(AppBrandFlavor.TUESDAY_PRO, proPosture)
        assertTrue(shareReport.contains("Tuesday.sh (STN-Pro) Fleet Check-Up"))
        assertTrue(shareReport.contains("⚠️ Action Needed"))

        val healthyReport = RhythmPostureEvaluator.formatShareReport(AppBrandFlavor.SURETHING_HOME, healthyItems)
        assertTrue(healthyReport.contains("SureThingNet Home Safety"))
        assertTrue(healthyReport.contains("✓ Everything OK"))
    }

    @Test
    fun entitlementGatePolicyIgnoresDemoSandboxAndGatesSecondRealSiteOnFreeTier() {
        val demoOnly = listOf(
            NetworkSite(id = "net-demo", name = "Demo Sandbox", devicesCount = 12, role = "owner")
        )
        val oneRealPlusDemo = listOf(
            NetworkSite(id = "net-home", name = "Seattle Home", devicesCount = 8, role = "owner"),
            NetworkSite(id = "net-demo", name = "Demo Sandbox", devicesCount = 12, role = "owner")
        )

        assertEquals(0, EntitlementGatePolicy.realNetworkCount(demoOnly))
        assertTrue(EntitlementGatePolicy.canCreateAdditionalNetwork("free", demoOnly))
        assertFalse(EntitlementGatePolicy.requiresUpgradeForNewSite("free", demoOnly))

        assertEquals(1, EntitlementGatePolicy.realNetworkCount(oneRealPlusDemo))
        assertFalse(EntitlementGatePolicy.canCreateAdditionalNetwork("free", oneRealPlusDemo))
        assertTrue(EntitlementGatePolicy.requiresUpgradeForNewSite(null, oneRealPlusDemo))

        assertTrue(EntitlementGatePolicy.canCreateAdditionalNetwork("pro_tier_1", oneRealPlusDemo))
        assertTrue(EntitlementGatePolicy.canCreateAdditionalNetwork("multisite_tier_1", oneRealPlusDemo))
        assertTrue(EntitlementGatePolicy.canCreateAdditionalNetwork("FLEET", oneRealPlusDemo))
    }

    @Test
    fun barcodeIdentifierParserNormalizesEan13ToUpcAAndPreserves2DMatrices() {
        val upcFromEan13 = BarcodeIdentifierParser.parse(" 0817882027373 ", Barcode.FORMAT_EAN_13)
        assertNotNull(upcFromEan13)
        assertEquals("817882027373", upcFromEan13!!.normalizedValue)
        assertFalse(upcFromEan13.is2DMatrix)

        val intlEan13 = BarcodeIdentifierParser.parse("4902370519969", Barcode.FORMAT_EAN_13)
        assertNotNull(intlEan13)
        assertEquals("4902370519969", intlEan13!!.normalizedValue)
        assertFalse(intlEan13.is2DMatrix)

        val qrLeadingZero = BarcodeIdentifierParser.parse("0123456789012", Barcode.FORMAT_QR_CODE)
        assertNotNull(qrLeadingZero)
        assertEquals("0123456789012", qrLeadingZero!!.normalizedValue)
        assertTrue(qrLeadingZero.is2DMatrix)

        assertNull(BarcodeIdentifierParser.parse("   ", Barcode.FORMAT_CODE_128))
    }

    @Test
    fun manualEnrollmentResolverSynthesizesGoldVerifiedAndProvisionalMetadata() {
        assertNull(
            ManualEnrollmentResolver.resolve(
                manualHostname = "   ",
                selectedClues = emptyList(),
                accumulatedIdentifiers = ExtractedDeviceIdentifiers()
            )
        )

        val modelClue = GoldClueItem(
            id = "model:udm-pro",
            kind = GoldClueKind.MODEL,
            title = "UDM-Pro",
            subtitle = "Gateway",
            vendor = "Ubiquiti",
            model = "UDM-Pro",
            upc = "810010073006"
        )
        val goldResolved = ManualEnrollmentResolver.resolve(
            manualHostname = "",
            selectedClues = listOf(modelClue),
            accumulatedIdentifiers = ExtractedDeviceIdentifiers(
                macAddress = "74:83:C2:00:11:22",
                serialNumber = "SN-UDM-99"
            )
        )
        assertNotNull(goldResolved)
        assertEquals("Ubiquiti UDM-Pro", goldResolved!!.hostname)
        assertEquals(InventoryConfidenceState.ENROLLED, goldResolved.inventoryState)
        assertTrue(goldResolved.osSummary.contains("Gold Verified"))
        assertEquals("Ubiquiti", goldResolved.metadata.manufacturer)
        assertEquals("UDM-Pro", goldResolved.metadata.modelNumber)
        assertEquals("74:83:C2:00:11:22", goldResolved.metadata.macAddress)
        assertEquals("SN-UDM-99", goldResolved.metadata.serialNumber)
        assertEquals("810010073006", goldResolved.metadata.barcodeOrQr)

        val brandClue = GoldClueItem(
            id = "brand:synology",
            kind = GoldClueKind.BRAND,
            title = "Synology",
            subtitle = "NAS Vendor",
            vendor = "Synology"
        )
        val typeClue = GoldClueItem(
            id = "type:nas",
            kind = GoldClueKind.DEVICE_TYPE,
            title = "NAS Storage",
            subtitle = "C5"
        )
        val provisionalResolved = ManualEnrollmentResolver.resolve(
            manualHostname = "Basement NAS",
            selectedClues = listOf(brandClue, typeClue),
            accumulatedIdentifiers = ExtractedDeviceIdentifiers(barcode = "084652009999")
        )
        assertNotNull(provisionalResolved)
        assertEquals("Basement NAS", provisionalResolved!!.hostname)
        assertEquals(InventoryConfidenceState.INCOMPLETE_OR_GUESSED, provisionalResolved.inventoryState)
        assertTrue(provisionalResolved.osSummary.contains("Manual Entry"))
        assertEquals("Synology", provisionalResolved.metadata.manufacturer)
        assertEquals("NAS Storage", provisionalResolved.metadata.modelNumber)
        assertEquals("084652009999", provisionalResolved.metadata.barcodeOrQr)
    }

    @Test
    fun timelineStatusEvaluatorComputesRunwayCheckInsScrubberDatesAndArrowClamping() {
        assertFalse(TimelineStatusEvaluator.isAnnualCheckInDue("2026-03-15", "2026"))
        assertTrue(TimelineStatusEvaluator.isAnnualCheckInDue("2025-11-10", "2026"))
        assertTrue(TimelineStatusEvaluator.isAnnualCheckInDue(null, "2026"))

        val currentYearAtt = makeAttestation("dev-ok", "2026-04-01")
        val healthySummary = TimelineStatusEvaluator.evaluateCandleStatus(
            candle = makeCandle(id = "dev-ok", latestAttestation = currentYearAtt),
            attestations = emptyList(),
            referenceYearPrefix = "2026"
        )
        assertEquals("ACTIVE • SUPPORTED RUNWAY", healthySummary.statusText)
        assertFalse(healthySummary.needsStatusCheck)

        val pastEolSummary = TimelineStatusEvaluator.evaluateCandleStatus(
            candle = makeCandle(id = "dev-eol", deployEndNorm = 0.85f, vendorEolNorm = 0.65f, latestAttestation = currentYearAtt),
            attestations = emptyList(),
            referenceYearPrefix = "2026"
        )
        assertEquals("ZOMBIE HARDWARE • PAST EOL", pastEolSummary.statusText)
        assertTrue(pastEolSummary.needsStatusCheck)

        val eolNearingSummary = TimelineStatusEvaluator.evaluateCandleStatus(
            candle = makeCandle(id = "dev-near", deployEndNorm = 0.72f, vendorEolNorm = 0.80f, latestAttestation = currentYearAtt),
            attestations = emptyList(),
            referenceYearPrefix = "2026"
        )
        assertEquals("EOL NEARS • RUNWAY LOW", eolNearingSummary.statusText)
        assertTrue(eolNearingSummary.needsStatusCheck)

        val defectSummary = TimelineStatusEvaluator.evaluateCandleStatus(
            candle = makeCandle(id = "dev-bad", presenceState = DevicePresenceState.STOPPED_WORKING, latestAttestation = currentYearAtt),
            attestations = emptyList(),
            referenceYearPrefix = "2026"
        )
        assertEquals("STOPPED WORKING • DEFECT", defectSummary.statusText)
        assertTrue(defectSummary.needsStatusCheck)

        assertEquals("2026-11-11 (TODAY)", TimelineStatusEvaluator.formatScrubberCursorDate(0.72f, 0.72f, "2026-11-11"))
        assertEquals("2026-11-11 (-12d)", TimelineStatusEvaluator.formatScrubberCursorDate(0.62f, 0.72f, "2026-11-11"))
        assertEquals("2026-11-11 (+12d projection)", TimelineStatusEvaluator.formatScrubberCursorDate(0.82f, 0.72f, "2026-11-11"))

        val categoryDevices = listOf(
            makeDevice("c1", "Switch OK", PortLifecycleState.OK, StnHardwareCategory.C2_SWITCH),
            makeDevice("c2", "Switch EOL", PortLifecycleState.EOL, StnHardwareCategory.C2_SWITCH),
            makeDevice("c3", "Switch CVE", PortLifecycleState.CVE_KEV, StnHardwareCategory.C2_SWITCH)
        )
        assertEquals(
            PortLifecycleState.CVE_KEV,
            TimelineStatusEvaluator.worstCategoryState(StnHardwareCategory.C2_SWITCH, categoryDevices)
        )
        assertNull(TimelineStatusEvaluator.worstCategoryState(StnHardwareCategory.C5_CAMERA, categoryDevices))

        assertEquals(0.08f, TimelineStatusEvaluator.clampPopoverArrowFraction(0f, 300f), 0.0001f)
        assertEquals(0.92f, TimelineStatusEvaluator.clampPopoverArrowFraction(9999f, 300f), 0.0001f)
        assertEquals(0.5f, TimelineStatusEvaluator.clampPopoverArrowFraction(100f, 0f), 0.0001f)
    }
}
