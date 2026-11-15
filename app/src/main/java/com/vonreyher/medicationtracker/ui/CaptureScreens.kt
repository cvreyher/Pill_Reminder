package com.vonreyher.medicationtracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.PackageCodeParser
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

/** Scans the PZN barcode or the securPharm code of a package and returns the PZN. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PackageBarcodeScreen(
    onPzn: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val currentOnPzn by rememberUpdatedState(onPzn)
    var hint by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    CaptureScaffold(title = "PZN scannen", onClose = onClose, camera = { torchOn ->
        BarcodeCameraPreview(
            torchOn = torchOn,
            formats = setOf(
                BarcodeReader.Format.PZN,
                BarcodeReader.Format.DATA_MATRIX,
                BarcodeReader.Format.CODE_39,
                BarcodeReader.Format.CODE_128,
                BarcodeReader.Format.EAN_13
            ),
            onResults = { results ->
                if (done) return@BarcodeCameraPreview
                val pzn = results.firstNotNullOfOrNull { result -> result.text?.let(PackageCodeParser::pznFrom) }
                if (pzn != null) {
                    done = true
                    currentOnPzn(pzn)
                } else {
                    hint = "Code erkannt, aber keine PZN gefunden. Versuche den Code mit „PZN“ oder den quadratischen Code."
                }
            }
        )
    }) {
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

/** Camera preview that reads barcodes of the given formats with ZXing-cpp and reports them on the main thread. */
@Composable
private fun BarcodeCameraPreview(
    torchOn: Boolean,
    formats: Set<BarcodeReader.Format>,
    onResults: (List<BarcodeReader.Result>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResults by rememberUpdatedState(onResults)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { BarcodeReader(BarcodeReader.Options(formats = formats, tryHarder = true, tryRotate = true)) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
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
                this.controller = controller
            }
        }
    )
}
