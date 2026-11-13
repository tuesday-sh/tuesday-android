package sh.tuesday.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens

/**
 * Canonical >= 11.sp monospace status pill badge (`design-tokens.json` actionsAndPillsMinPx = 11.0).
 */
@Composable
fun MonospaceBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fillAlpha: Float = 0.14f,
    borderAlpha: Float = 0.40f
) {
    val shape = RoundedCornerShape(TuesdayDimens.RadiusMicro)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = fillAlpha))
            .then(
                if (borderAlpha > 0f) Modifier.border(1.dp, color.copy(alpha = borderAlpha), shape)
                else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Shared WCAG-compliant (`>= 28.dp` height) filter chip used across state filters, zone pills, and zoom pills.
 */
@Composable
fun SelectableFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    accentColor: Color = palette.accent,
    leadingDotColor: Color? = null
) {
    val shape = RoundedCornerShape(TuesdayDimens.RadiusBadge)
    val bgColor = if (selected) accentColor.copy(alpha = 0.18f) else palette.elevated
    val borderColor = if (selected) accentColor else palette.border
    val textColor = if (selected) accentColor else palette.textMuted

    Row(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingDotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.5.dp)
                    .clip(CircleShape)
                    .background(leadingDotColor)
            )
        }
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Shared segmented tab control (`Photo` | `Scan` | `Manual`, `New` | `Join`, etc.).
 */
@Composable
fun SegmentedTabRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    useAccentFillOnSelected: Boolean = true,
    badgeForIndex: (Int) -> String? = { null },
    itemModifier: (Int) -> Modifier = { Modifier }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
            .background(if (useAccentFillOnSelected) palette.bg else palette.elevated)
            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val badgeText = badgeForIndex(index)
            Surface(
                onClick = { onSelect(index) },
                modifier = itemModifier(index)
                    .weight(1f)
                    .heightIn(min = TuesdayDimens.HitTargetCompact),
                shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                color = when {
                    selected && useAccentFillOnSelected -> palette.accent
                    selected -> palette.surface
                    else -> Color.Transparent
                },
                border = if (selected && !useAccentFillOnSelected) BorderStroke(1.dp, palette.border) else null
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = label,
                            color = when {
                                selected && useAccentFillOnSelected -> Color.White
                                selected -> palette.textPrimary
                                else -> palette.textMuted
                            },
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (!badgeText.isNullOrBlank()) {
                            MonospaceBadge(
                                text = badgeText,
                                color = if (selected && useAccentFillOnSelected) Color.White else palette.accent,
                                borderAlpha = 0f
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Canonical 36.dp circular `?` reference-guide button meeting WCAG 2.2 SC 2.5.8 (`>= 24.dp`).
 */
@Composable
fun HelpIconButton(
    palette: BrandPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionText: String = "Open Reference Guide"
) {
    Box(
        modifier = modifier
            .size(TuesdayDimens.HitTargetCompact)
            .clip(CircleShape)
            .background(palette.elevated)
            .border(1.dp, palette.border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDescriptionText },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            color = palette.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Shared guessed-model disambiguation strip (`✓ Confirm: <Model>` / `✕ Not My Model`)
 * used in [InboxItemNotchCard] and [sh.tuesday.app.ui.bulletins.BulletinsScreen].
 */
@Composable
fun CandidateDisambiguationStrip(
    candidateModel: String,
    palette: BrandPalette,
    onConfirmModel: (String) -> Unit,
    onRejectModel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmModifier: Modifier = Modifier,
    rejectModifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.padding(top = 4.dp)
    ) {
        Box(
            modifier = confirmModifier
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                .background(palette.accent.copy(alpha = 0.15f))
                .border(1.dp, palette.accent, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                .clickable { onConfirmModel(candidateModel) }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓ Confirm: $candidateModel",
                color = palette.accentText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Box(
            modifier = rejectModifier
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                .background(palette.elevated)
                .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                .clickable(onClick = onRejectModel)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✕ Not My Model",
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
