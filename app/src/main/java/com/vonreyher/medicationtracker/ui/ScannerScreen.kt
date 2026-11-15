package com.vonreyher.medicationtracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.BmpPage
import com.vonreyher.medicationtracker.data.BmpParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

/**
 * The BMP code is a dense DataMatrix (up to 144×144 modules), which needs far more pixels than
 * CameraX's default 640×480 analysis frames. Analyse at a high resolution instead.
 */
private val AnalysisSize = Size(2560, 1920)

/** Photos are decoded at most this large; enough for the DataMatrix, small enough for memory. */
private const val MaxPhotoSide = 4000

private fun dataMatrixReader() = BarcodeReader(
    BarcodeReader.Options(
        formats = setOf(BarcodeReader.Format.DATA_MATRIX),
        tryHarder = true,
        tryRotate = true,
        tryInvert = true
    )
)

/**
 * Full-screen scanner for the DataMatrix code of a Bundeseinheitlicher Medikationsplan.
 * Scans live from the camera or from a photo picked from the gallery, using the open-source
 * ZXing-cpp reader.
 *
 * [onPage] receives every recognised plan page and returns an error message to show,
 * or null if the page was accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onPage: (BmpPage) -> String?,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPage by rememberUpdatedState(onPage)
    var hint by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // Set once a page was accepted so that following frames don't report it again.
    var accepted by remember { mutableStateOf(false) }

    fun handle(results: List<BarcodeReader.Result>, reportMissing: Boolean) {
        if (accepted) return
        if (results.isEmpty()) {
            if (reportMissing) hint = "Im Foto wurde kein DataMatrix-Code gefunden."
            return
        }
        val page = results.firstNotNullOfOrNull { BmpParser.parseOrNull(it.bytes, it.text) }
        if (page == null) {
            hint = "Dieser Code ist kein Bundeseinheitlicher Medikationsplan."
            return
        }
        val error = currentOnPage(page)
        if (error == null) accepted = true else hint = error
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val results = withContext(Dispatchers.Default) {
                decodePhoto(context, uri)?.let { bitmap -> dataMatrixReader().read(bitmap, Rect(), 0) }
            }
            if (results == null) hint = "Das Foto konnte nicht geöffnet werden." else handle(results, reportMissing = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medikationsplan scannen") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Schließen")
                    }
                },
                actions = {
                    if (hasCameraPermission) {
                        IconToggleButton(checked = torchOn, onCheckedChange = { torchOn = it }) {
                            Icon(
                                painterResource(R.drawable.ic_flashlight),
                                contentDescription = if (torchOn) "Licht ausschalten" else "Licht einschalten"
                            )
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
                ) {
                    Text(
                        text = hint ?: "Halte den Code oben rechts auf dem Medikationsplan formatfüllend in den Rahmen. " +
                            "Bei schlechtem Licht hilft die Taschenlampe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hint != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = {
                        hint = null
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Aus Foto laden")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    torchOn = torchOn,
                    onResults = { handle(it, reportMissing = false) }
                )
                Box(
                    Modifier
                        .size(280.dp)
                        .border(3.dp, Color.White, MaterialTheme.shapes.large)
                )
            } else {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Zum Scannen braucht die App Zugriff auf die Kamera. " +
                            "Alternativ kannst du ein Foto des Medikationsplans laden.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Kamera erlauben")
                    }
                }
            }
        }
    }
}

/** Decodes a picked photo, downsampled only when it is larger than [MaxPhotoSide]. */
private fun decodePhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MaxPhotoSide) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()

@Composable
private fun CameraPreview(
    torchOn: Boolean,
    onResults: (List<BarcodeReader.Result>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResults by rememberUpdatedState(onResults)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { dataMatrixReader() }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(AnalysisSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()
            val mainExecutor = ContextCompat.getMainExecutor(context)
            setImageAnalysisAnalyzer(executor) { proxy ->
                val results = proxy.use { reader.read(it) }
                if (results.isNotEmpty()) mainExecutor.execute { currentOnResults(results) }
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
                // Pinch to zoom and tap to focus are handled by the controller.
                this.controller = controller
            }
        }
    )
}
