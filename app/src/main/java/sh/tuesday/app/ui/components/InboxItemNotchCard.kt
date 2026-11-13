package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.BulletinInboxView
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiSlot

/**
 * Standard bulletin inbox item rendered using [SingleNotchCard], [MonospaceBadge],
 * and [CandidateDisambiguationStrip].
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
            .uiSlot("bulletin-inbox-item", "id=${item.externalId};type=${item.bulletinType};completed=$isCompleted"),
        topEndHeader = {
            if (isCompleted) {
                MonospaceBadge(
                    text = "✓ RESOLVED",
                    color = TuesdayTheme.LedOkGreen
                )
            } else if (item.brokeThroughMute) {
                MonospaceBadge(
                    text = "🔕 High-Sev",
                    color = TuesdayTheme.LedEolAmber
                )
            }

            if (item.matchedByGuess) {
                MonospaceBadge(
                    text = "❓ Guess",
                    color = palette.textMuted
                )
            }

            Text(
                text = item.publishedAt,
                color = palette.textMuted,
                fontSize = 11.sp,
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
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal
        )

        if (!item.sourceUrl.isNullOrBlank()) {
            val uriHandler = LocalUriHandler.current
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .heightIn(min = 28.dp)
                    .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                    .background(palette.elevated)
                    .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                    .clickable { runCatching { uriHandler.openUri(item.sourceUrl) } }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↗ View Original Notice",
                    color = palette.accentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (item.matchedByGuess && item.candidateModels.isNotEmpty()) {
            CandidateDisambiguationStrip(
                candidateModel = item.candidateModels.first(),
                palette = palette,
                onConfirmModel = onConfirmModel,
                onRejectModel = onRejectModel
            )
        }
    }
}
