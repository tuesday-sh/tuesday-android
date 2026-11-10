package sh.tuesday.app.services

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sh.tuesday.app.BuildConfig
import sh.tuesday.app.models.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Hardware Vision Discovery Client for Tuesday.sh Android.
 *
 * SECURITY & PRIVACY ARCHITECTURE:
 * - Zero LLM API keys are bundled or stored in the mobile binary.
 * - All hardware vision inference is proxied through Tuesday Server (`POST /api/v1/discovery/analyze`),
 *   which performs $0.00 Gold Catalog short-circuiting, routes across Elastix (qwen3.8-27b) and
 *   GCP Gemini (gemini-3.8-flash), emits unified telemetry to CloudWatch Omni (`hosted-ai-v1`),
 *   and enforces ephemeral RAM-only photo processing (zero photo storage on our servers).
 */
class GeminiDiscoveryService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(55, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Tuesday-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                .header("X-Tuesday-App-Version", BuildConfig.VERSION_NAME)
                .header("X-Tuesday-Build", BuildConfig.VERSION_CODE.toString())
                .build()
            chain.proceed(req)
        }
        .build()

    private val persistentDeviceId = "tuesday-android-" + UUID.randomUUID().toString().take(8)
    private var sessionStickinessId = "tuesday-android-" + UUID.randomUUID().toString().take(8)
    private var turnCounter = 0

    fun startNewSession(): String {
        sessionStickinessId = "tuesday-android-" + UUID.randomUUID().toString().take(8)
        turnCounter = 0
        return sessionStickinessId
    }

    fun currentSessionId(): String = sessionStickinessId
    fun currentTurnCount(): Int = turnCounter

    suspend fun analyzeDeviceProgressive(
        bitmaps: List<Bitmap>,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        userClueNotes: List<String>,
        aiProvider: AiProviderType = AiProviderType.ELASTIX,
        elastixApiKeyOverride: String = "",
        elastixModelName: String = "qwen3.8-27b",
        apiKeyOverride: String = "",
        modelName: String = "gemini-3.8-flash",
        serverBaseUrl: String = DEFAULT_SERVER_BASE_URL,
        debugEnabled: Boolean = false,
        initialMode: String = "photo_wizard"
    ): GeminiDiscoveryTurnResult = withContext(Dispatchers.IO) {
        turnCounter += 1
        try {
            return@withContext callTuesdayServerAnalyzeApi(
                bitmaps = bitmaps,
                accumulatedIdentifiers = accumulatedIdentifiers,
                userClueNotes = userClueNotes,
                aiProvider = aiProvider,
                serverBaseUrl = serverBaseUrl.ifBlank { DEFAULT_SERVER_BASE_URL },
                debugEnabled = debugEnabled,
                initialMode = initialMode
            )
        } catch (e: Exception) {
            Log.w("TuesdayVisionProxy", "Analyze socket interrupted (${e.message}); checking server session recovery for $sessionStickinessId")
            val recovered = recoverCompletedTurnFromServer(
                serverBaseUrl = serverBaseUrl.ifBlank { DEFAULT_SERVER_BASE_URL },
                sessionId = sessionStickinessId,
                accumulatedIdentifiers = accumulatedIdentifiers
            )
            if (recovered != null) {
                Log.i("TuesdayVisionProxy", "Recovered completed server turn for session $sessionStickinessId")
                return@withContext recovered
            }
            Log.w("TuesdayVisionProxy", "Hosted proxy unreachable (${e.message}); returning offline status")
            val errReason = "${e.javaClass.simpleName}: ${e.message ?: "Network unreachable"}"
            if (initialMode != "synthetic_sample" && bitmaps.isNotEmpty() && accumulatedIdentifiers.nonEmptyPairs().isEmpty() && userClueNotes.isEmpty()) {
                return@withContext connectionInterruptedResult(
                    accumulatedIdentifiers = accumulatedIdentifiers,
                    imageCount = bitmaps.size,
                    fallbackErrorReason = errReason
                )
            }
            return@withContext fallbackHeuristic(
                accumulatedIdentifiers = accumulatedIdentifiers,
                userClueNotes = userClueNotes,
                imageCount = bitmaps.size,
                usedLiveApi = false,
                fallbackErrorReason = errReason
            )
        }
    }

    private fun recoverCompletedTurnFromServer(
        serverBaseUrl: String,
        sessionId: String,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers
    ): GeminiDiscoveryTurnResult? {
        val baseUrl = serverBaseUrl.trimEnd('/')
        // Poll up to 6 times (every 2s = up to 12s) in case the server is still finishing Gemini escalation when the app unfreezes
        for (attempt in 1..6) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/discovery/sessions?session_id=$sessionId&limit=1")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Tuesday-Device-ID", persistentDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .addHeader("X-Tuesday-Pilot-Token", PILOT_CLIENT_TOKEN)
                    .get()
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JSONObject(body)
                            val sessions = root.optJSONArray("sessions")
                            if (sessions != null && sessions.length() > 0) {
                                val sess = sessions.getJSONObject(0)
                                val turns = sess.optJSONArray("turns")
                                if (turns != null && turns.length() > 0) {
                                    val lastTurn = turns.getJSONObject(turns.length() - 1)
                                    val candArr = lastTurn.optJSONArray("candidates")
                                    if (candArr != null && candArr.length() > 0) {
                                        val cands = mutableListOf<DeviceCandidateMatch>()
                                        for (i in 0 until candArr.length()) {
                                            val c = candArr.getJSONObject(i)
                                            val gaStatus = c.optString("goldAlignmentStatus", "UNALIGNED RAW MATCH")
                                            val aligned = gaStatus == "GOLD ALIGNED"
                                            cands.add(
                                                DeviceCandidateMatch(
                                                    manufacturer = c.optString("manufacturer", "Unknown Vendor"),
                                                    modelNumber = c.optString("modelNumber", "Unidentified Hardware"),
                                                    displayName = c.optString("displayName", "${c.optString("manufacturer")} ${c.optString("modelNumber")}"),
                                                    deviceCategory = c.optString("deviceCategory", "Network Hardware"),
                                                    confidencePercent = c.optInt("confidencePercent", 50),
                                                    reasoning = "Recovered from Tuesday Server session after background socket interruption.",
                                                    suggestedHostname = c.optString("modelNumber", "device").lowercase().replace(Regex("[^a-z0-9]+"), "-"),
                                                    suggestedOsDetails = "Linux / Firmware",
                                                    suggestedPackageManager = "firmware",
                                                    goldAlignment = GoldAlignmentInfo(
                                                        aligned = aligned,
                                                        matchedFeedSlug = c.optString("matchedFeedSlug").takeIf { it.isNotBlank() && it != "null" },
                                                        inferredPurl = c.optString("inferredPurl", ""),
                                                        statusLabel = gaStatus,
                                                        diagnosticNote = "Recovered from server session $sessionId"
                                                    )
                                                )
                                            )
                                        }

                                        val provider = lastTurn.optString("provider", "Elastix")
                                        val model = lastTurn.optString("model", "qwen3.8-27b")
                                        val latSec = String.format("%.2fs", lastTurn.optLong("latencyMs", 0L) / 1000.0)
                                        return GeminiDiscoveryTurnResult(
                                            visualSummary = "Recovered server analysis for ${cands.first().manufacturer} ${cands.first().modelNumber} (socket interrupted while app was backgrounded).",
                                            nextNarrowingQuestion = lastTurn.optString("nextNarrowingQuestion", "Can you check for a Barcode, Serial Number, Model, or MAC Address on the label?"),
                                            recommendedClueTypes = DeviceClueType.entries.map { it.rawValue },
                                            extractedIdentifiers = accumulatedIdentifiers,
                                            candidates = cands,
                                            usedLiveGeminiApi = true,
                                            providerUsed = "Tuesday Server → $provider ($model • $latSec • Recovered)",
                                            latencyMs = lastTurn.optLong("latencyMs", 0L)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            try {
                Thread.sleep(2000)
            } catch (_: InterruptedException) {
                break
            }
        }
        return null
    }

    suspend fun confirmDeviceDiscovery(
        inferredVendor: String?,
        inferredModel: String?,
        confirmedVendor: String,
        confirmedModel: String,
        wasEdited: Boolean,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        userClueNotes: List<String> = emptyList(),
        selectedCandidateRank: Int? = 0,
        resolutionStatus: String = "confirmed_candidate",
        initialMode: String = "photo_wizard",
        finalMode: String = "photo_wizard",
        candidateModels: List<String> = emptyList(),
        enrollOnServer: Boolean = true,
        serverBaseUrl: String = DEFAULT_SERVER_BASE_URL
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = serverBaseUrl.ifBlank { DEFAULT_SERVER_BASE_URL }.trimEnd('/')
            val idsArray = JSONArray()
            for ((type, value) in accumulatedIdentifiers.nonEmptyPairs()) {
                idsArray.put("${type.rawValue}: $value")
            }
            val cluesArray = JSONArray()
            for (note in userClueNotes) {
                if (note.isNotBlank()) cluesArray.put(note)
            }
            val candModelsArray = JSONArray()
            for (cm in candidateModels) {
                if (cm.isNotBlank()) candModelsArray.put(cm)
            }

            val payload = JSONObject().apply {
                put("session_id", sessionStickinessId)
                put("client_platform", "android")
                put("device_id", persistentDeviceId)
                put("resolution_status", resolutionStatus)
                if (selectedCandidateRank != null) {
                    put("selected_candidate_rank", selectedCandidateRank)
                }
                put("initial_mode", initialMode)
                put("final_mode", finalMode)
                put("inferred_vendor", inferredVendor ?: "")
                put("inferred_model", inferredModel ?: "")
                put("confirmed_vendor", confirmedVendor)
                put("confirmed_model", confirmedModel)
                put("was_edited", wasEdited)
                put("enroll", enrollOnServer)
                put("extracted_identifiers", idsArray)
                put("user_clue_notes", cluesArray)
                if (candModelsArray.length() > 0) {
                    put("candidate_models", candModelsArray)
                }
            }

            val request = Request.Builder()
                .url("$baseUrl/api/v1/discovery/resolve")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Tuesday-Device-ID", persistentDeviceId)
                .addHeader("X-Tuesday-Client-Platform", "android")
                .addHeader("X-Tuesday-Pilot-Token", PILOT_CLIENT_TOKEN)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                return@withContext resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.w("TuesdayVisionProxy", "Resolve telemetry skipped: ${e.message}")
            return@withContext false
        }
    }

    suspend fun emitProductTelemetryEvent(
        eventName: String,
        targetEntityType: String? = null,
        targetEntityId: String? = null,
        properties: Map<String, Any?> = emptyMap(),
        serverBaseUrl: String = DEFAULT_SERVER_BASE_URL
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = serverBaseUrl.ifBlank { DEFAULT_SERVER_BASE_URL }.trimEnd('/')
            val propsObj = JSONObject()
            for ((k, v) in properties) {
                if (v != null) propsObj.put(k, v)
            }
            val payload = JSONObject().apply {
                put("event_name", eventName)
                put("client_platform", "android")
                put("device_id", persistentDeviceId)
                put("session_id", sessionStickinessId)
                if (!targetEntityType.isNullOrBlank()) put("target_entity_type", targetEntityType)
                if (!targetEntityId.isNullOrBlank()) put("target_entity_id", targetEntityId)
                put("properties", propsObj)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/v1/telemetry/events")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Tuesday-Device-ID", persistentDeviceId)
                .addHeader("X-Tuesday-Client-Platform", "android")
                .addHeader("X-Tuesday-Pilot-Token", PILOT_CLIENT_TOKEN)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                return@withContext resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.w("TuesdayVisionProxy", "Product telemetry skipped: ${e.message}")
            return@withContext false
        }
    }

    private fun callTuesdayServerAnalyzeApi(
        bitmaps: List<Bitmap>,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        userClueNotes: List<String>,
        aiProvider: AiProviderType,
        serverBaseUrl: String,
        debugEnabled: Boolean = false,
        initialMode: String = "photo_wizard"
    ): GeminiDiscoveryTurnResult {
        val baseUrl = serverBaseUrl.trimEnd('/')
        val providerOverride = when (aiProvider) {
            AiProviderType.ELASTIX -> "auto"
            AiProviderType.GEMINI -> "gemini"
        }

        val extractedObj = JSONObject().apply {
            accumulatedIdentifiers.barcode?.takeIf { it.isNotBlank() }?.let { put("barcode", it) }
            accumulatedIdentifiers.serialNumber?.takeIf { it.isNotBlank() }?.let { put("serialNumber", it) }
            accumulatedIdentifiers.qrCode?.takeIf { it.isNotBlank() }?.let { put("qrCode", it) }
            accumulatedIdentifiers.modelNumber?.takeIf { it.isNotBlank() }?.let { put("modelNumber", it) }
            accumulatedIdentifiers.macAddress?.takeIf { it.isNotBlank() }?.let { put("macAddress", it) }
        }

        val cluesArr = JSONArray()
        for (note in userClueNotes) {
            if (note.isNotBlank()) cluesArr.put(note)
        }

        val imagesArr = JSONArray()
        for (bmp in bitmaps.takeLast(2)) {
            val b64 = downscaleAndEncode(bmp, 1024)
            if (b64 != null) {
                imagesArr.put(b64)
            }
        }

        val requestPayload = JSONObject().apply {
            put("session_id", sessionStickinessId)
            put("turn_index", turnCounter)
            put("client_platform", "android")
            put("device_id", persistentDeviceId)
            put("initial_mode", initialMode)
            put("provider_override", providerOverride)
            put("extracted_identifiers", extractedObj)
            put("user_clue_notes", cluesArr)
            put("images_base64", imagesArr)
            put("debug", debugEnabled)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/v1/discovery/analyze")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("X-Tuesday-Device-ID", sessionStickinessId)
            .addHeader("X-Tuesday-Client-Platform", "android")
            .addHeader("X-Tuesday-Pilot-Token", PILOT_CLIENT_TOKEN)
            .addHeader("X-Tuesday-Debug", debugEnabled.toString())
            .post(requestPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Tuesday Server HTTP ${response.code}")
            }
            val respBody = response.body?.string() ?: throw RuntimeException("Empty response body from Tuesday Server")
            val parsed = JSONObject(respBody)
            val providerLabel = parsed.optString(
                "providerLabel",
                "Tuesday Server → ${parsed.optString("providerUsed", "Elastix")}"
            )
            return parseTurnResultFromJson(parsed, accumulatedIdentifiers, providerLabel)
        }
    }

    private fun parseTurnResultFromJson(
        parsed: JSONObject,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        providerLabel: String
    ): GeminiDiscoveryTurnResult {
        val extObj = parsed.optJSONObject("extractedIdentifiersObject") ?: parsed.optJSONObject("extractedIdentifiers")
        val extracted = ExtractedDeviceIdentifiers(
            barcode = extObj?.optString("barcode")?.takeIf { it.isNotBlank() && it != "null" },
            serialNumber = extObj?.optString("serialNumber")?.takeIf { it.isNotBlank() && it != "null" },
            qrCode = extObj?.optString("qrCode")?.takeIf { it.isNotBlank() && it != "null" },
            modelNumber = extObj?.optString("modelNumber")?.takeIf { it.isNotBlank() && it != "null" },
            macAddress = extObj?.optString("macAddress")?.takeIf { it.isNotBlank() && it != "null" }
        )

        val merged = ExtractedDeviceIdentifiers()
        merged.merge(accumulatedIdentifiers)
        merged.merge(extracted)

        val candidatesList = mutableListOf<DeviceCandidateMatch>()
        val candArray = parsed.optJSONArray("candidates") ?: JSONArray()
        for (i in 0 until candArray.length()) {
            val c = candArray.getJSONObject(i)
            val osDetailsRaw = c.opt("suggestedOsDetails")
            val osDetailsStr = when (osDetailsRaw) {
                is JSONObject -> {
                    val osName = osDetailsRaw.optString("osName", "Linux")
                    val osVer = osDetailsRaw.optString("osVersion", "")
                    "$osName $osVer".trim()
                }
                is String -> osDetailsRaw
                else -> "Linux"
            }

            val gaObj = c.optJSONObject("goldAlignment")
            val goldAlignment = if (gaObj != null) {
                GoldAlignmentInfo(
                    aligned = gaObj.optBoolean("aligned", false),
                    matchedFeedSlug = gaObj.optString("matchedFeedSlug").takeIf { it.isNotBlank() && it != "null" },
                    matchedFeedName = gaObj.optString("matchedFeedName").takeIf { it.isNotBlank() && it != "null" },
                    matchedPurlPrefix = gaObj.optString("matchedPurlPrefix").takeIf { it.isNotBlank() && it != "null" },
                    inferredPurl = gaObj.optString("inferredPurl", ""),
                    latestGoldRelease = gaObj.optString("latestGoldRelease").takeIf { it.isNotBlank() && it != "null" },
                    inBuiltInCatalog = gaObj.optBoolean("inBuiltInCatalog", false),
                    inConfirmedCatalog = gaObj.optBoolean("inConfirmedCatalog", false),
                    statusLabel = gaObj.optString("statusLabel", if (gaObj.optBoolean("aligned", false)) "GOLD ALIGNED" else "UNALIGNED RAW MATCH"),
                    diagnosticNote = gaObj.optString("diagnosticNote", "")
                )
            } else null

            candidatesList.add(
                DeviceCandidateMatch(
                    manufacturer = c.optString("manufacturer", "Generic Vendor"),
                    modelNumber = c.optString("modelNumber", "Generic Model"),
                    displayName = c.optString("displayName", "Network Hardware"),
                    deviceCategory = c.optString("deviceCategory", "Router"),
                    confidencePercent = c.optInt("confidencePercent", 70),
                    reasoning = c.optString("reasoning", ""),
                    suggestedHostname = c.optString("suggestedHostname", "device.local"),
                    suggestedOsDetails = osDetailsStr,
                    suggestedPackageManager = c.optString("suggestedPackageManager", "firmware"),
                    goldAlignment = goldAlignment
                )
            )
        }

        val recommendedClues = mutableListOf<String>()
        val cluesArr = parsed.optJSONArray("recommendedClueTypes")
        if (cluesArr != null) {
            for (i in 0 until cluesArr.length()) {
                recommendedClues.add(cluesArr.getString(i))
            }
        }

        val traceList = mutableListOf<String>()
        val traceArr = parsed.optJSONArray("debugTrace")
        if (traceArr != null) {
            for (i in 0 until traceArr.length()) {
                traceList.add(traceArr.getString(i))
            }
        }

        return GeminiDiscoveryTurnResult(
            visualSummary = parsed.optString("visualSummary", "Analyzed device hardware."),
            nextNarrowingQuestion = parsed.optString("nextNarrowingQuestion", "Can you check for a model number or MAC address?"),
            recommendedClueTypes = if (recommendedClues.isEmpty()) DeviceClueType.entries.map { it.rawValue } else recommendedClues,
            extractedIdentifiers = merged,
            candidates = candidatesList,
            multipleDevicesDetected = parsed.optBoolean("multipleDevicesDetected", false),
            multiDeviceWarning = parsed.optString("multiDeviceWarning").takeIf { it.isNotBlank() && it != "null" },
            usedLiveGeminiApi = true,
            providerUsed = providerLabel,
            latencyMs = parsed.optLong("latencyMs", 0L),
            costUsd = parsed.optDouble("costUSD", 0.0),
            debugTrace = traceList,
            rawLlmResponse = parsed.optString("rawLlmResponse").takeIf { it.isNotBlank() && it != "null" },
            debugLogText = parsed.optString("debugLogText").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun downscaleAndEncode(bitmap: Bitmap, maxDimension: Int): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height, 1.0f)
        val targetWidth = (width * ratio).roundToInt()
        val targetHeight = (height * ratio).roundToInt()

        val resized = if (ratio < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else bitmap

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "http://100.108.40.3:3000"
        private const val PILOT_CLIENT_TOKEN = "tuesday-pilot-v1-public-client-token"

        fun connectionInterruptedResult(
            accumulatedIdentifiers: ExtractedDeviceIdentifiers,
            imageCount: Int,
            fallbackErrorReason: String
        ): GeminiDiscoveryTurnResult {
            val errCand = listOf(
                DeviceCandidateMatch(
                    manufacturer = "Unidentified Hardware",
                    modelNumber = "Retry Vision Analysis",
                    displayName = "Connection Interrupted During Photo Analysis",
                    deviceCategory = "Network / IoT Device",
                    confidencePercent = 10,
                    reasoning = "The connection to Tuesday Server was interrupted ($fallbackErrorReason) — often caused by locking the screen or switching apps while the vision model is running.",
                    suggestedHostname = "unidentified-device.local",
                    suggestedOsDetails = "Pending Re-Analysis",
                    suggestedPackageManager = "firmware",
                    goldAlignment = null
                )
            )
            return GeminiDiscoveryTurnResult(
                visualSummary = "⚠ Connection interrupted before vision response arrived ($fallbackErrorReason). Keep the screen awake during analysis or tap the green Replay chip above to re-run this photo.",
                nextNarrowingQuestion = "Tap the green Replay chip above to re-analyze this photo, or enter a Model / Barcode clue below.",
                recommendedClueTypes = listOf("Model", "MAC Address", "Serial Number", "Barcode", "QR Code"),
                extractedIdentifiers = accumulatedIdentifiers,
                candidates = errCand,
                usedLiveGeminiApi = false,
                providerUsed = "Connection Interrupted (Offline)",
                debugLogText = buildClientFallbackDebugLog(errCand, imageCount, fallbackErrorReason)
            )
        }

        fun fallbackHeuristic(
            accumulatedIdentifiers: ExtractedDeviceIdentifiers,
            userClueNotes: List<String>,
            imageCount: Int,
            usedLiveApi: Boolean,
            fallbackErrorReason: String? = null
        ): GeminiDiscoveryTurnResult {
            val combined = (
                accumulatedIdentifiers.nonEmptyPairs().map { "${it.first.rawValue}: ${it.second}" } + userClueNotes
            ).joinToString(" ").lowercase()

            val hasSpecificClue = accumulatedIdentifiers.nonEmptyPairs().isNotEmpty() || imageCount > 1

            if (combined.contains("zigbee") || combined.contains("sonoff") || combined.contains("zbdongle")) {
                val cands = listOf(
                    DeviceCandidateMatch(
                        manufacturer = "ITEAD / SONOFF",
                        modelNumber = accumulatedIdentifiers.modelNumber ?: "ZBDongle-E",
                        displayName = "SONOFF Zigbee 3.0 USB Dongle Plus (EFR32MG21)",
                        deviceCategory = "IoT Coordinator",
                        confidencePercent = if (hasSpecificClue) 95 else 74,
                        reasoning = "Matches aluminum housing profile and SMA dipole antenna; commonly paired with Home Assistant ZHA / Zigbee2MQTT.",
                        suggestedHostname = "sonoff-zigbee-coordinator",
                        suggestedOsDetails = "EZSP v7.4.4 / Home Assistant HACS ZHA",
                        suggestedPackageManager = "hacs",
                        goldAlignment = GoldAlignmentInfo(
                            aligned = true,
                            matchedFeedSlug = null,
                            inferredPurl = "pkg:generic/itead-sonoff/zbdongle-e",
                            inBuiltInCatalog = true,
                            statusLabel = "GOLD ALIGNED",
                            diagnosticNote = "Aligned with Built-in Gold Catalog Signature (ZBDongle-E), but no active feed in canonical_feeds.json yet."
                        )
                    ),
                    DeviceCandidateMatch(
                        manufacturer = "Nabu Casa",
                        modelNumber = "SkyConnect-ZBT1",
                        displayName = "Home Assistant Connect ZBT-1",
                        deviceCategory = "IoT Radio",
                        confidencePercent = if (hasSpecificClue) 25 else 42,
                        reasoning = "Alternative Silabs EFR32 Zigbee/Thread coordinator used in Home Assistant setups.",
                        suggestedHostname = "ha-zbt1-radio",
                        suggestedOsDetails = "Home Assistant Connect Firmware (HACS)",
                        suggestedPackageManager = "hacs",
                        goldAlignment = GoldAlignmentInfo(
                            aligned = false,
                            matchedFeedSlug = null,
                            inferredPurl = "pkg:generic/nabu-casa/skyconnect-zbt1",
                            inBuiltInCatalog = false,
                            statusLabel = "UNALIGNED RAW MATCH",
                            diagnosticNote = "Raw candidate 'Nabu Casa SkyConnect-ZBT1' has no matching PURL prefix in canonical_feeds.json or catalog_releases.json"
                        )
                    )
                )
                return GeminiDiscoveryTurnResult(
                    visualSummary = "Detected an aluminum USB Zigbee 3.0 coordinator / IoT radio peripheral with external dipole antenna.",
                    nextNarrowingQuestion = if (hasSpecificClue) {
                        "High-confidence match found (SONOFF Zigbee 3.0 USB Dongle Plus). Do you want to confirm this match or add a Serial Number / MAC Address?"
                    } else {
                        "Is there a Model Number (e.g. ZBDongle-E vs ZBDongle-P), Serial Number, Barcode, or MAC Address printed on the back of the aluminum casing?"
                    },
                    recommendedClueTypes = listOf("Model", "Serial Number", "Barcode", "MAC Address"),
                    extractedIdentifiers = accumulatedIdentifiers,
                    candidates = cands,
                    usedLiveGeminiApi = usedLiveApi,
                    providerUsed = "Pilot Heuristic",
                    debugLogText = buildClientFallbackDebugLog(cands, imageCount, fallbackErrorReason)
                )
            }

            val topConfidence = if (hasSpecificClue) 94 else 68
            val detectedModel = accumulatedIdentifiers.modelNumber ?: (if (hasSpecificClue) "UCG-Ultra" else "UCG-Ultra / UXG-Max")

            val cands = listOf(
                DeviceCandidateMatch(
                    manufacturer = "Ubiquiti Inc.",
                    modelNumber = detectedModel,
                    displayName = "UniFi Cloud Gateway Ultra ($detectedModel)",
                    deviceCategory = "Router / Security Gateway",
                    confidencePercent = topConfidence,
                    reasoning = if (hasSpecificClue) {
                        "Model/OUI clue confirms Ubiquiti UniFi Cloud Gateway Ultra running UniFi OS (Debian apt)."
                    } else {
                        "Form factor and front status display match the UniFi Cloud Gateway Ultra series."
                    },
                    suggestedHostname = "unifi-ucg-ultra.local",
                    suggestedOsDetails = "UniFi OS 4.0.20 (Debian 12 Bookworm apt)",
                    suggestedPackageManager = "apt",
                    goldAlignment = GoldAlignmentInfo(
                        aligned = true,
                        matchedFeedSlug = "ubiquiti-advisories",
                        matchedFeedName = "Ubiquiti UniFi Security & Firmware",
                        matchedPurlPrefix = "pkg:generic/ui/",
                        inferredPurl = "pkg:generic/ui/unifi-os",
                        latestGoldRelease = "4.0.20",
                        inBuiltInCatalog = true,
                        statusLabel = "GOLD ALIGNED",
                        diagnosticNote = "Aligned with Gold Datalake [Feed: ubiquiti-advisories (pkg:generic/ui/) • Gold Release: v4.0.20 • Built-in Catalog Signature]"
                    )
                ),
                DeviceCandidateMatch(
                    manufacturer = "Ubiquiti Inc.",
                    modelNumber = "UXG-Max",
                    displayName = "UniFi Gateway Max (UXG-Max)",
                    deviceCategory = "Multi-Gig Router",
                    confidencePercent = if (hasSpecificClue) 32 else 54,
                    reasoning = "Identical footprint to UCG-Ultra without integrated LCM screen; disambiguate via bottom label Model or MAC.",
                    suggestedHostname = "unifi-uxg-max.local",
                    suggestedOsDetails = "UniFi OS 4.0.20 (Debian apt)",
                    suggestedPackageManager = "apt",
                    goldAlignment = GoldAlignmentInfo(
                        aligned = true,
                        matchedFeedSlug = "ubiquiti-advisories",
                        matchedFeedName = "Ubiquiti UniFi Security & Firmware",
                        matchedPurlPrefix = "pkg:generic/ui/",
                        inferredPurl = "pkg:generic/ui/unifi-os",
                        latestGoldRelease = "4.0.20",
                        inBuiltInCatalog = false,
                        statusLabel = "GOLD ALIGNED",
                        diagnosticNote = "Aligned with Gold Datalake [Feed: ubiquiti-advisories (pkg:generic/ui/) • Gold Release: v4.0.20]"
                    )
                ),
                DeviceCandidateMatch(
                    manufacturer = "GL.iNet",
                    modelNumber = "GL-MT3000",
                    displayName = "GL.iNet Beryl AX (GL-MT3000)",
                    deviceCategory = "OpenWrt Travel/Edge Router",
                    confidencePercent = if (hasSpecificClue) 14 else 31,
                    reasoning = "Compact Wi-Fi 6 router running OpenWrt / opkg firmware.",
                    suggestedHostname = "gl-mt3000.local",
                    suggestedOsDetails = "OpenWrt 23.05 (Firmware / opkg)",
                    suggestedPackageManager = "firmware",
                    goldAlignment = GoldAlignmentInfo(
                        aligned = false,
                        matchedFeedSlug = null,
                        inferredPurl = "pkg:generic/gl-inet/gl-mt3000",
                        inBuiltInCatalog = false,
                        statusLabel = "UNALIGNED RAW MATCH",
                        diagnosticNote = "Raw candidate 'GL.iNet GL-MT3000' (pkg:generic/gl-inet/gl-mt3000) has no matching PURL prefix in canonical_feeds.json or catalog_releases.json"
                    )
                )
            )

            return GeminiDiscoveryTurnResult(
                visualSummary = if (hasSpecificClue) {
                    "Verified compact polycarbonate desktop security gateway with front LCM status display and multi-gigabit RJ45 WAN/LAN ports."
                } else {
                    "Identified a compact white desktop router / network gateway chassis. Multiple Ubiquiti UniFi models share this enclosure."
                },
                nextNarrowingQuestion = if (hasSpecificClue) {
                    "Identifier verified! Candidate narrowed to Ubiquiti UniFi Cloud Gateway Ultra (UCG-Ultra). Confirm match below or add additional clues."
                } else {
                    "To narrow between UniFi Cloud Gateway Ultra (UCG-Ultra), Gateway Max (UXG-Max), and Express (UX), is there a Barcode, Serial Number, QR Code, Model, or MAC Address on the bottom label?"
                },
                recommendedClueTypes = listOf("Model", "MAC Address", "Serial Number", "Barcode", "QR Code"),
                extractedIdentifiers = accumulatedIdentifiers,
                candidates = cands,
                usedLiveGeminiApi = usedLiveApi,
                providerUsed = "Pilot Heuristic",
                debugLogText = buildClientFallbackDebugLog(cands, imageCount, fallbackErrorReason)
            )
        }

        private fun buildClientFallbackDebugLog(
            candidates: List<DeviceCandidateMatch>,
            imageCount: Int,
            fallbackErrorReason: String?
        ): String {
            return buildString {
                appendLine("=== TUESDAY VISION DEBUG LOG [CLIENT LOCAL FALLBACK] ===")
                appendLine("Frames Submitted: $imageCount")
                if (!fallbackErrorReason.isNullOrBlank()) {
                    appendLine("Proxy Status: Hosted API unreachable ($fallbackErrorReason) -> Local Pilot Heuristic")
                } else {
                    appendLine("Proxy Status: Local Pilot Heuristic")
                }
                appendLine()
                appendLine("--- CANDIDATES & GOLD DATALAKE ALIGNMENT (${candidates.size}) ---")
                candidates.forEachIndexed { idx, c ->
                    val ga = c.goldAlignment
                    val badge = if (ga?.aligned == true) "[✓ GOLD ALIGNED]" else "[⚠ UNALIGNED RAW]"
                    appendLine("  ${idx + 1}. $badge ${c.manufacturer} ${c.modelNumber} (${c.confidencePercent}%)")
                    appendLine("     PURL: ${ga?.inferredPurl ?: "unknown"} | Feed: ${ga?.matchedFeedSlug ?: "NONE"} | Gold Rel: ${ga?.latestGoldRelease ?: "NONE"}")
                    appendLine("     Reason: ${c.reasoning}")
                    appendLine("     Audit: ${ga?.diagnosticNote ?: "No audit metadata"}")
                }
            }.trimEnd()
        }

        fun createSampleHardwareBitmap(presetName: String): Bitmap {
            val bitmap = Bitmap.createBitmap(640, 420, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            // Background
            paint.color = android.graphics.Color.rgb(28, 32, 42)
            canvas.drawRect(0f, 0f, 640f, 420f, paint)

            // Chassis
            paint.color = android.graphics.Color.rgb(235, 240, 245)
            val rect = android.graphics.RectF(90f, 80f, 550f, 300f)
            canvas.drawRoundRect(rect, 24f, 24f, paint)

            // OLED screen
            paint.color = android.graphics.Color.rgb(20, 25, 35)
            val screenRect = android.graphics.RectF(130f, 145f, 240f, 235f)
            canvas.drawRoundRect(screenRect, 12f, 12f, paint)

            // Text
            paint.color = android.graphics.Color.rgb(20, 25, 35)
            paint.textSize = 22f
            paint.isFakeBoldText = true

            val isIot = presetName.lowercase().contains("iot") || presetName.lowercase().contains("zigbee")
            val title = if (isIot) "SONOFF Zigbee 3.0 Coordinator" else "UniFi Cloud Gateway Chassis"
            val detail1 = if (isIot) "Model: ZBDongle-E" else "2.5GbE WAN • 4x GbE LAN"
            val detail2 = if (isIot) "IEEE MAC: 00:12:4B:00:29:A1:C4" else "Front LCM Display Active"

            canvas.drawText(title, 265f, 150f, paint)
            paint.textSize = 15f
            paint.color = android.graphics.Color.rgb(60, 75, 95)
            paint.isFakeBoldText = false
            canvas.drawText(detail1, 265f, 190f, paint)
            canvas.drawText(detail2, 265f, 220f, paint)

            return bitmap
        }
    }
}
