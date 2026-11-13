package sh.tuesday.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.StnHardwareCategory
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.DetailModalDialog
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent

@Composable
fun ReferenceGuideModalDialog(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    onDismiss: () -> Unit
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val scrollState = rememberScrollState()
    val cardShape = RoundedCornerShape(TuesdayDimens.RadiusModalCard)

    DetailModalDialog(
        palette = palette,
        onDismissRequest = onDismiss,
        closeActionModifier = Modifier.uiAction("closeReferenceGuide")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .uiComponent("ReferenceGuideModal")
                .clip(cardShape)
                .background(palette.surface)
                .border(1.dp, palette.border, cardShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonospaceBadge(
                    text = "REFERENCE GUIDE",
                    color = palette.accentText
                )
                Text(
                    text = "SureThingNet Standard & Ordinal Spine",
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "SureThingNet Standard Categories (Scale C)",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "The 8 semantic hardware categories (C1 → C8) that govern our faceplate port strip, inventory classification, and progressive disclosure model.",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp
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
                                    .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                                    .background(palette.elevated)
                                    .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
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
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MonospaceBadge(
                                        text = "${cat.code}•${cat.osiLayer}",
                                        color = palette.accentText,
                                        borderAlpha = 0f
                                    )
                                }
                                Text(
                                    text = cat.description,
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 14.5.sp
                                )
                                Text(
                                    text = cat.examples,
                                    color = palette.accentText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

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
                            .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                            .background(if (isHuman) TuesdayTheme.LedEolAmber.copy(alpha = 0.1f) else palette.elevated)
                            .border(
                                1.dp,
                                if (isHuman) TuesdayTheme.LedEolAmber else palette.border,
                                RoundedCornerShape(TuesdayDimens.RadiusControl)
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        MonospaceBadge(
                            text = badge,
                            color = if (isHuman) TuesdayTheme.LedEolAmber else palette.accentText
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                color = palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(TuesdayDimens.RadiusControl)
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
            .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
            .background(palette.elevated)
            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = tag,
                color = palette.accentText,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = description,
            color = palette.textMuted,
            fontSize = 11.sp,
            lineHeight = 14.5.sp
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
                    color = palette.accentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pro,
                        color = palette.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stn,
                        color = palette.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
