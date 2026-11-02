package sh.tuesday.app.ui.bulletins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.*
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme

@Composable
fun BulletinsScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    payload: AssembledBulletinsPayload,
    onToggleMuteFeed: (String) -> Unit,
    onConfirmModel: (String) -> Unit,
    onRejectModel: () -> Unit
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // 1. Top-Level AI Summary Card (excludes muted feeds and provisional best-guess feeds)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStn) "✨ AI HOME BRIEFING • ACTIVE FEEDS" else "✦ AI BULLETIN DIGEST • UNMUTED FEEDS",
                        color = palette.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${payload.activeFeedCount}/${payload.matchedFeeds.size} Active",
                        color = palette.textMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (payload.aiBullets.isEmpty()) {
                    Text(
                        text = "All matched device feeds are currently muted. High-severity alerts still appear in your inbox below.",
                        color = palette.textMuted,
                        fontSize = 11.sp
                    )
                } else {
                    payload.aiBullets.forEach { bullet ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                text = bullet,
                                color = palette.textPrimary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Summary exclusion guardrail footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${payload.mutedFeedCount} Muted • ${payload.provisionalFeedCount} Best-Guess Excluded",
                        color = palette.textMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (payload.mutedHighSevOverrideCount > 0 || payload.provisionalHighSevCount > 0) {
                        Text(
                            text = "⚡ ${payload.mutedHighSevOverrideCount} Override • ❓ ${payload.provisionalHighSevCount} Guess",
                            color = TuesdayTheme.LedEolAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 2. Matched Device Feeds Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isStn) "MATCHED FEEDS FOR YOUR HOME (${payload.matchedFeeds.size})" else "MATCHED FEEDS (${payload.matchedFeeds.size} OF 148 BACKEND)",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "141 Unmatched Hidden",
                    color = palette.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 3. Matched Feed Rows (Confirmed Mute Toggles + Provisional Guess Disambiguation)
        items(payload.matchedFeeds, key = { it.id }) { feed ->
            val isProvisional = feed.matchMode == DeviceConfidenceTier.PROVISIONAL_GUESS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                fontSize = 11.sp,
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
                            fontSize = 9.sp,
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
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Rule 4 & 5: 1-Tap Model Disambiguation Chips for Provisional Best-Guess Devices
                if (isProvisional && feed.candidateModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        feed.candidateModels.forEach { candidate ->
                            Box(
                                modifier = Modifier
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

        // 4. Bulletin Inbox Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isStn) "HOME BULLETIN INBOX (${payload.inbox.size})" else "BULLETIN INBOX (${payload.inbox.size} ITEMS)",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Gold Lake Snapshot • 0 Live Calls",
                    color = TuesdayTheme.LedOkGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 5. Bulletin Inbox Cards
        items(payload.inbox, key = { it.externalId }) { item ->
            val accentStripe = when (item.bulletinType) {
                "recall", "cisa_kev", "cve" -> TuesdayTheme.LedCveRed
                "eol" -> TuesdayTheme.LedEolAmber
                else -> TuesdayTheme.LedUpdateBlue
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentStripe.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.bulletinType.uppercase(),
                                color = accentStripe,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (item.brokeThroughMute) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TuesdayTheme.LedEolAmber.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🔕 Muted • High-Sev Override",
                                    color = TuesdayTheme.LedEolAmber,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (item.matchedByGuess) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TuesdayTheme.LedGuessedGrey.copy(alpha = 0.28f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "❓ Best Guess",
                                    color = palette.textPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Text(
                        text = item.publishedAt,
                        color = palette.textMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = item.title,
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = item.body,
                    color = palette.textMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )

                if (item.matchedByGuess && item.candidateModels.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.accent.copy(alpha = 0.15f))
                                .border(1.dp, palette.accent, RoundedCornerShape(4.dp))
                                .clickable { onConfirmModel(item.candidateModels.first()) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "✓ Confirm: ${item.candidateModels.first()}",
                                color = palette.accent,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.elevated)
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .clickable { onRejectModel() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "✕ Not My Model",
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
    }
}
