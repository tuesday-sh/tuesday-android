package sh.tuesday.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class GoldClueSuggestionRepositoryTest {
    companion object {
        private lateinit var index: GoldClueIndexData

        @BeforeClass
        @JvmStatic
        fun setup() {
            val gzFile = File("src/main/assets/gold_clue_index.gci").let {
                if (it.exists()) it else File("app/src/main/assets/gold_clue_index.gci")
            }
            assertTrue("Expected compiled gold_clue_index.gci at ${gzFile.absolutePath}", gzFile.exists())
            index = gzFile.inputStream().use { GoldClueIndexData.parseGzipStream(it) }
        }
    }

    @Test
    fun `matches natural device types and brands like router, switch, doorbell, linksys, google`() {
        val routerHits = index.querySuggestions("router")
        assertTrue(routerHits.any { it.kind == GoldClueKind.DEVICE_TYPE && it.typeId == "router" && it.categoryCode == "C3" })

        val switchHits = index.querySuggestions("switch")
        assertTrue(switchHits.any { it.kind == GoldClueKind.DEVICE_TYPE && it.typeId == "switch" && it.categoryCode == "C4" })

        val doorbellHits = index.querySuggestions("doorbell")
        assertTrue(doorbellHits.any { it.kind == GoldClueKind.DEVICE_TYPE && it.typeId == "doorbell" })
        assertTrue(doorbellHits.any { it.kind == GoldClueKind.MODEL && it.title.contains("Doorbell", ignoreCase = true) })

        val linksysHits = index.querySuggestions("linksys")
        assertEquals(GoldClueKind.BRAND, linksysHits.first().kind)
        assertEquals("Linksys", linksysHits.first().vendor)

        val googleHits = index.querySuggestions("google")
        assertEquals(GoldClueKind.BRAND, googleHits.first().kind)
        assertEquals("Google", googleHits.first().vendor)
    }

    @Test
    fun `excludes enterprise ERP noise from ring and consumer queries`() {
        val ringHits = index.querySuggestions("ring")
        assertTrue(ringHits.any { it.vendor == "Amazon Ring" })
        assertFalse(ringHits.any { it.title.contains("banking", ignoreCase = true) || it.title.contains("turing", ignoreCase = true) })
    }

    @Test
    fun `contextual quick picks narrow progressively from Type to Brand to Model`() {
        val initialPicks = index.contextualQuickPicks(emptyList())
        assertTrue(initialPicks.all { it.kind == GoldClueKind.DEVICE_TYPE })

        val doorbellClue = index.querySuggestions("doorbell").first { it.kind == GoldClueKind.DEVICE_TYPE }
        val brandPicksForDoorbell = index.contextualQuickPicks(listOf(doorbellClue))
        assertTrue(brandPicksForDoorbell.any { it.vendor == "Google" })
        assertTrue(brandPicksForDoorbell.any { it.vendor == "Amazon Ring" })

        val googleBrandClue = brandPicksForDoorbell.first { it.vendor == "Google" }
        val modelPicksForGoogleDoorbell = index.contextualQuickPicks(listOf(doorbellClue, googleBrandClue))
        assertTrue(modelPicksForGoogleDoorbell.isNotEmpty())
        assertTrue(modelPicksForGoogleDoorbell.all { it.vendor == "Google" && it.title.contains("Doorbell", ignoreCase = true) })
    }

    @Test
    fun `resolves OUI MAC prefixes, UPC barcodes, and embedded FCC radio module warnings`() {
        val upcHits = index.querySuggestions("817882027373")
        assertEquals("UDM-Pro", upcHits.first().model)

        val macHits = index.querySuggestions("74:83:C2:9E:11")
        assertEquals("Ubiquiti", macHits.first().vendor)

        val radioHits = index.querySuggestions("2AC7Z-ESP32")
        val fccClue = radioHits.firstOrNull { it.kind == GoldClueKind.IDENTIFIER }
        assertNotNull(fccClue)
        assertNotNull(fccClue?.warningNote)
    }

    @Test
    fun `resolves local discovery result in 0 turns for exact SKU and narrowed Brand+Type, and returns null for unknown custom SKU`() {
        val doorbellType = index.querySuggestions("doorbell").first { it.kind == GoldClueKind.DEVICE_TYPE }
        val googleBrand = index.querySuggestions("google").first { it.kind == GoldClueKind.BRAND }
        val nestBattery = index.contextualQuickPicks(listOf(doorbellType, googleBrand)).first {
            it.title.contains("Battery", ignoreCase = true)
        }

        // Exact SKU selected -> 1 candidate at 99% confidence, GOLD ALIGNED, 0 live API calls
        val exactRes = index.resolveLocalDiscoveryResult(
            selected = listOf(doorbellType, googleBrand, nestBattery),
            accumulatedIdentifiers = sh.tuesday.app.models.ExtractedDeviceIdentifiers(),
            manualDeviceName = "Front Porch Doorbell"
        )
        assertNotNull(exactRes)
        assertEquals(false, exactRes!!.usedLiveGeminiApi)
        assertEquals(1, exactRes.candidates.size)
        assertEquals("Google", exactRes.candidates.first().manufacturer)
        assertEquals("Nest Doorbell (Battery)", exactRes.candidates.first().modelNumber)
        assertEquals(99, exactRes.candidates.first().confidencePercent)
        assertEquals("Front Porch Doorbell", exactRes.candidates.first().suggestedHostname)
        assertEquals(true, exactRes.candidates.first().goldAlignment?.aligned)

        // Partial narrowing (Doorbell + Google without model) -> returns both Google Nest Doorbell models locally
        val narrowedRes = index.resolveLocalDiscoveryResult(
            selected = listOf(doorbellType, googleBrand),
            accumulatedIdentifiers = sh.tuesday.app.models.ExtractedDeviceIdentifiers()
        )
        assertNotNull(narrowedRes)
        assertTrue(narrowedRes!!.candidates.size >= 2)
        assertTrue(narrowedRes.candidates.all { it.manufacturer == "Google" })

        // Unknown custom model not in pinned cache -> returns null so caller escalates to server
        val unknownCustom = index.classifyFreeTextClue("UnknownFutureModel-9999X")
        val missRes = index.resolveLocalDiscoveryResult(
            selected = listOf(unknownCustom),
            accumulatedIdentifiers = sh.tuesday.app.models.ExtractedDeviceIdentifiers()
        )
        assertEquals(null, missRes)
    }

    @Test
    fun `matches printer device type, narrows HP to printers without thin clients, and resolves Brother MAC OUI and FCC ID`() {
        val printerHits = index.querySuggestions("printer")
        val printerType = printerHits.firstOrNull { it.kind == GoldClueKind.DEVICE_TYPE && it.typeId == "printer" }
        assertNotNull("Expected Printer / Scanner device type clue", printerType)
        assertEquals("C8", printerType!!.categoryCode)
        assertTrue(printerHits.any { it.kind == GoldClueKind.MODEL && it.typeId == "printer" })

        val brandPicksForPrinter = index.contextualQuickPicks(listOf(printerType))
        assertTrue(brandPicksForPrinter.any { it.vendor == "Brother" })
        assertTrue(brandPicksForPrinter.any { it.vendor == "HP" })

        val hpBrand = brandPicksForPrinter.first { it.vendor == "HP" }
        val hpPrinterPicks = index.contextualQuickPicks(listOf(printerType, hpBrand))
        assertTrue(hpPrinterPicks.isNotEmpty())
        assertTrue(hpPrinterPicks.all { it.typeId == "printer" && it.vendor == "HP" })
        assertFalse(
            hpPrinterPicks.any {
                it.title.contains("t520", ignoreCase = true) ||
                    it.title.contains("t620", ignoreCase = true) ||
                    it.title.contains("elitedesk", ignoreCase = true)
            }
        )

        val macHits = index.querySuggestions("00:80:77:11:22:33")
        assertEquals("Brother", macHits.first().vendor)

        val fccHits = index.querySuggestions("B3Q-HLL2350DW")
        val fccIdentifier = fccHits.firstOrNull { it.kind == GoldClueKind.IDENTIFIER }
        assertNotNull("Expected FCC ID identifier hit for Brother B3Q grantee", fccIdentifier)
        assertEquals("Brother", fccIdentifier!!.vendor)
        assertEquals("C8", fccIdentifier.categoryCode)
    }

    @Test
    fun reconcilesUnalignedServerSharpMfpAndStripsSingleCandidateCenterPrefix() {
        val unalignedTurn = sh.tuesday.app.models.GeminiDiscoveryTurnResult(
            visualSummary = "Sharp MX-M453N multifunction printer",
            extractedIdentifiers = sh.tuesday.app.models.ExtractedDeviceIdentifiers(modelNumber = "MX-M453N"),
            candidates = listOf(
                sh.tuesday.app.models.DeviceCandidateMatch(
                    manufacturer = "Sharp",
                    modelNumber = "MX-M453N",
                    displayName = "[Center] Sharp MX-M453N Multifunction Printer",
                    deviceCategory = "Multifunction Printer",
                    confidencePercent = 95,
                    reasoning = "Vision OCR read Sharp MX-M453N",
                    suggestedHostname = "sharp-mx-m453n",
                    suggestedOsDetails = "Sharp OSA",
                    suggestedPackageManager = "firmware",
                    goldAlignment = sh.tuesday.app.models.GoldAlignmentInfo(
                        aligned = false,
                        inferredPurl = "pkg:generic/sharp/mx-m453n",
                        statusLabel = "UNALIGNED RAW MATCH"
                    )
                )
            )
        )

        val reconciled = index.reconcileDiscoveryResult(unalignedTurn)
        val top = reconciled.candidates.first()
        assertEquals("Sharp MX-M453N Multifunction Printer", top.displayName)
        assertTrue(top.goldAlignment!!.aligned)
        assertEquals("GOLD ALIGNED", top.goldAlignment!!.statusLabel)
    }

    @Test
    fun reconcileClearsFalseOutOfScopeAndRepairsHallucinatedDescriptionWhenGoldMatched() {
        val taintedTurn = sh.tuesday.app.models.GeminiDiscoveryTurnResult(
            visualSummary = "A small square brushed-silver Sharp MX-M453N module.",
            isNetworkedDevice = false,
            outOfScopeReason = "The Sharp MX-M453N is an infrared remote-control receiver/transmitter module with no Wi-Fi or Ethernet networking capability.",
            extractedIdentifiers = sh.tuesday.app.models.ExtractedDeviceIdentifiers(),
            candidates = listOf(
                sh.tuesday.app.models.DeviceCandidateMatch(
                    manufacturer = "Sharp",
                    modelNumber = "MX-M453N",
                    displayName = "[Center] Sharp MX-M453N IR remote module",
                    deviceCategory = "IR Remote Module",
                    confidencePercent = 92,
                    reasoning = "Sharp MX-M453N appears to be an infrared remote module.",
                    suggestedHostname = "sharp-mx-m453n",
                    suggestedOsDetails = "Unknown",
                    suggestedPackageManager = "none",
                    goldAlignment = sh.tuesday.app.models.GoldAlignmentInfo(
                        aligned = false,
                        inferredPurl = "pkg:generic/sharp/mx-m453n",
                        statusLabel = "UNALIGNED RAW MATCH"
                    )
                )
            )
        )

        val reconciled = index.reconcileDiscoveryResult(taintedTurn)
        assertTrue(reconciled.isNetworkedDevice)
        assertEquals(null, reconciled.outOfScopeReason)
        val top = reconciled.candidates.first()
        assertTrue(top.goldAlignment!!.aligned)
        assertEquals("GOLD ALIGNED", top.goldAlignment!!.statusLabel)
        assertFalse(top.displayName.contains("IR remote", ignoreCase = true))
        assertTrue(top.displayName.contains("Sharp MX-M453N", ignoreCase = true))
        assertFalse(top.reasoning.contains("infrared", ignoreCase = true))
        assertFalse(reconciled.visualSummary.contains("small square", ignoreCase = true))
    }

    @Test
    fun expandedNetworkedPrintersResolveInClueIndexAndUsbOnlyPrintersStayUnaligned() {
        val gciFile = listOf(
            java.io.File("src/main/assets/gold_clue_index.gci"),
            java.io.File("app/src/main/assets/gold_clue_index.gci")
        ).first { it.exists() }
        val index = gciFile.inputStream().use { GoldClueIndexData.parseGzipStream(it) }

        val expectedBrands = listOf(
            "Xerox", "Ricoh", "Konica Minolta", "Kyocera",
            "Zebra", "Rollo", "Prusa", "Creality"
        )
        expectedBrands.forEach { brand ->
            assertTrue(
                "Expected brand $brand in Gold Clue Index",
                index.brands.any { it.name.equals(brand, ignoreCase = true) }
            )
            val hits = index.querySuggestions(rawQuery = brand, selected = emptyList())
            assertTrue(
                "Expected querySuggestions hit for brand $brand",
                hits.any { it.kind == GoldClueKind.BRAND && it.title.equals(brand, ignoreCase = true) }
            )
        }

        val expectedModels = listOf(
            "Xerox" to "VersaLink C405dn",
            "Ricoh" to "IM C3000",
            "Konica Minolta" to "bizhub C360i",
            "Kyocera" to "ECOSYS M2540dw",
            "Zebra" to "ZD421"
        )
        expectedModels.forEach { (vendor, model) ->
            val normTarget = GoldClueIndexData.normalizeAlnum(model)
            val entry = index.models.firstOrNull {
                it.vendor.equals(vendor, ignoreCase = true) && it.normModel == normTarget
            }
            assertNotNull("Expected $vendor $model in Gold Clue Index", entry)
            assertEquals("Expected $vendor $model typeId=printer", "printer", entry!!.typeId)
        }

        assertEquals("Xerox", index.ouiMap["0000AA"])
        assertEquals("Zebra", index.ouiMap["00074D"])
        assertEquals("Xerox", index.querySuggestions(rawQuery = "00:00:AA:11:22:33", selected = emptyList()).first().vendor)
        assertEquals("Zebra", index.querySuggestions(rawQuery = "00:07:4D:11:22:33", selected = emptyList()).first().vendor)
        assertEquals("Xerox", index.fccGrantees["DO5"]?.vendor)
        assertEquals("Zebra", index.fccGrantees["UZ7"]?.vendor)

        val fccXeroxHits = index.querySuggestions(rawQuery = "DO5-VL405", selected = emptyList())
        assertTrue(
            "Expected FCC grantee suggestion for DO5-VL405",
            fccXeroxHits.any { it.vendor.equals("Xerox", ignoreCase = true) || it.title.contains("DO5", ignoreCase = true) }
        )
        val fccZebraHits = index.querySuggestions(rawQuery = "UZ7-ZD421", selected = emptyList())
        assertTrue(
            "Expected FCC grantee suggestion for UZ7-ZD421",
            fccZebraHits.any { it.vendor.equals("Zebra", ignoreCase = true) || it.title.contains("UZ7", ignoreCase = true) }
        )

        val usbCases = listOf(
            Triple("Brother", "HL-L2400D", "Brother HL-L2400D is a USB-only mono laser printer."),
            Triple("HP", "LaserJet Pro M404d", "HP LaserJet Pro M404d is a USB-only duplex laser printer.")
        )
        usbCases.forEach { (vendor, model, reason) ->
            val usbTurn = sh.tuesday.app.models.GeminiDiscoveryTurnResult(
                visualSummary = "$vendor $model desktop laser printer with USB-B port.",
                extractedIdentifiers = sh.tuesday.app.models.ExtractedDeviceIdentifiers(modelNumber = model),
                candidates = listOf(
                    sh.tuesday.app.models.DeviceCandidateMatch(
                        manufacturer = vendor,
                        modelNumber = model,
                        displayName = "$vendor $model",
                        deviceCategory = "Printer",
                        confidencePercent = 94,
                        reasoning = reason,
                        suggestedHostname = "usb-printer",
                        suggestedOsDetails = "Embedded Printer",
                        suggestedPackageManager = "firmware",
                        goldAlignment = sh.tuesday.app.models.GoldAlignmentInfo(
                            aligned = false,
                            statusLabel = "UNALIGNED RAW MATCH"
                        )
                    )
                ),
                isNetworkedDevice = false,
                outOfScopeReason = reason
            )

            val reconciled = index.reconcileDiscoveryResult(usbTurn)
            assertFalse("USB-only $vendor $model must keep isNetworkedDevice=false", reconciled.isNetworkedDevice)
            assertEquals(reason, reconciled.outOfScopeReason)
            assertFalse(reconciled.candidates.first().goldAlignment!!.aligned)
            assertEquals("UNALIGNED RAW MATCH", reconciled.candidates.first().goldAlignment!!.statusLabel)
        }
    }
}
