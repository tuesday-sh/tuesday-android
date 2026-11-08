package sh.tuesday.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import sh.tuesday.app.models.StnHardwareCategory
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
    onSelectNetwork: (String) -> Unit,
    onCreateNetwork: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newNetworkName by remember { mutableStateOf("") }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent.copy(alpha = 0.14f))
                            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NETWORKS",
                            color = palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Switch Site / Location",
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.uiAction("closeNetworkPicker")
                ) {
                    Text("✕", color = palette.textMuted, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Your account manages multiple physical locations and homelab installations.",
                color = palette.textMuted,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            Column(
                modifier = Modifier.uiSlot("network-picker-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                networks.forEach { net ->
                    val isActive = net.id == activeNetworkId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiAction("selectNetwork")
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) palette.accent.copy(alpha = 0.12f) else palette.elevated)
                            .border(
                                1.dp,
                                if (isActive) palette.accent else palette.border,
                                RoundedCornerShape(10.dp)
                            )
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
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) TuesdayTheme.LedOkGreen else palette.textMuted)
                            )
                            Column {
                                Text(
                                    text = net.name.ifBlank { "Default Network" },
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${net.role} • ${net.devicesCount} devices",
                                    color = palette.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            text = if (isActive) "✓ Active" else "Switch →",
                            color = if (isActive) TuesdayTheme.LedOkGreen else palette.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Create Another Network
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("create-network-form")
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.bg)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "+ ADD ANOTHER NETWORK",
                    color = palette.textMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newNetworkName,
                        onValueChange = { newNetworkName = it },
                        placeholder = {
                            Text("e.g. Vacation Cabin, Office Rack", fontSize = 11.sp, color = palette.textMuted)
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border
                        )
                    )
                    Button(
                        onClick = {
                            if (newNetworkName.isNotBlank()) {
                                onCreateNetwork(newNetworkName)
                                newNetworkName = ""
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.uiAction("createNetwork")
                    ) {
                        Text("Create", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReferenceGuideModalDialog(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    onDismiss: () -> Unit
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .uiComponent("ReferenceGuideModal")
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent.copy(alpha = 0.14f))
                            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "REFERENCE GUIDE",
                            color = palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "SureThingNet Standard & Ordinal Spine",
                        color = palette.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.uiAction("closeReferenceGuide")
                ) {
                    Text("✕", color = palette.textMuted, fontWeight = FontWeight.Bold)
                }
            }

            // Scrollable Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: SureThingNet Standard Categories (Scale C: C1 -> C8)
                Text(
                    text = "SureThingNet Standard Categories (Scale C)",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "The 8 semantic hardware categories (C1 → C8) that govern our faceplate port strip, inventory classification, and progressive disclosure model.",
                    color = palette.textMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                StnHardwareCategory.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { cat ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.elevated)
                                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                    .padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isStn) cat.stnLabel else cat.proLabel,
                                        color = palette.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(palette.accent.copy(alpha = 0.14f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${cat.code} • ${cat.osiLayer}",
                                            color = palette.accent,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Text(
                                    text = cat.description,
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 13.5.sp
                                )
                                Text(
                                    text = cat.examples,
                                    color = palette.accent,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Section 2: Industry-Standard Ordinal Scales (S0->S6, Z0->Z5, R1->R4)
                HorizontalDivider(color = palette.border)
                Text(
                    text = "📐 Industry-Standard Ordinal Scales (S0→S6 • Z0→Z5 • R1→R4)",
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                OrdinalScaleCard(
                    tag = "Scale S",
                    title = "The 7-Step Vertical Stack (S0 Space → S6 Apps)",
                    description = "Orders device detail sheets and powers root-cause alert suppression: if S0 Power or S2 Switch Port fails, downstream S6 App alerts are suppressed.",
                    rows = listOf(
                        Triple("S0", "Rack, Room & Power", "Room & Outlet — Where it lives & powers"),
                        Triple("S1", "Hardware & Lifespan", "Device & Warranty — Support & EOL tier"),
                        Triple("S2", "Port, Speed & PoE", "Cable & Wi-Fi Signal — Upstream switch jack"),
                        Triple("S3", "VLAN, IP & Firewall", "Network Lane — Trust ring & isolation"),
                        Triple("S4", "Firmware", "Built-in Software — Bootloader & OTA"),
                        Triple("S5", "OS, Kernel & Runtime", "System Software — Host OS & containers"),
                        Triple("S6", "Packages & Services", "Apps & Features — Exposed ports & PURLs")
                    ),
                    palette = palette
                )

                OrdinalScaleCard(
                    tag = "Scale Z",
                    title = "The 6 Inside-Out Trust Rings (Z0 Core → Z5 Internet)",
                    description = "Derived from CISA OT Asset Inventory & NSA Home Network Best Practices. Lower rings can initiate outward to higher rings, never backward.",
                    rows = listOf(
                        Triple("Z0", "Core Gear (VLAN 1)", "Wi-Fi & Network Gear — Routers, switches, APs"),
                        Triple("Z1", "Trusted (VLAN 10)", "Main Home Wi-Fi — Family laptops & phones"),
                        Triple("Z2", "Homelab (VLAN 20)", "Home Storage & Media — TrueNAS, Unraid, PVE"),
                        Triple("Z3", "Smart Home (VLAN 30)", "Smart Home Lane — TVs, cameras, IoT hubs"),
                        Triple("Z4", "Guest (VLAN 99)", "Guest Wi-Fi — Client-isolated visitor access"),
                        Triple("Z5", "Internet Uplink (WAN)", "Internet Doorway — Public ISP modem & WAN")
                    ),
                    palette = palette
                )

                OrdinalScaleCard(
                    tag = "Scale R",
                    title = "The 4 Operational Rhythms (R1 Event → R4 Annual)",
                    description = "Structures notifications and maintenance habits aligned with NIST CM-8 and NIST CSF without ITIL overhead.",
                    rows = listOf(
                        Triple("R1", "Plug & Tag (NIST CM-8)", "Name New Device — Instant on link-up"),
                        Triple("R2", "Monthly Patch Tuesday Readiness", "Monthly CVE/KEV & Firmware Triage — 2nd Tuesday"),
                        Triple("R3", "Seasonal Safety, Passwords & Drift Audit", "Seasonal Checkup — Every 90 days drift audit"),
                        Triple("R4", "Annual Gear Review", "Yearly Lifespan Check — Warranty & EOL")
                    ),
                    palette = palette
                )

                // Section 3: Classical OSI 7+1 Layer Model
                HorizontalDivider(color = palette.border)
                Text(
                    text = "🌐 The OSI 7+1 Layer Reference Model (L1–L8)",
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                val osiLayers = listOf(
                    Triple("LAYER 8", "The Human Layer (Users • Admins • Policy)", "Human approval workflows, advisory mute overrides, and quarterly audit accountability."),
                    Triple("LAYER 7", "Application (HTTP • DNS • RTSP • MQTT)", "Smart cameras (C5), IoT bridges (C6), Home Assistant, and application CVEs."),
                    Triple("LAYER 6", "Presentation (TLS/SSL • JSON • Encryption)", "Device TLS certificates and cryptographic firmware handshakes."),
                    Triple("LAYER 5", "Session (RPC • Sockets • WebRTC)", "Persistent camera streams and cloud telemetry tunnels."),
                    Triple("LAYER 4", "Transport (TCP • UDP • Port Numbers)", "Homelab servers (C4) listening on port daemons (443, 80, 53)."),
                    Triple("LAYER 3", "Network (IP Routing • Subnets • NAT)", "Core Gateways (C1: UDM-Pro / UXG) and VLAN routing boundaries."),
                    Triple("LAYER 2", "Data Link (MAC Frames • Switches • 802.1Q)", "Switch Backbone (C2) and the 16×8 LED matrix chassis (Ports 1–128)."),
                    Triple("LAYER 1", "Physical (Cat6 Copper • Fiber SFP+ • RF)", "Wireless Access Points (C3) and physical RJ45 switch jacks.")
                )

                osiLayers.forEachIndexed { idx, (badge, title, desc) ->
                    val isHuman = idx == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isHuman) TuesdayTheme.LedEolAmber.copy(alpha = 0.1f) else palette.elevated)
                            .border(
                                1.dp,
                                if (isHuman) TuesdayTheme.LedEolAmber else palette.border,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette.bg)
                                .border(1.dp, if (isHuman) TuesdayTheme.LedEolAmber else palette.border, RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = badge,
                                color = if (isHuman) TuesdayTheme.LedEolAmber else palette.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                color = palette.textMuted,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done Reading", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OrdinalScaleCard(
    tag: String,
    title: String,
    description: String,
    rows: List<Triple<String, String, String>>,
    palette: BrandPalette
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(palette.elevated)
            .border(1.dp, palette.border, RoundedCornerShape(9.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = tag,
                color = palette.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = description,
            color = palette.textMuted,
            fontSize = 10.sp,
            lineHeight = 13.5.sp
        )
        rows.forEach { (ord, pro, stn) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = ord,
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pro,
                        color = palette.textPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stn,
                        color = palette.textMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }
}
