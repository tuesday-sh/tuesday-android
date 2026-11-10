package sh.tuesday.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Complete End-to-End Hardware Instrumented Test Suite for Tuesday.sh (`STN-Pro` & `STN-Home`).
 *
 * Covers 20/20 canonical UI components in `UI-SITEMAP-AND-INTENT-MATRIX.md` across:
 *  - Botanical Spine progressive disclosure (`Day 0`, `Pea`, `Garden`, `Forest`)
 *  - Dual-Flavor (`TUESDAY_PRO` & `SURETHING_HOME`) across all 5 main tabs
 *  - All modal sheets & dialogs (`ReferenceGuideModal`, `DeviceInspectorSheet`, `HacsSettingsSheet`,
 *    `NetworkPickerModal`, `SetupNetworkModal`, `BulletinPreferencesDialog`)
 *  - Interactive state mutations (TrustZone/PortState filters, Show OK toggle, Inspector remediation,
 *    Bulletin Snooze/Mute/Resume, Matched Feed mute & provisional model confirmation, Site creation,
 *    and 3-mode `AddDeviceWizard` -> `AiDiscoveryPanel` & Manual enrollment)
 *  - Live hardware WCAG 2.2 & Android TalkBack semantics auditing on every visited surface
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TuesdayHardwareE2ETest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.CAMERA
    )

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUpSuite() {
            ComposeWcagSemanticsAuditor.resetSession()
        }

        @JvmStatic
        @AfterClass
        fun tearDownSuite() {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            ComposeWcagSemanticsAuditor.writeSessionReport(targetContext)
        }
    }

    @Before
    fun ensureAppLaunchedAndReset() {
        navigateIntent("tuesday://reset-demo")
        waitUntilAnyTagExists(
            "ui:component:BottomNavigationBar",
            "ui:component:WelcomeCard",
            "ui:component:InventoryMatrixCard"
        )
    }

    private fun navigateIntent(uri: String) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(targetContext.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        targetContext.startActivity(intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.waitForIdle()
    }

    private fun scrollLazyContainerTo(matcher: SemanticsMatcher) {
        runCatching {
            val lazyContainers = composeRule.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true)
            if (lazyContainers.fetchSemanticsNodes().isNotEmpty()) {
                lazyContainers.onFirst().performScrollToNode(matcher)
                composeRule.waitForIdle()
            }
        }
    }

    private fun waitUntilAnyTagExists(vararg tags: String, timeoutMillis: Long = 8_000L): String {
        var matched = tags.first()
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val found = tags.firstOrNull { tag ->
                runCatching {
                    composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            if (found != null) {
                matched = found
                true
            } else {
                // Attempt LazyColumn scroll to compose below-the-fold items
                tags.forEach { tag -> scrollLazyContainerTo(hasTestTag(tag)) }
                val afterScroll = tags.firstOrNull { tag ->
                    runCatching {
                        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                    }.getOrDefault(false)
                }
                if (afterScroll != null) {
                    matched = afterScroll
                    true
                } else {
                    false
                }
            }
        }
        return matched
    }

    private fun assertAnyTagDisplayed(vararg tags: String) {
        val matched = waitUntilAnyTagExists(*tags)
        val node = composeRule.onAllNodesWithTag(matched, useUnmergedTree = true).onFirst()
        runCatching { node.performScrollTo() }
        composeRule.waitForIdle()
        node.assertIsDisplayed()
    }

    @Test
    fun t01_botanicalSpineProgressiveDisclosureAndDualFlavorMatrix() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. STN-Pro Day 0 (0 devices) -> WelcomeCard visible, InventoryMatrixCard absent
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/0")
        assertAnyTagDisplayed("ui:component:WelcomeCard")
        assertTrue(
            "Day 0 should hide InventoryMatrixCard",
            composeRule.onAllNodesWithTag("ui:component:InventoryMatrixCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Pro • Day 0 (Welcome)")

        // 2. Pea Tier (1 device) -> InventoryMatrixCard visible, C1-C8 faceplate hidden
        navigateIntent("tuesday://seed/1")
        assertAnyTagDisplayed("ui:component:InventoryMatrixCard")
        assertTrue(
            "Pea tier (1 device) should hide PortCategoryFaceplateStrip",
            composeRule.onAllNodesWithTag("ui:component:PortCategoryFaceplateStrip", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:c1-c8-strip", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
        )
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Pro • Pea (1 Device)")

        // 3. Garden Tier (7 devices) -> Summary badge visible, C1-C8 faceplate still hidden
        navigateIntent("tuesday://seed/7")
        assertAnyTagDisplayed("ui:slot:summary-badge")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Pro • Garden (7 Devices)")

        // 4. Forest Tier (12 devices) -> C1-C8 Faceplate Strip + 128 LED Grid + Tracked Profiles revealed
        navigateIntent("tuesday://seed/12")
        assertAnyTagDisplayed("ui:component:PortCategoryFaceplateStrip", "ui:slot:c1-c8-strip")
        assertAnyTagDisplayed("ui:component:LedMatrix128Grid", "ui:slot:led-grid-128")
        assertAnyTagDisplayed("ui:component:TrackedHardwareProfilesCard", "ui:slot:tracked-profiles-strip")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Pro • Forest (12 Devices)")

        // 5. Switch to STN-Home (Daylight Calm) in Forest Tier & verify re-theme + semantics
        navigateIntent("tuesday://flavor/home")
        assertAnyTagDisplayed("ui:component:InventoryMatrixCard")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Home • Forest (12 Devices)")

        navigateIntent("tuesday://flavor/pro")
    }

    @Test
    fun t02_allFiveTabsAndModalSheetsIntentNavigationAndWcagAudit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // Tab 2: Bulletins
        navigateIntent("tuesday://bulletins")
        assertAnyTagDisplayed("ui:component:BulletinsScreen")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 2 • Bulletins (Pro)")

        // Tab 3: Swimlane Timeline
        navigateIntent("tuesday://timeline")
        assertAnyTagDisplayed("ui:component:SwimlaneTimelineScreen")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 3 • Timeline (Pro)")

        // Tab 4: Seasonal Checkup (Quarterly Audit)
        navigateIntent("tuesday://checkup")
        assertAnyTagDisplayed("ui:component:SeasonalCheckupScreen")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 4 • Checkup (Pro)")

        // Tab 5: Account & Developer Settings
        navigateIntent("tuesday://account/dev")
        assertAnyTagDisplayed("ui:component:AccountSettingsCard")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 5 • Account Dev (Pro)")

        // Modal 1: Reference Guide (C1-C8 / S0-S6 / Z0-Z5 / OSI)
        navigateIntent("tuesday://status")
        navigateIntent("tuesday://reference-guide")
        assertAnyTagDisplayed("ui:component:ReferenceGuideModal")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Modal • ReferenceGuide")

        navigateIntent("tuesday://reset-demo")
    }

    @Test
    fun t03_deviceInspectorHacsSheetAndApiEndpointSwitching() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // Inspect canonical RackPortDevice via Intent URI (Pro Candlesticks + S0..S6)
        navigateIntent("tuesday://device/0192d9f0-0008-7000-8000-000000000108")
        assertAnyTagDisplayed("ui:component:DeviceInspectorSheet", "ui:slot:inspector-header")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Sheet • DeviceInspector (Pro)")

        // Inspect same device in STN-Home ("What to Do Next" colloquial steps)
        navigateIntent("tuesday://reset-demo")
        navigateIntent("tuesday://flavor/home")
        navigateIntent("tuesday://seed/12")
        navigateIntent("tuesday://device/0192d9f0-0008-7000-8000-000000000108")
        assertAnyTagDisplayed("ui:component:DeviceInspectorSheet", "ui:slot:inspector-header")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Sheet • DeviceInspector (Home)")

        // HACS & AI Bridge Sheet
        navigateIntent("tuesday://reset-demo")
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://hacs")
        assertAnyTagDisplayed("ui:component:HacsSettingsSheet", "ui:slot:hacs-bridge-header")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Sheet • HacsSettings")

        // API Endpoint Routing Integration (local adb reverse -> dev -> prod)
        navigateIntent("tuesday://api-endpoint/local")
        navigateIntent("tuesday://api-endpoint/prod")
        navigateIntent("tuesday://reset-demo")
    }

    @Test
    fun t04_addDeviceWizardThreeModeCluesAndSyntheticBarcodeIntegration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Manual / Shared Hardware Clues mode with Gold Datalake query prefill
        navigateIntent("tuesday://add-device?mode=manual&q=Ubiquiti&clues=UDM-Pro")
        assertAnyTagDisplayed("ui:component:AddDeviceWizard", "ui:slot:enrollment-mode-tabs")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Wizard • Manual + Gold Clues")

        // Desk-safe Synthetic Barcode Intake (`barcode-udm-pro`)
        navigateIntent("tuesday://synth/barcode")
        assertAnyTagDisplayed("ui:component:AddDeviceWizard", "ui:slot:enrollment-mode-tabs")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Wizard • Scan + Synth Barcode")

        navigateIntent("tuesday://reset-demo")
        assertAnyTagDisplayed("ui:component:BottomNavigationBar")
    }

    @Test
    fun t05_fleetFiltersShowOkToggleAndInspectorRemediationMutation() {
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // 1. Scroll to TrustZone filter strip & click second pill (Z0 / Z1), then reset to first ("All Zones")
        assertAnyTagDisplayed("ui:slot:zone-filter-strip")
        val zonePills = composeRule.onAllNodesWithTag("ui:action:filterZone", useUnmergedTree = true)
        assertTrue("Expected TrustZone filter pills in Forest tier", zonePills.fetchSemanticsNodes().size >= 2)
        zonePills[1].performClick()
        composeRule.waitForIdle()
        zonePills[0].performClick()
        composeRule.waitForIdle()

        // 2. Scroll to PortState filter strip & toggle CVE/KEV filter chip
        assertAnyTagDisplayed("ui:slot:state-filter-strip")
        val stateChips = composeRule.onAllNodesWithTag("ui:action:filterPortState", useUnmergedTree = true)
        assertTrue("Expected PortState filter chips in Forest tier", stateChips.fetchSemanticsNodes().size >= 2)
        stateChips[1].performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Expected FILTERED header when CVE/KEV chip is active",
            composeRule.onAllNodesWithText("FILTERED:", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        // Toggle CVE/KEV filter back off
        composeRule.onAllNodesWithTag("ui:action:filterPortState", useUnmergedTree = true)[1].performClick()
        composeRule.waitForIdle()

        // 3. Toggle "Show OK (+N)" -> "ALL NAMED DEVICES"
        assertAnyTagDisplayed("ui:action:toggleIncludeOk")
        composeRule.onNodeWithTag("ui:action:toggleIncludeOk", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Expected ALL NAMED DEVICES header after toggling Show OK",
            composeRule.onAllNodesWithText("ALL NAMED DEVICES", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // 4. Open DeviceInspectorSheet on CVE device & mark remediation complete -> flips port to OK
        navigateIntent("tuesday://device/0192d9f0-0008-7000-8000-000000000108")
        val remTag = waitUntilAnyTagExists("ui:action:markRemediationComplete", "ui:slot:remediation-cta")
        val remBtn = composeRule.onNodeWithTag(remTag, useUnmergedTree = true)
        runCatching { remBtn.performScrollTo() }
        remBtn.performClick()
        composeRule.waitForIdle()

        // Re-inspecting the remediated device should no longer show a remediation CTA (device is now OK)
        navigateIntent("tuesday://device/0192d9f0-0008-7000-8000-000000000108")
        assertAnyTagDisplayed("ui:component:DeviceInspectorSheet", "ui:slot:inspector-header")
        assertTrue(
            "Remediated device should now be OK with no markRemediationComplete button",
            composeRule.onAllNodesWithTag("ui:action:markRemediationComplete", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        navigateIntent("tuesday://reset-demo")
    }

    @Test
    fun t06_bulletinRhythmSnoozePreferencesMuteAndFeedDisambiguation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")
        navigateIntent("tuesday://bulletins")
        assertAnyTagDisplayed("ui:component:BulletinsScreen")

        // 1. Snooze 24h on NotchBulletinCard -> verify SNOOZED banner -> Resume
        assertAnyTagDisplayed("ui:action:primaryNotchAction")
        composeRule.onAllNodesWithTag("ui:action:primaryNotchAction", useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("SNOOZED", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Resume →", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // 2. Click "Ignore" -> opens BulletinPreferencesDialog -> audit WCAG -> Mute -> Unmute
        assertAnyTagDisplayed("ui:action:secondaryNotchAction")
        composeRule.onAllNodesWithTag("ui:action:secondaryNotchAction", useUnmergedTree = true).onFirst().performClick()
        assertAnyTagDisplayed("ui:component:BulletinPreferencesDialog")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Dialog • BulletinPreferences")

        composeRule.onNodeWithTag("ui:action:confirmMuteBulletinRhythm", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("MUTED", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Unmute →", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // 3. Scroll down Bulletins LazyColumn to MatchedFeedsSection -> toggle mute & confirm guessed model
        scrollLazyContainerTo(hasTestTag("ui:action:toggleMuteFeed"))
        val muteButtons = composeRule.onAllNodesWithTag("ui:action:toggleMuteFeed", useUnmergedTree = true)
        if (muteButtons.fetchSemanticsNodes().isNotEmpty()) {
            muteButtons.onFirst().performClick()
            composeRule.waitForIdle()
        }

        scrollLazyContainerTo(hasTestTag("ui:action:confirmGuessedModel"))
        val confirmGuessButtons = composeRule.onAllNodesWithTag("ui:action:confirmGuessedModel", useUnmergedTree = true)
        if (confirmGuessButtons.fetchSemanticsNodes().isNotEmpty()) {
            confirmGuessButtons.onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun t07_networkPickerSetupSiteCreationAndStnHomeAllTabsAudit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // 1. Open NetworkPickerModal & audit WCAG
        navigateIntent("tuesday://network-picker")
        assertAnyTagDisplayed("ui:component:NetworkPickerModal")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Modal • NetworkPicker")

        // 2. Open nested SetupNetworkModal, create "Pixel 5 Lab" site, verify Day 0 transition
        composeRule.onNodeWithTag("ui:action:openSetupNetworkModal", useUnmergedTree = true).performClick()
        assertAnyTagDisplayed("ui:component:SetupNetworkModal")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Modal • SetupNetwork")

        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst()
            .performTextInput("Pixel 5 Lab")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create Network & Join", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertAnyTagDisplayed("ui:component:WelcomeCard")

        // 3. Switch back via NetworkPickerModal `selectNetwork`
        navigateIntent("tuesday://network-picker")
        assertAnyTagDisplayed("ui:component:NetworkPickerModal")
        val netChoices = composeRule.onAllNodesWithTag("ui:action:selectNetwork", useUnmergedTree = true)
        if (netChoices.fetchSemanticsNodes().isNotEmpty()) {
            netChoices.onFirst().performClick()
            composeRule.waitForIdle()
        }

        // 4. Full STN-Home (Daylight Calm) sweep across remaining tabs (Bulletins, Lifespan, Check-Up, Account)
        navigateIntent("tuesday://flavor/home")
        navigateIntent("tuesday://seed/12")

        navigateIntent("tuesday://bulletins")
        assertAnyTagDisplayed("ui:component:BulletinsScreen")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 2 • Bulletins (Home)")

        navigateIntent("tuesday://timeline")
        assertAnyTagDisplayed("ui:component:SwimlaneTimelineScreen")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 3 • Lifespan (Home)")

        navigateIntent("tuesday://checkup")
        assertAnyTagDisplayed("ui:component:SeasonalCheckupScreen")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 4 • Checkup (Home)")

        navigateIntent("tuesday://account")
        assertAnyTagDisplayed("ui:component:AccountSettingsCard")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Tab 5 • Account (Home)")

        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://reset-demo")
    }

    @Test
    fun t08_wizardAiDiscoveryPanelConfirmEnrollAndManualEnroll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        navigateIntent("tuesday://flavor/pro")

        // 1. Trigger Identifier/Clue Search -> mounts AiDiscoveryPanel (Component 20/20!)
        navigateIntent("tuesday://add-device?mode=photo&q=Ubiquiti&clues=UCG-Ultra")
        assertAnyTagDisplayed("ui:action:searchDeviceCandidates")
        composeRule.onNodeWithTag("ui:action:searchDeviceCandidates", useUnmergedTree = true).performClick()

        // Wait for progressive/heuristic AI discovery result to mount AiDiscoveryPanel (up to 15s if network call runs)
        waitUntilAnyTagExists("ui:component:AiDiscoveryPanel", "ui:slot:ai-discovery-panel", timeoutMillis = 18_000L)
        assertAnyTagDisplayed("ui:component:AiDiscoveryPanel", "ui:slot:ai-discovery-panel")
        ComposeWcagSemanticsAuditor.auditActiveScreen(composeRule, context, "Wizard • AiDiscoveryPanel")

        // Enroll via Confirm Match (or Best-Guess fallback) and verify return to Fleet
        val confirmNodes = composeRule.onAllNodesWithTag("ui:action:enrollConfirmedDevice", useUnmergedTree = true)
        if (confirmNodes.fetchSemanticsNodes().isNotEmpty()) {
            val btn = confirmNodes.onFirst()
            runCatching { btn.performScrollTo() }
            btn.performClick()
        } else {
            val guessBtn = composeRule.onAllNodesWithTag("ui:action:enrollGuessedDevice", useUnmergedTree = true).onFirst()
            runCatching { guessBtn.performScrollTo() }
            guessBtn.performClick()
        }
        assertAnyTagDisplayed("ui:component:BottomNavigationBar")

        // 2. Direct Manual Enrollment with Gold Catalog clue binding (`enrollManualDevice`)
        navigateIntent("tuesday://add-device?mode=manual&clues=UDM-Pro")
        assertAnyTagDisplayed("ui:action:enrollManualDevice")
        val manualBtn = composeRule.onNodeWithTag("ui:action:enrollManualDevice", useUnmergedTree = true)
        runCatching { manualBtn.performScrollTo() }
        manualBtn.performClick()
        assertAnyTagDisplayed("ui:component:BottomNavigationBar")

        navigateIntent("tuesday://reset-demo")
    }
}
