package com.vonreyher.medicationtracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.PackageCodeParser
import java.util.concurrent.Executors

/**
 * Reads text from a medicine package. Only text inside the frame counts, so the camera has to be
 * held right over the wanted line; recognised lines appear as chips and a tap fills the field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextCaptureScreen(
    fieldLabel: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    CaptureScaffold(
        title = "$fieldLabel scannen",
        onClose = onClose,
        camera = { torchOn ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val frame = with(density) {
                    val width = constraints.maxWidth * 0.88f
                    val height = FrameHeight.toPx()
                    Rect(
                        left = (constraints.maxWidth - width) / 2f,
                        top = (constraints.maxHeight - height) / 2f,
                        right = (constraints.maxWidth + width) / 2f,
                        bottom = (constraints.maxHeight + height) / 2f
                    )
                }
                TextRecognitionPreview(
                    recognizer = recognizer,
                    torchOn = torchOn,
                    onText = { text ->
                        val inFrame = text.textBlocks
                            .flatMap { it.lines }
                            .filter { line -> line.boundingBox?.let { frame.containsLine(it) } == true }
                            .map { it.text.trim() }
                            .filter { it.length >= 2 }
                            .distinct()
                        if (inFrame.isNotEmpty()) lines = inFrame
                    }
                )
                ScanFrame(frame)
            }
        }
    ) {
        Text(
            text = if (lines.isEmpty()) "Halte den gewünschten Text genau in den Rahmen."
            else "Tippe auf den passenden Text:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            lines.forEach { line ->
                SuggestionChip(onClick = { onPick(line) }, label = { Text(line) })
            }
        }
    }
}

private val FrameHeight = 120.dp

/** A line counts when it lies within the frame horizontally and its center is inside vertically. */
private fun Rect.containsLine(box: android.graphics.Rect): Boolean {
    val tolerance = width * 0.04f
    return box.left >= left - tolerance && box.right <= right + tolerance &&
        box.exactCenterY() in top..bottom
}

/** Dims everything but the frame, like a document scanner. */
@Composable
private fun ScanFrame(frame: Rect) {
    val corner = with(LocalDensity.current) { 24.dp.toPx() }
    val stroke = with(LocalDensity.current) { 3.dp.toPx() }
    val outline = MaterialTheme.colorScheme.primaryContainer
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = frame.topLeft,
            size = frame.size,
            cornerRadius = CornerRadius(corner),
            blendMode = BlendMode.Clear
        )
        drawRoundRect(
            color = outline,
            topLeft = frame.topLeft,
            size = frame.size,
            cornerRadius = CornerRadius(corner),
            style = DrawStroke(width = stroke)
        )
    }
}

/** Camera preview whose text results come in preview coordinates, so they can be matched to the frame. */
@Composable
private fun TextRecognitionPreview(
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    torchOn: Boolean,
    onText: (com.google.mlkit.vision.text.Text) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnText by rememberUpdatedState(onText)
    val executor = remember { Executors.newSingleThreadExecutor() }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()
            setImageAnalysisAnalyzer(
                executor,
                MlKitAnalyzer(
                    listOf(recognizer),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(context)
                ) { result -> result.getValue(recognizer)?.let { currentOnText(it) } }
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            executor.shutdown()
        }
    }
    LaunchedEffect(torchOn) { controller.enableTorch(torchOn) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        }
    )
}

/** Scans the barcode or securPharm code on a package and returns the PZN. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PackageBarcodeScreen(
    onPzn: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val currentOnPzn by rememberUpdatedState(onPzn)
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_128, Barcode.FORMAT_EAN_13)
                .build()
        )
    }
    DisposableEffect(scanner) { onDispose { scanner.close() } }
    var hint by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    CaptureScaffold(
        title = "PZN scannen",
        onClose = onClose,
        camera = { torchOn -> AnalyzingCameraPreview(torchOn = torchOn, analyze = { image ->
            if (done) {
                null
            } else {
                scanner.process(image).addOnSuccessListener { barcodes ->
                    if (done || barcodes.isEmpty()) return@addOnSuccessListener
                    val pzn = barcodes.firstNotNullOfOrNull { code -> code.rawValue?.let(PackageCodeParser::pznFrom) }
                    if (pzn != null) {
                        done = true
                        currentOnPzn(pzn)
                    } else {
                        hint = "Code erkannt, aber keine PZN gefunden. Versuche den Code mit „PZN“ oder den quadratischen Code."
                    }
                }
            }
        }) }
    ) {
        Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
        Text(
            text = hint ?: "Halte den PZN-Barcode oder den quadratischen Code der Packung in die Kamera.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (hint != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Camera area with a panel below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureScaffold(
    title: String,
    onClose: () -> Unit,
    camera: @Composable (torchOn: Boolean) -> Unit,
    panel: @Composable () -> Unit
) {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Schließen")
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconToggleButton(checked = torchOn, onCheckedChange = { torchOn = it }) {
                            Icon(painterResource(R.drawable.ic_flashlight), contentDescription = "Licht")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) { panel() }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (hasPermission) {
                camera(torchOn)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Zum Scannen braucht die App Zugriff auf die Kamera.", textAlign = TextAlign.Center)
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Kamera erlauben") }
                }
            }
        }
    }
}

/** Camera preview that hands every frame to [analyze], which returns the ML Kit task or null to skip it. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun AnalyzingCameraPreview(torchOn: Boolean, analyze: (InputImage) -> Task<*>?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAnalyze by rememberUpdatedState(analyze)
    val executor = remember { Executors.newSingleThreadExecutor() }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()
            setImageAnalysisAnalyzer(executor) { proxy ->
                val mediaImage = proxy.image
                val task = mediaImage?.let { currentAnalyze(InputImage.fromMediaImage(it, proxy.imageInfo.rotationDegrees)) }
                if (task == null) proxy.close() else task.addOnCompleteListener { proxy.close() }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            executor.shutdown()
        }
    }
    LaunchedEffect(torchOn) { controller.enableTorch(torchOn) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        }
    )
}
