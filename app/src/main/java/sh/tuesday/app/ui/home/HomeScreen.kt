package sh.tuesday.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.pointer.pointerInput
import sh.tuesday.app.R
import sh.tuesday.app.models.*
import sh.tuesday.app.services.BillingUiState
import sh.tuesday.app.services.ConnectivityMonitor
import sh.tuesday.app.services.TuesdayBillingManager
import sh.tuesday.app.services.TuesdayNotificationManager
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.bulletins.BulletinsScreen
import sh.tuesday.app.ui.components.BulletinChecklistStep
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.components.SurfaceListContainer
import sh.tuesday.app.ui.components.SurfaceListDivider
import sh.tuesday.app.ui.components.TuesdayBottomSheet
import sh.tuesday.app.ui.components.TuesdayListRow
import sh.tuesday.app.ui.components.TuesdayModalCard
import sh.tuesday.app.ui.tabs.MiniCandleBar
import sh.tuesday.app.ui.components.NotchBulletinCard
import sh.tuesday.app.ui.components.WifiWarningOverlay
import sh.tuesday.app.ui.hacs.HacsSettingsSheet
import sh.tuesday.app.ui.tabs.RhythmScreen
import sh.tuesday.app.ui.tabs.TimelineScreen
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

enum class MainNavTab(val labelPro: String, val labelStn: String) {
    STATUS("My Net", "Home Grid"),
    BULLETINS("Bulletins", "Bulletins"),
    TIMELINE("Timeline", "Lifespan"),
    CHECKUP("Check-Up", "Check-Up"),
    ACCOUNT("Settings", "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: TuesdayRepository,
    connectivity: ConnectivityMonitor,
    notificationManager: TuesdayNotificationManager? = null,
    billingManager: TuesdayBillingManager? = null,
    onRequestNotificationPermission: () -> Unit = {},
    onNavigateToAddDevice: (mode: String?, synth: String?, query: String?, clues: String?, autoSearch: Boolean) -> Unit,
    onNavigateToEditDevice: (deviceId: String) -> Unit = {},
    pendingDestination: sh.tuesday.app.navigation.DeepLinkDestination? = null,
    onConsumePendingDestination: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val rackDevices by repository.rackDevices.collectAsState()
    val brandFlavor by repository.brandFlavor.collectAsState()
    val mutedFeedSlugs by repository.mutedFeedSlugs.collectAsState()
    val includeOkDevices by repository.includeOkDevices.collectAsState()
    val selectedZoneFilter by repository.selectedZoneFilter.collectAsState()
    val selectedPortStateFilter by repository.selectedPortStateFilter.collectAsState()
    val userNetworks by repository.userNetworks.collectAsState()
    val activeNetworkId by repository.activeNetworkId.collectAsState()
    val activeDeviceCount by repository.activeDeviceCount.collectAsState()
    val isDeveloperMode by repository.isDeveloperMode.collectAsState()
    val isVisionDebugEnabled by repository.isVisionDebugEnabled.collectAsState()
    val isDiagnosticsEnabled by repository.isDiagnosticsEnabled.collectAsState()
    val entitlementTier by repository.entitlementTier.collectAsState()
    val activeSubscriptionSku by repository.activeSubscriptionSku.collectAsState()
    val billingUiState = if (billingManager != null) {
        val state by billingManager.uiState.collectAsState()
        state
    } else {
        BillingUiState()
    }
    val remindersEnabled by repository.remindersEnabled.collectAsState()
    val dispatchSubscribed by repository.dispatchSubscribed.collectAsState()
    val bulletinDismissed by repository.bulletinDismissed.collectAsState()
    val bulletinSnoozed by repository.bulletinSnoozed.collectAsState()
    val bulletinMuted by repository.bulletinMuted.collectAsState()
    val showStateFilterStrip by repository.showStateFilterStrip.collectAsState()
    val showZoneFilterStrip by repository.showZoneFilterStrip.collectAsState()
    val hacsConfig by repository.hacsConfig.collectAsState()
    val isWifiConnected by connectivity.isWifiConnected.collectAsState()

    var activeTab by remember { mutableStateOf(MainNavTab.STATUS) }
    var expandNetworkInSettings by remember { mutableStateOf(false) }
    var expandPlanInSettings by remember { mutableStateOf(false) }
    var expandRemindersInSettings by remember { mutableStateOf(false) }
    var expandPreferencesInSettings by remember { mutableStateOf(false) }
    var inspectedDevice by remember { mutableStateOf<RackPortDevice?>(null) }
    var pendingBulletinId by remember { mutableStateOf<String?>(null) }
    var showHacsSheet by remember { mutableStateOf(false) }
    var showNetworkPicker by remember { mutableStateOf(false) }
    var networkPickerInitialUpgradePrompt by remember { mutableStateOf(false) }
    var showReferenceGuide by remember { mutableStateOf(false) }
    var showProComingSoonModal by remember { mutableStateOf(false) }
    var pendingTimelineModal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notificationManager) {
        if (notificationManager != null && repository.activeDeviceCount.value > 0) {
            repository.syncPendingNotifications(notificationManager, forceResend = false, includeAllSites = false)
        }
    }

    LaunchedEffect(pendingDestination) {
        when (pendingDestination) {
            is sh.tuesday.app.navigation.DeepLinkDestination.Tab -> {
                when (pendingDestination.tab) {
                    "status" -> activeTab = MainNavTab.STATUS
                    "bulletins" -> activeTab = MainNavTab.BULLETINS
                    "timeline" -> activeTab = MainNavTab.TIMELINE
                    "checkup" -> activeTab = MainNavTab.CHECKUP
                    "account" -> activeTab = MainNavTab.ACCOUNT
                    "account-dev" -> {
                        repository.toggleDeveloperMode(true)
                        activeTab = MainNavTab.ACCOUNT
                    }
                }
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.Device -> {
                val dev = rackDevices.find { it.id.equals(pendingDestination.deviceId, ignoreCase = true) }
                if (dev != null) {
                    inspectedDevice = dev
                }
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.AddDevice -> {
                onNavigateToAddDevice(
                    pendingDestination.mode,
                    pendingDestination.synthPreset,
                    pendingDestination.clueQuery,
                    pendingDestination.cluesCsv,
                    pendingDestination.autoSearch
                )
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.HacsSettings -> {
                showHacsSheet = true
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.NetworkPicker -> {
                networkPickerInitialUpgradePrompt = false
                showNetworkPicker = true
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.UpgradePrompt -> {
                pendingDestination.sku?.let { billingManager?.selectPlan(it) }
                if (pendingDestination.source == "account") {
                    showNetworkPicker = false
                    expandPlanInSettings = true
                    activeTab = MainNavTab.ACCOUNT
                } else {
                    networkPickerInitialUpgradePrompt = true
                    showNetworkPicker = true
                }
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.ReferenceGuide -> {
                showReferenceGuide = true
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.SeedFleet -> {
                if (pendingDestination.count > 0) {
                    repository.enableDemoMode()
                    repository.setSimulatedDeviceCount(pendingDestination.count)
                } else {
                    repository.setSimulatedDeviceCount(0)
                }
                activeTab = MainNavTab.STATUS
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.ToggleFlavor -> {
                repository.toggleBrandFlavor()
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.SetFlavor -> {
                repository.setBrandFlavor(pendingDestination.flavor)
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.ResetDemo -> {
                showHacsSheet = false
                showNetworkPicker = false
                networkPickerInitialUpgradePrompt = false
                showReferenceGuide = false
                showProComingSoonModal = false
                inspectedDevice = null
                expandNetworkInSettings = false
                expandPlanInSettings = false
                expandRemindersInSettings = false
                expandPreferencesInSettings = false
                billingManager?.clearStatusMessage()
                repository.resetDemoData()
                activeTab = MainNavTab.STATUS
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.Preferences -> {
                pendingDestination.showStateFilterStrip?.let { repository.setShowStateFilterStrip(it) }
                pendingDestination.showZoneFilterStrip?.let { repository.setShowZoneFilterStrip(it) }
                if (pendingDestination.openSettings) {
                    expandPreferencesInSettings = true
                    activeTab = MainNavTab.ACCOUNT
                }
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.VisionDebug -> {
                repository.toggleDeveloperMode(true)
                repository.toggleVisionDebugMode(pendingDestination.enabled)
                if (pendingDestination.openWizard) {
                    onNavigateToAddDevice(null, null, null, null, false)
                }
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.SetApiEndpoint -> {
                repository.toggleDeveloperMode(true)
                repository.setTuesdayApiBaseUrl(pendingDestination.url)
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.SyncNotifications -> {
                if (notificationManager != null) {
                    repository.syncPendingNotifications(
                        notificationManager = notificationManager,
                        forceResend = pendingDestination.forceResend
                    )
                }
                activeTab = MainNavTab.BULLETINS
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.TriggerMockPatchTuesday -> {
                repository.toggleDeveloperMode(true)
                if (notificationManager != null) {
                    repository.triggerMockPatchTuesdayBulletin(notificationManager)
                }
                activeTab = MainNavTab.BULLETINS
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.TimelineAction -> {
                pendingTimelineModal = pendingDestination.modal ?: "none"
                activeTab = MainNavTab.TIMELINE
                onConsumePendingDestination()
            }
            null -> {}
        }
    }

    val palette = TuesdayTheme.paletteFor(brandFlavor)
    val isStn = brandFlavor == AppBrandFlavor.SURETHING_HOME
    val shouldShowWifiOverlay = hacsConfig.requireLocalWifi && !isWifiConnected

    val activeNetwork = remember(userNetworks, activeNetworkId) {
        userNetworks.find { it.id == activeNetworkId } ?: userNetworks.firstOrNull() ?: NetworkSite(id = "net-default", name = "", devicesCount = 0, role = "Default Site")
    }
    val visibleDevices = remember(rackDevices, activeDeviceCount) {
        rackDevices.take(activeDeviceCount)
    }
    val scaleTier = remember(visibleDevices.size) {
        InventoryScaleTier.forCount(visibleDevices.size)
    }

    val ledSlots = remember(visibleDevices, selectedZoneFilter) {
        BulletinsAndInventoryEngine.build128LedMatrix(visibleDevices, selectedZoneFilter)
    }
    val actionDevices = remember(visibleDevices, includeOkDevices, selectedZoneFilter, selectedPortStateFilter) {
        BulletinsAndInventoryEngine.filterActionFirstDevices(
            devices = visibleDevices,
            includeOk = includeOkDevices,
            zoneFilter = selectedZoneFilter,
            portStateFilter = selectedPortStateFilter
        )
    }
    val bulletinsPayload = remember(brandFlavor, visibleDevices, mutedFeedSlugs) {
        BulletinsAndInventoryEngine.assembleBulletins(
            flavor = brandFlavor,
            devices = visibleDevices,
            mutedFeedSlugs = mutedFeedSlugs
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = palette.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .uiComponent("TopAppBar", "flavor=${brandFlavor.badge};network=${activeNetwork.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Brand Identity Cluster + Flavor Toggle Pill
                    Row(
                        modifier = Modifier.uiSlot("brand-identity"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isStn) {
                            TuesdayCalendarHeartIcon(
                                tint = TuesdayTheme.ProAccent,
                                modifier = Modifier
                                    .size(20.dp)
                                    .uiSlot("brand-mark")
                            )
                            Text(
                                text = "tuesday.sh",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.uiSlot("brand-title")
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .uiSlot("brand-mark"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = brandFlavor.title,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.uiSlot("brand-title")
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .widthIn(min = 54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.elevated)
                                .clickable { showProComingSoonModal = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .uiAction("toggleBrandFlavor")
                        ) {
                            Text(
                                text = brandFlavor.badge,
                                color = palette.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.uiSlot("brand-badge")
                            )
                        }
                    }

                    // Right Scope & Action Cluster: Network Switcher + HACS/Menu Button
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 8.dp)
                            .uiSlot("scope-actions"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Multi-Network Site Switcher Pill (flexible width up to remaining space, ellipses long names)
                        Surface(
                            onClick = { showNetworkPicker = true },
                            shape = RoundedCornerShape(18.dp),
                            color = palette.elevated,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .heightIn(min = 36.dp)
                                .widthIn(min = 64.dp)
                                .uiAction("openNetworkPicker")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .uiSlot("network-switcher-pill")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(palette.textMuted.copy(alpha = 0.7f))
                                )
                                Text(
                                    text = activeNetwork.name.ifBlank { "My Network" },
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // HACS & Data Management Button (Hamburger icon with status dot)
                        Surface(
                            onClick = { showHacsSheet = true },
                            shape = RoundedCornerShape(18.dp),
                            color = palette.elevated,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .uiAction("openHacsSettings")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .uiSlot("hacs-menu-pill")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .border(1.2.dp, palette.textMuted.copy(alpha = 0.7f), CircleShape)
                                )
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu and HACS Settings",
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.uiComponent("BottomNavigationBar", "activeTab=${activeTab.name}")
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    val slotName = "nav-tab-${tab.name.lowercase()}"
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = tab },
                        modifier = Modifier.uiAction("switchTab"),
                        icon = {
                            BadgedBox(
                                modifier = Modifier.uiSlot(slotName),
                                badge = {
                                    if (tab == MainNavTab.BULLETINS) {
                                        val hasIncompleteWelcomeBulletin = !bulletinDismissed && !bulletinMuted && !bulletinSnoozed &&
                                            (visibleDevices.isEmpty() || !remindersEnabled || !dispatchSubscribed)
                                        if (bulletinsPayload.inbox.isNotEmpty()) {
                                            Badge(
                                                containerColor = TuesdayTheme.LedCveRed,
                                                modifier = Modifier.uiSlot("bulletins-badge", "count=${bulletinsPayload.inbox.size}")
                                            ) {
                                                Text(
                                                    text = bulletinsPayload.inbox.size.toString(),
                                                    color = Color.White,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        } else if (hasIncompleteWelcomeBulletin) {
                                            Badge(
                                                containerColor = TuesdayTheme.LedCveRed,
                                                modifier = Modifier.uiSlot("bulletins-badge", "dot=welcome")
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        MainNavTab.STATUS -> Icons.Default.GridView
                                        MainNavTab.BULLETINS -> Icons.Default.Notifications
                                        MainNavTab.TIMELINE -> Icons.Default.Timeline
                                        MainNavTab.CHECKUP -> Icons.Default.Checklist
                                        MainNavTab.ACCOUNT -> Icons.Default.Tune
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = if (isStn) tab.labelStn else tab.labelPro,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = palette.accent,
                            selectedTextColor = palette.accent,
                            unselectedIconColor = palette.textMuted,
                            unselectedTextColor = palette.textMuted,
                            indicatorColor = palette.accent.copy(alpha = 0.14f)
                        )
                    )
                }
            }
        },
        containerColor = palette.bg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .uiSlot("content-area", "activeTab=${activeTab.name}")
        ) {
            when (activeTab) {
                MainNavTab.STATUS -> StatusMatrixAndActionListScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    scaleTier = scaleTier,
                    visibleDevices = visibleDevices,
                    ledSlots = ledSlots,
                    actionDevices = actionDevices,
                    includeOkDevices = includeOkDevices,
                    selectedZoneFilter = selectedZoneFilter,
                    selectedPortStateFilter = selectedPortStateFilter,
                    onSelectZone = { repository.setZoneFilter(it) },
                    onSelectPortState = { repository.setPortStateFilter(it) },
                    onToggleIncludeOk = { repository.toggleIncludeOkDevices() },
                    onOpenReferenceGuide = { showReferenceGuide = true },
                    onNavigateToAddDevice = { onNavigateToAddDevice(null, null, null, null, false) },
                    onAddRouter = {
                        onNavigateToAddDevice(null, null, null, "Router / Gateway", false)
                    },
                    onAddAnyDevice = {
                        onNavigateToAddDevice(null, null, null, null, false)
                    },
                    onAddCategoryDevice = { cat ->
                        onNavigateToAddDevice(null, null, null, cat.defaultIntakeClue, false)
                    },
                    onNavigateToBulletins = { activeTab = MainNavTab.BULLETINS },
                    onNavigateToAccount = { activeTab = MainNavTab.ACCOUNT },
                    onEnableReminders = {
                        onRequestNotificationPermission()
                        expandRemindersInSettings = true
                        activeTab = MainNavTab.ACCOUNT
                    },
                    remindersEnabled = remindersEnabled,
                    dispatchSubscribed = dispatchSubscribed,
                    onSetDispatchSubscribed = { repository.setDispatchSubscribed(it) },
                    bulletinDismissed = bulletinDismissed,
                    onSetBulletinDismissed = { repository.setBulletinDismissed(it) },
                    bulletinSnoozed = bulletinSnoozed,
                    onSetBulletinSnoozed = { repository.setBulletinSnoozed(it) },
                    bulletinMuted = bulletinMuted,
                    onSetBulletinMuted = { repository.setBulletinMuted(it) },
                    showStateFilterStrip = showStateFilterStrip,
                    showZoneFilterStrip = showZoneFilterStrip,
                    onLoadDemoFleet = { repository.enableDemoMode() },
                    onInspectDevice = { devId ->
                        visibleDevices.find { it.id == devId }?.let { inspectedDevice = it }
                    },
                    networks = userNetworks,
                    activeNetworkId = activeNetwork.id,
                    onSelectNetwork = { repository.selectNetwork(it) }
                )
                MainNavTab.BULLETINS -> BulletinsScreen(
                    initialBulletinId = pendingBulletinId,
                    onConsumeInitialBulletin = { pendingBulletinId = null },
                    flavor = brandFlavor,
                    palette = palette,
                    payload = bulletinsPayload,
                    onToggleMuteFeed = { repository.toggleMuteFeed(it) },
                    onConfirmModel = { repository.confirmProvisionalDeviceModel(it) },
                    onRejectModel = { repository.rejectProvisionalDeviceModel() },
                    onOpenFeedsSettings = { activeTab = MainNavTab.ACCOUNT },
                    onInspectDevice = { devId ->
                        visibleDevices.find { it.id == devId }?.let { inspectedDevice = it }
                    },
                    onNavigateToAddDevice = { onNavigateToAddDevice(null, null, null, null, false) },
                    onNavigateToAccount = { activeTab = MainNavTab.ACCOUNT },
                    onNavigateToCheckUp = { activeTab = MainNavTab.CHECKUP },
                    onEnableReminders = {
                        onRequestNotificationPermission()
                        expandRemindersInSettings = true
                        activeTab = MainNavTab.ACCOUNT
                    },
                    remindersEnabled = remindersEnabled,
                    dispatchSubscribed = dispatchSubscribed,
                    onSetDispatchSubscribed = { repository.setDispatchSubscribed(it) },
                    activeDeviceCount = visibleDevices.size,
                    bulletinDismissed = bulletinDismissed,
                    onSetBulletinDismissed = { repository.setBulletinDismissed(it) },
                    bulletinSnoozed = bulletinSnoozed,
                    onSetBulletinSnoozed = { repository.setBulletinSnoozed(it) },
                    bulletinMuted = bulletinMuted,
                    onSetBulletinMuted = { repository.setBulletinMuted(it) }
                )
                MainNavTab.TIMELINE -> {
                    val attestations by repository.attestations.collectAsState()
                    TimelineScreen(
                        flavor = brandFlavor,
                        palette = palette,
                        devices = visibleDevices,
                        attestations = attestations,
                        initialModal = pendingTimelineModal,
                        onConsumeInitialModal = { pendingTimelineModal = null },
                        onOpenReferenceGuide = { showReferenceGuide = true },
                        onInspectDevice = { devId ->
                            visibleDevices.find { it.id == devId }?.let { inspectedDevice = it }
                        },
                        onRecordAttestation = { devId, type, date, note, sessId ->
                            repository.recordAttestation(devId, type, date, note, sessId)
                        },
                        onRecordBatchAudit = { ids, date, sessId, note ->
                            repository.recordBatchAudit(ids, date, sessId, note)
                        }
                    )
                }
                MainNavTab.CHECKUP -> RhythmScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    devices = visibleDevices,
                    bulletins = bulletinsPayload.inbox,
                    onOpenReferenceGuide = { showReferenceGuide = true }
                )
                MainNavTab.ACCOUNT -> AccountSettingsScreen(
                    params = AccountSettingsParams(
                        flavor = brandFlavor,
                        palette = palette,
                        networkLocationName = activeNetwork.name,
                        activeDeviceCount = visibleDevices.size,
                        isDeveloperMode = isDeveloperMode,
                        entitlementTier = entitlementTier,
                        activeSubscriptionSku = activeSubscriptionSku,
                        plans = billingUiState.plans,
                        selectedProductId = billingUiState.selectedProductId,
                        onSelectPlan = { sku -> billingManager?.selectPlan(sku) },
                        isPurchasing = billingUiState.isPurchasing,
                        isRestoring = billingUiState.isRestoring,
                        billingStatusMessage = billingUiState.statusMessage,
                        matchedFeeds = bulletinsPayload.matchedFeeds,
                        remindersEnabled = remindersEnabled,
                        onToggleReminders = { repository.setRemindersEnabled(it) },
                        onToggleMuteFeed = { repository.toggleMuteFeed(it) },
                        onUpgradeProTier1 = { sku ->
                            if (billingManager != null) {
                                billingManager.launchPurchaseFlow(activity = activity, productId = sku)
                            } else {
                                repository.upgradeToProTier1(productId = sku)
                            }
                        },
                        onRestorePurchases = {
                            if (billingManager != null) {
                                billingManager.restorePurchases()
                            } else {
                                repository.upgradeToProTier1()
                            }
                        },
                        onDowngradeFreeTier = {
                            billingManager?.clearStatusMessage()
                            repository.downgradeToFreeTier()
                        },
                        onSaveNetworkName = { repository.renameActiveNetwork(it) },
                        onToggleDeveloperMode = { repository.toggleDeveloperMode(it) },
                        isVisionDebugEnabled = isVisionDebugEnabled,
                        onToggleVisionDebugMode = { repository.toggleVisionDebugMode(it) },
                        isDiagnosticsEnabled = isDiagnosticsEnabled,
                        onToggleDiagnosticsMode = { repository.toggleDiagnosticsMode(it) },
                        tuesdayApiBaseUrl = hacsConfig.tuesdayApiBaseUrl,
                        onSetTuesdayApiBaseUrl = { repository.setTuesdayApiBaseUrl(it) },
                        onSetSimulatedDeviceCount = { repository.setSimulatedDeviceCount(it) },
                        onSwitchToFleetTab = { activeTab = MainNavTab.STATUS },
                        initialExpandNetwork = expandNetworkInSettings,
                        initialExpandPlan = expandPlanInSettings,
                        initialExpandReminders = expandRemindersInSettings,
                        initialExpandPreferences = expandPreferencesInSettings,
                        showStateFilterStrip = showStateFilterStrip,
                        onToggleShowStateFilterStrip = { repository.setShowStateFilterStrip(it) },
                        showZoneFilterStrip = showZoneFilterStrip,
                        onToggleShowZoneFilterStrip = { repository.setShowZoneFilterStrip(it) },
                        onResendInventoryNotifications = {
                            if (notificationManager != null) {
                                repository.syncPendingNotifications(
                                    notificationManager = notificationManager,
                                    forceResend = true,
                                    includeAllSites = true
                                )
                            } else {
                                0
                            }
                        },
                        onTriggerMockPatchTuesday = {
                            if (notificationManager != null) {
                                repository.triggerMockPatchTuesdayBulletin(notificationManager)
                            } else {
                                false
                            }
                        },
                        onResetDemoData = { repository.resetDemoData() }
                    )
                )
            }

            WifiWarningOverlay(
                isVisible = shouldShowWifiOverlay,
                hacsUrl = hacsConfig.baseUrl,
                onDisableRequirement = {
                    repository.updateHacsConfig(hacsConfig.copy(requireLocalWifi = false))
                    connectivity.setSimulateDisconnected(false)
                }
            )
        }
    }

    inspectedDevice?.let { device ->
        TuesdayBottomSheet(
            palette = palette,
            onDismissRequest = { inspectedDevice = null }
        ) {
            DeviceInspectorBottomSheet(
                device = device,
                palette = palette,
                isStn = isStn,
                onApplyPatch = {
                    repository.markDeviceUpdatesApplied(device.id)
                    inspectedDevice = null
                },
                onEditDevice = {
                    inspectedDevice = null
                    onNavigateToEditDevice(device.id)
                },
                onNavigateToBulletins = { bulletinId ->
                    pendingBulletinId = bulletinId
                    inspectedDevice = null
                    activeTab = MainNavTab.BULLETINS
                },
                onClose = { inspectedDevice = null }
            )
        }
    }

    if (showHacsSheet) {
        HacsSettingsSheet(
            entitlementTier = entitlementTier,
            activeNetworkName = activeNetwork.name,
            onOpenUpgradeModal = {
                networkPickerInitialUpgradePrompt = true
                showNetworkPicker = true
            },
            onOpenNetworkPicker = {
                networkPickerInitialUpgradePrompt = false
                showNetworkPicker = true
            },
            onExportBackupJson = { repository.exportInventoryBackupJson() },
            onImportBackupJson = { jsonStr -> repository.importInventoryBackupJson(jsonStr) },
            onDismiss = { showHacsSheet = false }
        )
    }

    if (showNetworkPicker) {
        NetworkPickerModalDialog(
            networks = userNetworks,
            activeNetworkId = activeNetworkId,
            palette = palette,
            entitlementTier = entitlementTier,
            flavor = brandFlavor,
            plans = billingUiState.plans,
            selectedProductId = billingUiState.selectedProductId,
            onSelectPlan = { sku -> billingManager?.selectPlan(sku) },
            isPurchasing = billingUiState.isPurchasing,
            billingStatusMessage = billingUiState.statusMessage,
            initialShowUpgradePrompt = networkPickerInitialUpgradePrompt,
            onSelectNetwork = { id ->
                repository.selectNetwork(id)
                networkPickerInitialUpgradePrompt = false
                showNetworkPicker = false
            },
            onCreateNetwork = { name ->
                repository.createNetwork(name)
                networkPickerInitialUpgradePrompt = false
                showNetworkPicker = false
            },
            onUpgradeMultiSite = { sku ->
                if (billingManager != null) {
                    billingManager.launchPurchaseFlow(activity = activity, productId = sku)
                } else {
                    repository.upgradeToProTier1(productId = sku)
                }
            },
            onOpenNetworkSettings = {
                expandNetworkInSettings = true
                activeTab = MainNavTab.ACCOUNT
            },
            onDismiss = {
                networkPickerInitialUpgradePrompt = false
                showNetworkPicker = false
            }
        )
    }

    if (showReferenceGuide) {
        ReferenceGuideModalDialog(
            flavor = brandFlavor,
            palette = palette,
            onDismiss = { showReferenceGuide = false }
        )
    }

    if (showProComingSoonModal) {
        TuesdayModalCard(
            palette = palette,
            title = "Coming Soon",
            subtitle = "Tuesday.sh (PRO) and SureThingNet (HOME) pair deep homelab telemetry with a calm household view so technical operators and families can share one inventory without the jargon.",
            headerTrailing = {
                MonospaceBadge(
                    text = "PRO ↔ HOME",
                    color = palette.accent
                )
            },
            scrollable = true,
            onDismissRequest = { showProComingSoonModal = false }
        ) {
            SurfaceListDivider(palette)

            SurfaceListContainer(palette = palette) {
                TuesdayListRow(
                    palette = palette,
                    title = "SureThingNet (HOME) Companion View",
                    trailingContent = {
                        MonospaceBadge(text = "v1.2", color = palette.accent)
                    },
                    subtitleContent = {
                        Text(
                            text = "Translates technical CVEs and PURLs into plain-English CPSC Safety Recalls, End-of-Life (EOL) replacement alerts, and guided Seasonal Home Check-Ups.",
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp
                        )
                    }
                )
                SurfaceListDivider(palette)
                TuesdayListRow(
                    palette = palette,
                    title = "Family Bridge & Household Sharing",
                    trailingContent = {
                        MonospaceBadge(text = "v1.1", color = palette.accent)
                    },
                    subtitleContent = {
                        Text(
                            text = "Invite a partner or family member with a read-only household view so everyone knows when smart-home, kitchen, or nursery gear needs an update or recall check.",
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp
                        )
                    }
                )
                SurfaceListDivider(palette)
                TuesdayListRow(
                    palette = palette,
                    title = "Multi-Site Homelab & Remote Care",
                    onClick = {
                        showProComingSoonModal = false
                        showNetworkPicker = true
                    },
                    trailingContent = {
                        MonospaceBadge(text = "LIVE", color = TuesdayTheme.LedOkGreen)
                    },
                    subtitleContent = {
                        Text(
                            text = "Free accounts include 1 primary network and 1 demo sandbox. Upgrade to Pro Tier 1 to unlock unlimited network locations and manage your primary rack alongside a parent's home or cabin Wi-Fi.",
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp
                        )
                    }
                )
            }
        }
    }
}
