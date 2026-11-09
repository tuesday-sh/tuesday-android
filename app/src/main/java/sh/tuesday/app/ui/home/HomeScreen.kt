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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.R
import sh.tuesday.app.models.*
import sh.tuesday.app.services.ConnectivityMonitor
import sh.tuesday.app.services.TuesdayNotificationManager
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.bulletins.BulletinsScreen
import sh.tuesday.app.ui.components.BulletinChecklistStep
import sh.tuesday.app.ui.components.CandleGraphCanvas
import sh.tuesday.app.ui.components.NotchBulletinCard
import sh.tuesday.app.ui.components.WifiWarningOverlay
import sh.tuesday.app.ui.hacs.HacsSettingsSheet
import sh.tuesday.app.ui.tabs.SeasonalCheckupScreen
import sh.tuesday.app.ui.tabs.SwimlaneTimelineScreen
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

enum class MainNavTab(val labelPro: String, val labelStn: String) {
    STATUS("My Net", "Home Grid"),
    BULLETINS("Bulletins", "Bulletins"),
    TIMELINE("Timeline", "Lifespan"),
    CHECKUP("Check-Up", "Check-Up"),
    ACCOUNT("Account", "Account")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: TuesdayRepository,
    connectivity: ConnectivityMonitor,
    notificationManager: TuesdayNotificationManager? = null,
    onNavigateToAddDevice: (String?, String?) -> Unit,
    pendingDestination: sh.tuesday.app.navigation.DeepLinkDestination? = null,
    onConsumePendingDestination: () -> Unit = {}
) {
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
    val entitlementTier by repository.entitlementTier.collectAsState()
    val remindersEnabled by repository.remindersEnabled.collectAsState()
    val dispatchSubscribed by repository.dispatchSubscribed.collectAsState()
    val bulletinDismissed by repository.bulletinDismissed.collectAsState()
    val bulletinSnoozed by repository.bulletinSnoozed.collectAsState()
    val bulletinMuted by repository.bulletinMuted.collectAsState()
    val hacsConfig by repository.hacsConfig.collectAsState()
    val isWifiConnected by connectivity.isWifiConnected.collectAsState()
    val isSimulatingWifi by connectivity.simulateWifiDisconnected.collectAsState()

    var activeTab by remember { mutableStateOf(MainNavTab.STATUS) }
    var expandNetworkInSettings by remember { mutableStateOf(false) }
    var inspectedDevice by remember { mutableStateOf<RackPortDevice?>(null) }
    var showHacsSheet by remember { mutableStateOf(false) }
    var showNetworkPicker by remember { mutableStateOf(false) }
    var showReferenceGuide by remember { mutableStateOf(false) }

    LaunchedEffect(notificationManager) {
        if (notificationManager != null) {
            repository.syncPendingNotifications(notificationManager, forceResend = false)
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
                onNavigateToAddDevice(pendingDestination.mode, pendingDestination.synthPreset)
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.HacsSettings -> {
                showHacsSheet = true
                onConsumePendingDestination()
            }
            sh.tuesday.app.navigation.DeepLinkDestination.NetworkPicker -> {
                showNetworkPicker = true
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
            sh.tuesday.app.navigation.DeepLinkDestination.ResetDemo -> {
                repository.resetDemoData()
                activeTab = MainNavTab.STATUS
                onConsumePendingDestination()
            }
            is sh.tuesday.app.navigation.DeepLinkDestination.VisionDebug -> {
                repository.toggleDeveloperMode(true)
                repository.toggleVisionDebugMode(pendingDestination.enabled)
                if (pendingDestination.openWizard) {
                    onNavigateToAddDevice(null, null)
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
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!isStn) {
                            TuesdayCalendarHeartIcon(
                                tint = TuesdayTheme.ProAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "tuesday.sh",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = brandFlavor.title,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Surface(
                            onClick = { repository.toggleBrandFlavor() },
                            shape = RoundedCornerShape(6.dp),
                            color = palette.elevated,
                            modifier = Modifier.uiAction("toggleBrandFlavor")
                        ) {
                            Text(
                                text = brandFlavor.badge,
                                color = palette.accent,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Multi-Network Site Switcher Pill
                    val isDemoActive = activeNetwork.id == "net-demo"
                    Surface(
                        onClick = { showNetworkPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.elevated,
                        border = if (isDemoActive) androidx.compose.foundation.BorderStroke(1.dp, TuesdayTheme.LedOkGreen.copy(alpha = 0.5f)) else null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .uiAction("openNetworkPicker")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (isDemoActive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(TuesdayTheme.LedOkGreen)
                                )
                            }
                            Text(
                                text = if (isDemoActive) "✨ Demo Network" else activeNetwork.name.ifBlank { "My Network" },
                                color = palette.textPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "▾",
                                color = palette.textMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // HACS Connectivity Pill
                    Surface(
                        onClick = { showHacsSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.elevated,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .uiAction("openHacsSettings")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hacsConfig.isConnected) TuesdayTheme.LedOkGreen else TuesdayTheme.LedCveRed)
                            )
                            Text(
                                text = "HACS",
                                color = palette.accent,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.uiComponent("BottomNavigationBar")
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = tab },
                        modifier = Modifier.uiAction("switchTab"),
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == MainNavTab.BULLETINS && bulletinsPayload.inbox.isNotEmpty()) {
                                        Badge(containerColor = TuesdayTheme.LedCveRed) {
                                            Text(
                                                text = bulletinsPayload.inbox.size.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
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
                                        MainNavTab.ACCOUNT -> Icons.Default.Person
                                    },
                                    contentDescription = tab.name,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = if (isStn) tab.labelStn else tab.labelPro,
                                fontSize = 9.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
                    onNavigateToAddDevice = { onNavigateToAddDevice(null, null) },
                    onNavigateToBulletins = { activeTab = MainNavTab.BULLETINS },
                    onNavigateToAccount = { activeTab = MainNavTab.ACCOUNT },
                    remindersEnabled = remindersEnabled,
                    dispatchSubscribed = dispatchSubscribed,
                    onSetDispatchSubscribed = { repository.setDispatchSubscribed(it) },
                    bulletinDismissed = bulletinDismissed,
                    onSetBulletinDismissed = { repository.setBulletinDismissed(it) },
                    bulletinSnoozed = bulletinSnoozed,
                    onSetBulletinSnoozed = { repository.setBulletinSnoozed(it) },
                    bulletinMuted = bulletinMuted,
                    onSetBulletinMuted = { repository.setBulletinMuted(it) },
                    onLoadDemoFleet = { repository.enableDemoMode() },
                    onInspectDevice = { devId ->
                        visibleDevices.find { it.id == devId }?.let { inspectedDevice = it }
                    }
                )
                MainNavTab.BULLETINS -> BulletinsScreen(
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
                    onNavigateToAddDevice = { onNavigateToAddDevice(null, null) },
                    onNavigateToAccount = { activeTab = MainNavTab.ACCOUNT },
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
                MainNavTab.TIMELINE -> SwimlaneTimelineScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    devices = visibleDevices
                )
                MainNavTab.CHECKUP -> SeasonalCheckupScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    devices = visibleDevices
                )
                MainNavTab.ACCOUNT -> AccountSettingsScreen(
                    params = AccountSettingsParams(
                        flavor = brandFlavor,
                        palette = palette,
                        networkLocationName = activeNetwork.name,
                        activeDeviceCount = visibleDevices.size,
                        isDeveloperMode = isDeveloperMode,
                        entitlementTier = entitlementTier,
                        matchedFeeds = bulletinsPayload.matchedFeeds,
                        remindersEnabled = remindersEnabled,
                        onToggleReminders = { repository.setRemindersEnabled(it) },
                        onToggleMuteFeed = { repository.toggleMuteFeed(it) },
                        onUpgradeProTier1 = { repository.upgradeToProTier1() },
                        onSaveNetworkName = { repository.renameActiveNetwork(it) },
                        onToggleDeveloperMode = { repository.toggleDeveloperMode(it) },
                        isVisionDebugEnabled = isVisionDebugEnabled,
                        onToggleVisionDebugMode = { repository.toggleVisionDebugMode(it) },
                        tuesdayApiBaseUrl = hacsConfig.tuesdayApiBaseUrl,
                        onSetTuesdayApiBaseUrl = { repository.setTuesdayApiBaseUrl(it) },
                        onSetSimulatedDeviceCount = { repository.setSimulatedDeviceCount(it) },
                        onSwitchToFleetTab = { activeTab = MainNavTab.STATUS },
                        initialExpandNetwork = expandNetworkInSettings,
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
                        }
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
        ModalBottomSheet(
            onDismissRequest = { inspectedDevice = null },
            containerColor = palette.surface
        ) {
            DeviceInspectorBottomSheet(
                device = device,
                palette = palette,
                isStn = isStn,
                onApplyPatch = {
                    repository.markDeviceUpdatesApplied(device.id)
                    inspectedDevice = null
                },
                onClose = { inspectedDevice = null }
            )
        }
    }

    if (showHacsSheet) {
        HacsSettingsSheet(
            config = hacsConfig,
            simulateWifiDisconnected = isSimulatingWifi,
            onSaveConfig = { repository.updateHacsConfig(it) },
            onToggleSimulateWifi = { connectivity.setSimulateDisconnected(it) },
            onResetDemoData = {
                repository.resetDemoData()
                connectivity.setSimulateDisconnected(false)
            },
            onDismiss = { showHacsSheet = false }
        )
    }

    if (showNetworkPicker) {
        NetworkPickerModalDialog(
            networks = userNetworks,
            activeNetworkId = activeNetworkId,
            palette = palette,
            onSelectNetwork = { id ->
                repository.selectNetwork(id)
                showNetworkPicker = false
            },
            onCreateNetwork = { name ->
                repository.createNetwork(name)
                showNetworkPicker = false
            },
            onOpenNetworkSettings = {
                expandNetworkInSettings = true
                activeTab = MainNavTab.ACCOUNT
            },
            onDismiss = { showNetworkPicker = false }
        )
    }

    if (showReferenceGuide) {
        ReferenceGuideModalDialog(
            flavor = brandFlavor,
            palette = palette,
            onDismiss = { showReferenceGuide = false }
        )
    }
}

@Composable
private fun StatusMatrixAndActionListScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    scaleTier: InventoryScaleTier,
    visibleDevices: List<RackPortDevice>,
    ledSlots: List<LedSlot>,
    actionDevices: List<RackPortDevice>,
    includeOkDevices: Boolean,
    selectedZoneFilter: TrustZone?,
    selectedPortStateFilter: PortLifecycleState?,
    onSelectZone: (TrustZone?) -> Unit,
    onSelectPortState: (PortLifecycleState?) -> Unit,
    onToggleIncludeOk: () -> Unit,
    onOpenReferenceGuide: () -> Unit,
    onNavigateToAddDevice: () -> Unit,
    onNavigateToBulletins: () -> Unit,
    onNavigateToAccount: () -> Unit = {},
    remindersEnabled: Boolean = false,
    dispatchSubscribed: Boolean = false,
    onSetDispatchSubscribed: (Boolean) -> Unit = {},
    bulletinDismissed: Boolean = false,
    onSetBulletinDismissed: (Boolean) -> Unit = {},
    bulletinSnoozed: Boolean = false,
    onSetBulletinSnoozed: (Boolean) -> Unit = {},
    bulletinMuted: Boolean = false,
    onSetBulletinMuted: (Boolean) -> Unit = {},
    onLoadDemoFleet: () -> Unit,
    onInspectDevice: (String) -> Unit
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val okCount = visibleDevices.count { it.portState == PortLifecycleState.OK }
    val cveCount = visibleDevices.count { it.portState == PortLifecycleState.CVE_KEV }
    val eolCount = visibleDevices.count { it.portState == PortLifecycleState.EOL }
    val guessedCount = visibleDevices.count { it.portState == PortLifecycleState.GUESSED }
    val updateCount = visibleDevices.count { it.portState == PortLifecycleState.UPDATE }
    val actionCount = visibleDevices.size - okCount

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Welcome Bulletin Card (kept visible on home screen above inventory for continuity)
        item {
            WelcomeCard(
                palette = palette,
                activeDeviceCount = visibleDevices.size,
                remindersEnabled = remindersEnabled,
                dispatchSubscribed = dispatchSubscribed,
                onSetDispatchSubscribed = onSetDispatchSubscribed,
                bulletinDismissed = bulletinDismissed,
                onSetBulletinDismissed = onSetBulletinDismissed,
                bulletinSnoozed = bulletinSnoozed,
                onSetBulletinSnoozed = onSetBulletinSnoozed,
                bulletinMuted = bulletinMuted,
                onSetBulletinMuted = onSetBulletinMuted,
                onAddRouter = onNavigateToAddDevice,
                onAddAnyDevice = onNavigateToAddDevice,
                onLoadDemoFleet = onLoadDemoFleet,
                onOpenReferenceGuide = onOpenReferenceGuide,
                onNavigateToBulletins = onNavigateToBulletins,
                onNavigateToAccount = onNavigateToAccount
            )
        }

        if (scaleTier != InventoryScaleTier.DAY_ZERO) {
            // 1. Inventory Card (8-Port Category Strip + 16x8 LED Matrix + Botanical Overlay)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiComponent("InventoryMatrixCard", "tier=${scaleTier.id};active=${visibleDevices.size}")
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header: "Inventory" + Summary Badge + "+ Add Device" CTA + "?" Reference Guide button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("header"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Inventory",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.uiSlot("title")
                            )
                            if (scaleTier.showSummaryBadge) {
                                Text(
                                    text = "$okCount OK · $actionCount Action",
                                    color = palette.accent,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.uiSlot("summary-badge")
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Always-present "+ Add Device" CTA button
                            Row(
                                modifier = Modifier
                                    .uiSlot("add-device-cta")
                                    .uiAction("openAddDevice")
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.accent)
                                    .clickable { onNavigateToAddDevice() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Add Device",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "+ Add Device",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .uiSlot("help-button")
                                    .uiAction("openReferenceGuide")
                                    .clip(CircleShape)
                                    .background(palette.elevated)
                                    .border(1.dp, palette.border, CircleShape)
                                    .clickable { onOpenReferenceGuide() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Unified ubnt-chassis container: 8-Port Diagram Strip + 16x8 LED Matrix + PNW Pixel Art Overlay
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isStn) Color(0xFFF1F5F9) else Color(0xFF0B0D11))
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 8-Port Hardware Category Faceplate Strip (C1..C8) for >= 8 devices
                        if (scaleTier.showPortDiagramStrip) {
                            PortCategoryFaceplateStrip(
                                devices = visibleDevices,
                                isStn = isStn,
                                onInspectCategoryDevice = { devId ->
                                    if (devId != null) onInspectDevice(devId) else onNavigateToAddDevice()
                                }
                            )
                            HorizontalDivider(color = palette.border, thickness = 1.dp)
                        }

                        // 16 columns x 8 rows compact LED grid with bottom-snapped PNW monochrome pixel art overlay
                        val rows = ledSlots.chunked(16)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiComponent("LedMatrix128Grid")
                                .uiSlot("led-grid-128")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                rows.forEach { rowSlots ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        rowSlots.forEach { slot ->
                                            val ledColor = TuesdayTheme.ledColorFor(slot.state, isStn)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .uiAction("inspectDevice")
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(
                                                        ledColor.copy(
                                                            alpha = when (slot.state) {
                                                                PortLifecycleState.UNASSIGNED -> 0.55f
                                                                PortLifecycleState.OK -> 0.85f
                                                                else -> 1f
                                                            }
                                                        )
                                                    )
                                                    .clickable(enabled = slot.deviceId != null) {
                                                        slot.deviceId?.let { onInspectDevice(it) }
                                                    }
                                            )
                                        }
                                        repeat((16 - rowSlots.size).coerceAtLeast(0)) {
                                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }

                            // Bottom-snapped monochrome pixel art overlay (Sprout / 3 Pines / 9-Tree PNW Horizon)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .uiComponent("MatrixBotanicalPixelArtOverlay")
                                    .uiSlot("botanical-overlay")
                            ) {
                                MatrixBotanicalPixelArtOverlay(
                                    scaleTier = scaleTier,
                                    activeCount = visibleDevices.size,
                                    isStn = isStn
                                )
                            }
                        }
                    }

                    // 5-State Color Legend Row (shown when filter chips are active)
                    if (scaleTier.showFilterChips) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiSlot("legend-row")
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LedLegendDot("OK ($okCount)", TuesdayTheme.LedOkGreen, palette)
                            LedLegendDot("CVE/KEV ($cveCount)", TuesdayTheme.LedCveRed, palette)
                            LedLegendDot("EOL ($eolCount)", TuesdayTheme.LedEolAmber, palette)
                            LedLegendDot("Guessed ($guessedCount)", TuesdayTheme.LedGuessedGrey, palette)
                            LedLegendDot("Update ($updateCount)", TuesdayTheme.LedUpdateBlue, palette)
                        }
                    }

                    // Trust Zone Filter Pills (shown at >= 8 devices)
                    if (scaleTier.showTrustZonePills) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiSlot("zone-filter-strip")
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ZoneFilterPill(
                                label = "All Zones",
                                selected = selectedZoneFilter == null,
                                palette = palette,
                                onClick = { onSelectZone(null) }
                            )
                            TrustZone.entries.forEach { zone ->
                                ZoneFilterPill(
                                    label = if (isStn) zone.stnLabel else "${zone.shortCode} ${zone.stnLabel}",
                                    selected = selectedZoneFilter == zone,
                                    palette = palette,
                                    onClick = { onSelectZone(zone) }
                                )
                            }
                        }
                    }
                }
            }

            // 1B. Tracked Hardware Profiles Strip (Forest Tier >= 8 devices on STN-Pro)
            if (scaleTier == InventoryScaleTier.FOREST && !isStn && visibleDevices.isNotEmpty()) {
                item {
                    TrackedHardwareProfilesCard(
                        devices = visibleDevices,
                        palette = palette,
                        onInspectDevice = onInspectDevice
                    )
                }
            }

            // 2. Top-of-Inventory State Filter Chips (for >= 2 devices)
            if (scaleTier.showFilterChips) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiSlot("state-filter-strip")
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StateFilterChip(
                                label = "All (${visibleDevices.size})",
                                dotColor = null,
                                selected = selectedPortStateFilter == null && includeOkDevices,
                                palette = palette,
                                onClick = {
                                    onSelectPortState(null)
                                    if (!includeOkDevices) onToggleIncludeOk()
                                }
                            )
                            StateFilterChip(
                                label = "CVE / KEV ($cveCount)",
                                dotColor = TuesdayTheme.LedCveRed,
                                selected = selectedPortStateFilter == PortLifecycleState.CVE_KEV,
                                palette = palette,
                                onClick = {
                                    onSelectPortState(
                                        if (selectedPortStateFilter == PortLifecycleState.CVE_KEV) null
                                        else PortLifecycleState.CVE_KEV
                                    )
                                }
                            )
                            StateFilterChip(
                                label = "EOL ($eolCount)",
                                dotColor = TuesdayTheme.LedEolAmber,
                                selected = selectedPortStateFilter == PortLifecycleState.EOL,
                                palette = palette,
                                onClick = {
                                    onSelectPortState(
                                        if (selectedPortStateFilter == PortLifecycleState.EOL) null
                                        else PortLifecycleState.EOL
                                    )
                                }
                            )
                            StateFilterChip(
                                label = "Guessed ($guessedCount)",
                                dotColor = TuesdayTheme.LedGuessedGrey,
                                selected = selectedPortStateFilter == PortLifecycleState.GUESSED,
                                palette = palette,
                                onClick = {
                                    onSelectPortState(
                                        if (selectedPortStateFilter == PortLifecycleState.GUESSED) null
                                        else PortLifecycleState.GUESSED
                                    )
                                }
                            )
                            StateFilterChip(
                                label = "Update ($updateCount)",
                                dotColor = TuesdayTheme.LedUpdateBlue,
                                selected = selectedPortStateFilter == PortLifecycleState.UPDATE,
                                palette = palette,
                                onClick = {
                                    onSelectPortState(
                                        if (selectedPortStateFilter == PortLifecycleState.UPDATE) null
                                        else PortLifecycleState.UPDATE
                                    )
                                }
                            )
                            StateFilterChip(
                                label = "OK ($okCount)",
                                dotColor = TuesdayTheme.LedOkGreen,
                                selected = selectedPortStateFilter == PortLifecycleState.OK,
                                palette = palette,
                                onClick = {
                                    onSelectPortState(
                                        if (selectedPortStateFilter == PortLifecycleState.OK) null
                                        else PortLifecycleState.OK
                                    )
                                }
                            )
                        }

                        // Action-First Header + Show/Hide OK Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiSlot("action-list-header"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    selectedPortStateFilter != null -> "FILTERED: ${(if (isStn) selectedPortStateFilter.stnLabel else selectedPortStateFilter.proLabel).uppercase()} (${actionDevices.size})"
                                    includeOkDevices -> "ALL NAMED DEVICES (${actionDevices.size})"
                                    else -> "NEEDS ATTENTION (${actionDevices.size})"
                                },
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            if (selectedPortStateFilter == null) {
                                Box(
                                    modifier = Modifier
                                        .uiAction("toggleIncludeOk")
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.elevated)
                                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                        .clickable { onToggleIncludeOk() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (includeOkDevices) "Hide OK Devices" else "Show OK (+$okCount)",
                                        color = palette.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Device Rows (Pea & Garden tiers show all enrolled devices; Forest shows Action-First filtered list)
            val devicesToShow = if (!scaleTier.showFilterChips) visibleDevices else actionDevices
            items(devicesToShow, key = { it.id }) { dev ->
                val stateColor = TuesdayTheme.ledColorFor(dev.portState, isStn)
                if (isStn) {
                    // STN-Home Consumer Card: 5dp colored left border + Room/Lane chips + Plain-English Headline + Action CTA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("device-row", "port=${dev.portIndex};state=${dev.portState.key};cat=${dev.category.code}")
                            .uiAction("inspectDevice")
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .clickable { onInspectDevice(dev.id) }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .heightIn(min = 92.dp)
                                .background(stateColor)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dev.name,
                                    color = palette.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(stateColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = dev.portState.stnLabel,
                                        color = stateColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📍 ${dev.roomLocation}",
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("•", color = palette.border, fontSize = 10.sp)
                                Text(
                                    text = dev.trustZone.stnLabel,
                                    color = palette.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("•", color = palette.border, fontSize = 10.sp)
                                Text(
                                    text = "Port #%02d".format(dev.portIndex),
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = dev.stnHeadline,
                                color = palette.textPrimary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (dev.portState != PortLifecycleState.OK) stateColor else stateColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (dev.portState) {
                                        PortLifecycleState.CVE_KEV -> "Read Safety Recall & Next Steps"
                                        PortLifecycleState.EOL -> "Why This Device Can't Be Updated"
                                        PortLifecycleState.GUESSED -> "Identify & Verify With Camera"
                                        PortLifecycleState.UPDATE -> "How to Update This Device"
                                        else -> "Verified & Protected"
                                    },
                                    color = if (dev.portState != PortLifecycleState.OK) Color.White else stateColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "→",
                                    color = if (dev.portState != PortLifecycleState.OK) Color.White else stateColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // STN-Pro Row: Port Square + Device Name + Asset Tag Pill + Tabular Spine Metadata + 3-Bar Risk Meter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("device-row", "port=${dev.portIndex};state=${dev.portState.key};cat=${dev.category.code}")
                            .uiAction("inspectDevice")
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .clickable { onInspectDevice(dev.id) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Port index + Category code badge
                        Column(
                            modifier = Modifier
                                .width(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(stateColor.copy(alpha = 0.16f))
                                .border(1.dp, stateColor, RoundedCornerShape(6.dp))
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "#%02d".format(dev.portIndex),
                                color = stateColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = dev.category.code,
                                color = palette.textMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = dev.name,
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.elevated)
                                        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = dev.assetTag,
                                        color = palette.textMuted,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = "${dev.trustZone.shortCode} • ${dev.roomLocation} • ${dev.version} • ${dev.lifecycleTier}",
                                color = palette.textMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = dev.proHeadline,
                                color = stateColor,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }

                        RiskMeter(portState = dev.portState, isStn = isStn)
                    }
                }
            }

            // Secondary "+ Add Another Device" CTA shown in Pea (1..2) and Garden (3..7) tiers
            if (!scaleTier.showFilterChips) {
                item {
                    OutlinedButton(
                        onClick = onNavigateToAddDevice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiAction("openAddDevice")
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "+ Add Another Device",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RiskMeter(
    portState: PortLifecycleState,
    isStn: Boolean = false
) {
    val activeBars = when (portState) {
        PortLifecycleState.CVE_KEV -> 3
        PortLifecycleState.EOL -> 3
        PortLifecycleState.GUESSED -> 2
        PortLifecycleState.UPDATE -> 2
        PortLifecycleState.OK -> 1
        PortLifecycleState.UNASSIGNED -> 0
    }
    val activeColor = TuesdayTheme.ledColorFor(portState, isStn)
    val inactiveColor = if (isStn) Color(0xFFCBD5E1) else Color(0xFF2D323E)

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (activeBars >= 1) activeColor else inactiveColor)
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (activeBars >= 2) activeColor else inactiveColor)
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (activeBars >= 3) activeColor else inactiveColor)
            )
        }
        Text(
            text = portState.proLabel,
            color = activeColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun WelcomeCard(
    palette: BrandPalette,
    activeDeviceCount: Int = 0,
    remindersEnabled: Boolean = false,
    dispatchSubscribed: Boolean = false,
    onSetDispatchSubscribed: (Boolean) -> Unit = {},
    bulletinDismissed: Boolean = false,
    onSetBulletinDismissed: (Boolean) -> Unit = {},
    bulletinSnoozed: Boolean = false,
    onSetBulletinSnoozed: (Boolean) -> Unit = {},
    bulletinMuted: Boolean = false,
    onSetBulletinMuted: (Boolean) -> Unit = {},
    onAddRouter: () -> Unit,
    onAddAnyDevice: () -> Unit,
    onLoadDemoFleet: () -> Unit,
    onOpenReferenceGuide: () -> Unit = {},
    onNavigateToBulletins: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {}
) {
    var showIgnoreDialog by remember { mutableStateOf(false) }

    if (showIgnoreDialog) {
        sh.tuesday.app.ui.components.BulletinPreferencesDialog(
            palette = palette,
            onDismissRequest = { showIgnoreDialog = false },
            onConfirmMute = {
                showIgnoreDialog = false
                onSetBulletinMuted(true)
                onSetBulletinDismissed(true)
            },
            onConfirmIgnoreUntilNextMonth = {
                showIgnoreDialog = false
                onSetBulletinSnoozed(true)
                onSetBulletinDismissed(true)
            }
        )
    }

    val step1Done = activeDeviceCount > 0
    val step2Done = remindersEnabled
    val step3Done = dispatchSubscribed
    val allStepsDone = step1Done && step2Done && step3Done

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("WelcomeCard", "tier=day_zero;active=0")
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!bulletinDismissed) {
            val context = LocalContext.current
            val steps = listOf(
                BulletinChecklistStep(
                    id = "step1",
                    num = "1",
                    title = "Inventory Your Network",
                    description = "Kick off Plug & Tag intake to map active routers, switches, NAS, and IoT nodes.",
                    linkText = if (step1Done) "✓ Added" else "Add Device →",
                    isDone = step1Done,
                    onClick = onAddRouter
                ),
                BulletinChecklistStep(
                    id = "step2",
                    num = "2",
                    title = "Enable Push Reminders",
                    description = "Receive an alert at 10:00 AM PT on Patch Tuesday with your prioritized triage list.",
                    linkText = if (step2Done) "✓ Active" else "Enable →",
                    isDone = step2Done,
                    onClick = onNavigateToAccount
                ),
                BulletinChecklistStep(
                    id = "step3",
                    num = "3",
                    title = "Subscribe to the Dispatch",
                    description = "Get human-curated patch analysis and firmware changelogs delivered every cycle.",
                    linkText = if (step3Done) "✓ Subscribed" else "Dispatch ↗",
                    isDone = step3Done,
                    onClick = {
                        onSetDispatchSubscribed(true)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tuesday.sh/dispatch"))
                        context.startActivity(intent)
                    }
                )
            )

            NotchBulletinCard(
                palette = palette,
                title = "Patch Tuesday Readiness",
                description = if (bulletinSnoozed) {
                    "💤 Reminder snoozed until tomorrow at 10:00 AM PT. Get your perimeter cataloged before vendors push critical firmware fixes."
                } else {
                    "Get your perimeter cataloged before vendors push critical firmware and OS fixes."
                },
                leftNotchLabel = "BULLETIN",
                onLeftNotchClick = onNavigateToBulletins,
                rightNotchLabel = "RHYTHM",
                onRightNotchClick = onOpenReferenceGuide,
                steps = steps,
                allStepsDone = allStepsDone,
                primaryActionLabel = "Snooze 24h",
                completeActionLabel = "Mark Complete",
                secondaryActionLabel = "Ignore",
                onPrimaryAction = {
                    if (allStepsDone) {
                        onSetBulletinDismissed(true)
                    } else {
                        onSetBulletinSnoozed(true)
                    }
                },
                onSecondaryAction = { showIgnoreDialog = true }
            )
        }

        if (bulletinDismissed && activeDeviceCount == 0) {
            // Router recommendation tip row and Add Device CTA block (visible once welcome bulletin is dismissed on Day 0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("router-advisory-tip")
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.accent.copy(alpha = 0.10f))
                    .border(1.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "💡 Best place to start: Your Router",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your router is the front door to your home network and the single highest-impact device to keep updated.",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }

            Button(
                onClick = onAddRouter,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiAction("openAddDevice")
            ) {
                Text("Add Your Router (Recommended) →", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onAddAnyDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .uiAction("openAddDevice")
            ) {
                Text("+ Add Any Device", color = palette.textPrimary, fontSize = 11.5.sp)
            }
        }

        if (activeDeviceCount == 0) {
            TextButton(
                onClick = onLoadDemoFleet,
                modifier = Modifier
                    .uiAction("enableDemoMode")
                    .uiAction("setSimulatedDeviceCount")
            ) {
                Text(
                    text = "Enable Demo Mode (seed Forest fleet)",
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LedLegendDot(label: String, color: Color, palette: BrandPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = palette.textMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PortCategoryFaceplateStrip(
    devices: List<RackPortDevice>,
    isStn: Boolean,
    onInspectCategoryDevice: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("PortCategoryFaceplateStrip")
            .uiSlot("c1-c8-strip"),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        StnHardwareCategory.entries.forEach { category ->
            val matchingDevs = devices.filter { it.category == category }
            val count = matchingDevs.size
            val hasDevices = count > 0
            val primaryDev = matchingDevs.firstOrNull()
            val stateColor = if (primaryDev != null) {
                TuesdayTheme.ledColorFor(primaryDev.portState, isStn)
            } else {
                if (isStn) Color(0xFF94A3B8) else Color(0xFF4B5563)
            }
            val emptyBorder = if (isStn) Color(0xFFCBD5E1) else Color(0xFF2D323E)
            val iconBoxBg = if (isStn) Color(0xFFF8FAFC) else Color(0xFF20232B)
            val iconBorderColor = if (hasDevices) stateColor.copy(alpha = 0.53f) else emptyBorder
            val tickColor = if (hasDevices) stateColor.copy(alpha = 0.53f) else emptyBorder
            val tileBgColor = if (hasDevices) stateColor else if (isStn) Color(0xFFE2E8F0) else Color(0xFF20232A)
            val tileTextColor = when {
                !hasDevices -> Color(0xFF6B7280)
                !isStn && (primaryDev?.portState == PortLifecycleState.OK || primaryDev?.portState == PortLifecycleState.EOL) -> Color(0xFF090D0A)
                else -> Color.White
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .uiSlot("category-col", "cat=${category.code};count=$count")
                    .uiAction("inspectCategoryDevice")
                    .clickable { onInspectCategoryDevice(primaryDev?.id) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top 1:1 Square Hardware Silhouette Box (.ubnt-diagram-icon)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(iconBoxBg)
                        .border(1.dp, iconBorderColor, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    HardwareCategorySvgIcon(
                        category = category,
                        tint = stateColor,
                        modifier = Modifier.fillMaxSize(0.56f)
                    )
                }

                // 2. Middle Vertical Connector Tick (.ubnt-diagram-tick: 1dp x 6dp)
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(1.dp)
                        .height(6.dp)
                        .background(tickColor)
                )

                // 3. Bottom 1:1 Square Port Count Tile (.ubnt-diagram-port-tile)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tileBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        color = tileTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareCategorySvgIcon(
    category: StnHardwareCategory,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val stroke = Stroke(
            width = 1.8f * sx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (category) {
            StnHardwareCategory.C1_GATEWAY -> {
                // <rect x="2" y="7" width="20" height="10" rx="2"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(2f * sx, 7f * sy),
                    size = Size(20f * sx, 10f * sy),
                    cornerRadius = CornerRadius(2f * sx, 2f * sy),
                    style = stroke
                )
                // <circle cx="6" cy="12" r="1.2"/>
                drawCircle(
                    color = tint,
                    radius = 1.3f * sx,
                    center = Offset(6f * sx, 12f * sy),
                    style = stroke
                )
                // <path d="M11 10h8v4h-8z"/>
                drawRect(
                    color = tint,
                    topLeft = Offset(11f * sx, 10f * sy),
                    size = Size(8f * sx, 4f * sy),
                    style = stroke
                )
            }
            StnHardwareCategory.C2_SWITCH -> {
                // <rect x="2" y="6" width="20" height="12" rx="2"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(2f * sx, 6f * sy),
                    size = Size(20f * sx, 12f * sy),
                    cornerRadius = CornerRadius(2f * sx, 2f * sy),
                    style = stroke
                )
                // 2x3 port squares inside chassis
                val xs = listOf(5.5f, 10.5f, 15.5f)
                val ys = listOf(8.8f, 13.2f)
                ys.forEach { y ->
                    xs.forEach { x ->
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(x * sx, y * sy),
                            size = Size(2.6f * sx, 2.2f * sy),
                            cornerRadius = CornerRadius(0.5f * sx, 0.5f * sy),
                            style = Stroke(width = 1.3f * sx)
                        )
                    }
                }
            }
            StnHardwareCategory.C3_AP -> {
                // <circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3.5"/>
                drawCircle(
                    color = tint,
                    radius = 9f * sx,
                    center = Offset(12f * sx, 12f * sy),
                    style = stroke
                )
                drawCircle(
                    color = tint,
                    radius = 3.5f * sx,
                    center = Offset(12f * sx, 12f * sy),
                    style = stroke
                )
            }
            StnHardwareCategory.C4_SERVER -> {
                // <rect x="3" y="4" width="18" height="6" rx="1.5"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3f * sx, 4f * sy),
                    size = Size(18f * sx, 6f * sy),
                    cornerRadius = CornerRadius(1.5f * sx, 1.5f * sy),
                    style = stroke
                )
                // <rect x="3" y="14" width="18" height="6" rx="1.5"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3f * sx, 14f * sy),
                    size = Size(18f * sx, 6f * sy),
                    cornerRadius = CornerRadius(1.5f * sx, 1.5f * sy),
                    style = stroke
                )
                drawCircle(color = tint, radius = 1.1f * sx, center = Offset(7f * sx, 7f * sy))
                drawCircle(color = tint, radius = 1.1f * sx, center = Offset(7f * sx, 17f * sy))
            }
            StnHardwareCategory.C5_CAMERA -> {
                // Camera body + prism + center lens
                val path = Path().apply {
                    moveTo(14.5f * sx, 4f * sy)
                    lineTo(9.5f * sx, 4f * sy)
                    lineTo(7f * sx, 7f * sy)
                    lineTo(4f * sx, 7f * sy)
                    lineTo(4f * sx, 20f * sy)
                    lineTo(20f * sx, 20f * sy)
                    lineTo(20f * sx, 7f * sy)
                    lineTo(17f * sx, 7f * sy)
                    close()
                }
                drawPath(path = path, color = tint, style = stroke)
                drawCircle(
                    color = tint,
                    radius = 3.5f * sx,
                    center = Offset(12f * sx, 13.5f * sy),
                    style = stroke
                )
            }
            StnHardwareCategory.C6_IOT -> {
                // <rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 12h6M12 9v6"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4f * sx, 4f * sy),
                    size = Size(16f * sx, 16f * sy),
                    cornerRadius = CornerRadius(3f * sx, 3f * sy),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(9f * sx, 12f * sy),
                    end = Offset(15f * sx, 12f * sy),
                    strokeWidth = 1.8f * sx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(12f * sx, 9f * sy),
                    end = Offset(12f * sx, 15f * sy),
                    strokeWidth = 1.8f * sx,
                    cap = StrokeCap.Round
                )
            }
            StnHardwareCategory.C7_CLIENT -> {
                // <rect x="4" y="4" width="16" height="11" rx="1.5"/><path d="M2 19h20M9 19v-4M15 19v-4"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4f * sx, 4f * sy),
                    size = Size(16f * sx, 11f * sy),
                    cornerRadius = CornerRadius(1.5f * sx, 1.5f * sy),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(2f * sx, 19f * sy),
                    end = Offset(22f * sx, 19f * sy),
                    strokeWidth = 1.8f * sx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(9f * sx, 15f * sy),
                    end = Offset(9f * sx, 19f * sy),
                    strokeWidth = 1.8f * sx
                )
                drawLine(
                    color = tint,
                    start = Offset(15f * sx, 15f * sy),
                    end = Offset(15f * sx, 19f * sy),
                    strokeWidth = 1.8f * sx
                )
            }
            StnHardwareCategory.C8_GUEST -> {
                // <circle cx="12" cy="7" r="4"/><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/>
                drawCircle(
                    color = tint,
                    radius = 4f * sx,
                    center = Offset(12f * sx, 7.5f * sy),
                    style = stroke
                )
                val body = Path().apply {
                    moveTo(5f * sx, 21f * sy)
                    lineTo(5f * sx, 18.5f * sy)
                    cubicTo(5f * sx, 16f * sy, 7f * sx, 15f * sy, 9.5f * sx, 15f * sy)
                    lineTo(14.5f * sx, 15f * sy)
                    cubicTo(17f * sx, 15f * sy, 19f * sx, 16f * sy, 19f * sx, 18.5f * sy)
                    lineTo(19f * sx, 21f * sy)
                }
                drawPath(path = body, color = tint, style = stroke)
            }
        }
    }
}

@Composable
private fun TuesdayCalendarHeartIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_tuesday_mark),
        contentDescription = "Tuesday.sh",
        tint = tint,
        modifier = modifier
    )
}

@Composable
private fun TrackedHardwareProfilesCard(
    devices: List<RackPortDevice>,
    palette: BrandPalette,
    onInspectDevice: (String) -> Unit
) {
    val featured = remember(devices) {
        BulletinsAndInventoryEngine.sortDevicesByRecentChanges(devices)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("TrackedHardwareProfilesCard")
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("tracked-profiles-header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tracked Hardware Profiles",
                color = palette.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Recent changes",
                color = palette.textMuted,
                fontSize = 9.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("tracked-profiles-strip")
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            featured.forEachIndexed { idx, dev ->
                val stateColor = TuesdayTheme.ledColorFor(dev.portState, isStn = false)
                val isSelected = idx == 0
                val badgeText = when (dev.portState) {
                    PortLifecycleState.CVE_KEV -> "!"
                    PortLifecycleState.EOL -> "EOL"
                    else -> "#${dev.portIndex}"
                }
                val verSub = if (dev.portState == PortLifecycleState.GUESSED || dev.version == "unverified") {
                    "guess"
                } else {
                    "v${dev.version}"
                }

                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .uiSlot("tracked-profile-squircle")
                        .uiAction("inspectDevice")
                        .clickable { onInspectDevice(dev.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (isSelected) palette.accent.copy(alpha = 0.14f)
                                else Color(0xFF1F222A)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) palette.accent else stateColor.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(11.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        HardwareCategorySvgIcon(
                            category = dev.category,
                            tint = stateColor,
                            modifier = Modifier.size(19.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF121418))
                                .border(1.dp, stateColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = stateColor,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = dev.shortLabel,
                        color = palette.textPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = verSub,
                        color = palette.textMuted,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixBotanicalPixelArtOverlay(
    scaleTier: InventoryScaleTier,
    activeCount: Int,
    isStn: Boolean
) {
    val fgColor = if (isStn) Color(0xFF94A3B8) else Color(0xFF475569)

    when (scaleTier) {
        InventoryScaleTier.DAY_ZERO -> {}
        InventoryScaleTier.PEA -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelArtSprout(
                    color = fgColor.copy(alpha = 0.85f),
                    size = 32.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isStn) Color.White.copy(alpha = 0.9f) else Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, if (isStn) Color(0xFFCBD5E1) else Color(0xFF334155), RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$activeCount ACTIVE • ${128 - activeCount} SLOTS AVAILABLE",
                        color = if (isStn) Color(0xFF475569) else Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        InventoryScaleTier.GARDEN -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom
            ) {
                PixelArtPineTree(color = fgColor.copy(alpha = 0.75f), width = 24.dp, height = 34.dp)
                PixelArtPineTree(color = fgColor.copy(alpha = 0.75f), width = 30.dp, height = 42.dp)
                PixelArtPineTree(color = fgColor.copy(alpha = 0.75f), width = 20.dp, height = 28.dp)
            }
        }
        InventoryScaleTier.FOREST -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                repeat(9) { idx ->
                    val isTall = idx % 2 == 0
                    PixelArtPineTree(
                        color = fgColor.copy(alpha = 0.45f),
                        width = if (isTall) 22.dp else 18.dp,
                        height = if (isTall) 32.dp else 26.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelArtSprout(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val px = this.size.width / 16f
        val py = this.size.height / 16f
        fun rect(x: Int, y: Int, w: Int, h: Int) {
            drawRect(color = color, topLeft = Offset(x * px, y * py), size = Size(w * px, h * py))
        }
        // Left leaf
        rect(2, 3, 3, 1); rect(1, 4, 5, 1); rect(1, 5, 5, 1); rect(2, 6, 4, 1); rect(4, 7, 2, 1)
        // Right leaf
        rect(10, 1, 3, 1); rect(9, 2, 5, 1); rect(9, 3, 6, 1); rect(9, 4, 5, 1); rect(8, 5, 4, 1); rect(8, 6, 2, 1)
        // Stem & Soil mound
        rect(6, 7, 3, 1); rect(7, 8, 2, 4); rect(6, 12, 3, 1); rect(5, 13, 5, 1); rect(3, 14, 9, 1); rect(4, 15, 7, 1)
    }
}

@Composable
private fun PixelArtPineTree(color: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val px = this.size.width / 16f
        val py = this.size.height / 22f
        fun rect(x: Int, y: Int, w: Int, h: Int) {
            drawRect(color = color, topLeft = Offset(x * px, y * py), size = Size(w * px, h * py))
        }
        rect(7, 0, 2, 2)
        rect(6, 2, 4, 2)
        rect(5, 4, 6, 2)
        rect(6, 6, 4, 2)
        rect(4, 8, 8, 2)
        rect(3, 10, 10, 2)
        rect(5, 12, 6, 2)
        rect(2, 14, 12, 2)
        rect(1, 16, 14, 2)
        rect(7, 18, 2, 4)
    }
}

@Composable
private fun StateFilterChip(
    label: String,
    dotColor: Color?,
    selected: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .uiAction("filterPortState")
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.surface)
            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            text = label,
            color = if (selected) palette.accent else palette.textPrimary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ZoneFilterPill(
    label: String,
    selected: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .uiAction("filterZone")
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.elevated)
            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = if (selected) palette.accent else palette.textMuted,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DeviceInspectorBottomSheet(
    device: RackPortDevice,
    palette: BrandPalette,
    isStn: Boolean,
    onApplyPatch: () -> Unit,
    onClose: () -> Unit
) {
    val stateColor = TuesdayTheme.ledColorFor(device.portState, isStn)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("DeviceInspectorSheet", "device=${device.id};state=${device.portState.key}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("inspector-header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isStn) {
                        "${device.portState.stnLabel.uppercase()} • ${device.lifecycleTier.uppercase()}"
                    } else {
                        "PORT #%02d • %s • %s (%s)".format(
                            device.portIndex,
                            device.assetTag,
                            device.category.code,
                            device.category.shortName
                        )
                    },
                    color = stateColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = device.name,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!isStn) {
                RiskMeter(portState = device.portState, isStn = false)
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier.uiAction("closeDeviceInspector")
            ) {
                Text(if (isStn) "Done" else "Close", color = palette.accent)
            }
        }

        if (isStn) {
            // STN-Home: Plain-English Headline + "What to Do Next" Numbered Action Steps
            Text(
                text = device.stnHeadline,
                color = palette.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.bg)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "What to Do Next",
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                val steps = listOf(
                    "1. Check status: ${device.s4Advisories}",
                    "2. Action step: ${device.s6Remediation}",
                    "3. Verify support window: ${device.s3Lifecycle}"
                )
                steps.forEach { step ->
                    Text(
                        text = step,
                        color = palette.textPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Column(
                modifier = Modifier.uiSlot("s0-s6-stack"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectorSectionRow("ROOM & PLACEMENT", device.roomLocation, palette)
                InspectorSectionRow("DEVICE LIFESPAN & MANUFACTURER SUPPORT", "${device.lifecycleTier} • ${device.s3Lifecycle}", palette)
                InspectorSectionRow("PLUGGED INTO (ROUTER PORT / WI-FI)", "Port #%02d • %s".format(device.portIndex, device.s5Topology), palette)
                InspectorSectionRow("WI-FI LANE", device.trustZone.stnLabel, palette)
                InspectorSectionRow("SOFTWARE VERSION & PUBLISHED WARNINGS", "Version ${device.version} • ${device.s4Advisories}", palette)
            }
        } else {
            // STN-Pro: OHLC Version Candlesticks & Telemetry Health + Technical S0..S6 Stack
            Box(modifier = Modifier.uiSlot("candlestick-chart")) {
                CandleGraphCanvas(
                    score = when (device.portState) {
                        PortLifecycleState.OK -> 98.0
                        PortLifecycleState.UPDATE -> 82.0
                        PortLifecycleState.GUESSED -> 64.0
                        PortLifecycleState.EOL -> 42.0
                        PortLifecycleState.CVE_KEV -> 28.0
                        PortLifecycleState.UNASSIGNED -> 100.0
                    }
                )
            }

            Column(
                modifier = Modifier.uiSlot("s0-s6-stack"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectorSectionRow("S0 Identity", device.s0Identity, palette)
                InspectorSectionRow("S1 Hardware", device.s1Hardware, palette)
                InspectorSectionRow("S2 Software (PURL)", device.s2Software, palette)
                InspectorSectionRow("S3 Lifecycle", device.s3Lifecycle, palette)
                InspectorSectionRow("S4 Advisories", device.s4Advisories, palette)
                InspectorSectionRow("S5 Topology", device.s5Topology, palette)
                InspectorSectionRow("S6 Remediation", device.s6Remediation, palette)
            }
        }

        if (device.portState != PortLifecycleState.OK && device.portState != PortLifecycleState.UNASSIGNED) {
            Button(
                onClick = onApplyPatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TuesdayTheme.LedOkGreen,
                    contentColor = if (isStn) Color.White else Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("remediation-cta")
                    .uiAction("markRemediationComplete")
                    .padding(top = 4.dp, bottom = 14.dp)
            ) {
                Text(
                    text = if (isStn) "✓ Mark Remediation Complete (Set Device OK)" else "✓ Mark Remediation Complete (Set Port OK)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun InspectorSectionRow(label: String, value: String, palette: BrandPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.elevated)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = palette.accent,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
