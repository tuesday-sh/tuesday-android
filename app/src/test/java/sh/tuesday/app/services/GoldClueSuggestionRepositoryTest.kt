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
}
