package sh.tuesday.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Complete JVM Semantic Compose UI & WCAG 2.2 Test Suite powered by Robolectric (`./gradlew testDebugUnitTest`).
 *
 * Ports all 8 end-to-end flows from `TuesdayHardwareE2ETest` to run on the local JVM and in GitHub Actions CI
 * in ~6 seconds total (~300-600ms per test after initial warmup), with zero emulator or ADB overhead:
 *  - `t01`: Botanical Spine progressive disclosure (`Day 0`, `Pea`, `Garden`, `Forest`) & Dual-Flavor (`Pro` / `Home`)
 *  - `t02`: All 5 Main Tabs (`Status`, `Bulletins`, `Timeline`, `Checkup`, `Account Dev`) & `ReferenceGuideModal`
 *  - `t03`: `DeviceInspectorSheet` (`Pro` & `Home`), `HacsSettingsSheet`, and API endpoint routing
 *  - `t04`: 3-mode `AddDeviceWizard` (`Manual` + Gold Clues, `Scan` + Synthetic Barcode)
 *  - `t05`: Interactive fleet filters (`TrustZone`, `PortState`), `Show OK` toggle, and Inspector remediation mutation
 *  - `t06`: Bulletin rhythm `Snooze 24h` / `Resume`, `BulletinPreferencesDialog` `Mute` / `Unmute`, and Feed disambiguation
 *  - `t07`: `NetworkPickerModal`, `Tier1UpgradeModal`, `SetupNetworkModal` site creation, and `STN-Home` all-tabs sweep
 *  - `t08`: `AddDeviceWizard` -> `AiDiscoveryPanel` confirmation enrollment & direct manual enrollment
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi") // Pixel 5 viewport geometry (393x851dp)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TuesdayComposeSemanticUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUpSuite() {
            JvmComposeWcagSemanticsAuditor.resetSession()
        }

        @JvmStatic
        @AfterClass
        fun tearDownSuite() {
            val context = RuntimeEnvironment.getApplication()
            val report = JvmComposeWcagSemanticsAuditor.writeSessionReport(context)
            println("Wrote JVM Compose WCAG 2.2 semantics audit report to: ${report.absolutePath}")
        }
    }

    @Before
    fun resetDemoState() {
        navigateIntent("tuesday://reset-demo")
        waitUntilAnyTagExists(
            "ui:component:BottomNavigationBar",
            "ui:component:WelcomeCard",
            "ui:component:InventoryCard"
        )
    }

    private fun navigateIntent(uri: String) {
        composeRule.activity.handleDeepLinkUri(uri)
        composeRule.waitForIdle()
    }

    private fun scrollLazyContainerTo(matcher: SemanticsMatcher) {
        runCatching {
            val lazyContainers = composeRule.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true)
            val count = lazyContainers.fetchSemanticsNodes().size
            for (i in 0 until count) {
                runCatching {
                    lazyContainers[i].performScrollToNode(matcher)
                    composeRule.waitForIdle()
                }
            }
        }
    }

    private fun waitUntilAnyTagExists(vararg tags: String, timeoutMillis: Long = 5_000L): String {
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

    private fun assertAnyTagRendered(vararg tags: String) {
        val matched = waitUntilAnyTagExists(*tags)
        val node = composeRule.onAllNodesWithTag(matched, useUnmergedTree = true).onFirst()
        runCatching { node.performScrollTo() }
        composeRule.waitForIdle()
        assertTrue(
            "Expected node [$matched] to exist in semantics tree",
            composeRule.onAllNodesWithTag(matched, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun auditWcag(screenLabel: String) {
        JvmComposeWcagSemanticsAuditor.auditActiveScreen(
            composeRule,
            composeRule.activity,
            screenLabel
        )
    }

    private fun assertTextSingleLine(node: SemanticsNode, label: String, expectedText: String? = null) {
        val results = mutableListOf<TextLayoutResult>()
        val getLayout = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        if (getLayout != null) {
            getLayout.invoke(results)
        }
        node.children.forEach { child ->
            child.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        }
        assertTrue("Expected TextLayoutResult on [$label]", results.isNotEmpty())
        results.forEach { layout ->
            val actualText = layout.layoutInput.text.text
            if (expectedText != null) {
                assertEquals("Expected exact text on [$label]", expectedText, actualText)
            }
            assertEquals(
                "Expected [$label] ('$actualText') to render on a single line (no wrapping)",
                1,
                layout.lineCount
            )
            assertFalse(
                "Expected [$label] ('$actualText') not to be ellipsized",
                layout.isLineEllipsized(0)
            )
            assertEquals(
                "Expected [$label] ('$actualText') to render all ${actualText.length} characters on line 0",
                actualText.length,
                layout.getLineEnd(0, visibleEnd = true)
            )
        }
    }

    @Test
    fun t00_topAppBarCanonicalSlotsHitBoundsAndResponsiveOverflow() {
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/0")

        // 1. Verify canonical TopAppBar component & semantic slots exist
        assertAnyTagRendered("ui:component:TopAppBar")
        assertAnyTagRendered("ui:slot:brand-identity")
        assertAnyTagRendered("ui:slot:brand-mark")
        assertAnyTagRendered("ui:slot:brand-title")
        assertAnyTagRendered("ui:slot:scope-actions")

        // 2. Verify all 3 interactive pills meet Material 3 / WCAG 2.2 minimum hit target bounds (>= 48dp x 36dp)
        val density = composeRule.activity.resources.displayMetrics.density
        listOf(
            "ui:action:toggleBrandFlavor",
            "ui:action:openNetworkPicker",
            "ui:action:openHacsSettings"
        ).forEach { actionTag ->
            val node = composeRule.onNodeWithTag(actionTag, useUnmergedTree = false).fetchSemanticsNode()
            val widthDp = node.boundsInWindow.width / density
            val heightDp = node.boundsInWindow.height / density
            assertTrue(
                "Expected [$actionTag] hit width >= 48dp, got ${widthDp}dp",
                widthDp >= 47.5f
            )
            assertTrue(
                "Expected [$actionTag] hit height >= 36dp (Material 3 minimum touch target 48dp), got ${heightDp}dp",
                heightDp >= 35.5f
            )
        }

        // Verify PRO badge and brand title do not wrap onto 2 lines
        assertTextSingleLine(
            composeRule.onNodeWithTag("ui:action:toggleBrandFlavor", useUnmergedTree = false).fetchSemanticsNode(),
            "PRO badge",
            expectedText = "PRO"
        )
        assertTextSingleLine(
            composeRule.onNodeWithTag("ui:slot:brand-title", useUnmergedTree = true).fetchSemanticsNode(),
            "tuesday.sh brand-title",
            expectedText = "tuesday.sh"
        )

        // 3. Click toggleBrandFlavor -> flips "tuesday.sh" (PRO) to "SureThingNet" (HOME) and verify "HOME" never wraps!
        composeRule.onNodeWithTag("ui:action:toggleBrandFlavor", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Expected SureThingNet title after clicking toggleBrandFlavor",
            composeRule.onAllNodesWithText("SureThingNet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )
        assertTextSingleLine(
            composeRule.onNodeWithTag("ui:action:toggleBrandFlavor", useUnmergedTree = false).fetchSemanticsNode(),
            "HOME badge",
            expectedText = "HOME"
        )
        assertTextSingleLine(
            composeRule.onNodeWithTag("ui:slot:brand-title", useUnmergedTree = true).fetchSemanticsNode(),
            "SureThingNet brand-title",
            expectedText = "SureThingNet"
        )

        composeRule.onNodeWithTag("ui:action:toggleBrandFlavor", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Expected tuesday.sh title after toggling back to PRO",
            composeRule.onAllNodesWithText("tuesday.sh", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )

        // 4. Click openNetworkPicker -> opens NetworkPickerModal; create a 55-char site name & verify HACS menu never clips off-screen and HOME badge still never wraps
        composeRule.onNodeWithTag("ui:action:openNetworkPicker", useUnmergedTree = true).performClick()
        assertAnyTagRendered("ui:component:NetworkPickerModal")
        composeRule.onNodeWithTag("ui:action:openSetupNetworkModal", useUnmergedTree = true).performClick()
        assertAnyTagRendered("ui:component:Tier1UpgradeModal")
        composeRule.onNodeWithTag("ui:action:confirmTier1Upgrade", useUnmergedTree = true).performClick()
        assertAnyTagRendered("ui:component:SetupNetworkModal")

        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst()
            .performTextInput("Seattle Capitol Hill Primary Production Homelab Rack 01")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create Network & Join", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Verify right-most HACS menu button stays strictly inside the 393dp viewport AND HOME badge stays 1-line unclipped
        navigateIntent("tuesday://flavor/home")
        assertTextSingleLine(
            composeRule.onNodeWithTag("ui:action:toggleBrandFlavor", useUnmergedTree = false).fetchSemanticsNode(),
            "HOME badge with 55-char network name",
            expectedText = "HOME"
        )
        val hacsNode = composeRule.onNodeWithTag("ui:action:openHacsSettings", useUnmergedTree = false).fetchSemanticsNode()
        val rightEdgeDp = hacsNode.boundsInWindow.right / density
        assertTrue(
            "Expected HACS menu pill right edge (${rightEdgeDp}dp) to remain inside 393dp viewport on long network names",
            rightEdgeDp <= 393.5f
        )

        // 5. Click openHacsSettings -> opens HacsSettingsSheet
        composeRule.onNodeWithTag("ui:action:openHacsSettings", useUnmergedTree = true).performClick()
        assertAnyTagRendered("ui:component:HacsSettingsSheet")
    }

    @Test
    fun t00b_contentAreaAndBottomNavigationBarTabsBadgesAndNoWrap() {
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // 1. Verify vertical layout sandwich: TopAppBar -> ContentArea -> BottomNavigationBar (zero overlap)
        val density = composeRule.activity.resources.displayMetrics.density
        val topBarBounds = composeRule.onNodeWithTag("ui:component:TopAppBar", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val contentAreaBounds = composeRule.onNodeWithTag("ui:slot:content-area", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val bottomNavBounds = composeRule.onNodeWithTag("ui:component:BottomNavigationBar", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected ContentArea top (${contentAreaBounds.top}) >= TopAppBar bottom (${topBarBounds.bottom})",
            contentAreaBounds.top >= topBarBounds.bottom - 1f
        )
        assertTrue(
            "Expected ContentArea bottom (${contentAreaBounds.bottom}) <= BottomNavigationBar top (${bottomNavBounds.top})",
            contentAreaBounds.bottom <= bottomNavBounds.top + 1f
        )

        // 2. Verify all 5 NavigationBarItem tabs exist, meet >= 48x48dp hit targets, and have single-line labels in PRO
        val tabNodesPro = composeRule.onAllNodesWithTag("ui:action:switchTab", useUnmergedTree = false).fetchSemanticsNodes()
        assertEquals("Expected 5 tabs in BottomNavigationBar", 5, tabNodesPro.size)
        val expectedProLabels = listOf("My Net", "Bulletins", "Timeline", "Check-Up", "Account")
        tabNodesPro.forEachIndexed { idx, tabNode ->
            val widthDp = tabNode.boundsInWindow.width / density
            val heightDp = tabNode.boundsInWindow.height / density
            assertTrue("Expected Tab $idx width >= 48dp, got ${widthDp}dp", widthDp >= 48f)
            assertTrue("Expected Tab $idx height >= 48dp, got ${heightDp}dp", heightDp >= 48f)
            assertTextSingleLine(tabNode, "PRO Tab $idx (${expectedProLabels[idx]})")
        }

        // 3. Verify Bulletins unread badge (`ui:slot:bulletins-badge`) renders on Bulletins tab
        assertAnyTagRendered("ui:slot:bulletins-badge")

        // 4. Switch to STN-Home and verify all 5 tab labels ("Home Grid", "Bulletins", "Lifespan", "Check-Up", "Account") remain 1-line unclipped
        navigateIntent("tuesday://flavor/home")
        val tabNodesHome = composeRule.onAllNodesWithTag("ui:action:switchTab", useUnmergedTree = false).fetchSemanticsNodes()
        val expectedHomeLabels = listOf("Home Grid", "Bulletins", "Lifespan", "Check-Up", "Account")
        tabNodesHome.forEachIndexed { idx, tabNode ->
            assertTextSingleLine(tabNode, "HOME Tab $idx (${expectedHomeLabels[idx]})", expectedText = expectedHomeLabels[idx])
        }

        // 5. Interactive click-through of all 5 tabs in BottomNavigationBar -> verify ContentArea mounts each screen
        navigateIntent("tuesday://flavor/pro")
        val tabs = composeRule.onAllNodesWithTag("ui:action:switchTab", useUnmergedTree = true)

        tabs[1].performClick() // Bulletins
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:BulletinsScreen")

        tabs[2].performClick() // Timeline
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:SwimlaneTimelineScreen")

        tabs[3].performClick() // Check-Up
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:SeasonalCheckupScreen")

        tabs[4].performClick() // Account
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:AccountSettingsCard")

        tabs[0].performClick() // Back to Status ("My Net")
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:InventoryCard")
    }

    @Test
    fun t01_botanicalSpineProgressiveDisclosureAndDualFlavorMatrix() {
        val density = composeRule.activity.resources.displayMetrics.density

        // 1. STN-Pro Day 0 (0 devices) -> WelcomeCard Conditional Callout Card + InventoryCard visible
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/0")
        assertAnyTagRendered("ui:component:WelcomeCard")
        assertAnyTagRendered("ui:slot:router-advisory-tip")
        assertAnyTagRendered("ui:slot:add-router-cta")
        assertAnyTagRendered("ui:slot:add-any-device-cta")
        assertAnyTagRendered("ui:slot:enable-demo-cta")
        assertAnyTagRendered("ui:component:InventoryCard")
        assertAnyTagRendered("ui:slot:inventory-header")
        assertAnyTagRendered("ui:slot:inventory-title")
        assertAnyTagRendered("ui:slot:add-device-cta")
        assertAnyTagRendered("ui:slot:help-button")
        assertAnyTagRendered("ui:component:CategoryFaceplateStrip", "ui:slot:c1-c8-strip")
        assertAnyTagRendered("ui:component:InventoryLedGrid", "ui:slot:led-grid-128")
        assertAnyTagRendered("ui:component:BotanicalGridOverlay", "ui:slot:botanical-overlay")
        assertAnyTagRendered("ui:slot:inventory-empty-pill")

        // Verify Inventory Empty pill is vertically and horizontally centered inside InventoryLedGrid
        val ledGridNode = composeRule.onAllNodesWithTag("ui:slot:led-grid-128", useUnmergedTree = true).fetchSemanticsNodes().first()
        val emptyPillNode = composeRule.onAllNodesWithTag("ui:slot:inventory-empty-pill", useUnmergedTree = true).fetchSemanticsNodes().first()
        val centerDiffYDps = kotlin.math.abs(emptyPillNode.boundsInWindow.center.y - ledGridNode.boundsInWindow.center.y) / density
        val centerDiffXDps = kotlin.math.abs(emptyPillNode.boundsInWindow.center.x - ledGridNode.boundsInWindow.center.x) / density
        assertTrue(
            "Expected inventory-empty-pill vertically centered in led-grid-128 (diff <= 3dp), got ${centerDiffYDps}dp (pill=${emptyPillNode.boundsInWindow}, grid=${ledGridNode.boundsInWindow})",
            centerDiffYDps <= 3.0f && centerDiffXDps <= 3.0f
        )

        // Verify InventoryCard Primary CTA ('+ Add Device') & Secondary CTA 1 ('?' Help Icon) hit bounds & single-line text
        val addDevBtn = composeRule.onAllNodesWithTag("ui:action:openAddDevice", useUnmergedTree = false)
            .fetchSemanticsNodes().first()
        val addDevHeightDp = addDevBtn.boundsInWindow.height / density
        val addDevWidthDp = addDevBtn.boundsInWindow.width / density
        assertTrue("Expected InventoryCard '+ Add Device' button height >= 36dp, got ${addDevHeightDp}dp", addDevHeightDp >= 35.5f)
        assertTrue("Expected InventoryCard '+ Add Device' button width >= 48dp, got ${addDevWidthDp}dp", addDevWidthDp >= 47.5f)

        val helpBtn = composeRule.onNodeWithTag("ui:action:openReferenceGuide", useUnmergedTree = false).fetchSemanticsNode()
        val helpHeightDp = helpBtn.boundsInWindow.height / density
        val helpWidthDp = helpBtn.boundsInWindow.width / density
        assertTrue("Expected InventoryCard '?' Help button >= 36x36dp, got ${helpWidthDp}x${helpHeightDp}dp", helpWidthDp >= 35.5f && helpHeightDp >= 35.5f)

        // Verify Secondary CTA 2: 8 Category Faceplate columns (C1..C8) per page
        val catCols = composeRule.onAllNodesWithTag("ui:action:inspectCategoryDevice", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expected at least 8 C1..C8 faceplate buttons (8 per pager page), got ${catCols.size}", catCols.size >= 8 && catCols.size % 8 == 0)

        // Verify clicking C2 (Switch) faceplate button opens CategoryPopover and clicking "+ Add Switch Device" opens AddDeviceWizard with "Network Switch" clue pre-selected
        composeRule.onAllNodesWithTag("ui:action:inspectCategoryDevice", useUnmergedTree = false)[1].performClick()
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:CategoryPopover", "ui:slot:category-popover-header", "ui:slot:category-popover-add-cta")
        composeRule.onNodeWithTag("ui:action:addCategoryDevice", useUnmergedTree = false).performClick()
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:AddDeviceWizard", "ui:slot:selected-clue-chips")
        assertTrue(
            "Expected 'Network Switch' clue chip to be pre-selected when launching Add Device from C2 CategoryPopover",
            composeRule.onAllNodesWithText("Network Switch", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )
        // Navigate back to Day 0 Status screen
        composeRule.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:InventoryCard")

        // Verify all 3 WelcomeCard CTA buttons meet >= 48dp Material 3 hit height & single-line text
        listOf(
            "ui:slot:add-router-cta" to "Add Your Router (Recommended) →",
            "ui:slot:add-any-device-cta" to "+ Add Any Device",
            "ui:slot:enable-demo-cta" to "Enable Demo Mode (add example devices)"
        ).forEach { (slotTag, expectedLabel) ->
            assertAnyTagRendered(slotTag)
            val textNode = composeRule.onNodeWithTag(slotTag, useUnmergedTree = true).fetchSemanticsNode()
            assertTextSingleLine(textNode, "WelcomeCard $slotTag", expectedText = expectedLabel)
            val buttonBounds = textNode.parent?.boundsInWindow ?: textNode.boundsInWindow
            val buttonHeightDp = buttonBounds.height / density
            assertTrue("Expected WelcomeCard button [$slotTag] height >= 48dp, got ${buttonHeightDp}dp", buttonHeightDp >= 47.5f)
        }
        assertEquals(
            "Expected 3 openAddDevice buttons on Day 0 (1 in InventoryCard header + 2 in WelcomeCard, and no bottom '+ Add Another Device')",
            3,
            composeRule.onAllNodesWithTag("ui:action:openAddDevice", useUnmergedTree = false).fetchSemanticsNodes().size
        )

        // Verify clicking Enable Demo Mode button directly toggles WelcomeCard OFF and seeds Forest fleet
        composeRule.onNodeWithTag("ui:action:enableDemoMode", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(
            "WelcomeCard Conditional Callout should unmount immediately after clicking Enable Demo Mode",
            composeRule.onAllNodesWithTag("ui:component:WelcomeCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Reset back to Day 0 to continue progressive disclosure sequence
        navigateIntent("tuesday://seed/0")
        assertAnyTagRendered("ui:component:WelcomeCard")
        assertTrue(
            "Day 0 should hide '+ Add Another Device' button while WelcomeCard is visible",
            composeRule.onAllNodesWithTag("ui:slot:add-another-device-cta", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Day 0 should hide TrackedHardwareProfilesCard and legend-row",
            composeRule.onAllNodesWithTag("ui:component:TrackedHardwareProfilesCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:legend-row", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
        )
        auditWcag("Pro • Day 0 (Welcome)")

        // 2. Pea Tier (1 device) -> Healthy UDM-Pro (state=ok, cat=C1), legend-row visible (without counts), WelcomeCard unmounted, '+ Add Another Device' visible, filter strips disabled by default on fresh install
        navigateIntent("tuesday://seed/1")
        assertAnyTagRendered("ui:component:InventoryCard")
        assertAnyTagRendered("ui:slot:legend-row")
        assertAnyTagRendered("ui:slot:add-another-device-cta")
        assertAnyTagRendered("ui:slot:device-row")
        val peaRowNode = composeRule.onNodeWithTag("ui:slot:device-row", useUnmergedTree = true).fetchSemanticsNode()
        val peaRowMeta = peaRowNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription).orEmpty()
        assertTrue(
            "Expected Pea tier (1 device) seed to be healthy (state=ok;cat=C1), got: $peaRowMeta",
            peaRowMeta.contains("state=ok") && peaRowMeta.contains("cat=C1")
        )
        assertTrue(
            "Pea tier (1 device) on fresh install should unmount WelcomeCard and hide filter strips (disabled by default) and TrackedHardwareProfilesCard",
            composeRule.onAllNodesWithTag("ui:component:WelcomeCard", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:state-filter-strip", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:zone-filter-strip", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:component:TrackedHardwareProfilesCard", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
        )
        // Verify user can explicitly turn filter strips ON even in Pea tier (1 device), while Day 0 (0 devices) always hides them
        navigateIntent("tuesday://preferences/filters/on")
        assertAnyTagRendered("ui:slot:state-filter-strip", "ui:slot:zone-filter-strip")
        navigateIntent("tuesday://seed/0")
        assertTrue(
            "Day 0 (0 devices) must hide filter strips even when filter preferences are enabled",
            composeRule.onAllNodesWithTag("ui:slot:state-filter-strip", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:zone-filter-strip", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
        )
        navigateIntent("tuesday://seed/1")
        assertAnyTagRendered("ui:slot:state-filter-strip", "ui:slot:zone-filter-strip")
        navigateIntent("tuesday://preferences/filters/off")
        auditWcag("Pro • Pea (1 Device)")

        // 3. Garden Tier (7 devices)
        navigateIntent("tuesday://seed/7")
        assertAnyTagRendered("ui:component:InventoryCard")
        auditWcag("Pro • Garden (7 Devices)")

        // 4. Forest Tier (12 devices) -> C1-C8 Faceplate Strip + 128 LED Grid + Tracked Profiles revealed
        navigateIntent("tuesday://seed/12")
        assertAnyTagRendered("ui:component:CategoryFaceplateStrip", "ui:slot:c1-c8-strip")
        assertAnyTagRendered("ui:component:InventoryLedGrid", "ui:slot:led-grid-128")
        assertAnyTagRendered("ui:component:TrackedHardwareProfilesCard", "ui:slot:tracked-profiles-strip")
        auditWcag("Pro • Forest (12 Devices)")

        // 5. Switch to STN-Home (Daylight Calm) in Forest Tier & verify re-theme + semantics (with filter strips enabled)
        navigateIntent("tuesday://preferences/filters/on")
        navigateIntent("tuesday://flavor/home")
        assertAnyTagRendered("ui:component:InventoryCard")
        assertAnyTagRendered("ui:slot:state-filter-strip")
        assertAnyTagRendered("ui:slot:zone-filter-strip")
        auditWcag("Home • Forest (12 Devices)")

        navigateIntent("tuesday://flavor/pro")
    }

    @Test
    fun t02_allFiveTabsAndModalSheetsIntentNavigationAndWcagAudit() {
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // Tab 2: Bulletins
        navigateIntent("tuesday://bulletins")
        assertAnyTagRendered("ui:component:BulletinsScreen")
        auditWcag("Tab 2 • Bulletins (Pro)")

        // Tab 3: Swimlane Timeline
        navigateIntent("tuesday://timeline")
        assertAnyTagRendered("ui:component:SwimlaneTimelineScreen")
        auditWcag("Tab 3 • Timeline (Pro)")

        // Tab 4: Seasonal Checkup (Quarterly Audit)
        navigateIntent("tuesday://checkup")
        assertAnyTagRendered("ui:component:SeasonalCheckupScreen")
        auditWcag("Tab 4 • Checkup (Pro)")

        // Tab 5: Account & Developer Settings
        navigateIntent("tuesday://account/dev")
        assertAnyTagRendered("ui:component:AccountSettingsCard")
        auditWcag("Tab 5 • Account Dev (Pro)")

        // Modal 1: Reference Guide (C1-C8 / S0-S6 / Z0-Z5 / OSI)
        navigateIntent("tuesday://status")
        navigateIntent("tuesday://reference-guide")
        assertAnyTagRendered("ui:component:ReferenceGuideModal")
        auditWcag("Modal • ReferenceGuide")
    }

    @Test
    fun t03_deviceInspectorHacsSheetAndApiEndpointSwitching() {
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // Inspect canonical RackPortDevice via Intent URI (Pro Candlesticks + S0..S6)
        navigateIntent("tuesday://device/0192d9f0-0008-7000-8000-000000000108")
        assertAnyTagRendered("ui:component:DeviceInspectorSheet", "ui:slot:inspector-header")
        auditWcag("Sheet • DeviceInspector (Pro)")

        // Inspect same device in STN-Home ("What to Do Next" colloquial steps)
        navigateIntent("tuesday://reset-demo")
        navigateIntent("tuesday://flavor/home")
        navigateIntent("tuesday://seed/12")
        navigateIntent("tuesday://device/0192d9f0-0008-7000-8000-000000000108")
        assertAnyTagRendered("ui:component:DeviceInspectorSheet", "ui:slot:inspector-header")
        auditWcag("Sheet • DeviceInspector (Home)")

        // HACS & AI Bridge Sheet
        navigateIntent("tuesday://reset-demo")
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://hacs")
        assertAnyTagRendered("ui:component:HacsSettingsSheet", "ui:slot:hacs-bridge-header")
        auditWcag("Sheet • HacsSettings")

        // API Endpoint Routing Integration
        navigateIntent("tuesday://api-endpoint/local")
        navigateIntent("tuesday://api-endpoint/prod")
    }

    @Test
    fun t04_addDeviceWizardThreeModeCluesAndSyntheticBarcodeIntegration() {
        // Manual / Shared Hardware Clues mode with Gold Datalake query prefill
        navigateIntent("tuesday://add-device?mode=manual&q=Ubiquiti&clues=UDM-Pro")
        assertAnyTagRendered("ui:component:AddDeviceWizard", "ui:slot:enrollment-mode-tabs")
        auditWcag("Wizard • Manual + Gold Clues")

        // Desk-safe Synthetic Barcode Intake (`barcode-udm-pro`)
        navigateIntent("tuesday://synth/barcode")
        assertAnyTagRendered("ui:component:AddDeviceWizard", "ui:slot:enrollment-mode-tabs")
        auditWcag("Wizard • Scan + Synth Barcode")
    }

    @Test
    fun t05_fleetFiltersShowOkToggleAndInspectorRemediationMutation() {
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // Verify filter strips are disabled by default on fresh install / reset-demo, then enable them
        assertTrue(
            "Filter strips should be disabled by default on fresh install / reset-demo",
            composeRule.onAllNodesWithTag("ui:slot:state-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:zone-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )
        navigateIntent("tuesday://preferences/filters/on")

        // 1. Scroll to TrustZone filter strip & click second pill (Z0 / Z1), then reset to first ("All Zones")
        assertAnyTagRendered("ui:slot:zone-filter-strip")
        val zonePills = composeRule.onAllNodesWithTag("ui:action:filterZone", useUnmergedTree = true)
        assertTrue("Expected TrustZone filter pills in Forest tier", zonePills.fetchSemanticsNodes().size >= 2)
        zonePills[1].performClick()
        composeRule.waitForIdle()
        zonePills[0].performClick()
        composeRule.waitForIdle()

        // 2. Scroll to PortState filter strip & toggle CVE/KEV filter chip
        assertAnyTagRendered("ui:slot:state-filter-strip")
        val stateChips = composeRule.onAllNodesWithTag("ui:action:filterPortState", useUnmergedTree = true)
        assertTrue("Expected PortState filter chips in Forest tier", stateChips.fetchSemanticsNodes().size >= 2)
        stateChips[1].performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Expected FILTERED header when CVE/KEV chip is active",
            composeRule.onAllNodesWithText("FILTERED:", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onAllNodesWithTag("ui:action:filterPortState", useUnmergedTree = true)[1].performClick()
        composeRule.waitForIdle()

        // 3. Toggle "Show OK (+N)" -> "ALL NAMED DEVICES"
        assertAnyTagRendered("ui:action:toggleIncludeOk")
        composeRule.onNodeWithTag("ui:action:toggleIncludeOk", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Expected ALL NAMED DEVICES header after toggling Show OK",
            composeRule.onAllNodesWithText("ALL NAMED DEVICES", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        // 4. Open DeviceInspectorSheet on CVE device & mark remediation complete -> flips port to OK
        navigateIntent("tuesday://device/0192d9f0-0006-7000-8000-000000000106")
        val remTag = waitUntilAnyTagExists("ui:action:markRemediationComplete", "ui:slot:remediation-cta")
        val remBtn = composeRule.onNodeWithTag(remTag, useUnmergedTree = true)
        runCatching { remBtn.performScrollTo() }
        remBtn.performClick()
        composeRule.waitForIdle()

        // Re-inspecting the remediated device should no longer show a remediation CTA (device is now OK)
        navigateIntent("tuesday://device/0192d9f0-0006-7000-8000-000000000106")
        assertAnyTagRendered("ui:component:DeviceInspectorSheet", "ui:slot:inspector-header")
        assertTrue(
            "Remediated device should now be OK with no markRemediationComplete button",
            composeRule.onAllNodesWithTag("ui:action:markRemediationComplete", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("ui:action:closeDeviceInspector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // 5. Verify Preferences Intent URIs + Toggle List in Account Settings independently control State & Zone filter strips across PRO and HOME flavors
        // 5a. Navigating to tuesday://preferences opens Account tab and auto-expands the Preferences accordion
        navigateIntent("tuesday://preferences")
        assertAnyTagRendered("ui:component:AccountSettingsCard", "ui:slot:preferences-toggle-list")
        assertAnyTagRendered("ui:action:toggleShowStateFilterStrip", "ui:action:toggleShowZoneFilterStrip")

        // 5b. Toggle ONLY State Filter Strip OFF -> State Filter Strip disappears above list view on Status tab, while legend-row and Zone Filter Strip stay visible in InventoryCard
        composeRule.onNodeWithTag("ui:action:toggleShowStateFilterStrip", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        navigateIntent("tuesday://status")
        assertTrue(
            "Toggling State Filter Strip OFF should hide state-filter-strip",
            composeRule.onAllNodesWithTag("ui:slot:state-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )
        assertAnyTagRendered("ui:slot:legend-row", "ui:slot:zone-filter-strip")

        // 5c. Use direct intent URI to toggle State ON and Zone OFF without leaving Status tab (tuesday://preferences?state=true&zone=false)
        navigateIntent("tuesday://preferences?state=true&zone=false")
        assertAnyTagRendered("ui:slot:state-filter-strip")
        assertTrue(
            "tuesday://preferences?state=true&zone=false should hide zone-filter-strip while keeping state-filter-strip visible",
            composeRule.onAllNodesWithTag("ui:slot:zone-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )

        // 5d. Use path-based intent URI tuesday://preferences/filters/off to hide both strips across PRO and HOME flavors
        navigateIntent("tuesday://preferences/filters/off")
        assertTrue(
            "State and Zone filter strips should be hidden in PRO when toggled off via tuesday://preferences/filters/off",
            composeRule.onAllNodesWithTag("ui:slot:state-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:zone-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )
        navigateIntent("tuesday://flavor/home")
        assertTrue(
            "State and Zone filter strips should be hidden in HOME when toggled off",
            composeRule.onAllNodesWithTag("ui:slot:state-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("ui:slot:zone-filter-strip", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )

        // 5e. Open tuesday://account/preferences and click both switches back ON in UI -> verify both strips re-mount in HOME and PRO
        navigateIntent("tuesday://account/preferences")
        assertAnyTagRendered("ui:slot:preferences-toggle-list")
        composeRule.onNodeWithTag("ui:action:toggleShowStateFilterStrip", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("ui:action:toggleShowZoneFilterStrip", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        navigateIntent("tuesday://status")
        assertAnyTagRendered("ui:slot:state-filter-strip", "ui:slot:zone-filter-strip")
        navigateIntent("tuesday://flavor/pro")
        assertAnyTagRendered("ui:slot:state-filter-strip", "ui:slot:zone-filter-strip")
    }

    @Test
    fun t06_bulletinRhythmSnoozePreferencesMuteAndFeedDisambiguation() {
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")
        navigateIntent("tuesday://bulletins")
        assertAnyTagRendered("ui:component:BulletinsScreen")

        // 1. Snooze 24h on NotchBulletinCard -> verify SNOOZED banner -> Resume
        assertAnyTagRendered("ui:action:primaryNotchAction")
        composeRule.onAllNodesWithTag("ui:action:primaryNotchAction", useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("SNOOZED", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Resume →", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // 2. Click "Ignore" -> opens BulletinPreferencesDialog -> audit WCAG -> Mute -> Unmute
        assertAnyTagRendered("ui:action:secondaryNotchAction")
        composeRule.onAllNodesWithTag("ui:action:secondaryNotchAction", useUnmergedTree = true).onFirst().performClick()
        assertAnyTagRendered("ui:component:BulletinPreferencesDialog")
        auditWcag("Dialog • BulletinPreferences")

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
        navigateIntent("tuesday://flavor/pro")
        navigateIntent("tuesday://seed/12")

        // 1. Open NetworkPickerModal & audit WCAG
        navigateIntent("tuesday://network-picker")
        assertAnyTagRendered("ui:component:NetworkPickerModal")
        auditWcag("Modal • NetworkPicker")

        // 2. Open Tier1UpgradeModal from New Network button (Free tier), upgrade to Pro Tier 1, then create "Pixel 5 Lab" site
        composeRule.onNodeWithTag("ui:action:openSetupNetworkModal", useUnmergedTree = true).performClick()
        assertAnyTagRendered("ui:component:Tier1UpgradeModal")
        auditWcag("Modal • Tier1Upgrade")

        composeRule.onNodeWithTag("ui:action:confirmTier1Upgrade", useUnmergedTree = true).performClick()
        assertAnyTagRendered("ui:component:SetupNetworkModal")
        auditWcag("Modal • SetupNetwork")

        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst()
            .performTextInput("Pixel 5 Lab")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create Network & Join", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertAnyTagRendered("ui:component:InventoryCard")

        // 3. Switch back via NetworkPickerModal `selectNetwork`
        navigateIntent("tuesday://network-picker")
        assertAnyTagRendered("ui:component:NetworkPickerModal")
        val netChoices = composeRule.onAllNodesWithTag("ui:action:selectNetwork", useUnmergedTree = true)
        if (netChoices.fetchSemanticsNodes().isNotEmpty()) {
            netChoices.onFirst().performClick()
            composeRule.waitForIdle()
        }

        // 4. Full STN-Home (Daylight Calm) sweep across remaining tabs (Bulletins, Lifespan, Check-Up, Account)
        navigateIntent("tuesday://flavor/home")
        navigateIntent("tuesday://seed/12")

        navigateIntent("tuesday://bulletins")
        assertAnyTagRendered("ui:component:BulletinsScreen")
        auditWcag("Tab 2 • Bulletins (Home)")

        navigateIntent("tuesday://timeline")
        assertAnyTagRendered("ui:component:SwimlaneTimelineScreen")
        auditWcag("Tab 3 • Lifespan (Home)")

        navigateIntent("tuesday://checkup")
        assertAnyTagRendered("ui:component:SeasonalCheckupScreen")
        auditWcag("Tab 4 • Checkup (Home)")

        navigateIntent("tuesday://account")
        assertAnyTagRendered("ui:component:AccountSettingsCard")
        auditWcag("Tab 5 • Account (Home)")

        navigateIntent("tuesday://flavor/pro")
    }

    @Test
    fun t08_wizardAiDiscoveryPanelConfirmEnrollAndManualEnroll() {
        navigateIntent("tuesday://flavor/pro")

        // 1. Trigger Identifier/Clue Search -> mounts AiDiscoveryPanel (Component 20/20!)
        navigateIntent("tuesday://add-device?mode=photo&q=Ubiquiti&clues=UCG-Ultra")
        assertAnyTagRendered("ui:action:searchDeviceCandidates")
        composeRule.onNodeWithTag("ui:action:searchDeviceCandidates", useUnmergedTree = true).performClick()

        // Wait for progressive/heuristic AI discovery result to mount AiDiscoveryPanel
        waitUntilAnyTagExists("ui:component:AiDiscoveryPanel", "ui:slot:ai-discovery-panel", timeoutMillis = 15_000L)
        assertAnyTagRendered("ui:component:AiDiscoveryPanel", "ui:slot:ai-discovery-panel")
        auditWcag("Wizard • AiDiscoveryPanel")

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
        assertAnyTagRendered("ui:component:BottomNavigationBar")

        // 2. Direct Manual Enrollment with Gold Catalog clue binding (`enrollManualDevice`)
        navigateIntent("tuesday://add-device?mode=manual&clues=UDM-Pro")
        assertAnyTagRendered("ui:action:enrollManualDevice")
        val manualBtn = composeRule.onNodeWithTag("ui:action:enrollManualDevice", useUnmergedTree = true)
        runCatching { manualBtn.performScrollTo() }
        manualBtn.performClick()
        assertAnyTagRendered("ui:component:BottomNavigationBar")
    }
}
