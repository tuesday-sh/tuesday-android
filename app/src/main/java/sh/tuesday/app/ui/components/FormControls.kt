package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens

/**
 * Shared preference row with title, optional monospace badge, description, and palette-styled [Switch].
 */
@Composable
fun PreferenceToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    switchModifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeColor: Color = palette.accent,
    accentTrackColor: Color = palette.accent
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!badgeText.isNullOrBlank()) {
                    MonospaceBadge(
                        text = badgeText,
                        color = badgeColor
                    )
                }
            }
            Text(
                text = description,
                color = palette.textMuted,
                fontSize = 11.sp,
                lineHeight = 14.5.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentTrackColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = palette.borderStrong
            )
        )
    }
}

/**
 * Palette-aware [OutlinedTextField] wrapper eliminating repetitive 12-line color overrides.
 */
@Composable
fun TuesdayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = if (!placeholder.isNullOrBlank()) {
            { Text(placeholder, fontSize = 12.sp, color = palette.textMuted) }
        } else {
            null
        },
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.border,
            focusedLabelColor = palette.accentText,
            unfocusedLabelColor = palette.textMuted,
            cursorColor = palette.accent,
            focusedContainerColor = palette.bg,
            unfocusedContainerColor = palette.bg
        )
    )
}

/**
 * Shared removable attachment chip with a WCAG 2.2 SC 2.5.8 compliant (`>= 24.dp`) dismiss hit target.
 */
@Composable
fun RemovableAttachmentChip(
    label: String,
    palette: BrandPalette,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = palette.accent,
    removeContentDescription: String = "Remove attachment",
    leadingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = RoundedCornerShape(TuesdayDimens.RadiusControl)
    Row(
        modifier = modifier
            .clip(shape)
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.5f), shape)
            .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        Text(
            text = label,
            color = palette.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = removeContentDescription,
                tint = palette.textMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
