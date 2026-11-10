package sh.tuesday.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import java.io.File
import java.util.Locale

/**
 * Live Hardware Jetpack Compose WCAG 2.2 & Android TalkBack Semantics Auditor.
 *
 * Walks the active `SemanticsNode` tree on physical hardware (e.g. Pixel 5, 393dp width) and audits:
 *  1. WCAG 2.2 SC 1.1.1 / 4.1.2 (Name, Role, Value): Every interactive node (`OnClick`, `ToggleableState`)
 *     must expose non-blank speakable text (`Text`, `EditableText`, `ContentDescription`, or `StateDescription`).
 *  2. WCAG 2.2 SC 2.5.8 (Target Size Minimum - AA): >= 24x24dp interactive bounds.
 *  3. Android Material Accessibility Guideline:     >= 48x48dp recommended touch target bounds.
 *
 * Appends findings to `<externalFilesDir>/test_reports/wcag_live_semantics_audit.md` for Antigravity pull.
 */
object ComposeWcagSemanticsAuditor {
    private const val TAG = "TuesdayWcagAudit"

    data class NodeFinding(
        val screenLabel: String,
        val severity: String, // "WCAG_AA_SPEAKABLE_TEXT" | "WCAG_AA_TARGET_24DP" | "MATERIAL_TARGET_48DP"
        val nodeTag: String,
        val speakableLabel: String,
        val widthDp: Float,
        val heightDp: Float,
        val detail: String
    )

    private val allFindings = mutableListOf<NodeFinding>()

    fun resetSession() {
        synchronized(allFindings) {
            allFindings.clear()
        }
    }

    fun auditActiveScreen(
        composeTestRule: ComposeTestRule,
        context: Context,
        screenLabel: String
    ): List<NodeFinding> {
        composeTestRule.waitForIdle()
        val density = context.resources.displayMetrics.density.coerceAtLeast(1.0f)
        val root = runCatching { composeTestRule.onRoot(useUnmergedTree = false).fetchSemanticsNode() }
            .getOrNull() ?: return emptyList()

        val screenFindings = mutableListOf<NodeFinding>()
        walkNode(root, density, screenLabel, screenFindings)

        synchronized(allFindings) {
            allFindings += screenFindings
        }

        Log.i(
            TAG,
            "[$screenLabel] Audited live Compose tree: ${screenFindings.size} findings " +
                "(unlabeled=${screenFindings.count { it.severity == "WCAG_AA_SPEAKABLE_TEXT" }}, " +
                "<24dp=${screenFindings.count { it.severity == "WCAG_AA_TARGET_24DP" }}, " +
                "<48dp=${screenFindings.count { it.severity == "MATERIAL_TARGET_48DP" }})"
        )
        return screenFindings
    }

    private fun walkNode(
        node: SemanticsNode,
        density: Float,
        screenLabel: String,
        out: MutableList<NodeFinding>
    ) {
        val cfg = node.config
        val isInteractive = cfg.contains(SemanticsActions.OnClick) ||
            cfg.contains(SemanticsActions.SetText) ||
            cfg.contains(SemanticsProperties.ToggleableState)

        val bounds = node.boundsInWindow
        val widthDp = bounds.width / density
        val heightDp = bounds.height / density

        // Only evaluate nodes actually visible inside the current viewport (> 1dp x 1dp)
        if (isInteractive && widthDp > 1f && heightDp > 1f) {
            val tag = cfg.getOrNull(SemanticsProperties.TestTag) ?: "<untagged>"
            val texts = cfg.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            val editable = cfg.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
            val contentDesc = cfg.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            val stateDesc = cfg.getOrNull(SemanticsProperties.StateDescription).orEmpty()

            // Also inspect merged/child text if any
            val combinedSpeakable = listOf(texts, editable, contentDesc, stateDesc)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" | ")

            if (combinedSpeakable.isBlank()) {
                out += NodeFinding(
                    screenLabel = screenLabel,
                    severity = "WCAG_AA_SPEAKABLE_TEXT",
                    nodeTag = tag,
                    speakableLabel = "<empty>",
                    widthDp = widthDp,
                    heightDp = heightDp,
                    detail = "Interactive node has OnClick/Toggleable but no Text or contentDescription for TalkBack (WCAG 1.1.1 / 4.1.2)"
                )
            }

            // Skip dense fixed-geometry LED grid squares per Tuesday.sh scanning-surface exception, audit everything else
            val isFixedGeometryPortSquare = tag == "ui:action:inspectDevice" && widthDp < 24f && heightDp < 24f
            if (!isFixedGeometryPortSquare) {
                if (widthDp < 24f || heightDp < 24f) {
                    out += NodeFinding(
                        screenLabel = screenLabel,
                        severity = "WCAG_AA_TARGET_24DP",
                        nodeTag = tag,
                        speakableLabel = combinedSpeakable.take(40),
                        widthDp = widthDp,
                        heightDp = heightDp,
                        detail = "Interactive target below WCAG 2.2 SC 2.5.8 minimum 24x24dp"
                    )
                } else if (widthDp < 48f || heightDp < 48f) {
                    out += NodeFinding(
                        screenLabel = screenLabel,
                        severity = "MATERIAL_TARGET_48DP",
                        nodeTag = tag,
                        speakableLabel = combinedSpeakable.take(40),
                        widthDp = widthDp,
                        heightDp = heightDp,
                        detail = "Interactive target between 24dp and 48dp (passes WCAG 2.2 AA 24dp, below Android Material 48x48dp recommendation)"
                    )
                }
            }
        }

        for (child in node.children) {
            walkNode(child, density, screenLabel, out)
        }
    }

    fun writeSessionReport(context: Context): File {
        val dir = context.getExternalFilesDir("test_reports") ?: File(context.filesDir, "test_reports")
        if (!dir.exists()) dir.mkdirs()
        val reportFile = File(dir, "wcag_live_semantics_audit.md")

        val snapshot = synchronized(allFindings) { allFindings.toList() }
        val lines = mutableListOf<String>()
        lines += "# Live Hardware Compose WCAG 2.2 & TalkBack Semantics Audit"
        lines += ""
        lines += "- **Device**: `${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}` (API ${android.os.Build.VERSION.SDK_INT})"
        lines += "- **Density**: `${context.resources.displayMetrics.density}x` (`${context.resources.configuration.screenWidthDp}dp x ${context.resources.configuration.screenHeightDp}dp`, fontScale=`${context.resources.configuration.fontScale}`)"
        lines += "- **Total Findings**: `${snapshot.size}`"
        lines += "  - `WCAG_AA_SPEAKABLE_TEXT` (unlabeled interactive nodes): `${snapshot.count { it.severity == "WCAG_AA_SPEAKABLE_TEXT" }}`"
        lines += "  - `WCAG_AA_TARGET_24DP` (< 24x24dp): `${snapshot.count { it.severity == "WCAG_AA_TARGET_24DP" }}`"
        lines += "  - `MATERIAL_TARGET_48DP` (24..47dp compact targets): `${snapshot.count { it.severity == "MATERIAL_TARGET_48DP" }}`"
        lines += ""
        lines += "| Screen | Severity | Tag | Speakable Text | Bounds (dp) | Notes |"
        lines += "| :--- | :--- | :--- | :--- | :---: | :--- |"

        // Deduplicate identical (screenLabel, severity, nodeTag, speakableLabel) rows for concise readability
        snapshot
            .distinctBy { "${it.screenLabel}|${it.severity}|${it.nodeTag}|${it.speakableLabel}" }
            .forEach { f ->
                lines += String.format(
                    Locale.US,
                    "| %s | `%s` | `%s` | `%s` | `%.1f x %.1f` | %s |",
                    f.screenLabel,
                    f.severity,
                    f.nodeTag,
                    f.speakableLabel.replace("|", "/").ifBlank { "<none>" },
                    f.widthDp,
                    f.heightDp,
                    f.detail
                )
            }

        val fullMarkdown = lines.joinToString("\n") + "\n"
        reportFile.writeText(fullMarkdown)
        Log.i(TAG, "Wrote live WCAG semantics report to: ${reportFile.absolutePath}")

        // Also persist to MediaStore Downloads so the report survives connectedDebugAndroidTest APK uninstall
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf("tuesday_wcag_live_semantics_audit.md")
                )
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "tuesday_wcag_live_semantics_audit.md")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(fullMarkdown.toByteArray(Charsets.UTF_8))
                    }
                    Log.i(TAG, "Persisted WCAG semantics report to /sdcard/Download/tuesday_wcag_live_semantics_audit.md")
                }
            }.onFailure { err ->
                Log.w(TAG, "MediaStore Downloads write skipped: ${err.message}")
            }
        }

        return reportFile
    }
}
