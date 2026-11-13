package sh.tuesday.app.ui.wizard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import sh.tuesday.app.domain.BarcodeIdentifierParser
import sh.tuesday.app.domain.ManualEnrollmentResolver
import sh.tuesday.app.models.*
import sh.tuesday.app.services.GeminiDiscoveryService
import sh.tuesday.app.services.GoldClueItem
import sh.tuesday.app.services.GoldClueKind
import sh.tuesday.app.services.GoldClueSuggestionRepository
import sh.tuesday.app.services.GoldenCorpusRecorder
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.components.SegmentedTabRow
import sh.tuesday.app.ui.components.SurfaceListContainer
import sh.tuesday.app.ui.components.SurfaceListDivider
import sh.tuesday.app.ui.components.TuesdayBottomSheet
import sh.tuesday.app.ui.components.TuesdayListRow
import sh.tuesday.app.ui.components.TuesdayTextField
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

enum class EnrollmentMode(val title: String) {
    PHOTO_WIZARD("Photo"),
    BARCODE_SCAN("Scan"),
    MANUAL_AGENT("Manual")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddDeviceScreen(
    repository: TuesdayRepository,
    discoveryService: GeminiDiscoveryService,
    initialEnrollmentMode: EnrollmentMode = EnrollmentMode.PHOTO_WIZARD,
    initialSynthPreset: String? = null,
    initialClueQuery: String? = null,
    initialCluesCsv: String? = null,
    initialAutoSearch: Boolean = false,
    onNavigateBack: () -> Unit
) {
    var mode by remember(initialEnrollmentMode) { mutableStateOf(initialEnrollmentMode) }
    val hacsConfig by repository.hacsConfig.collectAsState()
    val brandFlavor by repository.brandFlavor.collectAsState()
    val isDeveloperMode by repository.isDeveloperMode.collectAsState()
    val isVisionDebugEnabled by repository.isVisionDebugEnabled.collectAsState()
    val isDiagnosticsEnabled by repository.isDiagnosticsEnabled.collectAsState()
    val showVisionDebug = isDeveloperMode && isVisionDebugEnabled
    val palette = TuesdayTheme.paletteFor(brandFlavor)
    val isStn = brandFlavor == AppBrandFlavor.SURETHING_HOME
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val clueIndex by GoldClueSuggestionRepository.indexFlow.collectAsState()
    LaunchedEffect(hacsConfig.tuesdayApiBaseUrl) {
        GoldClueSuggestionRepository.ensureLocalLoaded(context)
        GoldClueSuggestionRepository.ensureLoaded(context, hacsConfig.tuesdayApiBaseUrl)
    }

    // Wizard State
    var currentSessionId by remember { mutableStateOf(discoveryService.startNewSession()) }
    var capturedBitmaps by remember { mutableStateOf(listOf<Bitmap>()) }
    var scannedBarcodesList by remember { mutableStateOf(listOf<String>()) }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Keep the screen awake while vision analysis is in flight so Android does not freeze the process and abort the socket
    DisposableEffect(isAnalyzing) {
        view.keepScreenOn = isAnalyzing
        onDispose {
            view.keepScreenOn = false
        }
    }
    var discoveryResult by remember { mutableStateOf<GeminiDiscoveryTurnResult?>(null) }
    var accumulatedIdentifiers by remember { mutableStateOf(ExtractedDeviceIdentifiers()) }
    var selectedClueItems by remember(initialCluesCsv) { mutableStateOf(listOf<GoldClueItem>()) }
    val userClueNotes = remember(selectedClueItems) { selectedClueItems.map { it.toServerClueString() } }
    var clueInputText by remember(initialClueQuery) { mutableStateOf(initialClueQuery ?: "") }

    fun addClueItem(item: GoldClueItem) {
        if (selectedClueItems.none { it.id == item.id }) {
            selectedClueItems = selectedClueItems + item
        }
        item.model?.takeIf { it.isNotBlank() }?.let { accumulatedIdentifiers.modelNumber = it }
        item.macAddress?.takeIf { it.isNotBlank() }?.let { accumulatedIdentifiers.macAddress = it }
        item.upc?.takeIf { it.isNotBlank() }?.let { accumulatedIdentifiers.barcode = it }
    }

    fun removeClueItem(item: GoldClueItem) {
        selectedClueItems = selectedClueItems.filterNot { it.id == item.id }
    }
    var isSaving by remember { mutableStateOf(false) }
    var currentCaptureSource by remember { mutableStateOf("camera") }
    var savedCorpusCount by remember { mutableStateOf(GoldenCorpusRecorder.getSavedSessionCount(context)) }
    var savedManifests by remember { mutableStateOf(GoldenCorpusRecorder.listSavedManifests(context)) }
    var lastSavedCorpusFolder by remember { mutableStateOf<String?>(null) }
    var debugLogHistory by remember { mutableStateOf(listOf<String>()) }
    var copyLogFeedback by remember { mutableStateOf<String?>(null) }

    // Manual State
    var manualHostname by remember { mutableStateOf("") }
    var manualOsDetails by remember { mutableStateOf("") }
    var manualPkgManager by remember { mutableStateOf(PackageManagerType.APT) }
    var manualToken by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun decodeUriToSoftwareBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun triggerProgressiveAnalysis(newBmp: Bitmap, captureSource: String = "camera") {
        currentCaptureSource = captureSource
        val updatedBitmaps = if (capturedBitmaps.contains(newBmp)) capturedBitmaps else capturedBitmaps + newBmp
        capturedBitmaps = updatedBitmaps
        scope.launch {
            isAnalyzing = true
            val res = discoveryService.analyzeDeviceProgressive(
                bitmaps = updatedBitmaps,
                accumulatedIdentifiers = accumulatedIdentifiers,
                userClueNotes = userClueNotes,
                aiProvider = hacsConfig.aiProvider,
                elastixApiKeyOverride = hacsConfig.elastixApiKey,
                elastixModelName = hacsConfig.elastixModel,
                apiKeyOverride = hacsConfig.geminiApiKey,
                modelName = hacsConfig.geminiModel,
                serverBaseUrl = hacsConfig.tuesdayApiBaseUrl,
                debugEnabled = showVisionDebug,
                initialMode = if (currentCaptureSource == "synthetic_sample") "synthetic_sample" else "photo_wizard"
            )
            accumulatedIdentifiers.merge(res.extractedIdentifiers)
            discoveryResult = res
            res.debugLogText?.takeIf { it.isNotBlank() }?.let { logEntry ->
                debugLogHistory = debugLogHistory + logEntry
            }
            if (isDeveloperMode && isDiagnosticsEnabled) {
                val savedDir = GoldenCorpusRecorder.saveCaptureSession(
                    context = context,
                    sessionId = currentSessionId,
                    bitmaps = updatedBitmaps,
                    result = res,
                    accumulatedIdentifiers = accumulatedIdentifiers,
                    userClueNotes = userClueNotes,
                    status = "auto_tagged",
                    captureSource = currentCaptureSource
                )
                lastSavedCorpusFolder = savedDir?.name
                savedCorpusCount = GoldenCorpusRecorder.getSavedSessionCount(context)
                savedManifests = GoldenCorpusRecorder.listSavedManifests(context)
            }
            isAnalyzing = false
        }
    }

    var scanStatusMessage by remember { mutableStateOf<String?>(null) }

    fun triggerIdentifierOnlyAnalysis(cluesOverride: List<GoldClueItem> = selectedClueItems) {
        scope.launch {
            // 0-Turn Local Pinned Gold Cache Short-Circuit when no camera frames require vision inspection
            if (capturedBitmaps.isEmpty()) {
                val idx = clueIndex ?: GoldClueSuggestionRepository.ensureLoaded(context, hacsConfig.tuesdayApiBaseUrl)
                val localHit = idx?.resolveLocalDiscoveryResult(
                    selected = cluesOverride,
                    accumulatedIdentifiers = accumulatedIdentifiers,
                    scannedBarcodes = scannedBarcodesList,
                    manualDeviceName = if (mode == EnrollmentMode.MANUAL_AGENT) manualHostname else "",
                    debugEnabled = showVisionDebug
                )
                if (localHit != null) {
                    accumulatedIdentifiers.merge(localHit.extractedIdentifiers)
                    discoveryResult = localHit
                    localHit.debugLogText?.takeIf { it.isNotBlank() }?.let { logEntry ->
                        debugLogHistory = debugLogHistory + logEntry
                    }
                    return@launch
                }
            }

            // Escalation to Server / AI only when photos are attached or local Gold cache returned 0 matches
            isAnalyzing = true
            val effectiveNotes = cluesOverride.map { it.toServerClueString() }
            val res = discoveryService.analyzeDeviceProgressive(
                bitmaps = capturedBitmaps,
                accumulatedIdentifiers = accumulatedIdentifiers,
                userClueNotes = effectiveNotes,
                aiProvider = hacsConfig.aiProvider,
                elastixApiKeyOverride = hacsConfig.elastixApiKey,
                elastixModelName = hacsConfig.elastixModel,
                apiKeyOverride = hacsConfig.geminiApiKey,
                modelName = hacsConfig.geminiModel,
                serverBaseUrl = hacsConfig.tuesdayApiBaseUrl,
                debugEnabled = showVisionDebug
            )
            accumulatedIdentifiers.merge(res.extractedIdentifiers)
            discoveryResult = res
            res.debugLogText?.takeIf { it.isNotBlank() }?.let { logEntry ->
                debugLogHistory = debugLogHistory + logEntry
            }
            isAnalyzing = false
        }
    }

    LaunchedEffect(initialCluesCsv, initialAutoSearch, clueIndex) {
        val idx = clueIndex ?: GoldClueSuggestionRepository.ensureLocalLoaded(context)
        if (!initialCluesCsv.isNullOrBlank()) {
            val built = mutableListOf<GoldClueItem>()
            initialCluesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { token ->
                val best = idx?.querySuggestions(token, built, limit = 1)?.firstOrNull()
                    ?: idx?.classifyFreeTextClue(token)
                    ?: GoldClueItem(
                        id = "type:${token.lowercase()}",
                        kind = GoldClueKind.DEVICE_TYPE,
                        title = token,
                        subtitle = "Device Type"
                    )
                if (built.none { it.id == best.id }) built.add(best)
                addClueItem(best)
            }
            if (initialAutoSearch) {
                triggerIdentifierOnlyAnalysis(built)
            }
        }
    }

    fun applyDecodedBarcode(rawValue: String, format: Int, sourceBitmap: Bitmap? = null) {
        val parsed = BarcodeIdentifierParser.parse(rawValue, format) ?: return
        if (parsed.is2DMatrix) {
            accumulatedIdentifiers.qrCode = parsed.normalizedValue
        } else {
            accumulatedIdentifiers.barcode = parsed.normalizedValue
        }
        if (!scannedBarcodesList.contains(parsed.normalizedValue)) {
            scannedBarcodesList = scannedBarcodesList + parsed.normalizedValue
        }
        if (sourceBitmap != null && !capturedBitmaps.contains(sourceBitmap)) {
            capturedBitmaps = capturedBitmaps + sourceBitmap
        }
    }

    fun decodeBarcodesFromBitmap(bmp: Bitmap) {
        val inputImage = InputImage.fromBitmap(bmp, 0)
        val scanner = BarcodeScanning.getClient()
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (first != null && !first.rawValue.isNullOrBlank()) {
                    applyDecodedBarcode(first.rawValue!!, first.format, sourceBitmap = bmp)
                } else {
                    scanStatusMessage = "⚠ No 1D/2D barcode matrix detected in file — saved to captures"
                    if (!capturedBitmaps.contains(bmp)) {
                        capturedBitmaps = capturedBitmaps + bmp
                    }
                }
            }
            .addOnFailureListener { e ->
                scanStatusMessage = "⚠ ML Kit decode error (${e.localizedMessage}) — saved to captures"
                if (!capturedBitmaps.contains(bmp)) {
                    capturedBitmaps = capturedBitmaps + bmp
                }
            }
    }

    fun launchLiveBarcodeScanner() {
        val scanner = GmsBarcodeScanning.getClient(context)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (!raw.isNullOrBlank()) {
                    applyDecodedBarcode(raw, barcode.format)
                }
            }
            .addOnFailureListener { e ->
                scanStatusMessage = "Scanner canceled or unavailable: ${e.localizedMessage ?: "try Upload File"}"
            }
    }

    // Auto-execute synthetic pre-seeded artifact intent if specified
    LaunchedEffect(initialSynthPreset) {
        if (!initialSynthPreset.isNullOrBlank()) {
            when (initialSynthPreset) {
                "router" -> {
                    mode = EnrollmentMode.PHOTO_WIZARD
                    currentCaptureSource = "synthetic_sample"
                    val sample = GeminiDiscoveryService.createSampleHardwareBitmap("router")
                    capturedBitmaps = capturedBitmaps + sample
                    triggerIdentifierOnlyAnalysis()
                }
                "iot", "iot-zigbee", "zigbee" -> {
                    mode = EnrollmentMode.PHOTO_WIZARD
                    currentCaptureSource = "synthetic_sample"
                    val sample = GeminiDiscoveryService.createSampleHardwareBitmap("iot-zigbee")
                    capturedBitmaps = capturedBitmaps + sample
                    addClueItem(
                        GoldClueItem(
                            id = "type:hub",
                            kind = GoldClueKind.DEVICE_TYPE,
                            title = "Smart Hub / Bridge",
                            subtitle = "C8 • Device Type",
                            categoryCode = "C8",
                            typeId = "hub"
                        )
                    )
                    triggerIdentifierOnlyAnalysis()
                }
                "barcode", "barcode-udm-pro", "udm-pro" -> {
                    mode = EnrollmentMode.BARCODE_SCAN
                    applyDecodedBarcode("817882027373", Barcode.FORMAT_UPC_A, sourceBitmap = GeminiDiscoveryService.createSampleHardwareBitmap("barcode-udm-pro"))
                }
                "qr", "qr-ucg-ultra", "ucg-ultra" -> {
                    mode = EnrollmentMode.BARCODE_SCAN
                    applyDecodedBarcode("https://setup.ui.com/entry?sku=UCG-Ultra&mac=74:AC:B9:4E:88:12", Barcode.FORMAT_QR_CODE, sourceBitmap = GeminiDiscoveryService.createSampleHardwareBitmap("qr-ucg-ultra"))
                }
            }
        }
    }

    // Upload File Picker (Photo Mode)
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeUriToSoftwareBitmap(uri)
            if (bmp != null) {
                currentCaptureSource = "gallery"
                if (!capturedBitmaps.contains(bmp)) {
                    capturedBitmaps = capturedBitmaps + bmp
                }
            }
        }
    }

    // Upload File Picker (Scan Mode — runs deterministic ML Kit barcode extraction on bitmap)
    val scanFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeUriToSoftwareBitmap(uri)
            if (bmp != null) {
                decodeBarcodesFromBitmap(bmp)
            }
        }
    }

    // Full-Resolution Camera Capture via FileProvider
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val bmp = decodeUriToSoftwareBitmap(uri)
            if (bmp != null) {
                currentCaptureSource = "camera"
                if (!capturedBitmaps.contains(bmp)) {
                    capturedBitmaps = capturedBitmaps + bmp
                }
            }
        }
    }

    fun launchCameraWithUri() {
        try {
            val photoFile = File(context.cacheDir, "tuesday_scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {}
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraWithUri()
        }
    }

    @Composable
    fun DevPresetsAndControlsRow(includePhotoSamples: Boolean) {
        val isElastix = hacsConfig.aiProvider == AiProviderType.ELASTIX
        val activeAiColor = if (isElastix) TuesdayTheme.HealthyGreen else TuesdayTheme.LedUpdateBlue
        val debugActiveColor = if (showVisionDebug) TuesdayTheme.WarningAmber else palette.textMuted
        val highlightBorderColor = TuesdayTheme.WarningAmber

        var showReplayMenu by remember { mutableStateOf(false) }
        var showSynthMenu by remember { mutableStateOf(false) }
        var showResultsMenu by remember { mutableStateOf(false) }

        // Left-side highlight card container for dev controls (2 columns: pickers left, toggles right)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .uiComponent("DevControlsCallout")
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("dev-controls")
                .clip(RoundedCornerShape(8.dp))
                .background(palette.elevated.copy(alpha = 0.6f))
                .drawBehind {
                    // 1px subtle full perimeter border
                    drawRoundRect(
                        color = palette.border.copy(alpha = 0.5f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                    // 3.5px prominent left-side highlight bar
                    drawRoundRect(
                        color = highlightBorderColor,
                        topLeft = androidx.compose.ui.geometry.Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(3.5.dp.toPx(), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {

            // Left Column: Dropdown Pickers (Replay..., Synthesize..., Results...)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (includePhotoSamples) {
                    // Replay... Native Picker Button
                    Box {
                        OutlinedButton(
                            onClick = { showReplayMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = TuesdayTheme.HealthyGreen.copy(alpha = 0.12f),
                                contentColor = TuesdayTheme.HealthyGreen
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TuesdayTheme.HealthyGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Replay...", color = TuesdayTheme.HealthyGreen, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TuesdayTheme.HealthyGreen, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = showReplayMenu,
                            onDismissRequest = { showReplayMenu = false },
                            modifier = Modifier.background(palette.elevated)
                        ) {
                            if (savedManifests.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No saved photo manifests", color = palette.textMuted, fontSize = 12.sp) },
                                    onClick = { showReplayMenu = false }
                                )
                            } else {
                                savedManifests.forEach { manifest ->
                                    val label = manifest.confirmedModelNumber ?: manifest.confirmedManufacturer ?: "Saved Photo"
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = TuesdayTheme.HealthyGreen, modifier = Modifier.size(14.dp))
                                                Column {
                                                    Text(label, color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(manifest.sessionId.take(16), color = palette.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        },
                                        onClick = {
                                            showReplayMenu = false
                                            val loaded = GoldenCorpusRecorder.loadSessionBitmaps(context, manifest.sessionId)
                                            if (loaded.isNotEmpty()) {
                                                currentSessionId = discoveryService.startNewSession()
                                                currentCaptureSource = "corpus_replay"
                                                capturedBitmaps = loaded
                                                accumulatedIdentifiers = ExtractedDeviceIdentifiers()
                                                selectedClueItems = emptyList()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Synthesize... Native Picker Button
                    Box {
                        OutlinedButton(
                            onClick = { showSynthMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TuesdayTheme.HacsCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = TuesdayTheme.HacsCyan.copy(alpha = 0.12f),
                                contentColor = TuesdayTheme.HacsCyan
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Synthesize...", color = TuesdayTheme.HacsCyan, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = showSynthMenu,
                            onDismissRequest = { showSynthMenu = false },
                            modifier = Modifier.background(palette.elevated)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Router, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(14.dp))
                                        Text("Synth Router", color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showSynthMenu = false
                                    currentCaptureSource = "synthetic_sample"
                                    val sample = GeminiDiscoveryService.createSampleHardwareBitmap("router")
                                    capturedBitmaps = capturedBitmaps + sample
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Sensors, contentDescription = null, tint = TuesdayTheme.WarningAmber, modifier = Modifier.size(14.dp))
                                        Text("Synth IoT", color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showSynthMenu = false
                                    currentCaptureSource = "synthetic_sample"
                                    val sample = GeminiDiscoveryService.createSampleHardwareBitmap("iot-zigbee")
                                    capturedBitmaps = capturedBitmaps + sample
                                    addClueItem(
                                        GoldClueItem(
                                            id = "type:hub",
                                            kind = GoldClueKind.DEVICE_TYPE,
                                            title = "Smart Hub / Bridge",
                                            subtitle = "C8 • Device Type",
                                            categoryCode = "C8",
                                            typeId = "hub"
                                        )
                                    )
                                }
                            )
                        }
                    }
                } else {
                    // Synthesize... Native Picker Button for Barcode / QR Scan Tab
                    Box {
                        OutlinedButton(
                            onClick = { showSynthMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TuesdayTheme.HacsCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = TuesdayTheme.HacsCyan.copy(alpha = 0.12f),
                                contentColor = TuesdayTheme.HacsCyan
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Synthesize...", color = TuesdayTheme.HacsCyan, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = showSynthMenu,
                            onDismissRequest = { showSynthMenu = false },
                            modifier = Modifier.background(palette.elevated)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.QrCode, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(14.dp))
                                        Column {
                                            Text("Sample Barcode (UDM-Pro)", color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                            Text("Code-128 • Gateway/Router", color = palette.textMuted, fontSize = 10.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    showSynthMenu = false
                                    applyDecodedBarcode("817882027373", Barcode.FORMAT_UPC_A, sourceBitmap = GeminiDiscoveryService.createSampleHardwareBitmap("barcode-udm-pro"))
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = TuesdayTheme.WarningAmber, modifier = Modifier.size(14.dp))
                                        Column {
                                            Text("Sample QR (UCG-Ultra)", color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                            Text("QR Code • Gateway/Router", color = palette.textMuted, fontSize = 10.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    showSynthMenu = false
                                    applyDecodedBarcode("https://setup.ui.com/entry?sku=UCG-Ultra&mac=74:AC:B9:4E:88:12", Barcode.FORMAT_QR_CODE, sourceBitmap = GeminiDiscoveryService.createSampleHardwareBitmap("qr-ucg-ultra"))
                                }
                            )
                        }
                    }
                }

                // Results... Dropdown Picker
                Box {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TuesdayTheme.HacsCyan.copy(alpha = 0.12f))
                            .border(1.dp, TuesdayTheme.HacsCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable { showResultsMenu = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .uiAction("openResultsPicker"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = null,
                            tint = TuesdayTheme.HacsCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Results...",
                            color = TuesdayTheme.HacsCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = TuesdayTheme.HacsCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showResultsMenu,
                        onDismissRequest = { showResultsMenu = false },
                        containerColor = palette.elevated
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Seed Snapshot (All Components)", color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                    Text("U6-Pro + U6-LR • Summary • IDs • Multi-device", color = palette.textMuted, fontSize = 10.sp)
                                }
                            },
                            onClick = {
                                showResultsMenu = false
                                discoveryResult = GeminiDiscoveryTurnResult(
                                    visualSummary = "Identified a circular white ceiling-mounted Ubiquiti UniFi U6-Pro access point with centered status LED ring; a second switch chassis is partially visible on the left edge.",
                                    extractedIdentifiers = ExtractedDeviceIdentifiers(
                                        modelNumber = "U6-Pro",
                                        macAddress = "74:AC:B9:12:34:56",
                                        serialNumber = "2245K-U6P-00192",
                                        barcode = "810010074512"
                                    ),
                                    candidates = listOf(
                                        DeviceCandidateMatch(
                                            manufacturer = "Ubiquiti",
                                            modelNumber = "U6-Pro",
                                            displayName = "Ubiquiti UniFi U6-Pro",
                                            deviceCategory = "Access Point",
                                            confidencePercent = 94,
                                            reasoning = "Dual-band Wi-Fi 6 ceiling AP matching chassis diameter and LED ring.",
                                            suggestedHostname = "unifi-u6-pro.local",
                                            suggestedOsDetails = "UniFi OS 4.0.20",
                                            suggestedPackageManager = "apt"
                                        ),
                                        DeviceCandidateMatch(
                                            manufacturer = "Ubiquiti",
                                            modelNumber = "U6-LR",
                                            displayName = "Ubiquiti UniFi U6-LR",
                                            deviceCategory = "Access Point",
                                            confidencePercent = 68,
                                            reasoning = "Similar circular form factor; slightly larger diameter.",
                                            suggestedHostname = "unifi-u6-lr.local",
                                            suggestedOsDetails = "UniFi OS 4.0.20",
                                            suggestedPackageManager = "apt"
                                        )
                                    ),
                                    multipleDevicesDetected = true,
                                    multiDeviceWarning = "Multiple devices detected in frame. Candidate #1 targets the central access point.",
                                    usedLiveGeminiApi = true,
                                    providerUsed = "Seed Snapshot (Local)",
                                    latencyMs = 12L
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Unsupported Device", color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                    Text("Non-networked / Bluetooth-only callout", color = palette.textMuted, fontSize = 10.sp)
                                }
                            },
                            onClick = {
                                showResultsMenu = false
                                discoveryResult = GeminiDiscoveryTurnResult(
                                    visualSummary = "Identified a portable Bluetooth speaker with no Ethernet port or Wi-Fi radio.",
                                    extractedIdentifiers = ExtractedDeviceIdentifiers(),
                                    candidates = emptyList(),
                                    isNetworkedDevice = false,
                                    outOfScopeReason = "This appears to be a Bluetooth-only speaker with no Wi-Fi or Ethernet radio and is outside SureThingNet Insights' scope.",
                                    usedLiveGeminiApi = true,
                                    providerUsed = "Seed Snapshot (Local)",
                                    latencyMs = 10L
                                )
                            }
                        )
                    }
                }
            }

            // Right Column: Toggle Switches for Debug Mode, Diagnostics, and AI Provider
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Debug Mode Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        if (!isDeveloperMode) {
                            repository.toggleDeveloperMode(true)
                            repository.toggleVisionDebugMode(true)
                        } else {
                            repository.toggleVisionDebugMode(!isVisionDebugEnabled)
                        }
                    }
                ) {
                    Text(
                        text = "Debug Mode",
                        color = if (showVisionDebug) TuesdayTheme.WarningAmber else palette.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = showVisionDebug,
                        onCheckedChange = { checked ->
                            if (!isDeveloperMode) {
                                repository.toggleDeveloperMode(true)
                            }
                            repository.toggleVisionDebugMode(checked)
                        },
                        modifier = Modifier
                            .scale(0.8f)
                            .uiAction("toggleVisionDebugMode"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TuesdayTheme.WarningAmber,
                            checkedTrackColor = TuesdayTheme.WarningAmber.copy(alpha = 0.35f),
                            uncheckedThumbColor = palette.textMuted,
                            uncheckedTrackColor = palette.surface
                        )
                    )
                }

                // Diagnostics Switch (save captures and debug log locally)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        if (!isDeveloperMode) {
                            repository.toggleDeveloperMode(true)
                            repository.toggleDiagnosticsMode(true)
                        } else {
                            repository.toggleDiagnosticsMode(!isDiagnosticsEnabled)
                        }
                    }
                ) {
                    val showDiagnostics = isDeveloperMode && isDiagnosticsEnabled
                    Text(
                        text = "Diagnostics",
                        color = if (showDiagnostics) TuesdayTheme.HacsCyan else palette.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = showDiagnostics,
                        onCheckedChange = { checked ->
                            if (!isDeveloperMode) {
                                repository.toggleDeveloperMode(true)
                            }
                            repository.toggleDiagnosticsMode(checked)
                        },
                        modifier = Modifier
                            .scale(0.8f)
                            .uiAction("toggleDiagnosticsMode"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TuesdayTheme.HacsCyan,
                            checkedTrackColor = TuesdayTheme.HacsCyan.copy(alpha = 0.35f),
                            uncheckedThumbColor = palette.textMuted,
                            uncheckedTrackColor = palette.surface
                        )
                    )
                }

                // AI Provider Switch (Gemini vs Elastix)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        val nextProvider = if (isElastix) AiProviderType.GEMINI else AiProviderType.ELASTIX
                        repository.updateHacsConfig(hacsConfig.copy(aiProvider = nextProvider))
                    }
                ) {
                    Text(
                        text = if (isElastix) "AI: Elastix" else "AI: Gemini",
                        color = activeAiColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isElastix,
                        onCheckedChange = { checked ->
                            val nextProvider = if (checked) AiProviderType.ELASTIX else AiProviderType.GEMINI
                            repository.updateHacsConfig(hacsConfig.copy(aiProvider = nextProvider))
                        },
                        modifier = Modifier
                            .scale(0.8f)
                            .uiAction("switchAiProvider"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TuesdayTheme.HealthyGreen,
                            checkedTrackColor = TuesdayTheme.HealthyGreen.copy(alpha = 0.35f),
                            uncheckedThumbColor = TuesdayTheme.LedUpdateBlue,
                            uncheckedTrackColor = TuesdayTheme.LedUpdateBlue.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isStn) "Add Home Device" else "Add Device", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        },
        containerColor = palette.bg
    ) { paddingValues ->
        val wizardScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .uiComponent("AddDeviceWizard")
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(wizardScrollState),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Mode Switcher Tabs: Photo | Scan | Manual
            SegmentedTabRow(
                options = EnrollmentMode.entries.map { it.title },
                selectedIndex = EnrollmentMode.entries.indexOf(mode),
                onSelect = { index ->
                    val item = EnrollmentMode.entries[index]
                    val prevMode = mode
                    mode = item
                    if (prevMode != item && discoveryService.currentTurnCount() > 0) {
                        scope.launch {
                            discoveryService.emitProductTelemetryEvent(
                                eventName = "discovery.mode_pivoted",
                                targetEntityType = "DiscoverySession",
                                targetEntityId = currentSessionId,
                                properties = mapOf(
                                    "fromMode" to prevMode.name.lowercase(),
                                    "toMode" to item.name.lowercase(),
                                    "turnsSoFar" to discoveryService.currentTurnCount()
                                ),
                                serverBaseUrl = hacsConfig.tuesdayApiBaseUrl
                            )
                        }
                    }
                },
                palette = palette,
                modifier = Modifier.uiSlot("enrollment-mode-tabs"),
                itemModifier = { Modifier.uiAction("switchEnrollmentMode") }
            )

            // Clue Collection Section (Unified 3-Mode Architecture)
            Text(
                text = when (mode) {
                    EnrollmentMode.PHOTO_WIZARD -> if (isStn) "Identify Device by Photo" else "Inventory Device by Photo"
                    EnrollmentMode.BARCODE_SCAN -> "Scan Barcode or QR Code"
                    EnrollmentMode.MANUAL_AGENT -> "Add Inventory Device Manually"
                },
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = when (mode) {
                    EnrollmentMode.PHOTO_WIZARD -> "Snap or upload hardware photos, or pick device type, brand, and model clues below before searching."
                    EnrollmentMode.BARCODE_SCAN -> "Scan 1D/2D barcodes for SureThingNet Insights lookup, or pick device clues below before searching."
                    EnrollmentMode.MANUAL_AGENT -> "Enter a device name and pick clues (e.g. Router, Doorbell, Google, Linksys) to search or add directly."
                },
                color = palette.textMuted,
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            )

            manualError?.let {
                Text(it, color = TuesdayTheme.CriticalRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Tab-Specific Input Mechanism (Photo Carousel / Buttons, Scan Scanner / Upload, or Manual Device Name)
            when (mode) {
                EnrollmentMode.PHOTO_WIZARD -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    launchCameraWithUri()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TuesdayTheme.HealthyGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .uiAction("captureHardwarePhoto")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Capture Photo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            border = BorderStroke(1.dp, palette.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.elevated,
                                contentColor = palette.textPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.uiAction("uploadHardwarePhoto")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                EnrollmentMode.BARCODE_SCAN -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { launchLiveBarcodeScanner() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TuesdayTheme.HealthyGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .uiAction("scanBarcodeOrQr")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scan Barcode / QR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { scanFileLauncher.launch("image/*") },
                            border = BorderStroke(1.dp, palette.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.elevated,
                                contentColor = palette.textPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.uiAction("uploadBarcodeImage")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Thumbnail Gallery View of Captures and Decoded Barcodes Under Capture Buttons
                    if (capturedBitmaps.isNotEmpty() || scannedBarcodesList.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "CAPTURED SCANS & BARCODES",
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 28.dp)
                                        .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                        .clickable {
                                            capturedBitmaps = emptyList()
                                            scannedBarcodesList = emptyList()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Clear All",
                                        color = TuesdayTheme.CriticalRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(capturedBitmaps) { bmp ->
                                    Box {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Scanned Label",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 75.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, TuesdayTheme.HacsCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.78f))
                                                .clickable {
                                                    capturedBitmaps = capturedBitmaps - bmp
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove Scan",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                items(scannedBarcodesList) { code ->
                                    val displayCode = if (code.length > 28) code.take(25) + "…" else code
                                    Box(
                                        modifier = Modifier
                                            .size(width = 140.dp, height = 75.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(palette.accent.copy(alpha = 0.12f))
                                            .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.QrCodeScanner,
                                                    contentDescription = null,
                                                    tint = palette.accentText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            scannedBarcodesList = scannedBarcodesList - code
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove Barcode",
                                                        tint = palette.accentText,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = displayCode,
                                                color = palette.textPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                EnrollmentMode.MANUAL_AGENT -> {
                    val pickedGoldModel = selectedClueItems.lastOrNull { it.kind == GoldClueKind.MODEL }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TuesdayTextField(
                            value = manualHostname,
                            onValueChange = {
                                manualHostname = it
                                if (manualError != null) manualError = null
                            },
                            label = "Device Name",
                            placeholder = pickedGoldModel?.chipLabel ?: "e.g. Living Room Router, Front Doorbell, Synology NAS",
                            palette = palette
                        )

                        if (manualHostname.isBlank() && pickedGoldModel != null) {
                            SuggestionChip(
                                onClick = { manualHostname = pickedGoldModel.chipLabel },
                                label = {
                                    Text(
                                        text = "Use \"${pickedGoldModel.chipLabel}\"",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = TuesdayTheme.HealthyGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = palette.surface,
                                    labelColor = palette.textPrimary
                                ),
                                border = BorderStroke(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            }

            // Shared Material 3 Multi-Item Hardware Clues Picker (Common Across Photo, Scan, Manual)
            val inferredNameClue = remember(mode, manualHostname, clueIndex, selectedClueItems) {
                if (mode == EnrollmentMode.MANUAL_AGENT) {
                    clueIndex?.inferFromDeviceName(manualHostname, selectedClueItems)
                } else null
            }

            GoldHardwareCluesPicker(
                clueIndex = clueIndex,
                queryText = clueInputText,
                onQueryTextChange = { clueInputText = it },
                selectedClues = selectedClueItems,
                onAddClue = { addClueItem(it) },
                onRemoveClue = { removeClueItem(it) },
                palette = palette,
                inferredFromDeviceName = inferredNameClue
            )

            // Unified Search Context Strip: Captured Photos + Selected Clues side-by-side
            if (capturedBitmaps.isNotEmpty() || selectedClueItems.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("selected-clue-chips")
                ) {
                    items(capturedBitmaps) { bmp ->
                        Box {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.80f))
                                    .clickable {
                                        capturedBitmaps = capturedBitmaps - bmp
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    items(selectedClueItems, key = { it.id }) { clue ->
                        InputChip(
                            selected = true,
                            onClick = { removeClueItem(clue) },
                            label = {
                                Text(
                                    text = clue.chipLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove ${clue.chipLabel}",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = palette.accent.copy(alpha = 0.14f),
                                selectedLabelColor = palette.textPrimary,
                                selectedTrailingIconColor = palette.textMuted
                            ),
                            border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.45f))
                        )
                    }
                }
            }

            // Shared Action Row: Search Button (+ Add Directly for Manual)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Shared Search Button
                Button(
                    onClick = {
                        // If user typed into textfield without hitting Add/Done first, commit it
                        val trimmed = clueInputText.trim()
                        var effectiveClues = selectedClueItems
                        if (trimmed.isNotBlank()) {
                            val resolved = clueIndex?.classifyFreeTextClue(trimmed) ?: GoldClueItem(
                                id = "custom:${trimmed.lowercase()}",
                                kind = GoldClueKind.CUSTOM,
                                title = trimmed,
                                subtitle = "Custom clue"
                            )
                            addClueItem(resolved)
                            if (effectiveClues.none { it.id == resolved.id }) {
                                effectiveClues = effectiveClues + resolved
                            }
                            clueInputText = ""
                        }

                        if (capturedBitmaps.isNotEmpty()) {
                            triggerProgressiveAnalysis(capturedBitmaps.last(), captureSource = currentCaptureSource)
                        } else {
                            triggerIdentifierOnlyAnalysis(effectiveClues)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TuesdayTheme.HacsCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .uiAction("searchDeviceCandidates")
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    val totalSignals = capturedBitmaps.size + scannedBarcodesList.size + selectedClueItems.size
                    Text(
                        text = if (totalSignals > 0) "Search ($totalSignals)" else "Search",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textPrimary),
                    modifier = Modifier.uiAction("cancelAddDevice")
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                // If on Manual tab, also provide direct "Add Device" button with Gold Catalog metadata binding
                if (mode == EnrollmentMode.MANUAL_AGENT) {
                    Button(
                        onClick = {
                            val trimmedClue = clueInputText.trim()
                            var effectiveClues = selectedClueItems
                            if (trimmedClue.isNotBlank()) {
                                val resolvedClue = clueIndex?.classifyFreeTextClue(trimmedClue) ?: GoldClueItem(
                                    id = "custom:${trimmedClue.lowercase()}",
                                    kind = GoldClueKind.CUSTOM,
                                    title = trimmedClue,
                                    subtitle = "Custom clue"
                                )
                                addClueItem(resolvedClue)
                                if (effectiveClues.none { it.id == resolvedClue.id }) {
                                    effectiveClues = effectiveClues + resolvedClue
                                }
                                clueInputText = ""
                            }

                            val resolvedManual = ManualEnrollmentResolver.resolve(
                                manualHostname = manualHostname,
                                selectedClues = effectiveClues,
                                accumulatedIdentifiers = accumulatedIdentifiers
                            )
                            if (resolvedManual == null) {
                                manualError = "Enter a Device Name or pick a SureThingNet Insights Model clue."
                                return@Button
                            }

                            scope.launch {
                                isSaving = true
                                repository.enrollDevice(
                                    hostname = resolvedManual.hostname,
                                    osDetails = resolvedManual.osSummary,
                                    packageManager = PackageManagerType.FIRMWARE,
                                    agentToken = "manual_entry_${System.currentTimeMillis()}",
                                    inventoryState = resolvedManual.inventoryState,
                                    discoveredMetadata = resolvedManual.metadata
                                )
                                isSaving = false
                                onNavigateBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TuesdayTheme.HealthyGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .uiAction("enrollManualDevice")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Device", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            if (isDeveloperMode) {
                DevPresetsAndControlsRow(includePhotoSamples = mode == EnrollmentMode.PHOTO_WIZARD)
            }

                if (isAnalyzing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = palette.accent,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Column {
                            Text(
                                "Analyzing device with ${hacsConfig.aiProvider.displayName}...",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Inspecting chassis profile, ports, OUI prefix, and printed labels", color = palette.textMuted, fontSize = 11.sp)
                        }
                    }
                }

                // Progressive Clues & Candidates Loop
                discoveryResult?.let { result ->
                    var inspectedCandidate by remember(result) { mutableStateOf<DeviceCandidateMatch?>(null) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RESULTS",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { discoveryResult = null }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .uiAction("clearDiscoveryResults"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Clear All",
                                color = palette.accentText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiComponent("AiDiscoveryPanel")
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("ai-discovery-panel"),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!result.isNetworkedDevice || !result.outOfScopeReason.isNullOrBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TuesdayTheme.WarningAmber.copy(alpha = 0.12f))
                                    .border(1.dp, TuesdayTheme.WarningAmber.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "UNSUPPORTED DEVICE",
                                    color = TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = result.outOfScopeReason
                                        ?: "This item does not appear to have Wi-Fi or Ethernet networking and isn't tracked in SureThingNet Insights.",
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Button(
                                    onClick = {
                                        discoveryResult = null
                                        capturedBitmaps = emptyList()
                                        scannedBarcodesList = emptyList()
                                        selectedClueItems = emptyList()
                                        launchCameraWithUri()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TuesdayTheme.WarningAmber,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .uiAction("startNewCapture")
                                ) {
                                    Text(
                                        text = "Start New Capture",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            // Discovered identifiers chips
                            if (accumulatedIdentifiers.nonEmptyPairs().isNotEmpty()) {
                                Text("DISCOVERED IDENTIFIERS", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(accumulatedIdentifiers.nonEmptyPairs()) { pair ->
                                        MonospaceBadge(
                                            text = "${pair.first.rawValue}: ${pair.second}",
                                            color = TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn),
                                            borderAlpha = 0f
                                        )
                                    }
                                }
                            }
                        }

                    // Multi-Device Confidence Warning Banner
                    if (result.multipleDevicesDetected || !result.multiDeviceWarning.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiSlot("multi-device-warning-banner")
                                .clip(RoundedCornerShape(10.dp))
                                .background(TuesdayTheme.WarningAmber.copy(alpha = 0.12f))
                                .border(
                                    1.dp,
                                    TuesdayTheme.WarningAmber.copy(alpha = 0.45f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "⚠ MULTIPLE DEVICES IN PHOTO — LOWER CONFIDENCE",
                                color = TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = result.multiDeviceWarning
                                    ?: "Multiple devices detected in this photo. Confidence can be lower when neighboring chassis appear in frame — for best results, snap a new photo framed around a single device.",
                                color = palette.textPrimary,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    // Candidate Cards
                    Text(
                        text = "CANDIDATE DEVICE MATCHES (${result.candidates.size})",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiComponent("CandidateResultsList")
                    ) {
                        SurfaceListContainer(
                            palette = palette,
                            modifier = Modifier.uiSlot("candidate-results-list")
                        ) {
                            result.candidates.forEachIndexed { index, candidate ->
                                val badgeColor = when {
                                    candidate.confidencePercent >= 80 -> TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn)
                                    candidate.confidencePercent >= 50 -> TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)
                                    else -> palette.accentText
                                }
                                TuesdayListRow(
                                    palette = palette,
                                    title = candidate.displayName,
                                    titleMaxLines = 2,
                                    onClick = { inspectedCandidate = candidate },
                                    modifier = Modifier.uiComponent("CandidateResultItem"),
                                    trailingContent = {
                                        MonospaceBadge(
                                            text = "${candidate.confidencePercent}% match",
                                            color = badgeColor
                                        )
                                    }
                                )
                                if (index < result.candidates.lastIndex) {
                                    SurfaceListDivider(palette)
                                }
                            }
                        }
                    }

                    inspectedCandidate?.let { candidate ->
                        val badgeColor = when {
                            candidate.confidencePercent >= 80 -> TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn)
                            candidate.confidencePercent >= 50 -> TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)
                            else -> palette.accentText
                        }
                        TuesdayBottomSheet(
                            palette = palette,
                            onDismissRequest = { inspectedCandidate = null }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = candidate.displayName,
                                        color = palette.textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f).padding(end = 10.dp)
                                    )
                                    MonospaceBadge(
                                        text = "${candidate.confidencePercent}% match",
                                        color = badgeColor
                                    )
                                }

                                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))

                                @Composable
                                fun DetailRow(label: String, value: String?) {
                                    if (!value.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(label, color = palette.textMuted, fontSize = 12.sp)
                                            Text(value, color = palette.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                DetailRow("Manufacturer", candidate.manufacturer)
                                DetailRow("Model", candidate.modelNumber)
                                candidate.goldAlignment?.let { ga ->
                                    DetailRow("SureThingNet Insights", ga.statusLabel)
                                    DetailRow("Feed", ga.matchedFeedName ?: ga.matchedFeedSlug)
                                    DetailRow("Latest Release", ga.latestGoldRelease)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isSaving = true
                                            val resolvedModel = candidate.modelNumber
                                            if (capturedBitmaps.isNotEmpty()) {
                                                GoldenCorpusRecorder.saveCaptureSession(
                                                    context = context,
                                                    sessionId = currentSessionId,
                                                    bitmaps = capturedBitmaps,
                                                    result = result,
                                                    accumulatedIdentifiers = accumulatedIdentifiers,
                                                    userClueNotes = userClueNotes,
                                                    status = "user_confirmed",
                                                    captureSource = currentCaptureSource,
                                                    confirmedCandidate = candidate,
                                                    confirmedModelOverride = resolvedModel
                                                )
                                                savedCorpusCount = GoldenCorpusRecorder.getSavedSessionCount(context)
                                                savedManifests = GoldenCorpusRecorder.listSavedManifests(context)
                                            }
                                            val meta = DiscoveredDeviceMetadata(
                                                manufacturer = candidate.manufacturer,
                                                modelNumber = resolvedModel,
                                                deviceCategory = candidate.deviceCategory,
                                                serialNumber = accumulatedIdentifiers.serialNumber,
                                                macAddress = accumulatedIdentifiers.macAddress,
                                                barcodeOrQr = accumulatedIdentifiers.barcode ?: accumulatedIdentifiers.qrCode,
                                                confidenceScore = candidate.confidencePercent,
                                                discoveryNotes = "Verified via Tuesday Hosted AI Proxy (${candidate.confidencePercent}% confidence)"
                                            )
                                            val idx = result.candidates.indexOf(candidate).coerceAtLeast(0)
                                            discoveryService.confirmDeviceDiscovery(
                                                inferredVendor = candidate.manufacturer,
                                                inferredModel = candidate.modelNumber,
                                                confirmedVendor = candidate.manufacturer,
                                                confirmedModel = resolvedModel,
                                                wasEdited = idx > 0,
                                                accumulatedIdentifiers = accumulatedIdentifiers,
                                                userClueNotes = userClueNotes,
                                                selectedCandidateRank = idx,
                                                resolutionStatus = if (mode == EnrollmentMode.BARCODE_SCAN && capturedBitmaps.isNotEmpty()) "pivoted_to_barcode" else "confirmed_candidate",
                                                initialMode = if (currentCaptureSource == "synthetic_sample") "synthetic_sample" else "photo_wizard",
                                                finalMode = if (mode == EnrollmentMode.BARCODE_SCAN) "barcode_scan" else if (currentCaptureSource == "synthetic_sample") "synthetic_sample" else "photo_wizard",
                                                candidateModels = result.candidates.map { it.modelNumber },
                                                serverBaseUrl = hacsConfig.tuesdayApiBaseUrl
                                            )
                                            repository.enrollDevice(
                                                hostname = candidate.suggestedHostname,
                                                osDetails = candidate.suggestedOsDetails,
                                                packageManager = candidate.resolvedPackageManager,
                                                agentToken = "",
                                                inventoryState = if (candidate.confidencePercent >= 80) InventoryConfidenceState.VERIFIED_MATCH else InventoryConfidenceState.INCOMPLETE_OR_GUESSED,
                                                discoveredMetadata = meta
                                            )
                                            isSaving = false
                                            inspectedCandidate = null
                                            onNavigateBack()
                                        }
                                    },
                                    enabled = !isSaving,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TuesdayTheme.HealthyGreen,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 16.dp)
                                        .uiAction("addToInventoryFromModal")
                                ) {
                                    Text(
                                        text = if (isSaving) "Adding..." else "Add to Inventory",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    }
                }

                // Developer Mode Vision API Debug Log View (Text Area)
                if (showVisionDebug) {
                    val combinedLogText = if (debugLogHistory.isEmpty()) {
                        buildString {
                            appendLine("=== TUESDAY VISION DEBUG CONSOLE READY ===")
                            appendLine("Server Endpoint: ${hacsConfig.tuesdayApiBaseUrl.trimEnd('/')}/api/v1/discovery/analyze (debug=true)")
                            appendLine("Livetail Stream: ${hacsConfig.tuesdayApiBaseUrl.trimEnd('/')}/api/v1/discovery/livetail?format=text")
                            appendLine("Active AI Provider: ${hacsConfig.aiProvider.displayName}")
                            appendLine()
                            appendLine("Submit a photo (or tap a Quick Pilot Hardware Preset above) to inspect:")
                            appendLine(" • Step-by-step server pipeline trace & latency")
                            appendLine(" • Raw LLM JSON output & extracted identifiers")
                            appendLine(" • Raw candidate matches (including unaligned candidates missing from Gold Datalake)")
                        }.trimEnd()
                    } else {
                        debugLogHistory.asReversed().joinToString("\n\n" + "=".repeat(64) + "\n\n")
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("vision-debug-log-panel")
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0E17))
                            .border(1.dp, TuesdayTheme.WarningAmber.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "🐞 VISION API DEBUG LOG (${debugLogHistory.size} TURN${if (debugLogHistory.size == 1) "" else "S"})",
                                    color = TuesdayTheme.WarningAmber,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Target: ${hacsConfig.tuesdayApiBaseUrl}",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }

                            copyLogFeedback?.let { msg ->
                                Text(
                                    text = msg,
                                    color = TuesdayTheme.HealthyGreen,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentPreset = ApiEndpointPreset.fromUrl(hacsConfig.tuesdayApiBaseUrl)
                                val isProdGcp = currentPreset == ApiEndpointPreset.PROD_GCP
                                val targetColor = if (isProdGcp) TuesdayTheme.LedUpdateBlue else TuesdayTheme.HealthyGreen
                                Surface(
                                    onClick = {
                                        val presets = ApiEndpointPreset.entries
                                        val nextIdx = if (currentPreset != null) (presets.indexOf(currentPreset) + 1) % presets.size else 0
                                        val nextPreset = presets[nextIdx]
                                        repository.setTuesdayApiBaseUrl(nextPreset.url)
                                        copyLogFeedback = "Switched API → ${nextPreset.url}"
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = targetColor.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .uiAction("cycleApiEndpointPreset")
                                        .border(1.dp, targetColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                ) {
                                    Text(
                                        text = "${currentPreset?.shortBadge ?: "⚙️ CUSTOM"} ⇄",
                                        color = targetColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                Surface(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(combinedLogText))
                                        copyLogFeedback = "✓ Copied ${combinedLogText.length} chars to clipboard"
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = TuesdayTheme.HacsCyan.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .uiAction("copyVisionDebugLog")
                                        .border(1.dp, TuesdayTheme.HacsCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                ) {
                                    Text(
                                        text = "Copy Log",
                                        color = TuesdayTheme.HacsCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                                if (debugLogHistory.isNotEmpty()) {
                                    Surface(
                                        onClick = {
                                            debugLogHistory = emptyList()
                                            copyLogFeedback = null
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = TuesdayTheme.CriticalRed.copy(alpha = 0.18f),
                                        modifier = Modifier
                                            .uiAction("clearVisionDebugLog")
                                            .border(1.dp, TuesdayTheme.CriticalRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    ) {
                                        Text(
                                            text = "Clear",
                                            color = TuesdayTheme.CriticalRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        SelectionContainer {
                            OutlinedTextField(
                                value = combinedLogText,
                                onValueChange = {},
                                readOnly = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.5.sp,
                                    color = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 420.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TuesdayTheme.WarningAmber.copy(alpha = 0.5f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                                    focusedContainerColor = Color(0xFF060911),
                                    unfocusedContainerColor = Color(0xFF060911)
                                )
                            )
                        }
                    }
                }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}
