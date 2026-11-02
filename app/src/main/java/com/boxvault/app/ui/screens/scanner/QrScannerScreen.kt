package com.boxvault.app.ui.screens.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.boxvault.app.R
import com.boxvault.app.data.preferences.UserPreferencesManager
import com.boxvault.app.util.sensory.SensoryFeedbackManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.ui.components.findActivity
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: QrScannerViewModel,
    onNavigateBack: () -> Unit,
    onExistingBoxScanned: (String) -> Unit,
    onUnassignedQrScanned: (String) -> Unit = onExistingBoxScanned,
    sensoryFeedbackManager: SensoryFeedbackManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sensoryManager = remember(sensoryFeedbackManager, context) {
        sensoryFeedbackManager ?: SensoryFeedbackManager(context, UserPreferencesManager(context))
    }

    val activity = remember(context) { context.findActivity() }
    var hasRequestedOnce by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isRequestingPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequestingPermission = false
        hasRequestedOnce = true
        hasCameraPermission = granted
    }

    val isPermanentlyDenied = remember(hasCameraPermission, hasRequestedOnce) {
        val notGranted = !hasCameraPermission
        val cannotShowRationale = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.CAMERA
        )
        notGranted && (cannotShowRationale && hasRequestedOnce)
    }

    val openAppSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.resolution) {
        when (val resolution = uiState.resolution) {
            is QrScanResolution.ExistingBoxFound -> {
                sensoryManager.onScanSuccess(haptic)
                viewModel.consumeResolution()
                onExistingBoxScanned(resolution.boxId)
            }
            is QrScanResolution.UnassignedQrLabelScanned -> {
                sensoryManager.onScanSuccess(haptic)
                viewModel.consumeResolution()
                onUnassignedQrScanned(resolution.presetBoxId)
            }
            null -> Unit
        }
    }

    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.scanner_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.scanner_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (hasCameraPermission && activeCamera?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = {
                                val nextTorch = !uiState.isTorchEnabled
                                activeCamera?.cameraControl?.enableTorch(nextTorch)
                                viewModel.onTorchToggled(nextTorch)
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.isTorchEnabled) {
                                    Icons.Default.FlashOn
                                } else {
                                    Icons.Default.FlashOff
                                },
                                contentDescription = stringResource(R.string.scanner_toggle_torch),
                                tint = if (uiState.isTorchEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Camera Viewport or Permission Prompt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = Color.Black,
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraXQrPreview(
                        modifier = Modifier.fillMaxSize(),
                        onCameraBound = { camera -> activeCamera = camera },
                        onQrDetected = viewModel::onQrPayloadDetected
                    )
                    ScannerReticleOverlay(
                        primaryColor = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_reticle_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.scanner_camera_perm_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPermanentlyDenied) {
                                stringResource(R.string.photo_camera_perm_settings_desc)
                            } else {
                                stringResource(R.string.scanner_camera_perm_desc)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        if (isPermanentlyDenied) {
                            Button(
                                onClick = openAppSettings
                            ) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.photo_camera_perm_open_settings))
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (!isRequestingPermission) {
                                        isRequestingPermission = true
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.scanner_grant_perm_btn))
                            }
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Manual Short-Code / QR URI Fallback Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.scanner_manual_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.manualInput,
                            onValueChange = viewModel::onManualInputChange,
                            placeholder = { Text(stringResource(R.string.scanner_manual_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { viewModel.submitManualLookup() }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = viewModel::submitManualLookup,
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.scanner_manual_open_btn))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraXQrPreview(
    modifier: Modifier = Modifier,
    onCameraBound: (Camera) -> Unit,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val imageAnalysisRef = remember { AtomicReference<ImageAnalysis?>(null) }
    val isActive = remember { AtomicBoolean(true) }

    DisposableEffect(Unit) {
        onDispose {
            isActive.set(false)
            runCatching { imageAnalysisRef.get()?.clearAnalyzer() }
            runCatching { cameraProviderRef.get()?.unbindAll() }
            runCatching { analysisExecutor.shutdown() }
            runCatching { barcodeScanner.close() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    if (!isActive.get()) {
                        val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                        cameraProvider?.unbindAll()
                        return@addListener
                    }

                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                        ?: return@addListener
                    cameraProviderRef.set(cameraProvider)

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    @Suppress("DEPRECATION")
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    imageAnalysisRef.set(imageAnalysis)

                    if (!isActive.get()) {
                        imageAnalysis.clearAnalyzer()
                        cameraProvider.unbindAll()
                        return@addListener
                    }

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (!isActive.get() || analysisExecutor.isShutdown) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    if (!isActive.get()) return@addOnSuccessListener
                                    val firstQr = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                    val rawValue = firstQr?.rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        onQrDetected(rawValue)
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    runCatching {
                        if (!isActive.get()) {
                            imageAnalysis.clearAnalyzer()
                            cameraProvider.unbindAll()
                            return@addListener
                        }
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        if (isActive.get()) {
                            onCameraBound(camera)
                        } else {
                            cameraProvider.unbindAll()
                        }
                    }
                },
                ContextCompat.getMainExecutor(ctx)
            )

            previewView
        }
    )
}

@Composable
private fun ScannerReticleOverlay(
    primaryColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boxSide = size.minDimension * 0.62f
        val left = (size.width - boxSide) / 2f
        val top = (size.height - boxSide) / 2f

        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(left, top),
            size = ComposeSize(boxSide, boxSide),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
