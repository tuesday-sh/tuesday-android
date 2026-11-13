package sh.tuesday.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayAppTheme
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.HelpIconButton
import sh.tuesday.app.ui.components.NotchBulletinCard
import sh.tuesday.app.ui.components.SegmentedTabRow
import sh.tuesday.app.ui.components.SingleNotchCard
import sh.tuesday.app.ui.components.SurfaceListContainer
import sh.tuesday.app.ui.components.TuesdayListRow
import sh.tuesday.app.ui.components.TuesdayModalCard
import sh.tuesday.app.ui.home.AccountSettingsParams
import sh.tuesday.app.ui.home.AccountSettingsScreen
import sh.tuesday.app.ui.home.DISCORD_INVITE_URL
import sh.tuesday.app.ui.home.EditDeviceScreen
import sh.tuesday.app.ui.uiAction

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class SharedComponentsSemanticTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun surfaceListAndTuesdayListRowRenderAndClickInBothFlavors() {
        var clickCount = 0
        var activeFlavor by mutableStateOf(AppBrandFlavor.TUESDAY_PRO)

        composeRule.setContent {
            TuesdayAppTheme(flavor = activeFlavor) {
                val palette = TuesdayTheme.colors
                SurfaceListContainer(palette = palette) {
                    TuesdayListRow(
                        palette = palette,
                        title = "Shared Hardware Row",
                        onClick = { clickCount++ },
                        modifier = Modifier.uiAction("sharedRowClick"),
                        subtitleContent = {
                            Text("Verified Subtitle", color = palette.textMuted)
                        }
                    )
                }
            }
        }

        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        for (flavor in listOf(AppBrandFlavor.TUESDAY_PRO, AppBrandFlavor.SURETHING_HOME)) {
            activeFlavor = flavor
            composeRule.waitForIdle()

            val rowNode = composeRule.onNodeWithTag("ui:action:sharedRowClick", useUnmergedTree = true)
            rowNode.assertIsDisplayed()
            val heightDp = rowNode.fetchSemanticsNode().boundsInRoot.height / density
            assertTrue("Expected TuesdayListRow height >= 44dp in $flavor, got $heightDp", heightDp >= 43.5f)
            rowNode.performClick()
        }

        assertEquals(2, clickCount)
    }

    @Test
    fun tuesdayModalCardFloatingCloseCircleMeetsHitBoundsAndDismisses() {
        var dismissedCount = 0

        composeRule.setContent {
            TuesdayAppTheme(flavor = AppBrandFlavor.TUESDAY_PRO) {
                TuesdayModalCard(
                    palette = TuesdayTheme.colors,
                    title = "Standardized Modal Chrome",
                    subtitle = "Floating top-right close target",
                    onDismissRequest = { dismissedCount++ },
                    closeActionModifier = Modifier.uiAction("testCloseModal")
                ) {
                    Text("Modal body content", color = TuesdayTheme.colors.textPrimary)
                }
            }
        }

        composeRule.waitForIdle()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val closeNode = composeRule.onNodeWithTag("ui:action:testCloseModal", useUnmergedTree = true)
        closeNode.assertIsDisplayed()
        val bounds = closeNode.fetchSemanticsNode().boundsInRoot
        val widthDp = bounds.width / density
        val heightDp = bounds.height / density
        assertTrue("Expected >= 36dp close width, got $widthDp", widthDp >= 35.5f)
        assertTrue("Expected >= 36dp close height, got $heightDp", heightDp >= 35.5f)

        closeNode.performClick()
        composeRule.waitForIdle()
        assertEquals(1, dismissedCount)
    }

    @Test
    fun singleNotchCardAndNotchBulletinCardInvokeCallbacks() {
        var singleLeftClicks = 0
        var bulletinLeftClicks = 0
        var bulletinRightClicks = 0
        var primaryClicks = 0
        var secondaryClicks = 0

        composeRule.setContent {
            TuesdayAppTheme(flavor = AppBrandFlavor.SURETHING_HOME) {
                val palette = TuesdayTheme.colors
                Column {
                    SingleNotchCard(
                        palette = palette,
                        accentColor = palette.accent,
                        notchLabel = "SINGLE NOTCH",
                        onNotchClick = { singleLeftClicks++ }
                    ) {
                        Text("Single notch body", color = palette.textPrimary)
                    }

                    NotchBulletinCard(
                        palette = palette,
                        title = "Welcome Bulletin",
                        description = "Prepare for Patch Tuesday",
                        leftNotchLabel = "BULLETIN",
                        onLeftNotchClick = { bulletinLeftClicks++ },
                        rightNotchLabel = "RHYTHM",
                        onRightNotchClick = { bulletinRightClicks++ },
                        primaryActionLabel = "Snooze 24h",
                        secondaryActionLabel = "Ignore",
                        onPrimaryAction = { primaryClicks++ },
                        onSecondaryAction = { secondaryClicks++ }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("SINGLE NOTCH", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("BULLETIN", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("ui:action:clickRightNotch", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("ui:action:primaryNotchAction", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("ui:action:secondaryNotchAction", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(1, singleLeftClicks)
        assertEquals(1, bulletinLeftClicks)
        assertEquals(1, bulletinRightClicks)
        assertEquals(1, primaryClicks)
        assertEquals(1, secondaryClicks)
    }

    @Test
    fun segmentedTabRowAndHelpIconButtonEnforceBoundsAndCallbacks() {
        var selectedTab by mutableIntStateOf(0)
        var helpClicks = 0

        composeRule.setContent {
            TuesdayAppTheme(flavor = AppBrandFlavor.TUESDAY_PRO) {
                val palette = TuesdayTheme.colors
                Column {
                    HelpIconButton(
                        palette = palette,
                        onClick = { helpClicks++ },
                        modifier = Modifier.uiAction("testHelpIcon")
                    )
                    SegmentedTabRow(
                        options = listOf("Photo", "Scan", "Manual"),
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                        palette = palette,
                        itemModifier = { idx -> Modifier.uiAction("segTab_$idx") }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val helpNode = composeRule.onNodeWithTag("ui:action:testHelpIcon", useUnmergedTree = true)
        val bounds = helpNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Expected HelpIconButton >= 36dp width", (bounds.width / density) >= 35.5f)
        assertTrue("Expected HelpIconButton >= 36dp height", (bounds.height / density) >= 35.5f)
        helpNode.performClick()

        composeRule.onNodeWithTag("ui:action:segTab_1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, selectedTab)

        composeRule.onNodeWithTag("ui:action:segTab_2", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(2, selectedTab)
        assertEquals(1, helpClicks)
    }

    @Test
    fun editDeviceScreenDoesNotExposeProhibitedAssetTagInput() {
        val app = RuntimeEnvironment.getApplication()
        val repository = TuesdayRepository(app)
        repository.setSimulatedDeviceCount(12)
        val targetDevice = repository.rackDevices.value.first()

        composeRule.setContent {
            TuesdayAppTheme(flavor = AppBrandFlavor.TUESDAY_PRO) {
                EditDeviceScreen(
                    deviceId = targetDevice.id,
                    repository = repository,
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ui:slot:edit-device-name", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ui:slot:edit-device-short-label", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ui:slot:edit-device-room", useUnmergedTree = true).assertIsDisplayed()

        // GEMINI.md:166 invariant — never expose enterprise Asset Tag inputs in mobile UI
        val assetTagSlotNodes = composeRule
            .onAllNodes(hasTestTag("ui:slot:edit-device-asset-tag"), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("Prohibited ui:slot:edit-device-asset-tag must not exist", assetTagSlotNodes.isEmpty())

        val assetTagLabelNodes = composeRule
            .onAllNodes(hasText("Asset Tag", substring = true, ignoreCase = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("Prohibited 'Asset Tag' text field must not exist", assetTagLabelNodes.isEmpty())
    }

    @Test
    fun accountSettingsDiscordFooterRendersIconMessageAndInviteLink() {
        var activeFlavor by mutableStateOf(AppBrandFlavor.TUESDAY_PRO)

        composeRule.setContent {
            TuesdayAppTheme(flavor = activeFlavor) {
                val palette = TuesdayTheme.colors
                AccountSettingsScreen(
                    params = AccountSettingsParams(
                        flavor = activeFlavor,
                        palette = palette,
                        networkLocationName = "Seattle Home",
                        activeDeviceCount = 6,
                        isDeveloperMode = false
                    )
                )
            }
        }

        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        for (flavor in listOf(AppBrandFlavor.TUESDAY_PRO, AppBrandFlavor.SURETHING_HOME)) {
            activeFlavor = flavor
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("ui:slot:discord-community-card", useUnmergedTree = true).assertIsDisplayed()
            val inviteRow = composeRule.onNodeWithTag("ui:action:openDiscordInvite", useUnmergedTree = true)
            inviteRow.assertIsDisplayed()
            val heightDp = inviteRow.fetchSemanticsNode().boundsInRoot.height / density
            assertTrue("Expected Discord footer row height >= 48dp in $flavor, got $heightDp", heightDp >= 48f)

            composeRule.onNodeWithText("Join our Discord Community", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithText(DISCORD_INVITE_URL, substring = true, useUnmergedTree = true).assertIsDisplayed()
        }
    }
}
