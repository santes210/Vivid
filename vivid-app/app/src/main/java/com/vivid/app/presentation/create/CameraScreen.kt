package com.vivid.app.presentation.create

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vivid.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vivid.app.theme.VividSpace
import androidx.compose.animation.core.animateFloatAsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onPhotoTaken: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val hasCameraPermission = cameraPermissionState.status.isGranted

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing by remember { mutableStateOf(false) }
    val captureInteraction = remember { MutableInteractionSource() }
    val capturePressed by captureInteraction.collectIsPressedAsState()
    val captureScale by animateFloatAsState(if (capturePressed) 0.9f else 1f, label = "captureScale")

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(onPhotoTaken)
    }

    fun bindCamera() {
        val provider = cameraProvider ?: return
        val previewSurface = previewView ?: return
        val lifecycleOwner = context as? LifecycleOwner ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewSurface.surfaceProvider)
        }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                capture
            )
            imageCapture = capture
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(cameraProvider, previewView, lensFacing) {
        if (hasCameraPermission) {
            bindCamera()
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Se necesita permiso de cámara")
            Spacer(modifier = Modifier.height(VividSpace.m))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Dar permiso")
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    previewView = view
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener(
                        {
                            cameraProvider = providerFuture.get()
                            bindCamera()
                        },
                        ContextCompat.getMainExecutor(ctx)
                    )
                }
            },
            update = {
                previewView = it
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VividSpace.m),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
            }
            IconButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_switch_camera), tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = VividSpace.xxl)
        ) {
            // Anillo clásico de captura: 72dp, escala al presionar y muestra progreso
            // mientras CameraX termina de escribir la foto.
            Surface(
                onClick = {
                    if (isCapturing) return@Surface
                    isCapturing = true
                    val photoFile = File(
                        context.cacheDir,
                        "vivid_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                    )

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    val capture = imageCapture ?: run {
                        isCapturing = false
                        return@Surface
                    }
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                isCapturing = false
                                onPhotoTaken(Uri.fromFile(photoFile))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                exception.printStackTrace()
                            }
                        }
                    )
                },
                interactionSource = captureInteraction,
                modifier = Modifier.size(72.dp).graphicsLayer { scaleX = captureScale; scaleY = captureScale },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.White.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.primary,
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(modifier = Modifier.size(56.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary) {}
                    if (isCapturing) CircularProgressIndicator(modifier = Modifier.size(72.dp), strokeWidth = 3.dp, color = Color.White)
                }
            }
        }

        FloatingActionButton(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = VividSpace.xl, bottom = VividSpace.xxl),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_open_gallery))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = VividSpace.xl, bottom = 56.dp)
                .background(Color.Black.copy(alpha = 0.35f), shape = MaterialTheme.shapes.small)
                .padding(horizontal = VividSpace.s, vertical = 6.dp)
        ) {
            Text("Galería", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}
