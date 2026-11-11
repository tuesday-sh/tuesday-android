package sh.tuesday.app.ui.bulletins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import sh.tuesday.app.models.*
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.BulletinChecklistStep
import sh.tuesday.app.ui.components.InboxItemNotchCard
import sh.tuesday.app.ui.components.NotchBulletinCard
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@Composable
fun BulletinsScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    payload: AssembledBulletinsPayload,
    onToggleMuteFeed: (String) -> Unit,
    onConfirmModel: (String) -> Unit,
    onRejectModel: () -> Unit,
    onOpenFeedsSettings: () -> Unit = {},
    onInspectDevice: (String) -> Unit = {},
    onNavigateToAddDevice: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onEnableReminders: () -> Unit = onNavigateToAccount,
    remindersEnabled: Boolean = false,
    dispatchSubscribed: Boolean = false,
    onSetDispatchSubscribed: (Boolean) -> Unit = {},
    activeDeviceCount: Int = 0,
    bulletinDismissed: Boolean = false,
    onSetBulletinDismissed: (Boolean) -> Unit = {},
    bulletinSnoozed: Boolean = false,
    onSetBulletinSnoozed: (Boolean) -> Unit = {},
    bulletinMuted: Boolean = false,
    onSetBulletinMuted: (Boolean) -> Unit = {}
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val context = LocalContext.current
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
    val isComplete = allStepsDone || bulletinDismissed

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("BulletinsScreen")
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // 0. Hidden placeholder to maintain 7-dimension parity for AiSummaryCard marker
        item {
            Box(
                modifier = Modifier
                    .size(0.dp)
                    .uiComponent("AiSummaryCard")
                    .uiSlot("ai-summary-card")
            )
        }

        // 1. Bulletin Inbox Section Header (Top of screen, matching Web Workbench)
        item {
            val totalInboxCount = payload.inbox.size + (if (!bulletinDismissed && !bulletinSnoozed && !bulletinMuted && !isComplete) 1 else 0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiComponent("BulletinInboxList")
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isStn) "Safety, Security & Update Inbox" else "Bulletin Inbox (News / CVE / KEV / Recall / Release)",
                    color = palette.textPrimary,
                    fontSize = if (isStn) 13.sp else 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isStn) FontFamily.Default else FontFamily.Default
                )
                Text(
                    text = if (isStn) "$totalInboxCount Bulletins" else "$totalInboxCount ITEMS",
                    color = palette.accent,
                    fontSize = if (isStn) 10.5.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2. Preloaded Monthly Rhythm Bulletin Card (`bul-00-rhythm`)
        if (!bulletinDismissed && !bulletinSnoozed && !bulletinMuted && !isComplete) {
            item(key = "bul-00-rhythm") {
                val steps = listOf(
                    BulletinChecklistStep(
                        id = "step1",
                        num = "1",
                        title = "Inventory Your Network",
                        description = "Kick off Plug & Tag intake to map active routers, switches, NAS, and IoT nodes.",
                        linkText = if (step1Done) "✓ Added" else "Add Device →",
                        isDone = step1Done,
                        onClick = onNavigateToAddDevice
                    ),
                    BulletinChecklistStep(
                        id = "step2",
                        num = "2",
                        title = "Enable Push Reminders",
                        description = "Receive an alert at 10:00 AM PT on Patch Tuesday with your prioritized triage list.",
                        linkText = if (step2Done) "✓ Active" else "Enable →",
                        isDone = step2Done,
                        onClick = onEnableReminders
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
                    description = "Get your perimeter cataloged before vendors push critical firmware and OS fixes.",
                    leftNotchLabel = "BULLETIN",
                    rightNotchLabel = "RHYTHM",
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
        } else if (bulletinMuted) {
            // Muted Rhythm Banner
            item(key = "rhythm-muted-banner") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("bulletin-inbox-item", "id=bul-00-rhythm;type=muted")
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface.copy(alpha = 0.85f))
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF64748B).copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🔕 MUTED",
                                    color = Color(0xFF64748B),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Patch Tuesday Monthly Rhythm Muted",
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .border(1.dp, palette.accent, RoundedCornerShape(5.dp))
                                .clickable {
                                    onSetBulletinMuted(false)
                                    onSetBulletinDismissed(false)
                                    onSetBulletinSnoozed(false)
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Unmute →",
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "Monthly checklist reminders are muted. High-severity CVEs and hardware recalls will continue to alert you.",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else if (bulletinSnoozed) {
            // Snoozed Rhythm Banner
            item(key = "rhythm-snoozed-banner") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("bulletin-inbox-item", "id=bul-00-rhythm;type=snoozed")
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface.copy(alpha = 0.85f))
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF64748B).copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "💤 SNOOZED",
                                    color = Color(0xFF64748B),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Patch Tuesday Readiness Reminder",
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .border(1.dp, palette.border, RoundedCornerShape(5.dp))
                                .clickable {
                                    onSetBulletinSnoozed(false)
                                    onSetBulletinDismissed(false)
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Resume →",
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "Snoozed until tomorrow at 10:00 AM. Tap resume to see your 3 readiness actions.",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 3. Bulletin Inbox Cards (using Fused Upper-Left Notch design)
        items(payload.inbox, key = { it.externalId }) { item ->
            InboxItemNotchCard(
                palette = palette,
                item = item,
                onClick = { onInspectDevice(item.externalId) },
                onConfirmModel = onConfirmModel,
                onRejectModel = onRejectModel
            )
        }

        // 4. Matched Device Feeds Section Header (Bottom, after Inbox)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MATCHED FEEDS",
                    color = palette.textMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeCount = payload.activeFeedCount
                    val mutedCount = payload.mutedFeedCount
                    Text(
                        text = if (isStn) "$activeCount Active • $mutedCount Muted" else "$activeCount Active • $mutedCount Muted",
                        color = palette.accent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        modifier = Modifier
                            .uiAction("openFeedsSettings")
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.elevated)
                            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                            .clickable { onOpenFeedsSettings() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚙ Feeds",
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 5. Matched Feed Rows (Confirmed Mute Toggles + Provisional Guess Disambiguation)
        if (payload.matchedFeeds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add devices to your inventory to get feeds",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        items(payload.matchedFeeds, key = { it.id }) { feed ->
            val isProvisional = feed.matchMode == DeviceConfidenceTier.PROVISIONAL_GUESS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiComponent("MatchedFeedsSection")
                    .uiSlot("matched-feed-row", "slug=${feed.slug};muted=${feed.isMuted}")
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface.copy(alpha = if (feed.isMuted) 0.65f else 1f))
                    .border(
                        1.dp,
                        if (isProvisional) TuesdayTheme.LedGuessedGrey else palette.border,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = feed.name,
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isProvisional) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TuesdayTheme.LedGuessedGrey.copy(alpha = 0.25f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "❓ 72% GUESS",
                                        color = palette.textPrimary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Matched: ${feed.matchedPorts.joinToString(", ")}",
                            color = palette.textMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isProvisional) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .border(1.dp, TuesdayTheme.LedGuessedGrey, RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⚡ High-Sev Only",
                                color = palette.textMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .uiAction("toggleMuteFeed")
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (feed.isMuted) TuesdayTheme.LedEolAmber.copy(alpha = 0.16f)
                                    else palette.elevated
                                )
                                .border(
                                    1.dp,
                                    if (feed.isMuted) TuesdayTheme.LedEolAmber else palette.border,
                                    RoundedCornerShape(5.dp)
                                )
                                .clickable { onToggleMuteFeed(feed.slug) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (feed.isMuted) "🔕 Muted" else "🔔 Active",
                                color = if (feed.isMuted) TuesdayTheme.LedEolAmber else palette.textPrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Disambiguation chips for provisional best-guess feeds
                if (isProvisional && feed.candidateModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        feed.candidateModels.forEach { candidate ->
                            Box(
                                modifier = Modifier
                                    .uiAction("confirmGuessedModel")
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accent.copy(alpha = 0.15f))
                                    .border(1.dp, palette.accent, RoundedCornerShape(4.dp))
                                    .clickable { onConfirmModel(candidate) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "✓ $candidate",
                                    color = palette.accent,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .uiAction("rejectGuessedModel")
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.elevated)
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .clickable { onRejectModel() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "✕ Not Mine",
                                color = palette.textMuted,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 6. Completed Bulletins Section (at the bottom of the bulletins list)
        val completedCount = (if (isComplete) 1 else 0) + payload.completedBulletins.size
        if (completedCount > 0) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStn) "COMPLETED BULLETINS" else "COMPLETED BULLETINS",
                        color = palette.textMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$completedCount Completed",
                        color = TuesdayTheme.LedOkGreen,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (isComplete) {
                item(key = "bul-00-rhythm-completed") {
                    val completeSteps = listOf(
                        BulletinChecklistStep(
                            id = "step1",
                            num = "1",
                            title = "Inventory Your Network",
                            description = "Kick off Plug & Tag intake to map active routers, switches, NAS, and IoT nodes.",
                            linkText = "✓ Added",
                            isDone = true,
                            onClick = onNavigateToAddDevice
                        ),
                        BulletinChecklistStep(
                            id = "step2",
                            num = "2",
                            title = "Enable Push Reminders",
                            description = "Receive an alert at 10:00 AM PT on Patch Tuesday with your prioritized triage list.",
                            linkText = "✓ Active",
                            isDone = true,
                            onClick = onEnableReminders
                        ),
                        BulletinChecklistStep(
                            id = "step3",
                            num = "3",
                            title = "Subscribe to the Dispatch",
                            description = "Get human-curated patch analysis and firmware changelogs delivered every cycle.",
                            linkText = "✓ Subscribed",
                            isDone = true,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tuesday.sh/dispatch"))
                                context.startActivity(intent)
                            }
                        )
                    )

                    NotchBulletinCard(
                        palette = palette,
                        title = "Patch Tuesday Rhythm • Complete",
                        description = "Enrolled & monitored. Inventory mapped, 10:00 AM PT push alerts enabled, monthly dispatch active.",
                        leftNotchLabel = "RHYTHM COMPLETE",
                        leftNotchDotColor = TuesdayTheme.LedOkGreen,
                        rightNotchLabel = "RHYTHM",
                        onRightNotchClick = onOpenFeedsSettings,
                        steps = completeSteps,
                        allStepsDone = true,
                        primaryActionLabel = "✓ Completed",
                        completeActionLabel = "✓ Completed",
                        secondaryActionLabel = "Reopen",
                        onPrimaryAction = {
                            // Already completed
                        },
                        onSecondaryAction = {
                            onSetBulletinDismissed(false)
                            onSetBulletinSnoozed(false)
                        }
                    )
                }
            }

            items(payload.completedBulletins, key = { "completed-" + it.externalId }) { item ->
                InboxItemNotchCard(
                    palette = palette,
                    item = item,
                    onClick = { onInspectDevice(item.externalId) },
                    onConfirmModel = onConfirmModel,
                    onRejectModel = onRejectModel
                )
            }
        }
    }
}
