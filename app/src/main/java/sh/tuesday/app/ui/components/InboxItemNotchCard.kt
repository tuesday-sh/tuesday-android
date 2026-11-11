package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.BulletinInboxView
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiSlot

/**
 * Standard bulletin inbox item rendered using [SingleNotchCard].
 *
 * Provides upper-left fused notch with severity LED dot and type label,
 * upper-right auxiliary tags, and generous breathing space before the headline text.
 */
@Composable
fun InboxItemNotchCard(
    palette: BrandPalette,
    item: BulletinInboxView,
    onClick: () -> Unit,
    onConfirmModel: (String) -> Unit = {},
    onRejectModel: () -> Unit = {},
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
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

    val notchText = if (isCompleted) {
        "✓ ${item.bulletinType.uppercase()} REMEDIATED"
    } else {
        item.bulletinType.uppercase()
    }

    SingleNotchCard(
        palette = palette,
        accentColor = accentColor,
        notchLabel = notchText,
        onClick = onClick,
        cardBorderColor = if (isCompleted) TuesdayTheme.LedOkGreen.copy(alpha = 0.4f) else accentColor.copy(alpha = 0.35f),
        modifier = modifier
            .uiSlot("bulletin-inbox-item", "id=${item.externalId};type=${item.bulletinType};completed=$isCompleted")
            .uiAction("inspectDevice"),
        topEndHeader = {
            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TuesdayTheme.LedOkGreen.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "✓ RESOLVED",
                        color = TuesdayTheme.LedOkGreen,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else if (item.brokeThroughMute) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TuesdayTheme.LedEolAmber.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "🔕 High-Sev",
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
                        text = "❓ Guess",
                        color = palette.textPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = item.publishedAt,
                color = palette.textMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        },
        contentTopPadding = 42.dp
    ) {
        Text(
            text = item.title,
            color = if (isCompleted) palette.textPrimary.copy(alpha = 0.88f) else palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 17.5.sp
        )

        Text(
            text = if (isCompleted) "✓ Remediation applied. All associated devices are protected and marked OK." else item.body,
            color = if (isCompleted) TuesdayTheme.LedOkGreen else palette.textMuted,
            fontSize = 11.5.sp,
            lineHeight = 15.5.sp,
            fontWeight = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal
        )

        if (!item.sourceUrl.isNullOrBlank()) {
            val uriHandler = LocalUriHandler.current
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.elevated)
                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                    .clickable { runCatching { uriHandler.openUri(item.sourceUrl) } }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "↗ View Original Notice",
                    color = palette.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (item.matchedByGuess && item.candidateModels.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
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
