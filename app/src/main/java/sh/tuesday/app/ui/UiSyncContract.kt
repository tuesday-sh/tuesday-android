package sh.tuesday.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * 3-Way Web-to-Native Semantic UI Attribution Contract (`web-native-ui-sync` skill).
 *
 * Maps directly to:
 *  - Web:     `data-ui-component`, `data-ui-slot`, `data-ui-action`, `data-ui-state`
 *  - Android: `Modifier.uiComponent(...)`, `Modifier.uiSlot(...)`, `Modifier.uiAction(...)`
 *  - iOS:     `.uiComponent(...)`, `.uiSlot(...)`, `.uiAction(...)`
 */
fun Modifier.uiComponent(name: String, state: String? = null): Modifier =
    this.testTag("ui:component:$name").let { mod ->
        if (state != null) mod.semantics { stateDescription = state } else mod
    }

fun Modifier.uiSlot(name: String, state: String? = null): Modifier =
    this.testTag("ui:slot:$name").let { mod ->
        if (state != null) mod.semantics { stateDescription = state } else mod
    }

fun Modifier.uiAction(name: String): Modifier =
    this.testTag("ui:action:$name")
