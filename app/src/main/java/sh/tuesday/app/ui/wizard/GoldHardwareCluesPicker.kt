package sh.tuesday.app.ui.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.services.GoldClueIndexData
import sh.tuesday.app.services.GoldClueItem
import sh.tuesday.app.services.GoldClueKind
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiSlot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoldHardwareCluesPicker(
    clueIndex: GoldClueIndexData?,
    queryText: String,
    onQueryTextChange: (String) -> Unit,
    selectedClues: List<GoldClueItem>,
    onAddClue: (GoldClueItem) -> Unit,
    onRemoveClue: (GoldClueItem) -> Unit,
    palette: BrandPalette,
    inferredFromDeviceName: GoldClueItem? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isCluesExpanded by remember { mutableStateOf(false) }
    var menuDismissedForQuery by remember { mutableStateOf<String?>(null) }

    val suggestions = remember(clueIndex, queryText, selectedClues) {
        clueIndex?.querySuggestions(queryText, selectedClues, limit = 6) ?: emptyList()
    }

    val quickPicks = remember(clueIndex, selectedClues) {
        clueIndex?.contextualQuickPicks(selectedClues, limit = 8) ?: emptyList()
    }

    val activeVendor = remember(selectedClues) {
        selectedClues.lastOrNull { !it.vendor.isNullOrBlank() }?.vendor
    }
    val activeTypeTitle = remember(selectedClues) {
        selectedClues.lastOrNull { it.kind == GoldClueKind.DEVICE_TYPE }?.title
    }

    val quickPickContextLabel = when {
        !activeVendor.isNullOrBlank() && !activeTypeTitle.isNullOrBlank() ->
            "${activeVendor.uppercase()} • ${activeTypeTitle.uppercase()} MODELS"
        !activeVendor.isNullOrBlank() ->
            "${activeVendor.uppercase()} GOLD MODELS"
        !activeTypeTitle.isNullOrBlank() ->
            "POPULAR BRANDS FOR ${activeTypeTitle.uppercase()}"
        else ->
            "QUICK PICK DEVICE TYPE"
    }

    val isDropdownExpanded = queryText.trim().isNotEmpty() &&
        suggestions.isNotEmpty() &&
        menuDismissedForQuery != queryText

    fun commitTypedText() {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) return
        val item = clueIndex?.classifyFreeTextClue(trimmed) ?: GoldClueItem(
            id = "custom:${trimmed.lowercase()}",
            kind = GoldClueKind.CUSTOM,
            title = trimmed,
            subtitle = "Custom clue"
        )
        onAddClue(item)
        onQueryTextChange("")
        menuDismissedForQuery = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .uiSlot("shared-hardware-clues"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { isCluesExpanded = !isCluesExpanded }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "CLUES",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                if (!isCluesExpanded) {
                    Text(
                        text = if (selectedClues.isNotEmpty()) "(${selectedClues.size} selected · Tap to add)" else "(Tap to add)",
                        color = TuesdayTheme.HacsCyan.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (clueIndex != null) {
                    Text(
                        text = "SureThingNet Insights v${clueIndex.datalakeVersion}",
                        color = TuesdayTheme.HealthyGreen.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (isCluesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isCluesExpanded) "Collapse Clues" else "Expand Clues",
                    tint = palette.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isCluesExpanded) {

        Text(
            text = "Choose context clues to help identify your device.",
            color = palette.textMuted,
            fontSize = 12.sp
        )

        // Optional 1-tap bridge if Manual Device Name matched a Gold SKU
        if (inferredFromDeviceName != null) {
            AssistChip(
                onClick = { onAddClue(inferredFromDeviceName) },
                label = {
                    Text(
                        text = "Link Gold SKU: ${inferredFromDeviceName.vendor} ${inferredFromDeviceName.title} (${inferredFromDeviceName.categoryCode})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = TuesdayTheme.HealthyGreen,
                        modifier = Modifier.size(15.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = TuesdayTheme.HealthyGreen.copy(alpha = 0.12f),
                    labelColor = palette.textPrimary
                ),
                border = BorderStroke(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.45f))
            )
        }

        // Material 3 Editable ExposedDropdownMenuBox for Multi-Item Autocomplete
        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { wantExpand ->
                if (!wantExpand) {
                    menuDismissedForQuery = queryText
                } else {
                    menuDismissedForQuery = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("gold-clue-autocomplete")
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = { next ->
                    menuDismissedForQuery = null
                    onQueryTextChange(next)
                },
                placeholder = {
                    Text(
                        text = "Type router, switch, doorbell, Google, Linksys...",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TuesdayTheme.HacsCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (queryText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { commitTypedText() },
                                modifier = Modifier.uiAction("addClue")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "Add clue",
                                    tint = TuesdayTheme.HacsCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commitTypedText()
                        focusManager.clearFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TuesdayTheme.HacsCyan,
                    unfocusedBorderColor = palette.border
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
            )

            ExposedDropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { menuDismissedForQuery = queryText },
                modifier = Modifier
                    .background(palette.elevated)
                    .uiSlot("gold-clue-suggestions")
            ) {
                suggestions.forEachIndexed { idx, suggestion ->
                    if (idx > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = palette.border.copy(alpha = 0.55f)
                        )
                    }
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = iconForClueKind(suggestion.kind, suggestion.warningNote != null),
                                contentDescription = null,
                                tint = colorForClueKind(suggestion.kind, suggestion.warningNote != null, palette),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (suggestion.kind == GoldClueKind.MODEL && !suggestion.vendor.isNullOrBlank()) {
                                        "${suggestion.vendor} • ${suggestion.title}"
                                    } else if (suggestion.kind == GoldClueKind.CUSTOM) {
                                        "Add \"${suggestion.title}\""
                                    } else {
                                        suggestion.title
                                    },
                                    color = palette.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = suggestion.warningNote ?: suggestion.subtitle,
                                    color = if (suggestion.warningNote != null) TuesdayTheme.WarningAmber else palette.textMuted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        trailingIcon = {
                            val badgeText = suggestion.categoryCode ?: suggestion.kind.badgeLabel
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.surface)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = colorForClueKind(suggestion.kind, suggestion.warningNote != null, palette),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        },
                        onClick = {
                            onAddClue(suggestion)
                            onQueryTextChange("")
                            menuDismissedForQuery = null
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.uiAction("pickGoldClue")
                    )
                }
            }
        }

        // Dynamic Contextual Material 3 SuggestionChip Strip (Faceted Narrowing: Type -> Brand -> Model)
        if (quickPicks.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.uiSlot("gold-quick-pick-chips")
            ) {
                    items(quickPicks, key = { it.id }) { pick ->
                        SuggestionChip(
                            onClick = { onAddClue(pick) },
                            label = {
                                Text(
                                    text = pick.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = colorForClueKind(pick.kind, false, palette),
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = palette.surface,
                                labelColor = palette.textPrimary
                            ),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.uiAction("quickPickGoldClue")
                        )
                    }
                }
            }
        }
        } // end if (isCluesExpanded)

        // Selected Multi-Item Clues: Material 3 FlowRow + InputChip Group
        if (false && selectedClues.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selectedClues.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Clear Clues",
                            color = TuesdayTheme.CriticalRed.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                selectedClues.toList().forEach { onRemoveClue(it) }
                            }
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("selected-clue-chips")
                ) {
                    selectedClues.forEach { item ->
                        val accent = colorForClueKind(item.kind, item.warningNote != null, palette)
                        InputChip(
                            selected = true,
                            onClick = { onRemoveClue(item) },
                            label = {
                                Text(
                                    text = item.chipLabel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = iconForClueKind(item.kind, item.warningNote != null),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${item.chipLabel}",
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.14f),
                                selectedLabelColor = palette.textPrimary
                            ),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                        )
                    }
                }

                // Surface embedded radio SoC warnings cleanly if any selected FCC ID is a radio module
                selectedClues.mapNotNull { it.warningNote }.distinct().forEach { warn ->
                    Text(
                        text = "⚠ $warn",
                        color = TuesdayTheme.WarningAmber,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

private fun iconForClueKind(kind: GoldClueKind, hasWarning: Boolean) = when {
    hasWarning -> Icons.Default.WarningAmber
    kind == GoldClueKind.DEVICE_TYPE -> Icons.Default.Category
    kind == GoldClueKind.BRAND -> Icons.Default.Business
    kind == GoldClueKind.MODEL -> Icons.Default.Verified
    kind == GoldClueKind.IDENTIFIER -> Icons.Default.QrCode
    else -> Icons.Default.Tag
}

private fun colorForClueKind(kind: GoldClueKind, hasWarning: Boolean, palette: BrandPalette): Color = when {
    hasWarning -> TuesdayTheme.WarningAmber
    kind == GoldClueKind.DEVICE_TYPE -> TuesdayTheme.HacsCyan
    kind == GoldClueKind.BRAND -> Color(0xFF60A5FA)
    kind == GoldClueKind.MODEL -> TuesdayTheme.HealthyGreen
    kind == GoldClueKind.IDENTIFIER -> TuesdayTheme.WarningAmber
    else -> palette.textPrimary
}
