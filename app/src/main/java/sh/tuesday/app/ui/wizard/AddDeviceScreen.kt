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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sh.tuesday.app.models.*
import sh.tuesday.app.services.GeminiDiscoveryService
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayTheme

enum class EnrollmentMode(val title: String) {
    PHOTO_WIZARD("AI Photo Wizard"),
    MANUAL_AGENT("Manual / Agent")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    repository: TuesdayRepository,
    discoveryService: GeminiDiscoveryService,
    onNavigateBack: () -> Unit
) {
    var mode by remember { mutableStateOf(EnrollmentMode.PHOTO_WIZARD) }
    val hacsConfig by repository.hacsConfig.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Wizard State
    var capturedBitmaps by remember { mutableStateOf(listOf<Bitmap>()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var discoveryResult by remember { mutableStateOf<GeminiDiscoveryTurnResult?>(null) }
    var accumulatedIdentifiers by remember { mutableStateOf(ExtractedDeviceIdentifiers()) }
    var userClueNotes by remember { mutableStateOf(listOf<String>()) }
    var selectedClueType by remember { mutableStateOf(DeviceClueType.MODEL_NUMBER) }
    var clueInputText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

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

    fun triggerProgressiveAnalysis(newBmp: Bitmap) {
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
                modelName = hacsConfig.geminiModel
            )
            accumulatedIdentifiers.merge(res.extractedIdentifiers)
            discoveryResult = res
            isAnalyzing = false
        }
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeUriToSoftwareBitmap(uri)
            if (bmp != null) {
                triggerProgressiveAnalysis(bmp)
            }
        }
    }

    // Full-Resolution Camera Capture via FileProvider
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val bmp = decodeUriToSoftwareBitmap(uri)
            if (bmp != null) {
                triggerProgressiveAnalysis(bmp)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Device", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TuesdayTheme.AppBar)
            )
        },
        containerColor = TuesdayTheme.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Mode Switcher Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TuesdayTheme.CardSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnrollmentMode.entries.forEach { item ->
                    val isSelected = mode == item
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TuesdayTheme.HealthyGreen else Color.Transparent)
                            .clickable { mode = item }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (item == EnrollmentMode.PHOTO_WIZARD) Icons.Default.CameraAlt else Icons.Default.Terminal,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = item.title,
                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (mode == EnrollmentMode.PHOTO_WIZARD) {
                // Step Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TuesdayTheme.HacsCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (capturedBitmaps.isEmpty()) "STEP 1 OF 3 • CAPTURE HARDWARE" else "STEP 2 OF 3 • PROGRESSIVE DISCOVERY",
                            color = TuesdayTheme.HacsCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AiProviderType.entries.forEach { prov ->
                            val isActive = hacsConfig.aiProvider == prov
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isActive) TuesdayTheme.HealthyGreen.copy(alpha = 0.2f) else TuesdayTheme.CardSurface)
                                    .border(
                                        1.dp,
                                        if (isActive) TuesdayTheme.HealthyGreen else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        repository.updateHacsConfig(hacsConfig.copy(aiProvider = prov))
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = prov.displayName,
                                    color = if (isActive) TuesdayTheme.HealthyGreen else Color.White.copy(alpha = 0.55f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Inventory Device by Photo",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Snap a photo of a router, switch, NAS, or IoT device. Gemini AI will analyze the hardware and progressively narrow candidate models using barcodes, serial numbers, QR codes, models, or MAC addresses.",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // Capture Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TuesdayTheme.CardSurface)
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (capturedBitmaps.isEmpty()) "Take Device Photo" else "Snap Label / Sticker",
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
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Library", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Quick Sample Presets
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "QUICK PILOT HARDWARE PRESETS (FOR SIMULATOR / FAST TESTING)",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    val sample = GeminiDiscoveryService.createSampleHardwareBitmap("router")
                                    capturedBitmaps = capturedBitmaps + sample
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
                                            modelName = hacsConfig.geminiModel
                                        )
                                        accumulatedIdentifiers.merge(res.extractedIdentifiers)
                                        discoveryResult = res
                                        isAnalyzing = false
                                    }
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
                                    Text("Sample Router Photo", color = TuesdayTheme.HacsCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Surface(
                                onClick = {
                                    val sample = GeminiDiscoveryService.createSampleHardwareBitmap("iot-zigbee")
                                    capturedBitmaps = capturedBitmaps + sample
                                    userClueNotes = userClueNotes + "Zigbee coordinator antenna"
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
                                            modelName = hacsConfig.geminiModel
                                        )
                                        accumulatedIdentifiers.merge(res.extractedIdentifiers)
                                        discoveryResult = res
                                        isAnalyzing = false
                                    }
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
                                    Text("Sample IoT Dongle", color = TuesdayTheme.WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                    modelName = hacsConfig.geminiModel
                                                )
                                                accumulatedIdentifiers.merge(res.extractedIdentifiers)
                                                discoveryResult = res
                                                isAnalyzing = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TuesdayTheme.HacsCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp)
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

                            Button(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        val meta = DiscoveredDeviceMetadata(
                                            manufacturer = candidate.manufacturer,
                                            modelNumber = accumulatedIdentifiers.modelNumber ?: candidate.modelNumber,
                                            deviceCategory = candidate.deviceCategory,
                                            serialNumber = accumulatedIdentifiers.serialNumber,
                                            macAddress = accumulatedIdentifiers.macAddress,
                                            barcodeOrQr = accumulatedIdentifiers.barcode ?: accumulatedIdentifiers.qrCode,
                                            confidenceScore = candidate.confidencePercent,
                                            discoveryNotes = "Verified via GCP Gemini AI Photo Wizard (${candidate.confidencePercent}% confidence)"
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
                                modifier = Modifier.fillMaxWidth()
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
                                .border(1.dp, TuesdayTheme.WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add as Incomplete / Best-Guess Device", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            repository.enrollDevice(
                                hostname = host,
                                osDetails = manualOsDetails.trim().ifBlank { "Linux (${manualPkgManager.rawValue})" },
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
