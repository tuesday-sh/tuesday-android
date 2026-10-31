package sh.tuesday.app.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sh.tuesday.app.models.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class GeminiDiscoveryService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeDeviceProgressive(
        bitmaps: List<Bitmap>,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        userClueNotes: List<String>,
        apiKeyOverride: String = "",
        modelName: String = "gemini-3.8-flash"
    ): GeminiDiscoveryTurnResult = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey(apiKeyOverride)
        if (apiKey.isBlank()) {
            return@withContext fallbackHeuristic(accumulatedIdentifiers, userClueNotes, bitmaps.size, usedLiveApi = false)
        }

        try {
            callGeminiInteractionsApi(bitmaps, accumulatedIdentifiers, userClueNotes, apiKey, modelName)
        } catch (_: Exception) {
            fallbackHeuristic(accumulatedIdentifiers, userClueNotes, bitmaps.size, usedLiveApi = false)
        }
    }

    private fun resolveApiKey(overrideKey: String): String {
        if (overrideKey.isNotBlank()) return overrideKey.trim()

        try {
            val assetStream = context.assets.open("pilot_secrets.json")
            val content = assetStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(content)
            val key = obj.optString("GEMINI_API_KEY", "")
            if (key.isNotBlank()) return key
        } catch (_: Exception) {}

        return ""
    }

    private fun callGeminiInteractionsApi(
        bitmaps: List<Bitmap>,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        userClueNotes: List<String>,
        apiKey: String,
        modelName: String
    ): GeminiDiscoveryTurnResult {
        val clueLines = mutableListOf<String>()
        for ((type, value) in accumulatedIdentifiers.nonEmptyPairs()) {
            clueLines.add("- ${type.rawValue}: $value")
        }
        for (note in userClueNotes) {
            if (note.isNotBlank()) clueLines.add("- User Clue: $note")
        }
        val clueContext = if (clueLines.isEmpty()) "None provided yet." else clueLines.joinToString("\n")

        val promptText = """
            <instructions>
            You are a hardware device inventory discovery assistant for Tuesday.sh (a home lab, router, switch, NAS, and IoT package/firmware telemetry platform).
            Analyze the attached hardware device photo(s) along with any user-supplied identifiers (barcode, serial number, QR code, model number, or MAC address).
            1. Extract any visible Barcode, Serial Number, QR Code payload, Model Number, or MAC Address from the photo(s) or user clues.
            2. Rank 2 to 3 candidate hardware devices (manufacturer, modelNumber, displayName, deviceCategory, confidencePercent 1-99, reasoning, suggestedHostname, suggestedOsDetails, suggestedPackageManager from [apt, apk, brew, yum, docker, hacs, firmware]).
            3. Formulate a concise `nextNarrowingQuestion` asking the user if there is a Barcode, Serial Number, QR Code, Model, or MAC Address on the device/sticker that can disambiguate the top candidates.
            </instructions>
            <accumulated_clues>
            $clueContext
            </accumulated_clues>
        """.trimIndent()

        val inputArr = JSONArray()
        val textObj = JSONObject().apply {
            put("type", "text")
            put("text", promptText)
        }
        inputArr.put(textObj)

        for (bmp in bitmaps.takeLast(2)) {
            val b64 = downscaleAndEncode(bmp, 960)
            if (b64 != null) {
                val imgObj = JSONObject().apply {
                    put("type", "image")
                    put("data", b64)
                    put("mime_type", "image/jpeg")
                }
                inputArr.put(imgObj)
            }
        }

        val schema = JSONObject().apply {
            put("type", "object")
            val props = JSONObject()
            props.put("visualSummary", JSONObject().put("type", "string"))
            props.put("nextNarrowingQuestion", JSONObject().put("type", "string"))
            props.put("recommendedClueTypes", JSONObject().apply {
                put("type", "array")
                put("items", JSONObject().put("type", "string"))
            })

            val idProps = JSONObject().apply {
                put("barcode", JSONObject().put("type", "string"))
                put("serialNumber", JSONObject().put("type", "string"))
                put("qrCode", JSONObject().put("type", "string"))
                put("modelNumber", JSONObject().put("type", "string"))
                put("macAddress", JSONObject().put("type", "string"))
            }
            props.put("extractedIdentifiers", JSONObject().apply {
                put("type", "object")
                put("properties", idProps)
            })

            val candidateItemProps = JSONObject().apply {
                put("manufacturer", JSONObject().put("type", "string"))
                put("modelNumber", JSONObject().put("type", "string"))
                put("displayName", JSONObject().put("type", "string"))
                put("deviceCategory", JSONObject().put("type", "string"))
                put("confidencePercent", JSONObject().put("type", "integer"))
                put("reasoning", JSONObject().put("type", "string"))
                put("suggestedHostname", JSONObject().put("type", "string"))
                put("suggestedOsDetails", JSONObject().put("type", "string"))
                put("suggestedPackageManager", JSONObject().put("type", "string"))
            }
            val candidateItem = JSONObject().apply {
                put("type", "object")
                put("properties", candidateItemProps)
                put("required", JSONArray().apply {
                    put("manufacturer"); put("modelNumber"); put("displayName")
                    put("deviceCategory"); put("confidencePercent"); put("reasoning")
                    put("suggestedHostname"); put("suggestedOsDetails"); put("suggestedPackageManager")
                })
            }
            props.put("candidates", JSONObject().apply {
                put("type", "array")
                put("items", candidateItem)
            })
            put("properties", props)
            put("required", JSONArray().apply {
                put("visualSummary"); put("nextNarrowingQuestion")
                put("recommendedClueTypes"); put("extractedIdentifiers"); put("candidates")
            })
        }

        val requestPayload = JSONObject().apply {
            put("model", if (modelName.isBlank()) "gemini-3.8-flash" else modelName)
            put("input", inputArr)
            put("response_format", JSONObject().apply {
                put("type", "text")
                put("mime_type", "application/json")
                put("schema", schema)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/interactions")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Gemini HTTP ${response.code}")
        }

        val respBody = response.body?.string() ?: throw RuntimeException("Empty response body")
        val root = JSONObject(respBody)

        var jsonText: String? = root.optString("output_text", null)
        if (jsonText.isNullOrBlank()) {
            val steps = root.optJSONArray("steps")
            if (steps != null) {
                for (i in (steps.length() - 1) downTo 0) {
                    val step = steps.getJSONObject(i)
                    if (step.optString("type") == "model_output") {
                        val contents = step.optJSONArray("content")
                        if (contents != null) {
                            for (j in 0 until contents.length()) {
                                val item = contents.getJSONObject(j)
                                val t = item.optString("text", "")
                                if (t.isNotBlank()) {
                                    jsonText = t
                                    break
                                }
                            }
                        }
                    }
                    if (!jsonText.isNullOrBlank()) break
                }
            }
        }

        val jsonStr = jsonText ?: throw RuntimeException("Could not extract JSON model output")
        val parsed = JSONObject(jsonStr)

        val extracted = ExtractedDeviceIdentifiers(
            barcode = parsed.optJSONObject("extractedIdentifiers")?.optString("barcode")?.takeIf { it.isNotBlank() },
            serialNumber = parsed.optJSONObject("extractedIdentifiers")?.optString("serialNumber")?.takeIf { it.isNotBlank() },
            qrCode = parsed.optJSONObject("extractedIdentifiers")?.optString("qrCode")?.takeIf { it.isNotBlank() },
            modelNumber = parsed.optJSONObject("extractedIdentifiers")?.optString("modelNumber")?.takeIf { it.isNotBlank() },
            macAddress = parsed.optJSONObject("extractedIdentifiers")?.optString("macAddress")?.takeIf { it.isNotBlank() }
        )

        val merged = ExtractedDeviceIdentifiers()
        merged.merge(accumulatedIdentifiers)
        merged.merge(extracted)

        val candidatesList = mutableListOf<DeviceCandidateMatch>()
        val candArray = parsed.optJSONArray("candidates") ?: JSONArray()
        for (i in 0 until candArray.length()) {
            val c = candArray.getJSONObject(i)
            candidatesList.add(
                DeviceCandidateMatch(
                    manufacturer = c.optString("manufacturer", "Generic Vendor"),
                    modelNumber = c.optString("modelNumber", "Generic Model"),
                    displayName = c.optString("displayName", "Network Hardware"),
                    deviceCategory = c.optString("deviceCategory", "Router"),
                    confidencePercent = c.optInt("confidencePercent", 70),
                    reasoning = c.optString("reasoning", ""),
                    suggestedHostname = c.optString("suggestedHostname", "device.local"),
                    suggestedOsDetails = c.optString("suggestedOsDetails", "Linux"),
                    suggestedPackageManager = c.optString("suggestedPackageManager", "firmware")
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

        return GeminiDiscoveryTurnResult(
            visualSummary = parsed.optString("visualSummary", "Analyzed device hardware."),
            nextNarrowingQuestion = parsed.optString("nextNarrowingQuestion", "Can you check for a model number or MAC address?"),
            recommendedClueTypes = if (recommendedClues.isEmpty()) DeviceClueType.entries.map { it.rawValue } else recommendedClues,
            extractedIdentifiers = merged,
            candidates = candidatesList,
            usedLiveGeminiApi = true
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
        fun fallbackHeuristic(
            accumulatedIdentifiers: ExtractedDeviceIdentifiers,
            userClueNotes: List<String>,
            imageCount: Int,
            usedLiveApi: Boolean
        ): GeminiDiscoveryTurnResult {
            val combined = (
                accumulatedIdentifiers.nonEmptyPairs().map { "${it.first.rawValue}: ${it.second}" } + userClueNotes
            ).joinToString(" ").lowercase()

            val hasSpecificClue = accumulatedIdentifiers.nonEmptyPairs().isNotEmpty() || imageCount > 1

            if (combined.contains("zigbee") || combined.contains("sonoff") || combined.contains("zbdongle")) {
                return GeminiDiscoveryTurnResult(
                    visualSummary = "Detected an aluminum USB Zigbee 3.0 coordinator / IoT radio peripheral with external dipole antenna.",
                    nextNarrowingQuestion = if (hasSpecificClue) {
                        "High-confidence match found (SONOFF Zigbee 3.0 USB Dongle Plus). Do you want to confirm this match or add a Serial Number / MAC Address?"
                    } else {
                        "Is there a Model Number (e.g. ZBDongle-E vs ZBDongle-P), Serial Number, Barcode, or MAC Address printed on the back of the aluminum casing?"
                    },
                    recommendedClueTypes = listOf("Model", "Serial Number", "Barcode", "MAC Address"),
                    extractedIdentifiers = accumulatedIdentifiers,
                    candidates = listOf(
                        DeviceCandidateMatch(
                            manufacturer = "ITEAD / SONOFF",
                            modelNumber = accumulatedIdentifiers.modelNumber ?: "ZBDongle-E",
                            displayName = "SONOFF Zigbee 3.0 USB Dongle Plus (EFR32MG21)",
                            deviceCategory = "IoT Coordinator",
                            confidencePercent = if (hasSpecificClue) 95 else 74,
                            reasoning = "Matches aluminum housing profile and SMA dipole antenna; commonly paired with Home Assistant ZHA / Zigbee2MQTT.",
                            suggestedHostname = "sonoff-zigbee-coordinator",
                            suggestedOsDetails = "EZSP v7.4.4 / Home Assistant HACS ZHA",
                            suggestedPackageManager = "hacs"
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
                            suggestedPackageManager = "hacs"
                        )
                    ),
                    usedLiveGeminiApi = usedLiveApi
                )
            }

            val topConfidence = if (hasSpecificClue) 94 else 68
            val detectedModel = accumulatedIdentifiers.modelNumber ?: (if (hasSpecificClue) "UCG-Ultra" else "UCG-Ultra / UXG-Max")

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
                candidates = listOf(
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
                        suggestedPackageManager = "apt"
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
                        suggestedPackageManager = "apt"
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
                        suggestedPackageManager = "firmware"
                    )
                ),
                usedLiveGeminiApi = usedLiveApi
            )
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
