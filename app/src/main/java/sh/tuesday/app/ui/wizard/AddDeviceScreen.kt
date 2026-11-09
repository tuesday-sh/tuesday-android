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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import sh.tuesday.app.models.*
import sh.tuesday.app.services.GeminiDiscoveryService
import sh.tuesday.app.services.GoldenCorpusRecorder
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayTheme
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
    onNavigateBack: () -> Unit
) {
    var mode by remember { mutableStateOf(EnrollmentMode.PHOTO_WIZARD) }
    val hacsConfig by repository.hacsConfig.collectAsState()
    val brandFlavor by repository.brandFlavor.collectAsState()
    val isDeveloperMode by repository.isDeveloperMode.collectAsState()
    val isVisionDebugEnabled by repository.isVisionDebugEnabled.collectAsState()
    val showVisionDebug = isDeveloperMode && isVisionDebugEnabled
    val palette = TuesdayTheme.paletteFor(brandFlavor)
    val isStn = brandFlavor == AppBrandFlavor.SURETHING_HOME
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Wizard State
    var currentSessionId by remember { mutableStateOf(discoveryService.startNewSession()) }
    var capturedBitmaps by remember { mutableStateOf(listOf<Bitmap>()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var discoveryResult by remember { mutableStateOf<GeminiDiscoveryTurnResult?>(null) }
    var accumulatedIdentifiers by remember { mutableStateOf(ExtractedDeviceIdentifiers()) }
    var userClueNotes by remember { mutableStateOf(listOf<String>()) }
    var selectedClueType by remember { mutableStateOf(DeviceClueType.MODEL_NUMBER) }
    var clueInputText by remember { mutableStateOf("") }
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
        val updatedBitmaps = capturedBitmaps + newBmp
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
                debugEnabled = showVisionDebug
            )
            accumulatedIdentifiers.merge(res.extractedIdentifiers)
            discoveryResult = res
            res.debugLogText?.takeIf { it.isNotBlank() }?.let { logEntry ->
                debugLogHistory = debugLogHistory + logEntry
            }
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
            isAnalyzing = false
        }
    }

    var scanStatusMessage by remember { mutableStateOf<String?>(null) }

    fun triggerIdentifierOnlyAnalysis() {
        scope.launch {
            isAnalyzing = true
            val res = discoveryService.analyzeDeviceProgressive(
                bitmaps = capturedBitmaps,
                accumulatedIdentifiers = accumulatedIdentifiers,
                userClueNotes = userClueNotes,
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

    fun applyDecodedBarcode(rawValue: String, format: Int) {
        val cleaned = rawValue.trim()
        if (cleaned.isEmpty()) return
        val is2D = format == Barcode.FORMAT_QR_CODE ||
            format == Barcode.FORMAT_DATA_MATRIX ||
            format == Barcode.FORMAT_AZTEC ||
            format == Barcode.FORMAT_PDF417
        // Normalize 13-digit 0-prefixed EAN-13 into 12-digit UPC-A for cross-platform Android/iOS parity
        val normalized = if (!is2D && cleaned.length == 13 && cleaned.startsWith("0") && cleaned.all { it.isDigit() }) {
            cleaned.removePrefix("0")
        } else {
            cleaned
        }
        if (is2D) {
            accumulatedIdentifiers.qrCode = normalized
            userClueNotes = userClueNotes + "QR Code=$normalized"
            scanStatusMessage = "✓ Decoded 2D QR/Matrix: $normalized"
        } else {
            accumulatedIdentifiers.barcode = normalized
            userClueNotes = userClueNotes + "Barcode=$normalized"
            scanStatusMessage = "✓ Decoded 1D Barcode: $normalized"
        }
        triggerIdentifierOnlyAnalysis()
    }

    fun decodeBarcodesFromBitmap(bmp: Bitmap) {
        val inputImage = InputImage.fromBitmap(bmp, 0)
        val scanner = BarcodeScanning.getClient()
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (first != null && !first.rawValue.isNullOrBlank()) {
                    applyDecodedBarcode(first.rawValue!!, first.format)
                } else {
                    scanStatusMessage = "⚠ No 1D/2D barcode matrix detected in file — falling back to Vision OCR"
                    triggerProgressiveAnalysis(bmp, captureSource = "gallery")
                }
            }
            .addOnFailureListener { e ->
                scanStatusMessage = "⚠ ML Kit decode error (${e.localizedMessage}) — falling back to Vision OCR"
                triggerProgressiveAnalysis(bmp, captureSource = "gallery")
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

    // Upload File Picker (Photo Mode)
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeUriToSoftwareBitmap(uri)
            if (bmp != null) {
                triggerProgressiveAnalysis(bmp, captureSource = "gallery")
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
                triggerProgressiveAnalysis(bmp, captureSource = "camera")
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "QUICK PILOT PRESETS & DEV CONTROLS",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (includePhotoSamples) {
                    // 1-Tap Replay Chips for Real Photos Saved in Golden Corpus
                    savedManifests.forEach { manifest ->
                        val label = manifest.confirmedModelNumber ?: manifest.confirmedManufacturer ?: "Saved Photo"
                        Surface(
                            onClick = {
                                val loaded = GoldenCorpusRecorder.loadSessionBitmaps(context, manifest.sessionId)
                                if (loaded.isNotEmpty()) {
                                    currentSessionId = discoveryService.startNewSession()
                                    currentCaptureSource = "corpus_replay"
                                    capturedBitmaps = loaded.dropLast(1)
                                    accumulatedIdentifiers = ExtractedDeviceIdentifiers()
                                    userClueNotes = emptyList()
                                    triggerProgressiveAnalysis(loaded.last(), captureSource = "corpus_replay")
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = TuesdayTheme.HealthyGreen.copy(alpha = 0.16f),
                            modifier = Modifier.border(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TuesdayTheme.HealthyGreen, modifier = Modifier.size(12.dp))
                                Text("Replay: $label", color = TuesdayTheme.HealthyGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            currentCaptureSource = "synthetic_sample"
                            val sample = GeminiDiscoveryService.createSampleHardwareBitmap("router")
                            capturedBitmaps = capturedBitmaps + sample
                            triggerIdentifierOnlyAnalysis()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = TuesdayTheme.HacsCyan.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(12.dp))
                            Text("Synth Router", color = TuesdayTheme.HacsCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        onClick = {
                            currentCaptureSource = "synthetic_sample"
                            val sample = GeminiDiscoveryService.createSampleHardwareBitmap("iot-zigbee")
                            capturedBitmaps = capturedBitmaps + sample
                            userClueNotes = userClueNotes + "Zigbee coordinator antenna"
                            triggerIdentifierOnlyAnalysis()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = TuesdayTheme.WarningAmber.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = TuesdayTheme.WarningAmber, modifier = Modifier.size(12.dp))
                            Text("Synth IoT", color = TuesdayTheme.WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Surface(
                        onClick = {
                            applyDecodedBarcode("UDM-Pro", Barcode.FORMAT_CODE_128)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = TuesdayTheme.HacsCyan.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(12.dp))
                            Text("Sample Barcode (UDM-Pro)", color = TuesdayTheme.HacsCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        onClick = {
                            applyDecodedBarcode("UCG-Ultra", Barcode.FORMAT_QR_CODE)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = TuesdayTheme.WarningAmber.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = TuesdayTheme.WarningAmber, modifier = Modifier.size(12.dp))
                            Text("Sample QR (UCG-Ultra)", color = TuesdayTheme.WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Debug Mode Toggle Pill
                Surface(
                    onClick = {
                        if (!isDeveloperMode) {
                            repository.toggleDeveloperMode(true)
                            repository.toggleVisionDebugMode(true)
                        } else {
                            repository.toggleVisionDebugMode(!isVisionDebugEnabled)
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = debugActiveColor.copy(alpha = if (showVisionDebug) 0.16f else 0.08f),
                    modifier = Modifier
                        .uiAction("toggleVisionDebugMode")
                        .border(1.dp, debugActiveColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (showVisionDebug) "🐞 DEBUG ON" else "🐞 DEBUG",
                            color = debugActiveColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }

                // AI Model Switcher Pill
                Surface(
                    onClick = {
                        val nextProvider = if (isElastix) AiProviderType.GEMINI else AiProviderType.ELASTIX
                        repository.updateHacsConfig(hacsConfig.copy(aiProvider = nextProvider))
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = activeAiColor.copy(alpha = 0.14f),
                    modifier = Modifier
                        .uiAction("switchAiProvider")
                        .border(1.dp, activeAiColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(activeAiColor)
                        )
                        Text(
                            text = if (isElastix) "AI: Elastix ⇄" else "AI: Gemini ⇄",
                            color = activeAiColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        },
        containerColor = palette.bg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .uiComponent("AddDeviceWizard")
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Mode Switcher Tabs: Photo | Scan | Manual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("enrollment-mode-tabs")
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnrollmentMode.entries.forEach { item ->
                    val isSelected = mode == item
                    val tabIcon = when (item) {
                        EnrollmentMode.PHOTO_WIZARD -> Icons.Default.CameraAlt
                        EnrollmentMode.BARCODE_SCAN -> Icons.Default.QrCodeScanner
                        EnrollmentMode.MANUAL_AGENT -> Icons.Default.Terminal
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .uiAction("switchEnrollmentMode")
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TuesdayTheme.HealthyGreen else Color.Transparent)
                            .clickable {
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
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else palette.textPrimary.copy(alpha = 0.75f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = item.title,
                                color = if (isSelected) Color.Black else palette.textPrimary.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (mode == EnrollmentMode.PHOTO_WIZARD || mode == EnrollmentMode.BARCODE_SCAN) {
                if (mode == EnrollmentMode.PHOTO_WIZARD) {
                    Text(
                        text = if (isStn) "Identify Device by Photo" else "Inventory Device by Photo",
                        color = palette.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Capture a photo of a device chassis, bottom label, or retail box. Tuesday AI will analyze the hardware and progressively narrow candidate models.",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    // Photo Capture Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("photo-capture-card")
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (capturedBitmaps.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(capturedBitmaps) { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Captured Device",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 140.dp, height = 95.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }

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

                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TuesdayTheme.ElevatedPill,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.uiAction("uploadHardwarePhoto")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        DevPresetsAndControlsRow(includePhotoSamples = true)
                    }
                } else {
                    Text(
                        text = "Scan Barcode or QR Code",
                        color = palette.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Deterministically decode 1D retail UPC/EAN barcodes, Code-128 serial stickers, or 2D QR / DataMatrix codes for $0.00 Gold Catalog lookup.",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    // Deterministic Barcode / QR Scan Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("barcode-scan-card")
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        scanStatusMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = if (msg.startsWith("✓")) TuesdayTheme.HealthyGreen else TuesdayTheme.WarningAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

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

                            Button(
                                onClick = { scanFileLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TuesdayTheme.ElevatedPill,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.uiAction("uploadBarcodeImage")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        DevPresetsAndControlsRow(includePhotoSamples = false)
                    }
                }

                if (isAnalyzing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TuesdayTheme.CardSurface)
                            .border(1.dp, TuesdayTheme.HacsCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = TuesdayTheme.HacsCyan,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Column {
                            Text(
                                "Analyzing device with ${hacsConfig.aiProvider.displayName}...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Inspecting chassis profile, ports, OUI prefix, and printed labels", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                    }
                }

                // Progressive Clues & Candidates Loop
                discoveryResult?.let { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiComponent("AiDiscoveryPanel")
                            .uiSlot("ai-discovery-panel")
                            .clip(RoundedCornerShape(12.dp))
                            .background(TuesdayTheme.CardSurface)
                            .border(1.dp, TuesdayTheme.HacsCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("AI HARDWARE DISCOVERY", color = TuesdayTheme.HacsCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(
                                result.providerUsed.uppercase(),
                                color = TuesdayTheme.HealthyGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(result.visualSummary, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)

                        // Golden Corpus Auto-Save Status & Quick Next-Device Loop Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiSlot("golden-corpus-bar")
                                .clip(RoundedCornerShape(8.dp))
                                .background(TuesdayTheme.HealthyGreen.copy(alpha = 0.12f))
                                .border(1.dp, TuesdayTheme.HealthyGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (currentCaptureSource == "synthetic_sample") {
                                        "🧪 SYNTHETIC SAMPLE (NOT SAVED TO CORPUS)"
                                    } else {
                                        "📦 GOLDEN CORPUS AUTO-SAVED ($savedCorpusCount TOTAL)"
                                    },
                                    color = if (currentCaptureSource == "synthetic_sample") TuesdayTheme.WarningAmber else TuesdayTheme.HealthyGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (currentCaptureSource == "synthetic_sample") {
                                        "Canvas test graphic — excluded from real photo corpus"
                                    } else {
                                        lastSavedCorpusFolder ?: "Saved to /sdcard/.../golden_corpus/"
                                    },
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        val sid = currentSessionId
                                        val topCand = result.candidates.firstOrNull()
                                        val idsSnapshot = accumulatedIdentifiers
                                        val cluesSnapshot = userClueNotes
                                        scope.launch {
                                            discoveryService.confirmDeviceDiscovery(
                                                inferredVendor = topCand?.manufacturer,
                                                inferredModel = topCand?.modelNumber,
                                                confirmedVendor = "",
                                                confirmedModel = "",
                                                wasEdited = false,
                                                accumulatedIdentifiers = idsSnapshot,
                                                userClueNotes = cluesSnapshot,
                                                selectedCandidateRank = null,
                                                resolutionStatus = "abandoned_discarded",
                                                enrollOnServer = false,
                                                serverBaseUrl = hacsConfig.tuesdayApiBaseUrl
                                            )
                                            GoldenCorpusRecorder.deleteCaptureSession(context, sid)
                                            savedCorpusCount = GoldenCorpusRecorder.getSavedSessionCount(context)
                                            lastSavedCorpusFolder = null
                                            currentSessionId = discoveryService.startNewSession()
                                            capturedBitmaps = emptyList()
                                            discoveryResult = null
                                            accumulatedIdentifiers = ExtractedDeviceIdentifiers()
                                            userClueNotes = emptyList()
                                            clueInputText = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = TuesdayTheme.CriticalRed.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .uiAction("discardCaptureSession")
                                        .border(
                                            1.dp,
                                            TuesdayTheme.CriticalRed.copy(alpha = 0.6f),
                                            RoundedCornerShape(6.dp)
                                        )
                                ) {
                                    Text(
                                        text = "✕ Discard",
                                        color = TuesdayTheme.CriticalRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                Surface(
                                    onClick = {
                                        // Reset wizard state to immediately scan the next piece of hardware
                                        currentSessionId = discoveryService.startNewSession()
                                        capturedBitmaps = emptyList()
                                        discoveryResult = null
                                        accumulatedIdentifiers = ExtractedDeviceIdentifiers()
                                        userClueNotes = emptyList()
                                        clueInputText = ""
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = TuesdayTheme.HealthyGreen,
                                    modifier = Modifier.uiAction("scanNextDevice")
                                ) {
                                    Text(
                                        text = "Scan Next →",
                                        color = Color.Black,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Text(result.nextNarrowingQuestion, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                        // Discovered identifiers chips
                        if (accumulatedIdentifiers.nonEmptyPairs().isNotEmpty()) {
                            Text("DISCOVERED IDENTIFIERS", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(accumulatedIdentifiers.nonEmptyPairs()) { pair ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(TuesdayTheme.HealthyGreen.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${pair.first.rawValue}: ${pair.second}",
                                            color = TuesdayTheme.HealthyGreen,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Add Clue Input
                        Column(
                            modifier = Modifier.uiSlot("clue-refinement-form"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("ADD IDENTIFIER TO NARROW CANDIDATES (BARCODE, S/N, QR, MODEL, MAC)", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(DeviceClueType.entries) { clue ->
                                    val isSelected = selectedClueType == clue
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) TuesdayTheme.HacsCyan else TuesdayTheme.ElevatedPill)
                                            .clickable { selectedClueType = clue }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = clue.rawValue,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = clueInputText,
                                    onValueChange = { clueInputText = it },
                                    placeholder = { Text(selectedClueType.placeholder, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TuesdayTheme.HacsCyan,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )

                                Button(
                                    onClick = {
                                        val trimmed = clueInputText.trim()
                                        if (trimmed.isNotBlank()) {
                                            when (selectedClueType) {
                                                DeviceClueType.MODEL_NUMBER -> accumulatedIdentifiers.modelNumber = trimmed
                                                DeviceClueType.MAC_ADDRESS -> accumulatedIdentifiers.macAddress = trimmed
                                                DeviceClueType.SERIAL_NUMBER -> accumulatedIdentifiers.serialNumber = trimmed
                                                DeviceClueType.BARCODE -> accumulatedIdentifiers.barcode = trimmed
                                                DeviceClueType.QR_CODE -> accumulatedIdentifiers.qrCode = trimmed
                                            }
                                            userClueNotes = userClueNotes + "${selectedClueType.rawValue}=$trimmed"
                                            clueInputText = ""
                                            scope.launch {
                                                isAnalyzing = true
                                                val res = discoveryService.analyzeDeviceProgressive(
                                                    bitmaps = capturedBitmaps,
                                                    accumulatedIdentifiers = accumulatedIdentifiers,
                                                    userClueNotes = userClueNotes,
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
                                                if (capturedBitmaps.isNotEmpty()) {
                                                    val savedDir = GoldenCorpusRecorder.saveCaptureSession(
                                                        context = context,
                                                        sessionId = currentSessionId,
                                                        bitmaps = capturedBitmaps,
                                                        result = res,
                                                        accumulatedIdentifiers = accumulatedIdentifiers,
                                                        userClueNotes = userClueNotes,
                                                        status = "refined_clues",
                                                        captureSource = currentCaptureSource
                                                    )
                                                    lastSavedCorpusFolder = savedDir?.name
                                                    savedCorpusCount = GoldenCorpusRecorder.getSavedSessionCount(context)
                                                    savedManifests = GoldenCorpusRecorder.listSavedManifests(context)
                                                }
                                                isAnalyzing = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TuesdayTheme.HacsCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.uiAction("refineDiscoveryClue")
                                ) {
                                    Text("Refine", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Candidate Cards
                    Text(
                        text = "CANDIDATE DEVICE MATCHES (${result.candidates.size})",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    result.candidates.forEachIndexed { index, candidate ->
                        val badgeColor = if (candidate.confidencePercent >= 80) TuesdayTheme.HealthyGreen else (if (candidate.confidencePercent >= 50) TuesdayTheme.WarningAmber else TuesdayTheme.HacsCyan)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiSlot("candidate-match-card")
                                .clip(RoundedCornerShape(12.dp))
                                .background(TuesdayTheme.CardSurface)
                                .border(1.dp, if (index == 0) badgeColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("${candidate.manufacturer} • Model ${candidate.modelNumber} • ${candidate.deviceCategory}", color = TuesdayTheme.HacsCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text("${candidate.confidencePercent}% MATCH", color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Text(candidate.reasoning, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)

                            candidate.goldAlignment?.let { ga ->
                                val isAligned = ga.aligned
                                val gaColor = if (isAligned) TuesdayTheme.HealthyGreen else TuesdayTheme.WarningAmber
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(gaColor.copy(alpha = 0.1f))
                                        .border(1.dp, gaColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isAligned) {
                                                "✓ GOLD ALIGNED • ${ga.matchedFeedSlug ?: "built-in-catalog"}"
                                            } else if (!ga.matchedFeedSlug.isNullOrBlank()) {
                                                "⚠ ${ga.statusLabel} • ${ga.matchedFeedSlug}"
                                            } else {
                                                "⚠ ${ga.statusLabel}"
                                            },
                                            color = gaColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (ga.inferredPurl.isNotBlank()) {
                                            Text(
                                                text = ga.inferredPurl,
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    if (showVisionDebug && ga.diagnosticNote.isNotBlank()) {
                                        Text(
                                            text = ga.diagnosticNote,
                                            color = Color.White.copy(alpha = 0.78f),
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        val resolvedModel = accumulatedIdentifiers.modelNumber ?: candidate.modelNumber
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
                                        discoveryService.confirmDeviceDiscovery(
                                            inferredVendor = candidate.manufacturer,
                                            inferredModel = candidate.modelNumber,
                                            confirmedVendor = candidate.manufacturer,
                                            confirmedModel = resolvedModel,
                                            wasEdited = (resolvedModel != candidate.modelNumber) || (index > 0),
                                            accumulatedIdentifiers = accumulatedIdentifiers,
                                            userClueNotes = userClueNotes,
                                            selectedCandidateRank = index,
                                            resolutionStatus = if (mode == EnrollmentMode.BARCODE_SCAN && capturedBitmaps.isNotEmpty()) "pivoted_to_barcode" else "confirmed_candidate",
                                            initialMode = if (currentCaptureSource == "synthetic_sample") "synthetic_sample" else "photo_wizard",
                                            finalMode = if (mode == EnrollmentMode.BARCODE_SCAN) "barcode_scan" else if (currentCaptureSource == "synthetic_sample") "synthetic_sample" else "photo_wizard",
                                            candidateModels = result.candidates.map { it.modelNumber },
                                            serverBaseUrl = hacsConfig.tuesdayApiBaseUrl
                                        )
                                        repository.enrollDevice(
                                            hostname = candidate.suggestedHostname,
                                            osDetails = "${candidate.displayName} • ${candidate.suggestedOsDetails}",
                                            packageManager = candidate.resolvedPackageManager,
                                            agentToken = "tuesday_ai_tok_${(1000..9999).random()}",
                                            inventoryState = InventoryConfidenceState.VERIFIED_MATCH,
                                            discoveredMetadata = meta
                                        )
                                        isSaving = false
                                        onNavigateBack()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TuesdayTheme.HealthyGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiAction("enrollConfirmedDevice")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirm Match & Add to Inventory", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Incomplete / Best-Guess Card
                    val bestGuess = result.candidates.firstOrNull()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("incomplete-guess-fallback-card")
                            .clip(RoundedCornerShape(12.dp))
                            .background(TuesdayTheme.CardSurface)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = TuesdayTheme.WarningAmber, modifier = Modifier.size(16.dp))
                            Text("Not Sure or Label Unreadable?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "You can stop narrowing at any time and add this device to your Tuesday.sh inventory as an Incomplete / Best-Guess entry to finish auditing later.",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    val meta = DiscoveredDeviceMetadata(
                                        manufacturer = bestGuess?.manufacturer ?: "Unknown Vendor",
                                        modelNumber = accumulatedIdentifiers.modelNumber ?: (bestGuess?.modelNumber ?: "Unidentified Hardware"),
                                        deviceCategory = bestGuess?.deviceCategory ?: "Network / IoT Device",
                                        serialNumber = accumulatedIdentifiers.serialNumber,
                                        macAddress = accumulatedIdentifiers.macAddress,
                                        barcodeOrQr = accumulatedIdentifiers.barcode ?: accumulatedIdentifiers.qrCode,
                                        confidenceScore = bestGuess?.confidencePercent ?: 35,
                                        discoveryNotes = "Added via AI Photo Wizard as Incomplete/Guessed"
                                    )
                                    val host = bestGuess?.let { "${it.suggestedHostname} (guessed)" } ?: "unidentified-device.local"
                                    GoldenCorpusRecorder.saveCaptureSession(
                                        context = context,
                                        sessionId = currentSessionId,
                                        bitmaps = capturedBitmaps,
                                        result = result,
                                        accumulatedIdentifiers = accumulatedIdentifiers,
                                        userClueNotes = userClueNotes,
                                        confirmedCandidate = bestGuess,
                                        status = "saved_best_guess",
                                        captureSource = currentCaptureSource
                                    )
                                    discoveryService.confirmDeviceDiscovery(
                                        inferredVendor = bestGuess?.manufacturer,
                                        inferredModel = bestGuess?.modelNumber,
                                        confirmedVendor = meta.manufacturer ?: "Unknown Vendor",
                                        confirmedModel = meta.modelNumber ?: "Unidentified Hardware",
                                        wasEdited = false,
                                        accumulatedIdentifiers = accumulatedIdentifiers,
                                        userClueNotes = userClueNotes,
                                        selectedCandidateRank = null,
                                        resolutionStatus = "saved_best_guess",
                                        initialMode = if (currentCaptureSource == "synthetic_sample") "synthetic_sample" else "photo_wizard",
                                        finalMode = if (mode == EnrollmentMode.BARCODE_SCAN) "barcode_scan" else if (currentCaptureSource == "synthetic_sample") "synthetic_sample" else "photo_wizard",
                                        candidateModels = result.candidates.map { it.modelNumber },
                                        serverBaseUrl = hacsConfig.tuesdayApiBaseUrl
                                    )
                                    repository.enrollDevice(
                                        hostname = host,
                                        osDetails = bestGuess?.let { "${it.displayName} • ${it.suggestedOsDetails}" } ?: "Unverified Hardware Photo (Pending Audit)",
                                        packageManager = bestGuess?.resolvedPackageManager ?: PackageManagerType.FIRMWARE,
                                        agentToken = "tuesday_ai_incomplete",
                                        inventoryState = InventoryConfidenceState.INCOMPLETE_OR_GUESSED,
                                        discoveredMetadata = meta
                                    )
                                    isSaving = false
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TuesdayTheme.WarningAmber.copy(alpha = 0.15f),
                                contentColor = TuesdayTheme.WarningAmber
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiAction("enrollGuessedDevice")
                                .border(1.dp, TuesdayTheme.WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add as Incomplete / Best-Guess Device", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            } else {
                // Manual Agent Enrollment Mode
                Text("Enroll Tuesday.sh Agent Device", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Enter the device metadata manually or scan an enrollment QR code.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)

                manualError?.let {
                    Text(it, color = TuesdayTheme.CriticalRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = manualHostname,
                    onValueChange = { manualHostname = it },
                    label = { Text("HOSTNAME / NODE ALIAS", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("e.g. unifi-gateway, surface95") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manualOsDetails,
                    onValueChange = { manualOsDetails = it },
                    label = { Text("OS / PLATFORM DETAILS", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("e.g. Debian 12 Bookworm (aarch64)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("AGENT TOKEN / WEBHOOK ID", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("e.g. tuesday_tok_8821") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val host = manualHostname.trim()
                        if (host.isBlank()) {
                            manualError = "Hostname is required."
                            return@Button
                        }
                        scope.launch {
                            isSaving = true
                            val topCand = discoveryResult?.candidates?.firstOrNull()
                            val cleanOs = manualOsDetails.trim().ifBlank { "Linux (${manualPkgManager.rawValue})" }
                            if (capturedBitmaps.isNotEmpty() && discoveryResult != null) {
                                GoldenCorpusRecorder.saveCaptureSession(
                                    context = context,
                                    sessionId = currentSessionId,
                                    bitmaps = capturedBitmaps,
                                    result = discoveryResult!!,
                                    accumulatedIdentifiers = accumulatedIdentifiers,
                                    userClueNotes = userClueNotes + listOf("ManualHost=$host", "ManualOS=$cleanOs"),
                                    confirmedCandidate = topCand,
                                    status = "pivoted_to_manual",
                                    captureSource = currentCaptureSource
                                )
                            }
                            discoveryService.confirmDeviceDiscovery(
                                inferredVendor = topCand?.manufacturer,
                                inferredModel = topCand?.modelNumber,
                                confirmedVendor = topCand?.manufacturer ?: "Manual Entry",
                                confirmedModel = host,
                                wasEdited = true,
                                accumulatedIdentifiers = accumulatedIdentifiers,
                                userClueNotes = userClueNotes + listOf("ManualHost=$host", "ManualOS=$cleanOs"),
                                selectedCandidateRank = null,
                                resolutionStatus = "pivoted_to_manual",
                                initialMode = if (capturedBitmaps.isNotEmpty()) "photo_wizard" else "manual_agent",
                                finalMode = "manual_agent",
                                serverBaseUrl = hacsConfig.tuesdayApiBaseUrl
                            )
                            repository.enrollDevice(
                                hostname = host,
                                osDetails = cleanOs,
                                packageManager = manualPkgManager,
                                agentToken = manualToken.trim().ifBlank { "tuesday_tok_auto" }
                            )
                            isSaving = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TuesdayTheme.HealthyGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Register in HACS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
