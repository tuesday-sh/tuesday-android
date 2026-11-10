package sh.tuesday.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.navigation.DeepLinkDestination

class DeepLinkDestinationTest {

    @Test
    fun parsesAllFiveMainTabsAndAliases() {
        assertEquals(DeepLinkDestination.Tab("status"), DeepLinkDestination.fromUri("tuesday://status"))
        assertEquals(DeepLinkDestination.Tab("status"), DeepLinkDestination.fromUri("tuesday://fleet"))
        assertEquals(DeepLinkDestination.Tab("bulletins"), DeepLinkDestination.fromUri("tuesday://bulletins"))
        assertEquals(DeepLinkDestination.Tab("timeline"), DeepLinkDestination.fromUri("tuesday://timeline"))
        assertEquals(DeepLinkDestination.Tab("checkup"), DeepLinkDestination.fromUri("tuesday://checkup"))
        assertEquals(DeepLinkDestination.Tab("account"), DeepLinkDestination.fromUri("tuesday://account"))
        assertEquals(DeepLinkDestination.Tab("account-dev"), DeepLinkDestination.fromUri("tuesday://account/dev"))
    }

    @Test
    fun parsesDeterministicFlavorAndSeedTiers() {
        assertEquals(
            DeepLinkDestination.SetFlavor(AppBrandFlavor.TUESDAY_PRO),
            DeepLinkDestination.fromUri("tuesday://flavor/pro")
        )
        assertEquals(
            DeepLinkDestination.SetFlavor(AppBrandFlavor.SURETHING_HOME),
            DeepLinkDestination.fromUri("tuesday://flavor/home")
        )
        assertEquals(DeepLinkDestination.ToggleFlavor, DeepLinkDestination.fromUri("tuesday://toggle-flavor"))
        assertEquals(DeepLinkDestination.ToggleFlavor, DeepLinkDestination.fromUri("tuesday://flavor"))

        assertEquals(DeepLinkDestination.SeedFleet(0), DeepLinkDestination.fromUri("tuesday://seed/0"))
        assertEquals(DeepLinkDestination.SeedFleet(1), DeepLinkDestination.fromUri("tuesday://seed/1"))
        assertEquals(DeepLinkDestination.SeedFleet(7), DeepLinkDestination.fromUri("tuesday://seed/7"))
        assertEquals(DeepLinkDestination.SeedFleet(12), DeepLinkDestination.fromUri("tuesday://seed/12"))
        assertEquals(DeepLinkDestination.ResetDemo, DeepLinkDestination.fromUri("tuesday://reset-demo"))
    }

    @Test
    fun parsesModalsWizardSynthAndApiEndpoints() {
        assertEquals(DeepLinkDestination.HacsSettings, DeepLinkDestination.fromUri("tuesday://hacs"))
        assertEquals(DeepLinkDestination.NetworkPicker, DeepLinkDestination.fromUri("tuesday://network-picker"))
        assertEquals(DeepLinkDestination.ReferenceGuide, DeepLinkDestination.fromUri("tuesday://reference-guide"))
        assertEquals(DeepLinkDestination.Device("dev-01"), DeepLinkDestination.fromUri("tuesday://device/dev-01"))

        val barcodeSynth = DeepLinkDestination.fromUri("tuesday://synth/barcode") as DeepLinkDestination.AddDevice
        assertEquals("scan", barcodeSynth.mode)
        assertEquals("barcode-udm-pro", barcodeSynth.synthPreset)

        val wizardClues = DeepLinkDestination.fromUri("tuesday://add-device?mode=manual&q=Ubiquiti&clues=UDM-Pro%2CC3") as DeepLinkDestination.AddDevice
        assertEquals("manual", wizardClues.mode)
        assertEquals("Ubiquiti", wizardClues.clueQuery)
        assertEquals("UDM-Pro,C3", wizardClues.cluesCsv)

        assertEquals(
            DeepLinkDestination.SetApiEndpoint("http://127.0.0.1:3000"),
            DeepLinkDestination.fromUri("tuesday://api-endpoint/local")
        )
        assertEquals(
            DeepLinkDestination.SetApiEndpoint("https://api.tuesday.sh"),
            DeepLinkDestination.fromUri("tuesday://api-endpoint/prod")
        )
        assertEquals(
            DeepLinkDestination.SetApiEndpoint("http://100.108.40.3:3000"),
            DeepLinkDestination.fromUri("tuesday://api-endpoint/dev")
        )
    }

    @Test
    fun rejectsMalformedOrForeignSchemes() {
        assertNull(DeepLinkDestination.fromUri(null))
        assertNull(DeepLinkDestination.fromUri(""))
        assertNull(DeepLinkDestination.fromUri("https://tuesday.sh/status"))
        assertNull(DeepLinkDestination.fromUri("tuesday://"))
        assertNull(DeepLinkDestination.fromUri("tuesday://unknown-route"))
        assertNull(DeepLinkDestination.fromUri("tuesday://device/"))
        assertTrue(DeepLinkDestination.fromUri("tuesday://vision-debug/off") is DeepLinkDestination.VisionDebug)
    }
}
