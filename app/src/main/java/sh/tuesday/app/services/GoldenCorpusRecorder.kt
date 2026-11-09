package sh.tuesday.app.services

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.tuesday.app.models.DeviceCandidateMatch
import sh.tuesday.app.models.ExtractedDeviceIdentifiers
import sh.tuesday.app.models.GeminiDiscoveryTurnResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class GoldenCorpusManifest(
    val sessionId: String,
    val capturedAtEpochMs: Long,
    val capturedAtIso: String,
    val providerUsed: String,
    val status: String, // "auto_tagged" | "user_confirmed" | "saved_best_guess" | "pivoted_to_manual"
    val captureSource: String = "camera", // "camera" | "gallery" | "corpus_replay"
    val confirmedManufacturer: String? = null,
    val confirmedModelNumber: String? = null,
    val confirmedCategory: String? = null,
    val confirmedDisplayName: String? = null,
    val visualSummary: String,
    val nextNarrowingQuestion: String,
    val extractedIdentifiers: ExtractedDeviceIdentifiers,
    val userClueNotes: List<String>,
    val candidates: List<DeviceCandidateMatch>,
    val photoFiles: List<String>
)

object GoldenCorpusRecorder {
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun getCorpusRootDir(context: Context): File {
        val base = context.getExternalFilesDir("golden_corpus")
            ?: File(context.filesDir, "golden_corpus")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun getSavedSessionCount(context: Context): Int {
        val root = getCorpusRootDir(context)
        return root.listFiles()?.count { it.isDirectory && File(it, "capture_manifest.json").exists() } ?: 0
    }

    fun listSavedManifests(context: Context, limit: Int = 6): List<GoldenCorpusManifest> {
        val root = getCorpusRootDir(context)
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val f = File(dir, "capture_manifest.json")
                if (f.exists()) {
                    runCatching { prettyJson.decodeFromString<GoldenCorpusManifest>(f.readText()) }.getOrNull()
                } else null
            }
            ?.filter { it.captureSource != "synthetic_sample" && it.confirmedModelNumber != "Retry Vision Analysis" }
            ?.sortedByDescending { it.capturedAtEpochMs }
            ?.take(limit)
            ?: emptyList()
    }

    fun loadSessionBitmaps(context: Context, sessionId: String): List<Bitmap> {
        val root = getCorpusRootDir(context)
        val dir = root.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("${sessionId}_") } ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("photo_") && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                runCatching {
                    val opts = BitmapFactory.Options().apply { inMutable = true }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                }.getOrNull()
            }
            ?: emptyList()
    }

    suspend fun deleteCaptureSession(
        context: Context,
        sessionId: String
    ) = withContext(Dispatchers.IO) {
        val root = getCorpusRootDir(context)
        root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("${sessionId}_") }
            ?.forEach { it.deleteRecursively() }

        // Remove matching MediaStore entries from Pictures/Tuesday
        try {
            context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("tuesday_${sessionId}_%")
            )
        } catch (_: Exception) {
        }

        rebuildCorpusIndex(root)
    }

    suspend fun purgeAllCaptures(context: Context): Int = withContext(Dispatchers.IO) {
        val root = getCorpusRootDir(context)
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val removedCount = dirs.size
        dirs.forEach { it.deleteRecursively() }
        File(root, "index.json").delete()

        // Clear camera cache files
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("tuesday_scan_") }
            ?.forEach { it.delete() }

        // Remove app-created MediaStore entries in Pictures/Tuesday
        try {
            context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("tuesday_%")
            )
        } catch (_: Exception) {
        }

        removedCount
    }

    suspend fun saveCaptureSession(
        context: Context,
        sessionId: String,
        bitmaps: List<Bitmap>,
        result: GeminiDiscoveryTurnResult,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        userClueNotes: List<String>,
        status: String = "auto_tagged",
        captureSource: String = "camera",
        confirmedCandidate: DeviceCandidateMatch? = null,
        confirmedModelOverride: String? = null
    ): File? = withContext(Dispatchers.IO) {
        // Never pollute the real Golden Corpus with synthetic test cards, offline errors, or duplicate corpus_replay runs
        if (captureSource == "synthetic_sample" || captureSource == "corpus_replay" || !result.usedLiveGeminiApi) {
            return@withContext null
        }

        val root = getCorpusRootDir(context)
        val topCandidate = confirmedCandidate ?: result.candidates.firstOrNull()
        val rawSlug = (confirmedModelOverride ?: topCandidate?.modelNumber ?: "hardware")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "device" }

        val sessionDirName = "${sessionId}_$rawSlug"
        // Remove any earlier folder with same sessionId prefix if slug changed after confirmation
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith("${sessionId}_") && it.name != sessionDirName }
            ?.forEach { it.deleteRecursively() }

        val sessionDir = File(root, sessionDirName)
        if (!sessionDir.exists()) sessionDir.mkdirs()

        val photoNames = mutableListOf<String>()
        bitmaps.forEachIndexed { idx, bmp ->
            val fileName = "photo_${idx + 1}.jpg"
            val photoFile = File(sessionDir, fileName)
            val isNewFile = !photoFile.exists()
            FileOutputStream(photoFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            photoNames.add(fileName)

            // Only publish newly captured camera frames to Pictures/Tuesday (avoid duplicating gallery uploads or corpus replays)
            if (isNewFile && captureSource == "camera") {
                exportToSystemMediaLibrary(context, bmp, "tuesday_${sessionId}_${idx + 1}.jpg")
            }
        }

        val now = System.currentTimeMillis()
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val manifest = GoldenCorpusManifest(
            sessionId = sessionId,
            capturedAtEpochMs = now,
            capturedAtIso = isoFmt.format(Date(now)),
            providerUsed = result.providerUsed,
            status = status,
            captureSource = captureSource,
            confirmedManufacturer = topCandidate?.manufacturer,
            confirmedModelNumber = confirmedModelOverride ?: topCandidate?.modelNumber,
            confirmedCategory = topCandidate?.deviceCategory,
            confirmedDisplayName = topCandidate?.displayName,
            visualSummary = result.visualSummary,
            nextNarrowingQuestion = result.nextNarrowingQuestion,
            extractedIdentifiers = accumulatedIdentifiers,
            userClueNotes = userClueNotes,
            candidates = result.candidates,
            photoFiles = photoNames
        )

        val manifestFile = File(sessionDir, "capture_manifest.json")
        manifestFile.writeText(prettyJson.encodeToString(manifest))

        // Rebuild index.json of all sessions for fast adb pull inspection
        rebuildCorpusIndex(root)

        sessionDir
    }

    private fun exportToSystemMediaLibrary(context: Context, bitmap: Bitmap, displayName: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Tuesday")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (_: Exception) {
        }
    }

    private fun rebuildCorpusIndex(root: File) {
        try {
            val manifests = root.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?.mapNotNull { dir ->
                    val f = File(dir, "capture_manifest.json")
                    if (f.exists()) {
                        runCatching { prettyJson.decodeFromString<GoldenCorpusManifest>(f.readText()) }.getOrNull()
                    } else null
                } ?: emptyList()

            File(root, "index.json").writeText(prettyJson.encodeToString(manifests))
        } catch (_: Exception) {
        }
    }
}
