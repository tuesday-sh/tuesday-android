package sh.tuesday.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.*
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@Composable
internal fun StatusMatrixAndActionListScreen(
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
    onAddRouter: () -> Unit = onNavigateToAddDevice,
    onAddAnyDevice: () -> Unit = onNavigateToAddDevice,
    onAddCategoryDevice: (StnHardwareCategory) -> Unit = { onNavigateToAddDevice() },
    onNavigateToBulletins: () -> Unit,
    onNavigateToAccount: () -> Unit = {},
    onEnableReminders: () -> Unit = onNavigateToAccount,
    remindersEnabled: Boolean = false,
    dispatchSubscribed: Boolean = false,
    onSetDispatchSubscribed: (Boolean) -> Unit = {},
    bulletinDismissed: Boolean = false,
    onSetBulletinDismissed: (Boolean) -> Unit = {},
    bulletinSnoozed: Boolean = false,
    onSetBulletinSnoozed: (Boolean) -> Unit = {},
    bulletinMuted: Boolean = false,
    onSetBulletinMuted: (Boolean) -> Unit = {},
    showStateFilterStrip: Boolean = true,
    showZoneFilterStrip: Boolean = true,
    onLoadDemoFleet: () -> Unit,
    onInspectDevice: (String) -> Unit,
    networks: List<NetworkSite> = emptyList(),
    activeNetworkId: String = "",
    onSelectNetwork: (String) -> Unit = {}
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // 1. Inventory Card (8-Port Category Strip + 16x8 LED Grid + Botanical Overlay)
        // Always rendered even on Day 0 (visibleDevices.isEmpty()) to maintain continuous physical chassis anchoring during swipe
        item {
            InventoryCard(
                scaleTier = scaleTier,
                visibleDevices = visibleDevices,
                isStn = isStn,
                palette = palette,
                networks = networks,
                activeNetworkId = activeNetworkId,
                selectedZoneFilter = selectedZoneFilter,
                selectedPortStateFilter = selectedPortStateFilter,
                showZoneFilterStrip = showZoneFilterStrip,
                onSelectNetwork = onSelectNetwork,
                onSelectZone = onSelectZone,
                onSelectPortState = onSelectPortState,
                onOpenReferenceGuide = onOpenReferenceGuide,
                onNavigateToAddDevice = onNavigateToAddDevice,
                onAddCategoryDevice = onAddCategoryDevice,
                onInspectDevice = onInspectDevice
            )
        }

        // Day 0 Conditional Callout Card (`WelcomeCard` — shown below inventory grid when visibleDevices.isEmpty())
        if (visibleDevices.isEmpty()) {
            item {
                WelcomeCard(
                    palette = palette,
                    onAddRouter = onAddRouter,
                    onAddAnyDevice = onAddAnyDevice,
                    onLoadDemoFleet = onLoadDemoFleet
                )
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

        // 2. Lifecycle State Filter Strip (placed directly above the device list view when >= 1 device & preference allow)
        if (visibleDevices.isNotEmpty() && showStateFilterStrip) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("state-filter-strip")
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    StateFilterChip(
                        label = "All",
                        dotColor = null,
                        selected = selectedPortStateFilter == null,
                        palette = palette,
                        onClick = { onSelectPortState(null) }
                    )
                    listOf(
                        PortLifecycleState.CVE_KEV,
                        PortLifecycleState.EOL,
                        PortLifecycleState.GUESSED,
                        PortLifecycleState.UPDATE,
                        PortLifecycleState.OK
                    ).forEach { state ->
                        StateFilterChip(
                            label = if (isStn) state.stnLabel else state.proLabel,
                            dotColor = TuesdayTheme.ledColorFor(state, isStn),
                            selected = selectedPortStateFilter == state,
                            palette = palette,
                            onClick = {
                                onSelectPortState(if (selectedPortStateFilter == state) null else state)
                            }
                        )
                    }
                }
            }
        }

        // 2B. Top-of-Inventory Action-First Header + Show/Hide OK Toggle (for Forest tier >= 8 devices)
        if (scaleTier.showFilterChips) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("action-list-header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            selectedPortStateFilter != null -> "FILTERED: ${(if (isStn) selectedPortStateFilter.stnLabel else selectedPortStateFilter.proLabel).uppercase()}"
                            includeOkDevices -> "ALL NAMED DEVICES"
                            else -> "NEEDS ATTENTION"
                        },
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (selectedPortStateFilter == null) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 28.dp)
                                .uiAction("toggleIncludeOk")
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.elevated)
                                .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                .clickable { onToggleIncludeOk() }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (includeOkDevices) "Hide OK Devices" else "Show OK Devices",
                                color = palette.accentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 3. Device Rows (Pea & Garden tiers show all enrolled devices unless a filter is active; Forest shows Action-First filtered list)
        val devicesToShow = if (!scaleTier.showFilterChips) {
            visibleDevices
                .filter { selectedZoneFilter == null || it.trustZone == selectedZoneFilter }
                .filter { selectedPortStateFilter == null || it.portState == selectedPortStateFilter }
        } else {
            actionDevices
        }
        items(devicesToShow, key = { it.id }) { dev ->
            val stateColor = TuesdayTheme.ledColorFor(dev.portState, isStn)
            if (isStn) {
                // STN-Home Consumer Card: 5dp colored left border + Room/Lane chips + Plain-English Headline + Action CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("device-row", "port=${dev.portIndex};state=${dev.portState.key};cat=${dev.category.code}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                MonospaceBadge(
                                    text = dev.portState.stnLabel,
                                    color = stateColor,
                                    borderAlpha = 0f
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📍 ${dev.roomLocation}",
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("•", color = palette.border, fontSize = 11.sp)
                                Text(
                                    text = dev.trustZone.stnLabel,
                                    color = palette.accentText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("•", color = palette.border, fontSize = 11.sp)
                                Text(
                                    text = dev.category.code,
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val topBulletin = remember(dev.id, dev.purls) {
                                BulletinsAndInventoryEngine.findTopBulletinForDevice(dev)
                            }
                            val detailBlurb = topBulletin?.stnTitle ?: "${dev.category.stnLabel} • ${dev.roomLocation}"
                            Text(
                                text = detailBlurb,
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
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
                                    fontSize = 11.sp,
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
                }
            } else {
                // STN-Pro Row: Category Square + Device Name + Tabular Spine Metadata + 3-Bar Risk Meter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("device-row", "port=${dev.portIndex};state=${dev.portState.key};cat=${dev.category.code}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiAction("inspectDevice")
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .clickable { onInspectDevice(dev.id) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category silhouette icon badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(stateColor.copy(alpha = 0.16f))
                                .border(1.dp, stateColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            HardwareCategorySvgIcon(
                                category = dev.category,
                                tint = stateColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        val topBulletin = remember(dev.id, dev.purls) {
                            BulletinsAndInventoryEngine.findTopBulletinForDevice(dev)
                        }
                        val detailBlurb = if (topBulletin != null) {
                            if (isStn) topBulletin.stnTitle else topBulletin.proTitle
                        } else {
                            val catLabel = if (isStn) dev.category.stnLabel else dev.category.proLabel
                            "$catLabel • ${dev.roomLocation}"
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = dev.name,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = detailBlurb,
                                color = if (topBulletin != null) stateColor else palette.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 14.5.sp
                            )
                        }

                        val noticeCount = remember(dev.id, dev.purls) {
                            BulletinsAndInventoryEngine.countBulletinsForDevice(dev)
                        }
                        RiskMeter(portState = dev.portState, isStn = isStn, noticeCount = noticeCount, palette = palette)
                    }
                }
            }
        }

        // Secondary "+ Add Another Device" CTA shown in Pea (1 device) when WelcomeCard is hidden and filter chips are not yet shown
        if (visibleDevices.isNotEmpty() && !scaleTier.showFilterChips) {
            item {
                OutlinedButton(
                    onClick = onNavigateToAddDevice,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .uiAction("openAddDevice")
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "+ Add Another Device",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.uiSlot("add-another-device-cta")
                    )
                }
            }
        }
    }
}

@Composable
internal fun WelcomeCard(
    palette: BrandPalette,
    onAddRouter: () -> Unit,
    onAddAnyDevice: () -> Unit,
    onLoadDemoFleet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("WelcomeCard", "visible=true;tier=day0")
            .clip(RoundedCornerShape(10.dp))
            .background(palette.accent.copy(alpha = 0.10f))
            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("router-advisory-tip"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "💡 Best place to start: Your Router",
                color = palette.accentText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your router is the front door to your home network and the single highest-impact device to keep updated.",
                color = palette.textPrimary.copy(alpha = 0.88f),
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        Button(
            onClick = onAddRouter,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .uiAction("openAddDevice")
        ) {
            Text(
                text = "Add Your Router (Recommended) →",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.uiSlot("add-router-cta")
            )
        }

        OutlinedButton(
            onClick = onAddAnyDevice,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .uiAction("openAddDevice")
        ) {
            Text(
                text = "+ Add Any Device",
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.uiSlot("add-any-device-cta")
            )
        }

        TextButton(
            onClick = onLoadDemoFleet,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .uiAction("enableDemoMode")
        ) {
            Text(
                text = "Enable Demo Mode (add example devices)",
                color = palette.accentText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.uiSlot("enable-demo-cta")
            )
        }
    }
}

@Composable
internal fun TrackedHardwareProfilesCard(
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Recent changes",
                color = palette.textMuted,
                fontSize = 11.sp
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
                Box(modifier = Modifier.uiSlot("tracked-profile-squircle")) {
                    Column(
                        modifier = Modifier
                            .width(58.dp)
                            .uiAction("inspectDevice")
                            .clickable { onInspectDevice(dev.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isSelected) palette.accent.copy(alpha = 0.14f)
                                    else palette.elevated
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
                        }
                        Text(
                            text = dev.shortLabel,
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
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
    isStn: Boolean = false,
    noticeCount: Int = 0,
    palette: BrandPalette = TuesdayTheme.colors
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
    val inactiveColor = palette.borderStrong

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Sev Bars Diagram
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (activeBars >= 1) activeColor else inactiveColor)
            )
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (activeBars >= 2) activeColor else inactiveColor)
            )
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (activeBars >= 3) activeColor else inactiveColor)
            )
        }

        // Relevant Advisories Count Badge / Notice Counter
        if (noticeCount > 0) {
            Text(
                text = if (noticeCount == 1) "1 notice" else "$noticeCount notices",
                color = activeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Text(
                text = "0 notices",
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
