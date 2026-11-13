package sh.tuesday.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.domain.EntitlementGatePolicy
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.NetworkSite
import sh.tuesday.app.services.SubscriptionPlanOffer
import sh.tuesday.app.services.TuesdayBillingManager
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.components.PlanOfferSelector
import sh.tuesday.app.ui.components.SegmentedTabRow
import sh.tuesday.app.ui.components.TuesdayModalCard
import sh.tuesday.app.ui.components.TuesdayTextField
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@Composable
fun NetworkPickerModalDialog(
    networks: List<NetworkSite>,
    activeNetworkId: String,
    palette: BrandPalette,
    entitlementTier: String = "free",
    flavor: AppBrandFlavor = AppBrandFlavor.TUESDAY_PRO,
    plans: List<SubscriptionPlanOffer> = TuesdayBillingManager.defaultPlanOffers(),
    selectedProductId: String = TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL,
    onSelectPlan: (String) -> Unit = {},
    isPurchasing: Boolean = false,
    billingStatusMessage: String? = null,
    initialShowUpgradePrompt: Boolean = false,
    onSelectNetwork: (String) -> Unit,
    onCreateNetwork: (String) -> Unit,
    onUpgradeMultiSite: (String) -> Unit = {},
    onOpenNetworkSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var showSetupModal by remember { mutableStateOf(false) }
    var showUpgradePrompt by remember(initialShowUpgradePrompt) { mutableStateOf(initialShowUpgradePrompt) }
    var activePlanSku by remember(selectedProductId) { mutableStateOf(selectedProductId) }

    val canAddNetworkWithoutUpgrade = EntitlementGatePolicy.canCreateAdditionalNetwork(entitlementTier, networks)

    val currentNetwork = remember(networks, activeNetworkId) {
        networks.find { it.id == activeNetworkId } ?: networks.firstOrNull() ?: NetworkSite(
            id = "net-default",
            name = "My Network",
            devicesCount = 0,
            role = "Primary Site"
        )
    }

    val availableNetworks = remember(networks, activeNetworkId) {
        networks.filter { it.id != activeNetworkId }
    }

    if (showSetupModal) {
        SetupNetworkModalDialog(
            palette = palette,
            onCreateNetwork = { name ->
                onCreateNetwork(name)
                showSetupModal = false
                onDismiss()
            },
            onDismiss = { showSetupModal = false }
        )
    } else if (showUpgradePrompt) {
        Tier1UpgradeModalDialog(
            palette = palette,
            plans = plans,
            selectedProductId = activePlanSku,
            onSelectPlan = { sku ->
                activePlanSku = sku
                onSelectPlan(sku)
            },
            isPurchasing = isPurchasing,
            billingStatusMessage = billingStatusMessage,
            onConfirmUpgrade = { sku ->
                onUpgradeMultiSite(sku)
                showUpgradePrompt = false
                showSetupModal = true
            },
            onDismiss = { showUpgradePrompt = false }
        )
    } else {
        TuesdayModalCard(
            palette = palette,
            title = "Networks",
            onDismissRequest = onDismiss,
            modifier = Modifier.uiComponent("NetworkPickerModal"),
            closeActionModifier = Modifier.uiAction("closeNetworkPicker")
        ) {
            // Current Network Widget with Cog button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("current-network-widget")
                    .clip(RoundedCornerShape(TuesdayDimens.RadiusModalCard))
                    .background(palette.accent.copy(alpha = 0.08f))
                    .border(1.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(TuesdayDimens.RadiusModalCard))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TuesdayTheme.LedOkGreen)
                        )
                        Text(
                            text = "CURRENT NETWORK",
                            color = palette.accentText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            onDismiss()
                            onOpenNetworkSettings()
                        },
                        modifier = Modifier
                            .size(TuesdayDimens.HitTargetCompact)
                            .uiAction("openNetworkSettings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit Network Details",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TuesdayTheme.LedOkGreen)
                            )
                            Text(
                                text = currentNetwork.name.ifBlank { "My Network" },
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = currentNetwork.role,
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(TuesdayDimens.RadiusBadge),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Text(
                            text = "${currentNetwork.devicesCount} devices",
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (availableNetworks.isNotEmpty()) {
                Column(
                    modifier = Modifier.uiSlot("available-networks-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SWITCH TO NETWORK",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )

                    availableNetworks.forEach { net ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiAction("selectNetwork")
                                .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
                                .background(palette.elevated)
                                .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard))
                                .clickable {
                                    onSelectNetwork(net.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(palette.textMuted.copy(alpha = 0.5f))
                                )
                                Column {
                                    Text(
                                        text = net.name.ifBlank { "My Network" },
                                        color = palette.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${net.role} • ${net.devicesCount} devices",
                                        color = palette.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Switch",
                                    color = palette.accentText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.East,
                                    contentDescription = null,
                                    tint = palette.accentText,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (canAddNetworkWithoutUpgrade) {
                        showSetupModal = true
                    } else {
                        showUpgradePrompt = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .uiAction("openSetupNetworkModal"),
                shape = RoundedCornerShape(TuesdayDimens.RadiusCard),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (canAddNetworkWithoutUpgrade) "Add a network" else "Add a network (Multi-Site)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun Tier1UpgradeModalDialog(
    palette: BrandPalette,
    plans: List<SubscriptionPlanOffer>,
    selectedProductId: String,
    onSelectPlan: (String) -> Unit,
    isPurchasing: Boolean = false,
    billingStatusMessage: String? = null,
    onConfirmUpgrade: (String) -> Unit,
    onDismiss: () -> Unit
) {
    TuesdayModalCard(
        palette = palette,
        title = "Tuesday.sh Pro Tier 1",
        subtitle = "Free accounts include 1 primary network and 1 demo sandbox. Upgrade to Pro Tier 1 to unlock unlimited network locations, Family Site Management, and live cloud sync.",
        onDismissRequest = onDismiss,
        modifier = Modifier.uiComponent("Tier1UpgradeModal"),
        borderColor = palette.accent.copy(alpha = 0.6f),
        closeActionModifier = Modifier.uiAction("closeTier1UpgradeModal"),
        leadingContent = {
            Text("⚡", fontSize = 18.sp)
        },
        headerTrailing = {
            MonospaceBadge(
                text = "MULTI-SITE",
                color = palette.accentText
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("tier1-upgrade-card")
                .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
                .background(palette.elevated)
                .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✓ Unlimited physical sites & remote locations",
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "✓ Family & Team Site Delegation (QR & invite pairing)",
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "✓ Live cross-site security bulletins & cloud sync",
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        PlanOfferSelector(
            plans = plans,
            selectedProductId = selectedProductId,
            onSelectPlan = onSelectPlan,
            palette = palette,
            cardModifier = { Modifier.uiAction("selectTier1Plan") }
        )

        if (!billingStatusMessage.isNullOrBlank()) {
            Text(
                text = billingStatusMessage,
                color = palette.textPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onConfirmUpgrade(selectedProductId) },
                enabled = !isPurchasing,
                modifier = Modifier
                    .weight(1f)
                    .uiAction("confirmTier1Upgrade"),
                shape = RoundedCornerShape(TuesdayDimens.RadiusCard),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isPurchasing) "Processing..." else "Upgrade & Continue",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(TuesdayDimens.RadiusCard),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Text("Not Now", color = palette.textMuted, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
fun SetupNetworkModalDialog(
    palette: BrandPalette,
    onCreateNetwork: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFlowIndex by remember { mutableIntStateOf(0) } // 0 = New, 1 = Join
    var newNetworkName by remember { mutableStateOf("") }

    TuesdayModalCard(
        palette = palette,
        title = "Setup a network",
        onDismissRequest = onDismiss,
        modifier = Modifier.uiComponent("SetupNetworkModal")
    ) {
        SegmentedTabRow(
            options = listOf("New", "Join"),
            selectedIndex = selectedFlowIndex,
            onSelect = { selectedFlowIndex = it },
            palette = palette,
            useAccentFillOnSelected = false,
            badgeForIndex = { idx -> if (idx == 1) "SOON" else null }
        )

        if (selectedFlowIndex == 0) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Create a new isolated site or installation profile for your hardware rack.",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                TuesdayTextField(
                    value = newNetworkName,
                    onValueChange = { newNetworkName = it },
                    label = "Network Name",
                    placeholder = "e.g. Vacation Cabin, Office Rack",
                    palette = palette
                )

                Button(
                    onClick = {
                        val trimmed = newNetworkName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateNetwork(trimmed)
                        }
                    },
                    enabled = newNetworkName.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(TuesdayDimens.RadiusCard),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Create Network & Join", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
                    .background(palette.elevated)
                    .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🤝 Family & Team Delegation",
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Joining existing networks via QR code invitation or pairing tokens is coming soon in M4 (Family Delegation Networks).",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
