package com.manima.foodlens.feature.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.designsystem.FoodLensTokens
import com.manima.foodlens.core.ui.MessageState
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun ScannerRoute(
    onLookup: (gtin14: String) -> Unit,
    onManualEntry: () -> Unit,
    onBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScannerEvent.Lookup -> onLookup(event.gtin14)
            }
        }
    }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    LaunchedEffect(Unit) {
        if (hasCamera && !granted) permission.launch(Manifest.permission.CAMERA)
    }
    val photoScanner = remember { newBarcodeScanner() }
    DisposableEffect(Unit) { onDispose { photoScanner.close() } }
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { viewModel.onPhotoScanned(detectInPhoto(context, photoScanner, uri)) }
    }
    val onPickPhoto = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    when {
        !hasCamera -> MessageState(
            title = stringResource(R.string.scan_no_camera_title),
            body = stringResource(R.string.scan_no_camera_body),
            primaryAction = stringResource(R.string.scan_enter_barcode) to onManualEntry,
            secondaryAction = stringResource(R.string.scan_from_photo) to onPickPhoto
        )
        !granted -> MessageState(
            title = stringResource(R.string.scan_permission_title),
            body = stringResource(R.string.scan_permission_body),
            primaryAction = if (denied) {
                stringResource(R.string.scan_open_settings) to { context.openAppSettings() }
            } else {
                stringResource(R.string.scan_allow_camera) to { permission.launch(Manifest.permission.CAMERA) }
            },
            secondaryAction = stringResource(R.string.scan_enter_barcode) to onManualEntry,
            modifier = Modifier.testTag("camera_permission_rationale")
        )
        else -> ScannerContent(
            state = state,
            onDetected = viewModel::onDetected,
            onToggleTorch = viewModel::toggleTorch,
            onManualEntry = onManualEntry,
            onPickPhoto = onPickPhoto,
            onBack = onBack
        )
    }
    // Re-check after returning from system Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = context.hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun ScannerContent(
    state: ScannerUiState,
    onDetected: (Detection) -> Boolean,
    onToggleTorch: () -> Unit,
    onManualEntry: () -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(torchOn = state.torchOn, onDetected = onDetected, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 280.dp, height = 180.dp)
                .border(BorderStroke(3.dp, FoodLensTokens.Orange), RoundedCornerShape(20.dp))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            FilledTonalButton(onClick = onToggleTorch, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (state.torchOn) R.string.scan_torch_off else R.string.scan_torch_on))
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
        ) {
            val hint = when (state.message) {
                ScanMessage.None -> stringResource(R.string.scan_hint)
                ScanMessage.InvalidBarcode -> stringResource(R.string.scan_invalid)
                ScanMessage.NoBarcodeInPhoto -> stringResource(R.string.scan_photo_none)
            }
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium) {
                Text(
                    hint,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).testTag("scan_hint")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onManualEntry, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(stringResource(R.string.scan_enter_barcode), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onPickPhoto,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.scan_from_photo))
                }
            }
        }
    }
}

/** CameraX preview + analysis bound to the lifecycle; released when the screen leaves (SPEC §10.4). */
@Composable
private fun CameraPreview(torchOn: Boolean, onDetected: (Detection) -> Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val currentOnDetected by rememberUpdatedState(onDetected)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val scanner = remember { newBarcodeScanner() }
    val stabilizer = remember { ScanStabilizer() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val tone = remember { Beeper(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)
        provider = cameraProvider
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(TARGET_WIDTH, TARGET_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(
            executor,
            BarcodeAnalyzer(scanner, stabilizer) { detection ->
                if (currentOnDetected(detection)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    tone.beep()
                }
            }
        )
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }
    LaunchedEffect(camera, torchOn) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(torchOn)
    }
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            scanner.close()
            executor.shutdown()
            tone.release()
        }
    }
    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        cam.cameraControl.setZoomRatio(current * zoom)
                    }
                }
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }
                }
        )
    }
}

/** Short confirmation tone, silent unless the ringer is in normal mode (SPEC §10.4). */
private class Beeper(context: Context) {
    private val audio = context.getSystemService<AudioManager>()
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
    } catch (_: RuntimeException) {
        null
    }

    fun beep() {
        if (audio?.ringerMode ==
            AudioManager.RINGER_MODE_NORMAL
        ) {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, DURATION_MS)
        }
    }

    fun release() {
        tone?.release()
    }

    private companion object {
        const val VOLUME = 60
        const val DURATION_MS = 80
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private const val TARGET_WIDTH = 1280
private const val TARGET_HEIGHT = 720
