package sh.tuesday.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sh.tuesday.app.R
import sh.tuesday.app.domain.EntitlementGatePolicy
import sh.tuesday.app.models.ApiEndpointPreset
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.MatchedFeedView
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.services.GoldenCorpusRecorder
import sh.tuesday.app.services.SubscriptionPlanOffer
import sh.tuesday.app.services.TuesdayBillingManager
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.PlanOfferSelector
import sh.tuesday.app.ui.components.PreferenceToggleRow
import sh.tuesday.app.ui.components.SelectableFilterChip
import sh.tuesday.app.ui.components.SurfaceListContainer
import sh.tuesday.app.ui.components.SurfaceListDivider
import sh.tuesday.app.ui.components.TuesdayListRow
import sh.tuesday.app.ui.components.TuesdayModalCard
import sh.tuesday.app.ui.components.TuesdayTextField
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

data class AccountSettingsParams(
    val flavor: AppBrandFlavor,
    val palette: BrandPalette,
    val networkLocationName: String,
    val activeDeviceCount: Int,
    val isDeveloperMode: Boolean,
    val entitlementTier: String = "free",
    val activeSubscriptionSku: String = TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL,
    val plans: List<SubscriptionPlanOffer> = TuesdayBillingManager.defaultPlanOffers(),
    val selectedProductId: String = TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL,
    val onSelectPlan: (String) -> Unit = {},
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val billingStatusMessage: String? = null,
    val matchedFeeds: List<MatchedFeedView> = emptyList(),
    val remindersEnabled: Boolean = false,
    val onToggleReminders: ((Boolean) -> Unit)? = null,
    val onToggleMuteFeed: (String) -> Unit = {},
    val onUpgradeProTier1: (String) -> Unit = {},
    val onRestorePurchases: () -> Unit = {},
    val onDowngradeFreeTier: () -> Unit = {},
    val onSaveNetworkName: (String) -> Unit = {},
    val onToggleDeveloperMode: (Boolean) -> Unit = {},
    val isVisionDebugEnabled: Boolean = false,
    val onToggleVisionDebugMode: (Boolean) -> Unit = {},
    val isDiagnosticsEnabled: Boolean = false,
    val onToggleDiagnosticsMode: (Boolean) -> Unit = {},
    val tuesdayApiBaseUrl: String = "https://api.tuesday.sh",
    val onSetTuesdayApiBaseUrl: (String) -> Unit = {},
    val onSetSimulatedDeviceCount: (Int) -> Unit = {},
    val onSwitchToFleetTab: () -> Unit = {},
    val initialExpandNetwork: Boolean = false,
    val initialExpandPlan: Boolean = false,
    val initialExpandReminders: Boolean = false,
    val initialExpandPreferences: Boolean = false,
    val showStateFilterStrip: Boolean = true,
    val onToggleShowStateFilterStrip: (Boolean) -> Unit = {},
    val showZoneFilterStrip: Boolean = true,
    val onToggleShowZoneFilterStrip: (Boolean) -> Unit = {},
    val onResendInventoryNotifications: suspend () -> Int = { 0 },
    val onTriggerMockPatchTuesday: () -> Boolean = { false },
    val onResetDemoData: () -> Unit = {}
)

data class ReminderCategoryDef(
    val key: String,
    val title: String,
    val desc: String,
    val isCritical: Boolean
)

val REMINDER_DEFINITIONS = listOf(
    ReminderCategoryDef(
        key = "high_severity_alerts",
        title = "High Severity Alerts (CVEs & Recalls)",
        desc = "Critical vulnerabilities, active CISA KEV disclosures, and physical recalls. (If disabled, alerts still display in app without ringing push notifications).",
        isCritical = true
    ),
    ReminderCategoryDef(
        key = "monthly_rhythm",
        title = "Monthly Rhythm (Patch Tuesday)",
        desc = "Prep checklists and release day drop digests scheduled for the second Tuesday of each month.",
        isCritical = false
    ),
    ReminderCategoryDef(
        key = "quarterly_checkup",
        title = "Quarterly Physical Audit & Checkup",
        desc = "Quarterly reminders to inspect physical patch cabling, port labels, and admin credential hygiene.",
        isCritical = false
    ),
    ReminderCategoryDef(
        key = "routine_firmware",
        title = "Routine Firmware & Maintenance",
        desc = "General manufacturer software improvements, minor patches, and feature updates.",
        isCritical = false
    ),
    ReminderCategoryDef(
        key = "lifecycle_eol",
        title = "Lifespan & Hardware Retirement (EOL)",
        desc = "Early warning notices before vendor security support and firmware patch commitments expire.",
        isCritical = false
    )
)

private enum class SettingsSection(val title: String) {
    NETWORK("Network"),
    PLAN("Plan & Subscription"),
    PREFERENCES("Preferences"),
    DEV_MODE("Developer Mode"),
    REMINDERS("Reminders"),
    FEEDS("Bulletin Feeds")
}

@Composable
private fun SettingsMenuRow(
    palette: BrandPalette,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    TuesdayListRow(
        palette = palette,
        title = title,
        onClick = onClick,
        subtitleContent = if (!subtitle.isNullOrBlank()) {
            {
                Text(
                    text = subtitle,
                    color = palette.textMuted,
                    fontSize = 11.5.sp
                )
            }
        } else null
    )
}

@Composable
fun AccountSettingsScreen(params: AccountSettingsParams) {
    val flavor = params.flavor
    val palette = params.palette
    val networkLocationName = params.networkLocationName
    val activeDeviceCount = params.activeDeviceCount
    val isDeveloperMode = params.isDeveloperMode
    val matchedFeeds = params.matchedFeeds
    val onToggleMuteFeed = params.onToggleMuteFeed
    val onSaveNetworkName = params.onSaveNetworkName
    val onToggleDeveloperMode = params.onToggleDeveloperMode
    val onSetSimulatedDeviceCount = params.onSetSimulatedDeviceCount
    val onSwitchToFleetTab = params.onSwitchToFleetTab

    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    var draftName by remember(networkLocationName) { mutableStateOf(networkLocationName) }
    var draftApiUrl by remember(params.tuesdayApiBaseUrl) { mutableStateOf(params.tuesdayApiBaseUrl) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedCorpusCount by remember { mutableStateOf(GoldenCorpusRecorder.getSavedSessionCount(context)) }
    var purgeFeedback by remember { mutableStateOf<String?>(null) }
    var pushSimFeedback by remember { mutableStateOf<String?>(null) }
    var isResendingNotifs by remember { mutableStateOf(false) }

    val isProTier1 = EntitlementGatePolicy.isProTier1Entitled(params.entitlementTier)
    var draftPlanSku by remember(params.selectedProductId) { mutableStateOf(params.selectedProductId) }
    val okGreen = TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn)
    val cveRed = TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, isStn)

    val reminderStates = remember(params.remindersEnabled) {
        mutableStateMapOf(
            "high_severity_alerts" to params.remindersEnabled,
            "monthly_rhythm" to params.remindersEnabled,
            "quarterly_checkup" to params.remindersEnabled,
            "routine_firmware" to params.remindersEnabled,
            "lifecycle_eol" to params.remindersEnabled
        )
    }
    val activeRemindersCount = reminderStates.values.count { it }
    val activeFeedsCount = matchedFeeds.count { !it.isMuted }
    val mutedFeedsCount = matchedFeeds.count { it.isMuted }
    var activeSection by remember(
        params.initialExpandPreferences,
        params.initialExpandPlan,
        params.initialExpandReminders,
        params.initialExpandNetwork
    ) {
        mutableStateOf(
            when {
                params.initialExpandPreferences -> SettingsSection.PREFERENCES
                params.initialExpandPlan -> SettingsSection.PLAN
                params.initialExpandReminders -> SettingsSection.REMINDERS
                params.initialExpandNetwork -> SettingsSection.NETWORK
                else -> null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = TuesdayDimens.ScreenPaddingHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uiComponent("AccountSettingsCard"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("account-header")
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Settings & Preferences",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Configure network profiles, alert reminder preferences, matched firmware feeds, and developer diagnostics.",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            SurfaceListContainer(palette = palette) {
                SettingsMenuRow(
                    palette = palette,
                    title = SettingsSection.NETWORK.title,
                    subtitle = networkLocationName.ifBlank { null },
                    onClick = { activeSection = SettingsSection.NETWORK }
                )
                SurfaceListDivider(palette)
                SettingsMenuRow(
                    palette = palette,
                    title = SettingsSection.PLAN.title,
                    subtitle = params.entitlementTier,
                    onClick = { activeSection = SettingsSection.PLAN }
                )
                SurfaceListDivider(palette)
                SettingsMenuRow(
                    palette = palette,
                    title = SettingsSection.PREFERENCES.title,
                    subtitle = null,
                    onClick = { activeSection = SettingsSection.PREFERENCES }
                )
                SurfaceListDivider(palette)
                SettingsMenuRow(
                    palette = palette,
                    title = SettingsSection.DEV_MODE.title,
                    subtitle = if (params.isDeveloperMode) "Enabled" else "Disabled",
                    onClick = { activeSection = SettingsSection.DEV_MODE }
                )
                SurfaceListDivider(palette)
                SettingsMenuRow(
                    palette = palette,
                    title = SettingsSection.REMINDERS.title,
                    subtitle = "$activeRemindersCount active",
                    onClick = { activeSection = SettingsSection.REMINDERS }
                )
                SurfaceListDivider(palette)
                SettingsMenuRow(
                    palette = palette,
                    title = SettingsSection.FEEDS.title,
                    subtitle = "$activeFeedsCount active • $mutedFeedsCount muted",
                    onClick = { activeSection = SettingsSection.FEEDS }
                )
            }

            DiscordCommunityCard(
                palette = palette,
                onOpenInvite = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            )

            val section = activeSection
            if (section != null) {
                TuesdayModalCard(
                    palette = palette,
                    title = section.title,
                    onDismissRequest = { activeSection = null },
                    scrollable = true
                ) {
                    SurfaceListDivider(palette)

                    when (section) {
                        SettingsSection.NETWORK -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.uiSlot("account-profile-summary"))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .uiSlot("location-name-editor"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TuesdayTextField(
                                            value = draftName,
                                            onValueChange = { draftName = it },
                                            label = if (isStn) "Home Network Name" else "Location / Network Name",
                                            placeholder = if (isStn) "e.g. Our House, Main Home Wi-Fi" else "e.g. Primary Homelab, Main Rack",
                                            palette = palette,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = { onSaveNetworkName(draftName) },
                                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                            shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                                            modifier = Modifier
                                                .heightIn(min = TuesdayDimens.HitTargetStandard)
                                                .uiAction("saveNetworkName")
                                        ) {
                                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        SettingsSection.PLAN -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiSlot("account-plan-entitlement"),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isProTier1) {
                                        "Your Tuesday.sh Pro Tier 1 subscription is active. You can create unlimited network locations with live cloud sync and cross-site security telemetry."
                                    } else {
                                        "Free accounts include 1 primary network and 1 demo sandbox. Upgrade to Tuesday.sh Pro Tier 1 via Google Play to track unlimited locations with live cloud sync."
                                    },
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                if (!isProTier1) {
                                    PlanOfferSelector(
                                        plans = params.plans,
                                        selectedProductId = draftPlanSku,
                                        onSelectPlan = { sku ->
                                            draftPlanSku = sku
                                            params.onSelectPlan(sku)
                                        },
                                        palette = palette,
                                        cardModifier = { Modifier.uiAction("selectTier1Plan") }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { params.onUpgradeProTier1(draftPlanSku) },
                                            enabled = !params.isPurchasing,
                                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                            shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                                            modifier = Modifier
                                                .weight(1f)
                                                .uiAction("upgradeProTier1")
                                        ) {
                                            Text(
                                                text = if (params.isPurchasing) "Processing..." else "Upgrade to Pro Tier 1",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { params.onRestorePurchases() },
                                            enabled = !params.isRestoring,
                                            shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                                            modifier = Modifier.uiAction("restorePurchases")
                                        ) {
                                            Text(
                                                text = if (params.isRestoring) "Restoring..." else "Restore",
                                                color = palette.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                                            .background(okGreen.copy(alpha = 0.12f))
                                            .border(1.dp, okGreen.copy(alpha = 0.45f), RoundedCornerShape(TuesdayDimens.RadiusControl))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "✓ Active • Tuesday.sh Pro Tier 1",
                                                color = okGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "SKU: ${params.activeSubscriptionSku}",
                                                color = palette.textMuted,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val playUri = Uri.parse(
                                                    "https://play.google.com/store/account/subscriptions?sku=${params.activeSubscriptionSku}&package=${context.packageName}"
                                                )
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, playUri))
                                                }
                                            },
                                            shape = RoundedCornerShape(TuesdayDimens.RadiusBadge),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.uiAction("managePlaySubscription")
                                        ) {
                                            Text(
                                                text = "Manage in Play",
                                                color = palette.textPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (isDeveloperMode) {
                                        OutlinedButton(
                                            onClick = { params.onDowngradeFreeTier() },
                                            shape = RoundedCornerShape(TuesdayDimens.RadiusBadge),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .uiAction("resetEntitlementFree")
                                        ) {
                                            Text(
                                                text = "🛠️ Dev: Reset Entitlement to Free Tier",
                                                color = palette.textMuted,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                if (!params.billingStatusMessage.isNullOrBlank()) {
                                    Text(
                                        text = params.billingStatusMessage,
                                        color = palette.textPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        SettingsSection.PREFERENCES -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiSlot("preferences-toggle-list"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Customize which filter strips appear inside the Inventory card across both HOME and PRO flavors.",
                                    color = palette.textMuted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )

                                PreferenceToggleRow(
                                    title = if (isStn) "Device Health Filter Strip" else "Lifecycle State Filter Strip",
                                    description = "Shows the lifecycle state filter chips (CVE/KEV, EOL, Guessed, Update, OK) above the device list when at least 1 device is enrolled.",
                                    checked = params.showStateFilterStrip,
                                    onCheckedChange = params.onToggleShowStateFilterStrip,
                                    palette = palette,
                                    modifier = Modifier
                                        .uiSlot("pref-state-filter-row")
                                        .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                                        .background(palette.elevated)
                                        .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
                                        .padding(10.dp),
                                    switchModifier = Modifier.uiAction("toggleShowStateFilterStrip")
                                )

                                PreferenceToggleRow(
                                    title = if (isStn) "Network Lane Filter Strip" else "Trust Zone Filter Strip",
                                    description = "Shows network segmentation pills (All Zones, Main / Trusted, IoT, Guest, Infrastructure) when at least 1 device is enrolled.",
                                    checked = params.showZoneFilterStrip,
                                    onCheckedChange = params.onToggleShowZoneFilterStrip,
                                    palette = palette,
                                    modifier = Modifier
                                        .uiSlot("pref-zone-filter-row")
                                        .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                                        .background(palette.elevated)
                                        .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
                                        .padding(10.dp),
                                    switchModifier = Modifier.uiAction("toggleShowZoneFilterStrip")
                                )
                            }
                        }

                        SettingsSection.DEV_MODE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiSlot("developer-mode-card")
                                    .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                                    .background(palette.accent.copy(alpha = 0.06f))
                                    .border(1.dp, palette.accent.copy(alpha = 0.22f), RoundedCornerShape(TuesdayDimens.RadiusControl))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PreferenceToggleRow(
                                    title = if (isStn) "Household Scale Tester" else "State Simulator",
                                    description = if (isStn) {
                                        "Test different household device scales and prototype states."
                                    } else {
                                        "Enables in-app state controls & diagnostic testing tools."
                                    },
                                    checked = isDeveloperMode,
                                    onCheckedChange = onToggleDeveloperMode,
                                    palette = palette,
                                    switchModifier = Modifier.uiAction("toggleDeveloperMode")
                                )

                                if (isDeveloperMode) {
                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))

                                    PreferenceToggleRow(
                                        title = "🐞 Vision API Debug & Raw Match Logs",
                                        description = "Shows a live monospace API log view on Add Device with raw LLM matches and Gold Datalake alignment diagnostics.",
                                        checked = params.isVisionDebugEnabled,
                                        onCheckedChange = params.onToggleVisionDebugMode,
                                        palette = palette,
                                        accentTrackColor = okGreen,
                                        modifier = Modifier.uiSlot("vision-debug-toggle-row"),
                                        switchModifier = Modifier.uiAction("toggleVisionDebugMode")
                                    )

                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))

                                    PreferenceToggleRow(
                                        title = "Diagnostics",
                                        description = "Saves captured photos and debug logs locally to /sdcard/.../diagnostics/.",
                                        checked = params.isDiagnosticsEnabled,
                                        onCheckedChange = params.onToggleDiagnosticsMode,
                                        palette = palette,
                                        accentTrackColor = okGreen,
                                        modifier = Modifier.uiSlot("diagnostics-toggle-row"),
                                        switchModifier = Modifier.uiAction("toggleDiagnosticsMode")
                                    )

                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .uiSlot("dev-api-endpoint-selector"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val activePreset = ApiEndpointPreset.fromUrl(params.tuesdayApiBaseUrl)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "TUESDAY API ENDPOINT TARGET:",
                                                color = palette.textMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = activePreset?.shortBadge ?: "⚙️ CUSTOM URL",
                                                color = if (activePreset == ApiEndpointPreset.PROD_GCP) palette.accentText else okGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ApiEndpointPreset.entries.forEach { preset ->
                                                DevStateSimButton(
                                                    label = preset.shortBadge,
                                                    isSelected = activePreset == preset,
                                                    palette = palette,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = {
                                                        draftApiUrl = preset.url
                                                        params.onSetTuesdayApiBaseUrl(preset.url)
                                                    }
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TuesdayTextField(
                                                value = draftApiUrl,
                                                onValueChange = { draftApiUrl = it },
                                                label = "Endpoint URL",
                                                placeholder = "http://100.108.40.3:3000 or https://api.tuesday.sh",
                                                palette = palette,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = { params.onSetTuesdayApiBaseUrl(draftApiUrl) },
                                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                                shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                                                modifier = Modifier
                                                    .heightIn(min = TuesdayDimens.HitTargetStandard)
                                                    .uiAction("saveApiEndpoint")
                                            ) {
                                                Text("Set", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .uiSlot("dev-push-notification-simulator"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "PUSH NOTIFICATIONS & PATCH TUESDAY SIMULATOR:",
                                            color = palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .uiAction("resendInventoryNotifications")
                                                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                                .background(palette.accent.copy(alpha = 0.16f))
                                                .border(1.dp, palette.accent.copy(alpha = 0.65f), RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                                .clickable(enabled = !isResendingNotifs) {
                                                    scope.launch {
                                                        isResendingNotifs = true
                                                        pushSimFeedback = "Dispatching notifications for enrolled inventory..."
                                                        val sent = params.onResendInventoryNotifications()
                                                        isResendingNotifs = false
                                                        pushSimFeedback = "✓ Resent $sent notification(s) for enrolled inventory"
                                                    }
                                                }
                                                .padding(vertical = 9.dp, horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isResendingNotifs) {
                                                    "⏳ Resending Inventory Notifications..."
                                                } else {
                                                    "🔔 Resend Inventory Notifications (Force Dispatch)"
                                                },
                                                color = palette.accentText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .uiAction("triggerMockPatchTuesday")
                                                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                                .background(okGreen.copy(alpha = 0.14f))
                                                .border(1.dp, okGreen.copy(alpha = 0.6f), RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                                .clickable {
                                                    val shown = params.onTriggerMockPatchTuesday()
                                                    pushSimFeedback = if (shown) {
                                                        "✓ Sent Mock Patch Tuesday Bulletin & restored Rhythm card"
                                                    } else {
                                                        "⚠ Notification permission not granted (Rhythm card restored)"
                                                    }
                                                }
                                                .padding(vertical = 9.dp, horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "📅 Send Mock Patch Tuesday Bulletin",
                                                color = okGreen,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (pushSimFeedback != null) {
                                            Text(
                                                text = pushSimFeedback!!,
                                                color = palette.textPrimary,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))
                                    Text(
                                        text = "INVENTORY SEEDS:",
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        DevStateSimButton(
                                            label = "🔄 Reset (0)",
                                            isSelected = activeDeviceCount == 0,
                                            palette = palette,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                params.onResetDemoData()
                                                params.onSetSimulatedDeviceCount(0)
                                                params.onSwitchToFleetTab()
                                            }
                                        )
                                        DevStateSimButton(
                                            label = "🌱 Pea (1)",
                                            isSelected = activeDeviceCount == 1,
                                            palette = palette,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onSetSimulatedDeviceCount(1)
                                                onSwitchToFleetTab()
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        DevStateSimButton(
                                            label = "🪴 Garden (7)",
                                            isSelected = activeDeviceCount == 7,
                                            palette = palette,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onSetSimulatedDeviceCount(7)
                                                onSwitchToFleetTab()
                                            }
                                        )
                                        DevStateSimButton(
                                            label = "🌲 Forest (12)",
                                            isSelected = activeDeviceCount == 12,
                                            palette = palette,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onSetSimulatedDeviceCount(12)
                                                onSwitchToFleetTab()
                                            }
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .uiAction("purgeGoldenCorpus")
                                            .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                            .background(cveRed.copy(alpha = 0.14f))
                                            .border(1.dp, cveRed.copy(alpha = 0.55f), RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                            .clickable {
                                                scope.launch {
                                                    val removed = GoldenCorpusRecorder.purgeAllCaptures(context)
                                                    savedCorpusCount = 0
                                                    purgeFeedback = "Purged $removed capture session(s)"
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = purgeFeedback ?: "🗑️ Purge Diagnostic Captures ($savedCorpusCount Saved)",
                                            color = cveRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        SettingsSection.REMINDERS -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isStn) {
                                        "Choose which notifications ring your phone. Turning off a reminder keeps security bulletins visible inside the app without noisy alerts."
                                    } else {
                                        "Configure generalized notification channels. Disabling a category stops push alerts while preserving in-app inbox items."
                                    },
                                    color = palette.textMuted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )

                                REMINDER_DEFINITIONS.forEach { def ->
                                    val isChecked = reminderStates[def.key] ?: false
                                    PreferenceToggleRow(
                                        title = def.title,
                                        description = def.desc,
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            reminderStates[def.key] = checked
                                            if (def.key == "monthly_rhythm") {
                                                params.onToggleReminders?.invoke(checked)
                                            }
                                        },
                                        palette = palette,
                                        badgeText = if (def.isCritical) "IN-APP ALWAYS" else null,
                                        badgeColor = cveRed,
                                        accentTrackColor = okGreen,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                                            .background(palette.elevated)
                                            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
                                            .padding(10.dp),
                                        switchModifier = if (def.key == "monthly_rhythm") {
                                            Modifier.uiAction("toggleMonthlyReminders")
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                            }
                        }

                        SettingsSection.FEEDS -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                                        .background(cveRed.copy(alpha = 0.08f))
                                        .border(1.dp, cveRed.copy(alpha = 0.3f), RoundedCornerShape(TuesdayDimens.RadiusControl))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "⚠️ High Severity Guardrail Policy",
                                            color = cveRed,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "High Severity bulletins (CISA KEV disclosures and physical recalls) cannot be muted. You can snooze them or disable push reminders so they only show in-app.",
                                            color = palette.textMuted,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                Text(
                                    text = if (isStn) "MATCHED HOME DEVICE FEEDS:" else "MATCHED HARDWARE FEEDS:",
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                if (matchedFeeds.isEmpty()) {
                                    Text(
                                        text = "No feeds matched yet. Add devices to activate security feeds.",
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    matchedFeeds.forEach { feed ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                                .background(palette.elevated)
                                                .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(if (feed.isMuted) "🔕" else "📡", fontSize = 11.sp)
                                                    Text(
                                                        text = feed.name,
                                                        color = palette.textPrimary,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = feed.matchedPorts.joinToString(", "),
                                                    color = palette.textMuted,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            Button(
                                                onClick = { onToggleMuteFeed(feed.slug) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (feed.isMuted) palette.border else palette.elevated,
                                                    contentColor = if (feed.isMuted) palette.textPrimary else palette.accentText
                                                ),
                                                shape = RoundedCornerShape(TuesdayDimens.RadiusBadge),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.border(
                                                    1.dp,
                                                    if (feed.isMuted) palette.textMuted else palette.accent,
                                                    RoundedCornerShape(TuesdayDimens.RadiusBadge)
                                                )
                                            ) {
                                                Text(
                                                    text = if (feed.isMuted) "Muted" else "Mute",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal const val DISCORD_INVITE_URL = "https://discord.gg/2KEYJARecD"
private val DiscordBlurple = Color(0xFF5865F2)

@Composable
internal fun DiscordCommunityCard(
    palette: BrandPalette,
    onOpenInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceListContainer(
        palette = palette,
        modifier = modifier.uiSlot("discord-community-card")
    ) {
        TuesdayListRow(
            palette = palette,
            title = "Join our Discord Community",
            minHeight = 56.dp,
            titleMaxLines = 2,
            onClick = onOpenInvite,
            modifier = Modifier.uiAction("openDiscordInvite"),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(TuesdayDimens.HitTargetCompact)
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                        .background(DiscordBlurple),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_discord),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            subtitleContent = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Have feedback, hardware questions, or feature ideas? Come hang out and chat directly with the team.",
                        color = palette.textMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp
                    )
                    Text(
                        text = "https://discord.gg/2KEYJARecD ↗",
                        color = palette.accentText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        )
    }
}

@Composable
private fun DevStateSimButton(
    label: String,
    isSelected: Boolean,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SelectableFilterChip(
        label = label,
        selected = isSelected,
        onClick = onClick,
        palette = palette,
        modifier = modifier.uiAction("setSimulatedDeviceCount")
    )
}
