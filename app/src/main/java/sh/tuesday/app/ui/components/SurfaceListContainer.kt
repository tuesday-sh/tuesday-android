package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens

@Composable
fun SurfaceListContainer(
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard)),
        content = content
    )
}

@Composable
fun SurfaceListDivider(palette: BrandPalette) {
    HorizontalDivider(
        color = palette.border.copy(alpha = 0.6f),
        thickness = 1.dp
    )
}

@Composable
fun TuesdayListRow(
    palette: BrandPalette,
    title: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    titleMaxLines: Int = 1,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    subtitleContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Expand item",
            tint = palette.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = TuesdayDimens.CardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leadingContent != null) {
            leadingContent()
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitleContent != null) {
                subtitleContent()
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun StatusListRow(
    palette: BrandPalette,
    dotColor: Color,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Expand item",
    subtitle: @Composable () -> Unit
) {
    TuesdayListRow(
        palette = palette,
        title = title,
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        },
        subtitleContent = { subtitle() },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = contentDescription,
                tint = palette.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
fun DetailModalDialog(
    palette: BrandPalette,
    onDismissRequest: () -> Unit,
    closeActionModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = closeActionModifier
                        .size(TuesdayDimens.HitTargetCompact)
                        .clip(CircleShape)
                        .background(palette.surface)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun TuesdayModalCard(
    palette: BrandPalette,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = palette.border,
    closeActionModifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    DetailModalDialog(
        palette = palette,
        onDismissRequest = onDismissRequest,
        closeActionModifier = closeActionModifier
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TuesdayDimens.RadiusModalCard))
                .background(palette.surface)
                .border(1.dp, borderColor, RoundedCornerShape(TuesdayDimens.RadiusModalCard))
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (leadingContent != null) {
                        leadingContent()
                    }
                    Text(
                        text = title,
                        color = palette.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (headerTrailing != null) {
                    headerTrailing()
                }
            }

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuesdayBottomSheet(
    palette: BrandPalette,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.textPrimary,
        content = content
    )
}
