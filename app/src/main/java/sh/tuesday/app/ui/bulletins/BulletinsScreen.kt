package sh.tuesday.app.ui.bulletins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.*
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.BulletinChecklistStep
import sh.tuesday.app.ui.components.BulletinPreferencesDialog
import sh.tuesday.app.ui.components.CandidateDisambiguationStrip
import sh.tuesday.app.ui.components.DetailModalDialog
import sh.tuesday.app.ui.components.InboxItemNotchCard
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.components.NotchBulletinCard
import sh.tuesday.app.ui.components.SectionHeading
import sh.tuesday.app.ui.components.StatusListRow
import sh.tuesday.app.ui.components.SurfaceListContainer
import sh.tuesday.app.ui.components.SurfaceListDivider
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@OptIn(ExperimentalLayoutApi::class)
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
    onNavigateToCheckUp: () -> Unit = {},
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
    onSetBulletinMuted: (Boolean) -> Unit = {},
    initialBulletinId: String? = null,
    onConsumeInitialBulletin: () -> Unit = {}
) {
    var showIgnoreDialog by remember { mutableStateOf(false) }
    var expandedModal by remember { mutableStateOf<ExpandedBulletinModal?>(null) }

    LaunchedEffect(initialBulletinId, payload) {
        if (!initialBulletinId.isNullOrBlank()) {
            val activeMatch = payload.inbox.find { it.externalId == initialBulletinId }
            val completedMatch = payload.completedBulletins.find { it.externalId == initialBulletinId }
            when {
                activeMatch != null -> expandedModal = ExpandedBulletinModal.Inbox(activeMatch, isCompleted = false)
                completedMatch != null -> expandedModal = ExpandedBulletinModal.Inbox(completedMatch, isCompleted = true)
            }
            onConsumeInitialBulletin()
        }
    }

    val step1Done = activeDeviceCount > 0
    val step2Done = remindersEnabled
    val step3Done = dispatchSubscribed
    val allStepsDone = step1Done && step2Done && step3Done
    val isComplete = allStepsDone || bulletinDismissed

    expandedModal?.let { modal ->
        DetailModalDialog(
            palette = palette,
            onDismissRequest = { expandedModal = null }
        ) {
            when (modal) {
                is ExpandedBulletinModal.Inbox -> {
                    InboxItemNotchCard(
                        palette = palette,
                        item = modal.item,
                        onClick = {
                            expandedModal = null
                            onInspectDevice(modal.item.externalId)
                        },
                        onConfirmModel = onConfirmModel,
                        onRejectModel = onRejectModel,
                        isCompleted = modal.isCompleted
                    )
                }
                is ExpandedBulletinModal.Welcome -> {
                    WelcomeNotchBulletinCard(
                        palette = palette,
                        isCompleted = modal.isCompleted,
                        step1Done = step1Done,
                        step2Done = step2Done,
                        step3Done = step3Done,
                        allStepsDone = allStepsDone,
                        onNavigateToAddDevice = {
                            expandedModal = null
                            onNavigateToAddDevice()
                        },
                        onEnableReminders = onEnableReminders,
                        onSetDispatchSubscribed = onSetDispatchSubscribed,
                        onNavigateToCheckUp = {
                            expandedModal = null
                            onNavigateToCheckUp()
                        },
                        onSetBulletinDismissed = { dismissed ->
                            if (dismissed) expandedModal = null
                            onSetBulletinDismissed(dismissed)
                        },
                        onSetBulletinSnoozed = { snoozed ->
                            if (snoozed) expandedModal = null
                            onSetBulletinSnoozed(snoozed)
                        },
                        onShowIgnoreDialog = {
                            expandedModal = null
                            showIgnoreDialog = true
                        }
                    )
                }
                is ExpandedBulletinModal.Feed -> {
                    MatchedFeedCard(
                        palette = palette,
                        feed = modal.feed,
                        onToggleMuteFeed = { slug ->
                            expandedModal = null
                            onToggleMuteFeed(slug)
                        },
                        onConfirmModel = { model ->
                            expandedModal = null
                            onConfirmModel(model)
                        },
                        onRejectModel = {
                            expandedModal = null
                            onRejectModel()
                        }
                    )
                }
            }
        }
    }

    if (showIgnoreDialog) {
        BulletinPreferencesDialog(
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("BulletinsScreen")
            .padding(horizontal = TuesdayDimens.ScreenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(0.dp)
                    .uiComponent("AiSummaryCard")
            ) {
                Box(
                    modifier = Modifier
                        .size(0.dp)
                        .uiSlot("ai-summary-card")
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiComponent("BulletinInboxList")
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading(
                    text = "Bulletin Inbox",
                    palette = palette
                )
            }
        }

        if (bulletinMuted) {
            item(key = "rhythm-muted-banner") {
                RhythmSnoozeOrMuteBanner(
                    palette = palette,
                    slotMeta = "id=bul-00-rhythm;type=muted",
                    badgeText = "🔕 MUTED",
                    title = "Patch Tuesday Monthly Rhythm Muted",
                    description = "Monthly checklist reminders are muted. High-severity CVEs and hardware recalls will continue to alert you.",
                    actionLabel = "Unmute →",
                    onAction = {
                        onSetBulletinMuted(false)
                        onSetBulletinDismissed(false)
                        onSetBulletinSnoozed(false)
                    }
                )
            }
        } else if (bulletinSnoozed) {
            item(key = "rhythm-snoozed-banner") {
                RhythmSnoozeOrMuteBanner(
                    palette = palette,
                    slotMeta = "id=bul-00-rhythm;type=snoozed",
                    badgeText = "💤 SNOOZED",
                    title = "Patch Tuesday Readiness Reminder",
                    description = "Snoozed until tomorrow at 10:00 AM. Tap resume to see your 3 readiness actions.",
                    actionLabel = "Resume →",
                    onAction = {
                        onSetBulletinSnoozed(false)
                        onSetBulletinDismissed(false)
                    }
                )
            }
        }

        val showActiveWelcome = !bulletinDismissed && !bulletinSnoozed && !bulletinMuted && !isComplete
        if (showActiveWelcome || payload.inbox.isNotEmpty()) {
            item(key = "inbox-listview") {
                BulletinListView(
                    palette = palette,
                    items = payload.inbox,
                    isCompleted = false,
                    showWelcomeRow = showActiveWelcome,
                    onSelectWelcome = { expandedModal = ExpandedBulletinModal.Welcome(isCompleted = false) },
                    onSelectBulletin = { item -> expandedModal = ExpandedBulletinModal.Inbox(item, isCompleted = false) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading(
                    text = "Matched Feeds",
                    palette = palette
                )
                Box(
                    modifier = Modifier
                        .size(TuesdayDimens.HitTargetCompact)
                        .uiAction("openFeedsSettings")
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                        .clickable { onOpenFeedsSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Feeds settings",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (payload.matchedFeeds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add devices to your inventory to get feeds",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (payload.matchedFeeds.isNotEmpty()) {
            item(key = "matched-feeds-listview") {
                MatchedFeedsListView(
                    palette = palette,
                    feeds = payload.matchedFeeds,
                    onSelectFeed = { feed -> expandedModal = ExpandedBulletinModal.Feed(feed) }
                )
            }
        }

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
                    SectionHeading(
                        text = "Completed",
                        palette = palette
                    )
                }
            }

            if (isComplete || payload.completedBulletins.isNotEmpty()) {
                item(key = "completed-listview") {
                    BulletinListView(
                        palette = palette,
                        items = payload.completedBulletins,
                        isCompleted = true,
                        showWelcomeRow = isComplete,
                        onSelectWelcome = { expandedModal = ExpandedBulletinModal.Welcome(isCompleted = true) },
                        onSelectBulletin = { item -> expandedModal = ExpandedBulletinModal.Inbox(item, isCompleted = true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RhythmSnoozeOrMuteBanner(
    palette: BrandPalette,
    slotMeta: String,
    badgeText: String,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiSlot("bulletin-inbox-item", slotMeta)
            .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
            .background(palette.surface.copy(alpha = 0.9f))
            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonospaceBadge(
                    text = badgeText,
                    color = palette.textMuted
                )
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 28.dp)
                    .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                    .border(1.dp, palette.accent, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    color = palette.accentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Text(
            text = description,
            color = palette.textMuted,
            fontSize = 11.5.sp,
            lineHeight = 15.5.sp
        )
    }
}

private sealed interface ExpandedBulletinModal {
    data class Inbox(val item: BulletinInboxView, val isCompleted: Boolean) : ExpandedBulletinModal
    data class Welcome(val isCompleted: Boolean) : ExpandedBulletinModal
    data class Feed(val feed: MatchedFeedView) : ExpandedBulletinModal
}

@Composable
private fun WelcomeNotchBulletinCard(
    palette: BrandPalette,
    isCompleted: Boolean,
    step1Done: Boolean,
    step2Done: Boolean,
    step3Done: Boolean,
    allStepsDone: Boolean,
    onNavigateToAddDevice: () -> Unit,
    onEnableReminders: () -> Unit,
    onSetDispatchSubscribed: (Boolean) -> Unit,
    onNavigateToCheckUp: () -> Unit,
    onSetBulletinDismissed: (Boolean) -> Unit,
    onSetBulletinSnoozed: (Boolean) -> Unit,
    onShowIgnoreDialog: () -> Unit
) {
    val context = LocalContext.current
    val steps = listOf(
        BulletinChecklistStep(
            id = "step1",
            num = "1",
            title = "Inventory Your Network",
            description = "Kick off Plug & Tag intake to map active routers, switches, NAS, and IoT nodes.",
            linkText = if (isCompleted || step1Done) "✓ Added" else "Add Device →",
            isDone = isCompleted || step1Done,
            onClick = onNavigateToAddDevice
        ),
        BulletinChecklistStep(
            id = "step2",
            num = "2",
            title = "Enable Push Reminders",
            description = "Receive an alert at 10:00 AM PT on Patch Tuesday with your prioritized triage list.",
            linkText = if (isCompleted || step2Done) "✓ Active" else "Enable →",
            isDone = isCompleted || step2Done,
            onClick = onEnableReminders
        ),
        BulletinChecklistStep(
            id = "step3",
            num = "3",
            title = "Subscribe to the Dispatch",
            description = "Get human-curated patch analysis and firmware changelogs delivered every cycle.",
            linkText = if (isCompleted || step3Done) "✓ Subscribed" else "Dispatch ↗",
            isDone = isCompleted || step3Done,
            onClick = {
                onSetDispatchSubscribed(true)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tuesday.sh/dispatch"))
                context.startActivity(intent)
            }
        )
    )

    if (isCompleted) {
        NotchBulletinCard(
            palette = palette,
            title = "Patch Tuesday Check-Up • Complete",
            description = "Enrolled & monitored. Inventory mapped, 10:00 AM PT push alerts enabled, monthly dispatch active.",
            leftNotchLabel = "CHECK-UP COMPLETE",
            leftNotchDotColor = TuesdayTheme.LedOkGreen,
            rightNotchLabel = "CHECK-UP",
            onRightNotchClick = onNavigateToCheckUp,
            steps = steps,
            allStepsDone = true,
            primaryActionLabel = "Completed",
            completeActionLabel = "Completed",
            secondaryActionLabel = "Reopen",
            onPrimaryAction = {},
            onSecondaryAction = {
                onSetBulletinDismissed(false)
                onSetBulletinSnoozed(false)
            }
        )
    } else {
        NotchBulletinCard(
            palette = palette,
            title = "Patch Tuesday Readiness",
            description = "Get your perimeter cataloged before vendors push critical firmware and OS fixes.",
            leftNotchLabel = "BULLETIN",
            rightNotchLabel = "CHECK-UP",
            onRightNotchClick = onNavigateToCheckUp,
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
            onSecondaryAction = onShowIgnoreDialog
        )
    }
}

@Composable
private fun BulletinListView(
    palette: BrandPalette,
    items: List<BulletinInboxView>,
    isCompleted: Boolean,
    showWelcomeRow: Boolean = false,
    onSelectWelcome: () -> Unit = {},
    onSelectBulletin: (BulletinInboxView) -> Unit
) {
    SurfaceListContainer(palette = palette) {
        if (showWelcomeRow) {
            WelcomeBulletinListRow(
                palette = palette,
                isCompleted = isCompleted,
                onClick = onSelectWelcome
            )
            if (items.isNotEmpty()) {
                SurfaceListDivider(palette)
            }
        }
        items.forEachIndexed { index, item ->
            BulletinListRow(
                palette = palette,
                item = item,
                isCompleted = isCompleted,
                onClick = { onSelectBulletin(item) }
            )
            if (index < items.lastIndex) {
                SurfaceListDivider(palette)
            }
        }
    }
}

@Composable
private fun WelcomeBulletinListRow(
    palette: BrandPalette,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val accentColor = if (isCompleted) TuesdayTheme.LedOkGreen else palette.accentText
    StatusListRow(
        palette = palette,
        dotColor = if (isCompleted) TuesdayTheme.LedOkGreen else palette.accent,
        title = if (isCompleted) "Patch Tuesday Check-Up • Complete" else "Patch Tuesday Readiness",
        onClick = onClick,
        contentDescription = "Expand welcome bulletin",
        modifier = Modifier.uiSlot("bulletin-inbox-item", "id=bul-00-rhythm;type=welcome;completed=$isCompleted")
    ) {
        Text(
            text = if (isCompleted) "CHECK-UP COMPLETE" else "BULLETIN • CHECK-UP",
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun BulletinListRow(
    palette: BrandPalette,
    item: BulletinInboxView,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val accentColor = if (isCompleted) {
        TuesdayTheme.LedOkGreen
    } else {
        when (item.bulletinType.lowercase()) {
            "recall", "cisa_kev", "cve" -> TuesdayTheme.LedCveRed
            "eol" -> TuesdayTheme.LedEolAmber
            else -> TuesdayTheme.LedUpdateBlue
        }
    }

    StatusListRow(
        palette = palette,
        dotColor = accentColor,
        title = item.title,
        onClick = onClick,
        contentDescription = "Expand bulletin",
        modifier = Modifier.uiSlot("bulletin-inbox-item", "id=${item.externalId};type=${item.bulletinType};completed=$isCompleted")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.bulletinType.uppercase(),
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (isCompleted) {
                Text(
                    text = "• RESOLVED",
                    color = TuesdayTheme.LedOkGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            } else if (item.brokeThroughMute) {
                Text(
                    text = "• HIGH-SEV",
                    color = TuesdayTheme.LedEolAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MatchedFeedsListView(
    palette: BrandPalette,
    feeds: List<MatchedFeedView>,
    onSelectFeed: (MatchedFeedView) -> Unit
) {
    SurfaceListContainer(
        palette = palette,
        modifier = Modifier.uiComponent("MatchedFeedsSection")
    ) {
        feeds.forEachIndexed { index, feed ->
            MatchedFeedListRow(
                palette = palette,
                feed = feed,
                onClick = { onSelectFeed(feed) }
            )
            if (index < feeds.lastIndex) {
                SurfaceListDivider(palette)
            }
        }
    }
}

@Composable
private fun MatchedFeedListRow(
    palette: BrandPalette,
    feed: MatchedFeedView,
    onClick: () -> Unit
) {
    val isProvisional = feed.matchMode == DeviceConfidenceTier.PROVISIONAL_GUESS
    val dotColor = when {
        isProvisional -> TuesdayTheme.LedGuessedGrey
        feed.isMuted -> palette.textMuted
        else -> TuesdayTheme.LedOkGreen
    }

    StatusListRow(
        palette = palette,
        dotColor = dotColor,
        title = feed.name,
        onClick = onClick,
        contentDescription = "Expand feed",
        modifier = Modifier.uiSlot("matched-feed-row", "slug=${feed.slug};muted=${feed.isMuted}")
    ) {
        Text(
            text = "Matched: ${feed.matchedPorts.joinToString(", ")}",
            color = palette.textMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchedFeedCard(
    palette: BrandPalette,
    feed: MatchedFeedView,
    onToggleMuteFeed: (String) -> Unit,
    onConfirmModel: (String) -> Unit,
    onRejectModel: () -> Unit
) {
    val isProvisional = feed.matchMode == DeviceConfidenceTier.PROVISIONAL_GUESS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
            .background(palette.surface)
            .border(
                1.dp,
                if (isProvisional) TuesdayTheme.LedGuessedGrey else palette.border,
                RoundedCornerShape(TuesdayDimens.RadiusCard)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = feed.name,
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isProvisional) {
                        MonospaceBadge(
                            text = "❓ 72% GUESS",
                            color = palette.textMuted
                        )
                    }
                }
                Text(
                    text = "Matched: ${feed.matchedPorts.joinToString(", ")}",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (isProvisional) {
                MonospaceBadge(
                    text = "⚡ High-Sev Only",
                    color = palette.textMuted
                )
            } else {
                Box(
                    modifier = Modifier
                        .heightIn(min = 28.dp)
                        .uiAction("toggleMuteFeed")
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                        .background(
                            if (feed.isMuted) TuesdayTheme.LedEolAmber.copy(alpha = 0.16f)
                            else palette.elevated
                        )
                        .border(
                            1.dp,
                            if (feed.isMuted) TuesdayTheme.LedEolAmber else palette.border,
                            RoundedCornerShape(TuesdayDimens.RadiusBadge)
                        )
                        .clickable { onToggleMuteFeed(feed.slug) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (feed.isMuted) "🔕 Muted" else "🔔 Active",
                        color = if (feed.isMuted) TuesdayTheme.LedEolAmber else palette.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }

        if (isProvisional && feed.candidateModels.isNotEmpty()) {
            if (feed.candidateModels.size == 1) {
                CandidateDisambiguationStrip(
                    candidateModel = feed.candidateModels.first(),
                    palette = palette,
                    onConfirmModel = onConfirmModel,
                    onRejectModel = onRejectModel,
                    confirmModifier = Modifier.uiAction("confirmGuessedModel"),
                    rejectModifier = Modifier.uiAction("rejectGuessedModel")
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    feed.candidateModels.forEach { candidate ->
                        Box(
                            modifier = Modifier
                                .heightIn(min = 28.dp)
                                .uiAction("confirmGuessedModel")
                                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                .background(palette.accent.copy(alpha = 0.15f))
                                .border(1.dp, palette.accent, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                .clickable { onConfirmModel(candidate) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓ $candidate",
                                color = palette.accentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 28.dp)
                            .uiAction("rejectGuessedModel")
                            .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                            .background(palette.elevated)
                            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                            .clickable { onRejectModel() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕ Not Mine",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
