package sh.tuesday.app.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sh.tuesday.app.models.DeviceCandidateMatch
import sh.tuesday.app.models.ExtractedDeviceIdentifiers
import sh.tuesday.app.models.GeminiDiscoveryTurnResult
import sh.tuesday.app.models.GoldAlignmentInfo
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

enum class GoldClueKind(val badgeLabel: String) {
    DEVICE_TYPE("Type"),
    BRAND("Brand"),
    MODEL("Model"),
    IDENTIFIER("ID"),
    CUSTOM("Clue")
}

data class GoldClueItem(
    val id: String,
    val kind: GoldClueKind,
    val title: String,
    val subtitle: String,
    val categoryCode: String? = null,
    val typeId: String? = null,
    val vendor: String? = null,
    val model: String? = null,
    val upc: String? = null,
    val fccId: String? = null,
    val macAddress: String? = null,
    val warningNote: String? = null
) {
    val chipLabel: String
        get() = when (kind) {
            GoldClueKind.DEVICE_TYPE -> title
            GoldClueKind.BRAND -> vendor ?: title
            GoldClueKind.MODEL -> if (!vendor.isNullOrBlank() && !title.startsWith(vendor, ignoreCase = true)) {
                "$vendor $title"
            } else {
                title
            }
            GoldClueKind.IDENTIFIER -> title
            GoldClueKind.CUSTOM -> title
        }

    fun toServerClueString(): String = when (kind) {
        GoldClueKind.DEVICE_TYPE -> if (!categoryCode.isNullOrBlank()) "Device Type: $title ($categoryCode)" else "Device Type: $title"
        GoldClueKind.BRAND -> "Brand: ${vendor ?: title}"
        GoldClueKind.MODEL -> "Model: ${vendor?.let { "$it " } ?: ""}${model ?: title}".trim()
        GoldClueKind.IDENTIFIER -> title
        GoldClueKind.CUSTOM -> title
    }
}

class GoldClueIndexData(
    val etag: String,
    val datalakeVersion: String,
    val types: List<TypeEntry>,
    val brands: List<BrandEntry>,
    val models: List<ModelEntry>,
    val upcMap: Map<String, ModelEntry>,
    val fccGrantees: Map<String, FccGranteeEntry>,
    val ouiMap: Map<String, String>
) {
    data class TypeEntry(
        val id: String,
        val label: String,
        val categoryCode: String,
        val aliases: List<String>,
        val normTokens: List<String>
    )

    data class BrandEntry(
        val name: String,
        val rank: Int,
        val skuCount: Int,
        val categories: List<String>,
        val aliases: List<String>,
        val normAliases: List<String>
    )

    data class ModelEntry(
        val vendor: String,
        val brandRank: Int,
        val model: String,
        val normModel: String,
        val searchBlob: String,
        val categoryCode: String,
        val upc: String,
        val fccId: String,
        val normFcc: String,
        val typeId: String
    )

    data class FccGranteeEntry(
        val prefix: String,
        val vendor: String,
        val categoryCode: String,
        val isRadioModule: Boolean,
        val notes: String
    )

    fun contextualQuickPicks(selected: List<GoldClueItem>, limit: Int = 8): List<GoldClueItem> {
        // Once an explicit Gold Model is selected, no further quick-pick narrowing is needed
        if (selected.any { it.kind == GoldClueKind.MODEL }) return emptyList()

        val selectedIds = selected.map { it.id }.toSet()
        val activeVendor = selected.lastOrNull { !it.vendor.isNullOrBlank() }?.vendor
        val activeType = selected.lastOrNull { !it.typeId.isNullOrBlank() }?.typeId
        val activeCat = selected.lastOrNull { !it.categoryCode.isNullOrBlank() }?.categoryCode

        // 1. If a Brand is selected -> suggest top Gold Models for that Brand (strictly filtered by Type when present)
        if (!activeVendor.isNullOrBlank()) {
            val strictTypeMatches = if (activeType != null) {
                models.filter { it.vendor.equals(activeVendor, ignoreCase = true) && it.typeId == activeType }
            } else emptyList()

            val brandModels = if (strictTypeMatches.isNotEmpty()) {
                strictTypeMatches
            } else {
                models.filter { entry ->
                    entry.vendor.equals(activeVendor, ignoreCase = true) &&
                        (activeCat == null || entry.categoryCode == activeCat)
                }
            }
            val fallbackBrandModels = if (brandModels.isNotEmpty()) brandModels else models.filter {
                it.vendor.equals(activeVendor, ignoreCase = true)
            }
            return fallbackBrandModels
                .map { it.toClueItem() }
                .filter { it.id !in selectedIds }
                .take(limit)
        }

        // 2. If a Device Type is selected (e.g. "Router", "Doorbell", "Switch") -> suggest top Brands that make that type!
        if (!activeType.isNullOrBlank() || !activeCat.isNullOrBlank()) {
            val matchingVendorsWithType = models
                .filter { (!activeType.isNullOrBlank() && it.typeId == activeType) }
                .map { it.vendor.lowercase() }
                .toSet()

            val candidateBrands = brands.filter { b ->
                if (matchingVendorsWithType.isNotEmpty()) {
                    b.name.lowercase() in matchingVendorsWithType
                } else {
                    activeCat != null && activeCat in b.categories
                }
            }
            return candidateBrands
                .map { it.toClueItem() }
                .filter { it.id !in selectedIds }
                .take(limit)
        }

        // 3. Default (0 clues yet) -> common human-spoken Device Types
        return types
            .map { it.toClueItem() }
            .filter { it.id !in selectedIds }
            .take(limit)
    }

    fun querySuggestions(
        rawQuery: String,
        selected: List<GoldClueItem> = emptyList(),
        limit: Int = 6
    ): List<GoldClueItem> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return emptyList()

        val selectedIds = selected.map { it.id }.toSet()
        val activeVendor = selected.lastOrNull { !it.vendor.isNullOrBlank() }?.vendor
        val activeType = selected.lastOrNull { !it.typeId.isNullOrBlank() }?.typeId
        val activeCat = selected.lastOrNull { !it.categoryCode.isNullOrBlank() }?.categoryCode

        val qLow = trimmed.lowercase()
        val qNorm = normalizeAlnum(trimmed)
        val qTokens = qLow.split(Regex("\\s+")).filter { it.isNotBlank() }

        val results = mutableListOf<Pair<Int, GoldClueItem>>()

        // A. Deterministic Identifier Checks (UPC/EAN digits, MAC OUI hex, FCC Grantee prefix)
        val digitsOnly = if (trimmed.all { it.isDigit() }) trimmed else ""
        if (digitsOnly.length in 8..14) {
            val normalizedUpc = if (digitsOnly.length == 13 && digitsOnly.startsWith("0")) digitsOnly.removePrefix("0") else digitsOnly
            upcMap[normalizedUpc]?.let { hit ->
                results.add(1000 to hit.toClueItem(customSubtitle = "${hit.vendor} • ${hit.categoryCode} • UPC $normalizedUpc"))
            }
        }

        val hexClean = trimmed.uppercase().replace(Regex("[:\\-.]"), "")
        val looksLikeMac = (trimmed.contains(":") || trimmed.contains("-")) && hexClean.length in 6..12 && hexClean.all { it in '0'..'9' || it in 'A'..'F' }
        if (looksLikeMac) {
            val ouiPrefix = hexClean.take(6)
            val ouiVendor = ouiMap[ouiPrefix]
            val formattedMac = hexClean.chunked(2).joinToString(":")
            val sub = if (ouiVendor != null) "IEEE OUI → $ouiVendor" else "Hardware MAC Address"
            results.add(
                980 to GoldClueItem(
                    id = "mac:$formattedMac",
                    kind = GoldClueKind.IDENTIFIER,
                    title = "MAC $formattedMac",
                    subtitle = sub,
                    vendor = ouiVendor,
                    macAddress = formattedMac
                )
            )
        }

        val fccUpper = qNorm.uppercase()
        if (fccUpper.length >= 3) {
            val granteeHit = fccGrantees.entries.firstOrNull { (prefix, _) ->
                fccUpper.startsWith(prefix) && (trimmed.contains("-") || fccUpper.length >= prefix.length + 2 || prefix.length >= 4)
            }?.value
            if (granteeHit != null) {
                val warn = if (granteeHit.isRadioModule) {
                    "Embedded radio module (${granteeHit.vendor}) — outer device may be a different brand"
                } else null
                results.add(
                    940 to GoldClueItem(
                        id = "fcc:${trimmed.uppercase()}",
                        kind = GoldClueKind.IDENTIFIER,
                        title = "FCC ${trimmed.uppercase()}",
                        subtitle = if (granteeHit.isRadioModule) {
                            "Radio SoC • ${granteeHit.notes}"
                        } else {
                            "${granteeHit.vendor} • ${granteeHit.categoryCode} FCC Grant"
                        },
                        categoryCode = granteeHit.categoryCode,
                        vendor = if (granteeHit.isRadioModule) null else granteeHit.vendor,
                        fccId = trimmed.uppercase(),
                        warningNote = warn
                    )
                )
            }
        }

        // B. Device Types (e.g. "router", "switch", "doorbell", "modem", "ups")
        for (t in types) {
            val exactAlias = t.aliases.any { it == qLow } || t.label.lowercase() == qLow
            val prefixAlias = t.aliases.any { it.startsWith(qLow) } || t.normTokens.any { it.startsWith(qNorm) }
            if (exactAlias || (qNorm.length >= 2 && prefixAlias)) {
                val score = if (exactAlias) 920 else 830
                results.add(score to t.toClueItem())
            }
        }

        // C. Gold Brands (e.g. "google", "linksys", "ubiquiti", "ring", "netgear")
        for (b in brands) {
            val bLow = b.name.lowercase()
            val exact = bLow == qLow || b.aliases.any { it == qLow }
            val prefix = bLow.startsWith(qLow) || b.aliases.any { it.startsWith(qLow) } || b.normAliases.any { it.startsWith(qNorm) }
            if (exact || (qNorm.length >= 2 && prefix)) {
                val rankBonus = (50 - b.rank.coerceAtMost(50))
                val typeBonus = if (activeCat != null && activeCat in b.categories) 30 else 0
                val score = (if (exact) 900 else 790) + rankBonus + typeBonus
                results.add(score to b.toClueItem())
            }
        }

        // D. Gold Hardware Models (matching model, vendor+model, or type tag like "doorbell")
        if (qNorm.length >= 2) {
            for (m in models) {
                var modelScore = 0
                if (m.normModel == qNorm) {
                    modelScore = 950
                } else if (m.normModel.startsWith(qNorm)) {
                    modelScore = 820
                } else if (m.normFcc.isNotEmpty() && m.normFcc.startsWith(fccUpper)) {
                    modelScore = 860
                } else if (qTokens.all { tok -> m.searchBlob.contains(tok) || m.normModel.contains(normalizeAlnum(tok)) }) {
                    modelScore = 710
                } else if (qNorm.length >= 3 && m.normModel.contains(qNorm)) {
                    modelScore = 680
                }

                if (modelScore > 0) {
                    val rankBonus = (40 - m.brandRank.coerceAtMost(40))
                    val vendorContextBonus = if (!activeVendor.isNullOrBlank() && m.vendor.equals(activeVendor, ignoreCase = true)) 95 else 0
                    val typeContextBonus = when {
                        !activeType.isNullOrBlank() && m.typeId == activeType -> 70
                        !activeCat.isNullOrBlank() && m.categoryCode == activeCat -> 35
                        else -> 0
                    }
                    results.add((modelScore + rankBonus + vendorContextBonus + typeContextBonus) to m.toClueItem())
                }
            }
        }

        val deduped = results
            .sortedByDescending { it.first }
            .map { it.second }
            .filter { it.id !in selectedIds }
            .distinctBy { it.id }
            .take(limit)
            .toMutableList()

        // Always allow adding free-form text if not an exact title match
        val exactMatchExists = deduped.any {
            it.title.equals(trimmed, ignoreCase = true) || it.chipLabel.equals(trimmed, ignoreCase = true)
        }
        if (!exactMatchExists && trimmed.length >= 2) {
            val customItem = classifyFreeTextClue(trimmed)
            if (customItem.id !in selectedIds) {
                if (deduped.size >= limit) {
                    deduped[deduped.lastIndex] = customItem
                } else {
                    deduped.add(customItem)
                }
            }
        }

        return deduped
    }

    /**
     * Checks if a typed Device Name on the Manual tab contains a recognizable Gold SKU or Brand.
     */
    fun inferFromDeviceName(deviceName: String, selected: List<GoldClueItem>): GoldClueItem? {
        val clean = deviceName.trim()
        if (clean.length < 3) return null
        val selectedIds = selected.map { it.id }.toSet()
        val tokens = clean.split(Regex("[\\s/()_]+")).filter { it.length >= 3 }

        for (tok in tokens) {
            val normTok = normalizeAlnum(tok)
            if (normTok.length >= 3 && normTok.any { it.isDigit() }) {
                val exactSku = models.firstOrNull { it.normModel == normTok }
                if (exactSku != null) {
                    val item = exactSku.toClueItem()
                    if (item.id !in selectedIds) return item
                }
            }
        }
        return null
    }

    fun classifyFreeTextClue(rawText: String): GoldClueItem {
        val trimmed = rawText.trim()
        val norm = normalizeAlnum(trimmed)

        // Check exact type, brand, or model first
        types.firstOrNull { it.label.equals(trimmed, ignoreCase = true) || it.aliases.any { a -> a.equals(trimmed, ignoreCase = true) } }
            ?.let { return it.toClueItem() }
        brands.firstOrNull { it.name.equals(trimmed, ignoreCase = true) || it.aliases.any { a -> a.equals(trimmed, ignoreCase = true) } }
            ?.let { return it.toClueItem() }
        models.firstOrNull { it.normModel == norm }
            ?.let { return it.toClueItem() }

        return GoldClueItem(
            id = "custom:${trimmed.lowercase()}",
            kind = GoldClueKind.CUSTOM,
            title = trimmed,
            subtitle = "Add custom hardware clue"
        )
    }

    /**
     * Resolves hardware candidates directly from the pinned local Gold Clue Index when no camera
     * bitmaps require multimodal vision inspection (`0` network turns, `0` LLM tokens, `< 1ms`).
     *
     * Returns `null` when zero local Gold SKUs match (e.g. an unknown/newly released model or
     * unmatched free-text description), signaling the caller to escalate to the server API.
     */
    fun resolveLocalDiscoveryResult(
        selected: List<GoldClueItem>,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers,
        scannedBarcodes: List<String> = emptyList(),
        manualDeviceName: String = "",
        debugEnabled: Boolean = false
    ): GeminiDiscoveryTurnResult? {
        val startNs = System.nanoTime()

        // 1. Check exact UPC/GTIN barcode match first (from scanned barcodes or selected clues)
        val candidateUpcs = buildList {
            accumulatedIdentifiers.barcode?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(scannedBarcodes)
            selected.mapNotNull { it.upc }.forEach { add(it) }
        }.map { raw ->
            val clean = raw.trim()
            if (clean.length == 13 && clean.startsWith("0") && clean.all { it.isDigit() }) {
                clean.removePrefix("0")
            } else {
                clean
            }
        }.distinct()

        val upcHits = candidateUpcs.mapNotNull { upcMap[it] }

        // 2. Check explicitly picked Gold MODEL clues
        val explicitModels = selected.filter { it.kind == GoldClueKind.MODEL }.mapNotNull { item ->
            val normM = normalizeAlnum(item.model ?: item.title)
            models.firstOrNull { m ->
                (item.vendor == null || m.vendor.equals(item.vendor, ignoreCase = true)) && m.normModel == normM
            }
        }

        // 3. Check custom free-text clues — every custom clue must match candidate searchBlob/normModel,
        //    otherwise we return null so the server/LLM can interpret the custom clue.
        val customClues = selected.filter { it.kind == GoldClueKind.CUSTOM }.map { it.title.trim().lowercase() }

        val exactMatches = (upcHits + explicitModels).distinctBy { "${it.vendor}:${it.normModel}" }

        val matchedEntries: List<ModelEntry>
        val isExactSkuHit: Boolean

        if (exactMatches.isNotEmpty()) {
            matchedEntries = exactMatches.take(5)
            isExactSkuHit = true
        } else {
            val activeVendor = selected.lastOrNull { !it.vendor.isNullOrBlank() }?.vendor
            val activeType = selected.lastOrNull { !it.typeId.isNullOrBlank() }?.typeId
            val activeCat = selected.lastOrNull { !it.categoryCode.isNullOrBlank() }?.categoryCode

            val hasFacet = !activeVendor.isNullOrBlank() || !activeType.isNullOrBlank() || customClues.isNotEmpty()
            if (!hasFacet && manualDeviceName.isBlank()) {
                return null
            }

            val nameTokens = if (!hasFacet && manualDeviceName.isNotBlank()) {
                manualDeviceName.trim().lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
            } else emptyList()

            val filtered = models.filter { entry ->
                val vendorOk = activeVendor.isNullOrBlank() || entry.vendor.equals(activeVendor, ignoreCase = true)
                val typeOk = when {
                    !activeType.isNullOrBlank() -> entry.typeId == activeType || (entry.typeId.isEmpty() && entry.categoryCode == activeCat)
                    !activeCat.isNullOrBlank() -> entry.categoryCode == activeCat
                    else -> true
                }
                val customOk = customClues.all { clue ->
                    val cTokens = clue.split(Regex("\\s+")).filter { it.isNotBlank() }
                    cTokens.all { tok -> entry.searchBlob.contains(tok) || entry.normModel.contains(normalizeAlnum(tok)) }
                }
                val nameOk = nameTokens.isEmpty() || nameTokens.any { tok ->
                    val nTok = normalizeAlnum(tok)
                    nTok.length >= 3 && (entry.normModel == nTok || entry.searchBlob.contains(tok))
                }
                vendorOk && typeOk && customOk && nameOk
            }

            if (filtered.isEmpty()) {
                return null
            }
            matchedEntries = filtered.sortedBy { it.brandRank }.take(5)
            isExactSkuHit = false
        }

        val typeLabelsById = types.associate { it.id to "${it.categoryCode} • ${it.label}" }
        val extracted = ExtractedDeviceIdentifiers(
            barcode = accumulatedIdentifiers.barcode ?: matchedEntries.firstOrNull()?.upc?.ifBlank { null },
            serialNumber = accumulatedIdentifiers.serialNumber,
            qrCode = accumulatedIdentifiers.qrCode,
            modelNumber = if (isExactSkuHit || matchedEntries.size == 1) matchedEntries.first().model else accumulatedIdentifiers.modelNumber,
            macAddress = accumulatedIdentifiers.macAddress
        )

        val candidates = matchedEntries.mapIndexed { idx, entry ->
            val conf = when {
                isExactSkuHit && idx == 0 -> 99
                isExactSkuHit -> (95 - idx * 3).coerceAtLeast(80)
                matchedEntries.size == 1 -> 94
                else -> (89 - idx * 3).coerceAtLeast(75)
            }
            val dispName = if (entry.model.startsWith(entry.vendor, ignoreCase = true)) {
                entry.model
            } else {
                "${entry.vendor} ${entry.model}"
            }
            val categoryDisplay = typeLabelsById[entry.typeId] ?: categoryLabelFor(entry.categoryCode)
            val suggestedHost = manualDeviceName.trim().ifBlank {
                "${entry.vendor}-${entry.model}".lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            }
            val purl = "pkg:generic/${normalizeAlnum(entry.vendor)}/${entry.model.lowercase().replace(Regex("[^a-z0-9.+-]+"), "-").trim('-')}"

            DeviceCandidateMatch(
                manufacturer = entry.vendor,
                modelNumber = entry.model,
                displayName = dispName,
                deviceCategory = categoryDisplay,
                confidencePercent = conf,
                reasoning = if (isExactSkuHit) {
                    "Exact SKU match in Pinned Gold Hardware Catalog (v$datalakeVersion • 0 network turns)."
                } else {
                    "Matched selected hardware clues in Pinned Gold Hardware Catalog (v$datalakeVersion • 0 network turns)."
                },
                suggestedHostname = suggestedHost,
                suggestedOsDetails = "Gold Catalog v$datalakeVersion (${entry.categoryCode})",
                suggestedPackageManager = "firmware",
                goldAlignment = GoldAlignmentInfo(
                    aligned = true,
                    matchedFeedSlug = "pinned-gold-v$datalakeVersion",
                    inferredPurl = purl,
                    inBuiltInCatalog = true,
                    inConfirmedCatalog = true,
                    statusLabel = "GOLD ALIGNED",
                    diagnosticNote = "Pinned Gold Clue Index [$etag] • 0 network turns • 0 LLM tokens"
                )
            )
        }

        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        val summary = if (isExactSkuHit) {
            "Resolved ${candidates.first().displayName} instantly from Pinned Gold Catalog."
        } else {
            "Found ${candidates.size} matching Gold Catalog ${if (candidates.size == 1) "model" else "models"} for your selected clues."
        }
        val nextPrompt = if (isExactSkuHit || candidates.size == 1) {
            "Exact Gold Catalog match ready — confirm below to add to your inventory."
        } else {
            "Select your model below, or add another clue / photo to narrow further."
        }

        val debugText = if (debugEnabled) {
            buildString {
                appendLine("[PINNED_GOLD_CACHE_HIT] etag=$etag datalake=v$datalakeVersion latency=${elapsedMs}ms")
                appendLine("  network_turns=0 llm_turns=0 total_tokens=0")
                appendLine("  clues=${selected.map { it.toServerClueString() }}")
                appendLine("  candidates=${candidates.map { "${it.manufacturer} ${it.modelNumber} (${it.confidencePercent}%)" }}")
            }.trimEnd()
        } else null

        return GeminiDiscoveryTurnResult(
            visualSummary = summary,
            extractedIdentifiers = extracted,
            candidates = candidates,
            multipleDevicesDetected = false,
            usedLiveGeminiApi = false,
            providerUsed = "Local Cache • v$datalakeVersion",
            latencyMs = elapsedMs,
            costUsd = 0.0,
            debugTrace = listOf("PINNED_GOLD_CACHE_HIT ($etag)"),
            debugLogText = debugText
        )
    }

    private fun categoryLabelFor(code: String): String = when (code) {
        "C1" -> "C1 • Power / UPS"
        "C2" -> "C2 • WAN / Modem"
        "C3" -> "C3 • Gateway / Router"
        "C4" -> "C4 • Core Switch"
        "C5" -> "C5 • Patch Panel"
        "C6" -> "C6 • Compute / NAS"
        "C7" -> "C7 • Wireless AP"
        else -> "C8 • IoT / Hub / Misc"
    }

    private fun TypeEntry.toClueItem() = GoldClueItem(
        id = "type:$id",
        kind = GoldClueKind.DEVICE_TYPE,
        title = label,
        subtitle = "$categoryCode • Device Type",
        categoryCode = categoryCode,
        typeId = id
    )

    private fun BrandEntry.toClueItem() = GoldClueItem(
        id = "brand:${name.lowercase()}",
        kind = GoldClueKind.BRAND,
        title = name,
        subtitle = "Brand • $skuCount Gold ${if (skuCount == 1) "Model" else "Models"} (${categories.take(3).joinToString(", ")})",
        categoryCode = categories.firstOrNull(),
        vendor = name
    )

    private fun ModelEntry.toClueItem(customSubtitle: String? = null) = GoldClueItem(
        id = "model:${vendor.lowercase()}:$normModel",
        kind = GoldClueKind.MODEL,
        title = model,
        subtitle = customSubtitle ?: "$vendor • $categoryCode • Gold Catalog",
        categoryCode = categoryCode,
        typeId = typeId.ifBlank { null },
        vendor = vendor,
        model = model,
        upc = upc.ifBlank { null },
        fccId = fccId.ifBlank { null }
    )

    companion object {
        fun normalizeAlnum(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9]"), "")

        fun parseJsonString(jsonStr: String): GoldClueIndexData {
            val root = JSONObject(jsonStr)
            val etag = root.optString("etag", "bundled")
            val ver = root.optString("datalakeVersion", "2.1.0")

            val typesList = mutableListOf<TypeEntry>()
            val typesArr = root.optJSONArray("types")
            if (typesArr != null) {
                for (i in 0 until typesArr.length()) {
                    val obj = typesArr.getJSONObject(i)
                    val aliases = mutableListOf<String>()
                    val alArr = obj.optJSONArray("aliases")
                    if (alArr != null) {
                        for (j in 0 until alArr.length()) aliases.add(alArr.getString(j).lowercase())
                    }
                    val label = obj.getString("label")
                    val tokens = (aliases + label.lowercase().split(Regex("[\\s/]+")))
                        .map { normalizeAlnum(it) }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    typesList.add(
                        TypeEntry(
                            id = obj.getString("id"),
                            label = label,
                            categoryCode = obj.optString("cat", "C8"),
                            aliases = aliases,
                            normTokens = tokens
                        )
                    )
                }
            }

            val brandsList = mutableListOf<BrandEntry>()
            val brandRankMap = mutableMapOf<String, Int>()
            val brandAliasesMap = mutableMapOf<String, List<String>>()
            val brandsArr = root.optJSONArray("brands")
            if (brandsArr != null) {
                for (i in 0 until brandsArr.length()) {
                    val obj = brandsArr.getJSONObject(i)
                    val name = obj.getString("name")
                    val rank = obj.optInt("rank", 999)
                    val count = obj.optInt("count", 0)
                    val cats = mutableListOf<String>()
                    obj.optJSONArray("cats")?.let { ca ->
                        for (j in 0 until ca.length()) cats.add(ca.getString(j))
                    }
                    val aliases = mutableListOf<String>()
                    obj.optJSONArray("aliases")?.let { aa ->
                        for (j in 0 until aa.length()) aliases.add(aa.getString(j).lowercase())
                    }
                    brandRankMap[name] = rank
                    brandAliasesMap[name] = aliases
                    brandsList.add(
                        BrandEntry(
                            name = name,
                            rank = rank,
                            skuCount = count,
                            categories = cats,
                            aliases = aliases,
                            normAliases = (aliases + name.lowercase()).map { normalizeAlnum(it) }.distinct()
                        )
                    )
                }
            }

            val modelsList = mutableListOf<ModelEntry>()
            val upcLookup = mutableMapOf<String, ModelEntry>()
            val hwObj = root.optJSONObject("hw")
            if (hwObj != null) {
                val vendorKeys = hwObj.keys()
                while (vendorKeys.hasNext()) {
                    val vendor = vendorKeys.next()
                    val bRank = brandRankMap[vendor] ?: 999
                    val bAliases = brandAliasesMap[vendor]?.joinToString(" ") ?: ""
                    val skuArr = hwObj.optJSONArray(vendor) ?: continue
                    for (i in 0 until skuArr.length()) {
                        val tuple = skuArr.getJSONArray(i)
                        val model = tuple.getString(0)
                        val catInt = tuple.optInt(1, 8)
                        val catCode = "C${catInt.coerceIn(1, 8)}"
                        val upc = tuple.optString(2, "")
                        val fcc = tuple.optString(3, "")
                        val typeId = tuple.optString(4, "")
                        val entry = ModelEntry(
                            vendor = vendor,
                            brandRank = bRank,
                            model = model,
                            normModel = normalizeAlnum(model),
                            searchBlob = "${vendor.lowercase()} $bAliases ${model.lowercase()} $typeId ${catCode.lowercase()}",
                            categoryCode = catCode,
                            upc = upc,
                            fccId = fcc,
                            normFcc = normalizeAlnum(fcc).uppercase(),
                            typeId = typeId
                        )
                        modelsList.add(entry)
                        if (upc.isNotBlank()) {
                            upcLookup[upc] = entry
                        }
                    }
                }
            }

            val fccMap = mutableMapOf<String, FccGranteeEntry>()
            val fccObj = root.optJSONObject("fcc")
            if (fccObj != null) {
                val fKeys = fccObj.keys()
                while (fKeys.hasNext()) {
                    val prefix = fKeys.next()
                    val info = fccObj.getJSONObject(prefix)
                    fccMap[prefix.uppercase()] = FccGranteeEntry(
                        prefix = prefix.uppercase(),
                        vendor = info.optString("v", ""),
                        categoryCode = "C${info.optInt("c", 8).coerceIn(1, 8)}",
                        isRadioModule = info.optString("t", "H") == "R",
                        notes = info.optString("n", "")
                    )
                }
            }

            val ouiLookup = mutableMapOf<String, String>()
            val ouiObj = root.optJSONObject("oui")
            if (ouiObj != null) {
                val oKeys = ouiObj.keys()
                while (oKeys.hasNext()) {
                    val k = oKeys.next()
                    ouiLookup[k.uppercase()] = ouiObj.getString(k)
                }
            }

            return GoldClueIndexData(
                etag = etag,
                datalakeVersion = ver,
                types = typesList,
                brands = brandsList,
                models = modelsList,
                upcMap = upcLookup,
                fccGrantees = fccMap,
                ouiMap = ouiLookup
            )
        }

        fun parseGzipStream(inputStream: InputStream): GoldClueIndexData {
            val jsonText = GZIPInputStream(inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseJsonString(jsonText)
        }
    }
}

object GoldClueSuggestionRepository {
    private const val ASSET_FILE_NAME = "gold_clue_index.gci"
    private const val CACHED_FILE_NAME = "gold_clue_index_latest.gci"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _indexFlow = MutableStateFlow<GoldClueIndexData?>(null)
    val indexFlow: StateFlow<GoldClueIndexData?> = _indexFlow.asStateFlow()

    @Volatile
    private var lastEdgeSyncEpochMs: Long = 0L

    fun ensureLocalLoaded(context: Context): GoldClueIndexData? {
        var current = _indexFlow.value
        if (current == null) {
            current = loadBestLocalSnapshot(context)
            _indexFlow.value = current
        }
        return current
    }

    suspend fun ensureLoaded(context: Context, serverBaseUrl: String? = null): GoldClueIndexData? =
        withContext(Dispatchers.IO) {
            val current = ensureLocalLoaded(context)
            if (!serverBaseUrl.isNullOrBlank()) {
                val now = System.currentTimeMillis()
                if (now - lastEdgeSyncEpochMs > 15 * 60 * 1000L) {
                    lastEdgeSyncEpochMs = now
                    syncFromEdgeIfNewer(context, serverBaseUrl, current?.etag)
                }
            }
            _indexFlow.value
        }

    private fun loadBestLocalSnapshot(context: Context): GoldClueIndexData? {
        val bundled = try {
            context.assets.open(ASSET_FILE_NAME).use { GoldClueIndexData.parseGzipStream(it) }
        } catch (e: Exception) {
            Log.w("GoldClueIndex", "Failed loading bundled Gold clue index: ${e.message}")
            null
        }

        val cachedFile = File(context.filesDir, CACHED_FILE_NAME)
        if (cachedFile.exists() && cachedFile.length() > 1024) {
            try {
                val cached = cachedFile.inputStream().use { GoldClueIndexData.parseGzipStream(it) }
                if (bundled == null || cached.etag != bundled.etag) {
                    return cached
                }
            } catch (e: Exception) {
                Log.w("GoldClueIndex", "Corrupt cached Gold clue index, falling back to bundled: ${e.message}")
                cachedFile.delete()
            }
        }
        return bundled
    }

    private fun syncFromEdgeIfNewer(context: Context, serverBaseUrl: String, currentEtag: String?) {
        val cleanBase = serverBaseUrl.trim().trimEnd('/')
        if (cleanBase.isEmpty()) return
        try {
            val reqBuilder = Request.Builder()
                .url("$cleanBase/api/v1/catalog/clue-index")
                .header("User-Agent", "TuesdayAndroid/1.0")
                .get()
            if (!currentEtag.isNullOrBlank()) {
                reqBuilder.header("If-None-Match", "\"$currentEtag\"")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code == 304) {
                    return
                }
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes() ?: return
                    if (bytes.size < 1024) return
                    val parsed = bytes.inputStream().use { GoldClueIndexData.parseGzipStream(it) }
                    val tmpFile = File(context.filesDir, "$CACHED_FILE_NAME.tmp")
                    val targetFile = File(context.filesDir, CACHED_FILE_NAME)
                    tmpFile.writeBytes(bytes)
                    tmpFile.renameTo(targetFile)
                    _indexFlow.value = parsed
                }
            }
        } catch (_: Exception) {
            // Offline wiring-closet safe: keep pinned baseline asset silently
        }
    }
}
