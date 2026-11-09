package sh.tuesday.app.ui.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.MatchedFeedView
import sh.tuesday.app.services.GoldenCorpusRecorder
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
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
    val matchedFeeds: List<MatchedFeedView> = emptyList(),
    val remindersEnabled: Boolean = false,
    val onToggleReminders: ((Boolean) -> Unit)? = null,
    val onToggleMuteFeed: (String) -> Unit = {},
    val onUpgradeProTier1: () -> Unit = {},
    val onSaveNetworkName: (String) -> Unit = {},
    val onToggleDeveloperMode: (Boolean) -> Unit = {},
    val isVisionDebugEnabled: Boolean = true,
    val onToggleVisionDebugMode: (Boolean) -> Unit = {},
    val tuesdayApiBaseUrl: String = "http://100.108.40.3:3000",
    val onSetTuesdayApiBaseUrl: (String) -> Unit = {},
    val onSetSimulatedDeviceCount: (Int) -> Unit = {},
    val onSwitchToFleetTab: () -> Unit = {},
    val onResendInventoryNotifications: suspend () -> Int = { 0 },
    val onTriggerMockPatchTuesday: () -> Boolean = { false }
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

    // Accordion Expansion States
    var isNetworkOpen by remember { mutableStateOf(false) }
    var isDevModeOpen by remember(isDeveloperMode) { mutableStateOf(isDeveloperMode) }
    var isRemindersOpen by remember { mutableStateOf(false) }
    var isFeedsOpen by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uiComponent("AccountSettingsCard"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header (Unboxed)
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

            // UNBOXED ACCORDION LIST
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = palette.border.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                // ==================== 1. NETWORK ACCORDION ITEM ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = palette.border.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(0.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isNetworkOpen = !isNetworkOpen }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🌐", fontSize = 16.sp)
                            Text(
                                text = "Network",
                                color = palette.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (networkLocationName.isNotBlank()) {
                                Text(
                                    text = networkLocationName,
                                    color = palette.accent,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = if (isNetworkOpen) "▾" else "›",
                            color = palette.textMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(visible = isNetworkOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Hidden slot retained for cross-platform parity
                            Box(modifier = Modifier.uiSlot("account-profile-summary"))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiSlot("location-name-editor"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isStn) "HOME NETWORK NAME" else "LOCATION / NETWORK NAME",
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = draftName,
                                        onValueChange = { draftName = it },
                                        singleLine = true,
                                        placeholder = {
                                            Text(
                                                if (isStn) "e.g. Our House, Main Home Wi-Fi" else "e.g. Primary Homelab, Main Rack",
                                                fontSize = 12.5.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = palette.textPrimary,
                                            unfocusedTextColor = palette.textPrimary,
                                            focusedBorderColor = palette.accent,
                                            unfocusedBorderColor = palette.border
                                        )
                                    )
                                    Button(
                                        onClick = { onSaveNetworkName(draftName) },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.uiAction("saveNetworkName")
                                    ) {
                                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== 2. DEVELOPER MODE ACCORDION ITEM ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = palette.border.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(0.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDevModeOpen = !isDevModeOpen }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🛠️", fontSize = 16.sp)
                            Text(
                                text = "Developer Mode",
                                color = palette.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isDevModeOpen) "▾" else "›",
                            color = palette.textMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(visible = isDevModeOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiSlot("developer-mode-card")
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.accent.copy(alpha = 0.06f))
                                    .border(1.dp, palette.accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isStn) "Household Scale Tester" else "State Simulator",
                                            color = palette.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isStn) {
                                                "Test different household device scales and prototype states."
                                            } else {
                                                "Enables in-app state controls & diagnostic testing tools."
                                            },
                                            color = palette.textMuted,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                    Switch(
                                        checked = isDeveloperMode,
                                        onCheckedChange = onToggleDeveloperMode,
                                        modifier = Modifier.uiAction("toggleDeveloperMode"),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = palette.accent,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = palette.border
                                        )
                                    )
                                }

                                if (isDeveloperMode) {
                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))

                                    // Vision API Debug Console Toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .uiSlot("vision-debug-toggle-row"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "🐞 Vision API Debug & Raw Match Logs",
                                                color = palette.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Shows a live monospace API log view on Add Device with raw LLM matches and Gold Datalake alignment diagnostics.",
                                                color = palette.textMuted,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                        Switch(
                                            checked = params.isVisionDebugEnabled,
                                            onCheckedChange = params.onToggleVisionDebugMode,
                                            modifier = Modifier.uiAction("toggleVisionDebugMode"),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = TuesdayTheme.HealthyGreen,
                                                uncheckedThumbColor = Color.White,
                                                uncheckedTrackColor = palette.border
                                            )
                                        )
                                    }

                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))

                                    // API Endpoint Target Switcher (Local vs Prod GCP)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .uiSlot("dev-api-endpoint-selector"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val activePreset = sh.tuesday.app.models.ApiEndpointPreset.fromUrl(params.tuesdayApiBaseUrl)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "TUESDAY API ENDPOINT TARGET:",
                                                color = palette.textMuted,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = activePreset?.shortBadge ?: "⚙️ CUSTOM URL",
                                                color = if (activePreset == sh.tuesday.app.models.ApiEndpointPreset.PROD_GCP) TuesdayTheme.HacsCyan else TuesdayTheme.HealthyGreen,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            sh.tuesday.app.models.ApiEndpointPreset.entries.forEach { preset ->
                                                val isSel = activePreset == preset
                                                DevStateSimButton(
                                                    label = preset.shortBadge,
                                                    isSelected = isSel,
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
                                            OutlinedTextField(
                                                value = draftApiUrl,
                                                onValueChange = { draftApiUrl = it },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.5.sp,
                                                    color = palette.textPrimary
                                                ),
                                                placeholder = {
                                                    Text("http://100.108.40.3:3000 or https://api.tuesday.sh", fontSize = 11.sp)
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = palette.accent,
                                                    unfocusedBorderColor = palette.border
                                                )
                                            )
                                            Button(
                                                onClick = { params.onSetTuesdayApiBaseUrl(draftApiUrl) },
                                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.uiAction("saveApiEndpoint")
                                            ) {
                                                Text("Set", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))

                                    // Push Notification & Patch Tuesday Simulator
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .uiSlot("dev-push-notification-simulator"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "PUSH NOTIFICATIONS & PATCH TUESDAY SIMULATOR:",
                                            color = palette.textMuted,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .uiAction("resendInventoryNotifications")
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(palette.accent.copy(alpha = 0.16f))
                                                .border(1.dp, palette.accent.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
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
                                                color = palette.accent,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .uiAction("triggerMockPatchTuesday")
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TuesdayTheme.HealthyGreen.copy(alpha = 0.14f))
                                                .border(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
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
                                                color = TuesdayTheme.HealthyGreen,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (pushSimFeedback != null) {
                                            Text(
                                                text = pushSimFeedback!!,
                                                color = palette.textPrimary,
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))
                                    Text(
                                        text = "INVENTORY SEEDS:",
                                        color = palette.textMuted,
                                        fontSize = 9.5.sp,
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
                                                onSetSimulatedDeviceCount(0)
                                                onSwitchToFleetTab()
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

                                    // Offline Golden Corpus Purge Control
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .uiAction("purgeGoldenCorpus")
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(TuesdayTheme.LedCveRed.copy(alpha = 0.14f))
                                            .border(1.dp, TuesdayTheme.LedCveRed.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
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
                                            text = purgeFeedback ?: "🗑️ Purge Golden Corpus Captures ($savedCorpusCount Saved)",
                                            color = TuesdayTheme.LedCveRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== 3. REMINDERS ACCORDION ITEM ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = palette.border.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(0.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isRemindersOpen = !isRemindersOpen }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🔔", fontSize = 16.sp)
                            Text(
                                text = "Reminders",
                                color = palette.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$activeRemindersCount of ${REMINDER_DEFINITIONS.size} Active",
                                color = TuesdayTheme.LedOkGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = if (isRemindersOpen) "▾" else "›",
                            color = palette.textMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(visible = isRemindersOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.elevated)
                                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = def.title,
                                                color = palette.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (def.isCritical) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(TuesdayTheme.LedCveRed.copy(alpha = 0.14f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "IN-APP ALWAYS",
                                                        color = TuesdayTheme.LedCveRed,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = def.desc,
                                            color = palette.textMuted,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            reminderStates[def.key] = checked
                                            if (def.key == "monthly_rhythm") {
                                                params.onToggleReminders?.invoke(checked)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = TuesdayTheme.LedOkGreen,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = palette.border
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 4. FEEDS ACCORDION ITEM ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = palette.border.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(0.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFeedsOpen = !isFeedsOpen }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("📡", fontSize = 16.sp)
                            Text(
                                text = "Feeds",
                                color = palette.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$activeFeedsCount Active • $mutedFeedsCount Muted",
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = if (isFeedsOpen) "▾" else "›",
                            color = palette.textMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(visible = isFeedsOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // High Severity Advisory Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TuesdayTheme.LedCveRed.copy(alpha = 0.08f))
                                    .border(1.dp, TuesdayTheme.LedCveRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = "⚠️ High Severity Guardrail Policy",
                                        color = TuesdayTheme.LedCveRed,
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
                                fontSize = 10.sp,
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
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.elevated)
                                            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
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
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Button(
                                            onClick = { onToggleMuteFeed(feed.slug) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (feed.isMuted) palette.border else palette.elevated,
                                                contentColor = if (feed.isMuted) palette.textPrimary else palette.accent
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.border(
                                                1.dp,
                                                if (feed.isMuted) palette.textMuted else palette.accent,
                                                RoundedCornerShape(6.dp)
                                            )
                                        ) {
                                            Text(
                                                text = if (feed.isMuted) "Muted" else "Mute",
                                                fontSize = 10.5.sp,
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

@Composable
private fun DevStateSimButton(
    label: String,
    isSelected: Boolean,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .uiAction("setSimulatedDeviceCount")
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) palette.accent.copy(alpha = 0.2f) else palette.surface)
            .border(1.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) palette.accent else palette.textPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
