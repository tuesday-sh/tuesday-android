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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.NetworkSite
import sh.tuesday.app.services.SubscriptionPlanOffer
import sh.tuesday.app.services.TuesdayBillingManager
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
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

    // In Free tier: only 1 real network + 1 demo network allowed. Creating additional real networks requires Multi-Site (Tier 1) on Tuesday.sh Pro.
    val isMultiSiteAllowed = entitlementTier == "multisite_tier_1" || entitlementTier == "pro_tier_1"
    val realNetworksCount = networks.count { it.id != "net-demo" }
    val canAddNetworkWithoutUpgrade = isMultiSiteAllowed || realNetworksCount < 1

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
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .uiComponent("NetworkPickerModal")
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: "Networks" title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Networks",
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .uiAction("closeNetworkPicker")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = palette.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Current Network Widget with Cog button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("current-network-widget")
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.accent.copy(alpha = 0.08f))
                    .border(1.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
                            color = palette.accent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Settings / Edit Network Cog Button
                    IconButton(
                        onClick = {
                            onDismiss()
                            onOpenNetworkSettings()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .uiAction("openNetworkSettings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit Network Details",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(17.dp)
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
                            fontSize = 11.5.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
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

            // Available Networks (excluding current network)
            if (availableNetworks.isNotEmpty()) {
                Column(
                    modifier = Modifier.uiSlot("available-networks-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SWITCH TO NETWORK",
                        color = palette.textMuted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )

                    availableNetworks.forEach { net ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiAction("selectNetwork")
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.elevated)
                                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
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
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${net.role} • ${net.devicesCount} devices",
                                        color = palette.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Switch",
                                    color = palette.accent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.East,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // "Add a network" Button -> Opens "Setup a network" Modal (or opens Tier 1 Upgrade Modal if on Free)
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
                shape = RoundedCornerShape(10.dp),
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .uiComponent("Tier1UpgradeModal")
                .uiSlot("tier1-upgrade-card")
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Title, Badge, and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚡", fontSize = 18.sp)
                    Text(
                        text = "Tuesday.sh Pro Tier 1",
                        color = palette.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "MULTI-SITE",
                            color = palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .uiAction("closeTier1UpgradeModal")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = palette.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "Free accounts include 1 primary network and 1 demo sandbox. Upgrade to Pro Tier 1 to unlock unlimited network locations, Family Site Management, and live cloud sync.",
                color = palette.textMuted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            // Feature Highlights
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.elevated)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "✓ Unlimited physical sites & remote locations",
                    color = palette.textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "✓ Family & Team Site Delegation (QR & invite pairing)",
                    color = palette.textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "✓ Live cross-site security bulletins & cloud sync",
                    color = palette.textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Monthly vs Annual Plan Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                plans.forEach { offer ->
                    val isSelected = selectedProductId == offer.productId
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .uiAction("selectTier1Plan")
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) palette.accent.copy(alpha = 0.14f) else palette.elevated)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) palette.accent else palette.border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectPlan(offer.productId) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = offer.title,
                                color = if (isSelected) palette.accent else palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (offer.badge != null) {
                                Text(
                                    text = offer.badge,
                                    color = TuesdayTheme.LedOkGreen,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = "${offer.formattedPrice} ${offer.periodLabel}",
                            color = palette.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

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
                    shape = RoundedCornerShape(10.dp),
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
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text("Not Now", color = palette.textMuted, fontSize = 12.5.sp)
                }
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
    var selectedFlow by remember { mutableStateOf("new") } // "new" or "join"
    var newNetworkName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .uiComponent("SetupNetworkModal")
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: "Setup a network" title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Setup a network",
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = palette.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tabs / Segmented Control: New vs Join
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.elevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { selectedFlow = "new" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedFlow == "new") palette.surface else Color.Transparent,
                    border = if (selectedFlow == "new") BorderStroke(1.dp, palette.border) else null
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "New",
                            color = if (selectedFlow == "new") palette.textPrimary else palette.textMuted,
                            fontSize = 12.5.sp,
                            fontWeight = if (selectedFlow == "new") FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Surface(
                    onClick = { selectedFlow = "join" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedFlow == "join") palette.surface else Color.Transparent,
                    border = if (selectedFlow == "join") BorderStroke(1.dp, palette.border) else null
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Join",
                                color = if (selectedFlow == "join") palette.textPrimary else palette.textMuted,
                                fontSize = 12.5.sp,
                                fontWeight = if (selectedFlow == "join") FontWeight.Bold else FontWeight.Medium
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = palette.accent.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "SOON",
                                    color = palette.accent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Flow 1: New Network
            if (selectedFlow == "new") {
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

                    OutlinedTextField(
                        value = newNetworkName,
                        onValueChange = { newNetworkName = it },
                        label = { Text("Network Name", fontSize = 12.sp) },
                        placeholder = {
                            Text("e.g. Vacation Cabin, Office Rack", fontSize = 12.sp, color = palette.textMuted)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border
                        )
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
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Create Network & Join", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
                // Flow 2: Join Network (Coming Soon)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
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
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
