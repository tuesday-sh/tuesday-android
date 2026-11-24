package sh.tuesday.app.domain

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Client-side formatted Home & Rack Dossier exporter (Pro Tier 1).
 *
 * Sovereign Local JSON backup remains 100% Free; formatted human-readable
 * exports (Insurance/Disaster-Recovery PDF, CSV spreadsheet, Markdown spec sheet)
 * are unlocked with Pro Tier 1.
 */
object RackDossierExporter {

    fun currentTimestampIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    fun formatMarkdown(
        siteName: String,
        devices: List<RackPortDevice>,
        generatedAtIso: String = currentTimestampIso()
    ): String {
        val safeSite = siteName.ifBlank { "My Network" }
        val sorted = devices.sortedWith(
            compareBy<RackPortDevice> { it.category.ordinal }
                .thenBy { it.name.lowercase(Locale.US) }
        )
        val cveCount = sorted.count { it.portState == PortLifecycleState.CVE_KEV }
        val eolCount = sorted.count { it.portState == PortLifecycleState.EOL }
        val updateCount = sorted.count { it.portState == PortLifecycleState.UPDATE }
        val guessedCount = sorted.count { it.portState == PortLifecycleState.GUESSED }
        val okCount = sorted.count { it.portState == PortLifecycleState.OK }

        return buildString {
            appendLine("# $safeSite — Hardware & Network Dossier")
            appendLine()
            appendLine("- **Generated**: $generatedAtIso")
            appendLine("- **Tracked Devices**: ${sorted.size}")
            appendLine("- **Posture Summary**: $okCount OK • $updateCount Update • $eolCount EOL • $cveCount CVE/KEV • $guessedCount Unverified")
            appendLine()
            appendLine("| Category | Device | Room / Location | Firmware | Status | Trust Zone |")
            appendLine("| :--- | :--- | :--- | :--- | :--- | :--- |")
            sorted.forEach { d ->
                val cat = "${d.category.code} ${d.category.proLabel}"
                val dev = d.name.replace("|", "\\|")
                val room = d.roomLocation.ifBlank { "—" }.replace("|", "\\|")
                val ver = d.version.ifBlank { "—" }.replace("|", "\\|")
                val status = d.portState.name
                val zone = d.trustZone.displayLabel
                appendLine("| $cat | $dev | $room | `$ver` | $status | $zone |")
            }
        }
    }

    fun formatCsv(
        siteName: String,
        devices: List<RackPortDevice>
    ): String {
        val safeSite = siteName.ifBlank { "My Network" }
        val sorted = devices.sortedWith(
            compareBy<RackPortDevice> { it.category.ordinal }
                .thenBy { it.name.lowercase(Locale.US) }
        )
        return buildString {
            appendLine("Site,CategoryCode,Category,Name,ShortLabel,Room,Firmware,Status,TrustZone,Lifecycle,PURLs")
            sorted.forEach { d ->
                val cols = listOf(
                    safeSite,
                    d.category.code,
                    d.category.proLabel,
                    d.name,
                    d.shortLabel,
                    d.roomLocation,
                    d.version,
                    d.portState.name,
                    d.trustZone.displayLabel,
                    d.lifecycleTier,
                    d.purls.joinToString("; ")
                )
                appendLine(cols.joinToString(",") { escapeCsv(it) })
            }
        }
    }

    fun renderPdfBytes(
        siteName: String,
        devices: List<RackPortDevice>,
        generatedAtIso: String = currentTimestampIso()
    ): ByteArray {
        val safeSite = siteName.ifBlank { "My Network" }
        val sorted = devices.sortedWith(
            compareBy<RackPortDevice> { it.category.ordinal }
                .thenBy { it.name.lowercase(Locale.US) }
        )

        val pageWidth = 612
        val pageHeight = 792
        val margin = 40f
        val doc = PdfDocument()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 85, 99)
            textSize = 10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 120, 25)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(31, 41, 55)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(209, 213, 219)
            strokeWidth = 0.75f
        }

        val rowsPerPage = 32
        val chunks = if (sorted.isEmpty()) listOf(emptyList()) else sorted.chunked(rowsPerPage)

        chunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = 52f
            canvas.drawText("$safeSite — Hardware Dossier", margin, y, titlePaint)
            y += 16f
            canvas.drawText(
                "Tuesday.sh Pro • ${sorted.size} tracked devices • Generated $generatedAtIso • Page ${pageIndex + 1}/${chunks.size}",
                margin,
                y,
                subtitlePaint
            )
            y += 14f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 18f

            // Table column headers
            canvas.drawText("CAT", margin, y, headerPaint)
            canvas.drawText("DEVICE", margin + 38f, y, headerPaint)
            canvas.drawText("ROOM", margin + 235f, y, headerPaint)
            canvas.drawText("FIRMWARE", margin + 355f, y, headerPaint)
            canvas.drawText("STATUS", margin + 455f, y, headerPaint)
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 16f

            if (chunk.isEmpty()) {
                canvas.drawText("No devices tracked in this network site yet.", margin, y, rowPaint)
            } else {
                chunk.forEach { d ->
                    canvas.drawText(d.category.code, margin, y, rowPaint)
                    canvas.drawText(d.name.take(32), margin + 38f, y, rowPaint)
                    canvas.drawText(d.roomLocation.ifBlank { "—" }.take(18), margin + 235f, y, rowPaint)
                    canvas.drawText(d.version.ifBlank { "—" }.take(14), margin + 355f, y, rowPaint)
                    canvas.drawText(d.portState.name, margin + 455f, y, rowPaint)
                    y += 19f
                }
            }

            doc.finishPage(page)
        }

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
