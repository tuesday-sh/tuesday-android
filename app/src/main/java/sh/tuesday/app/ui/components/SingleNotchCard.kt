package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent

/**
 * Reusable Single Fused Upper-Left Notch Card container backed by [FusedTopStartNotch].
 */
@Composable
fun SingleNotchCard(
    palette: BrandPalette,
    accentColor: Color,
    notchLabel: String,
    notchDotColor: Color = accentColor,
    onNotchClick: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    cardBorderColor: Color = accentColor.copy(alpha = 0.35f),
    topEndHeader: (@Composable RowScope.() -> Unit)? = null,
    contentTopPadding: Dp = 42.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val hasCallerModifier = modifier !== Modifier
    val shape = RoundedCornerShape(TuesdayDimens.RadiusCard)

    Box(
        modifier = (if (hasCallerModifier) modifier else Modifier.uiComponent("SingleNotchCard"))
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, cardBorderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        FusedTopStartNotch(
            accentColor = accentColor,
            strokeColor = cardBorderColor,
            label = notchLabel,
            dotColor = notchDotColor,
            onClick = onNotchClick,
            modifier = Modifier.uiAction("clickLeftNotch")
        )

        if (topEndHeader != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = topEndHeader
            )
        }

        Column(
            modifier = (if (hasCallerModifier) Modifier.uiComponent("SingleNotchCard") else Modifier)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = contentTopPadding, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content
        )
    }
}
