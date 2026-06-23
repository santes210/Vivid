package com.vivid.app.presentation.create

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de cámara con VideoCapture (CameraX).
 *
 * Reutiliza el patrón de tu `CameraScreen` existente pero
 * graba video MP4 en lugar de tomar fotos.
 *
 * Permisos: CAMERA + RECORD_AUDIO (ya están en tu Manifest).
 */
@SuppressLint("MissingPermission")
@Composable
fun CameraVideoScreen(
    navController: NavController,
    onVideoRecorded: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (!hasPermissions) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Vivid necesita cámara y micrófono para grabar Reels.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }) { Text("Conceder permisos") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { navController.popBackStack() }) { Text("Cancelar") }
        }
        return
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView!!.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD,
                        androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                videoCapture = capture
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    previewView = view
                    bindCamera()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón cerrar
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
        }

        // Botón cambiar cámara
        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                bindCamera()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Cameraswitch, contentDescription = "Cambiar cámara", tint = Color.White)
        }

        // Botón grabar (estilo IG: círculo grande, rojo cuando graba)
        FloatingActionButton(
            onClick = {
                if (isRecording) {
                    // Detener
                    @SuppressLint("MissingPermission")
                    videoCapture?.record?.stop()
                    isRecording = false
                } else {
                    // Iniciar
                    val name = "vivid_reel_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
                    val outFile = File(context.cacheDir, name)
                    val output = FileOutputOptions.Builder(outFile).build()

                    @SuppressLint("MissingPermission")
                    val recording = videoCapture?.record?.output?.prepareRecording(context, output)

                    // Con audio
                    recording?.withAudioEnabled()?.start(
                        ContextCompat.getMainExecutor(context)
                    ) { event ->
                        when (event) {
                            is VideoRecordEvent.Finalize -> {
                                if (event.hasError()) {
                                    // error
                                } else {
                                    onVideoRecorded(Uri.fromFile(outFile))
                                    navController.popBackStack()
                                }
                            }
                            is VideoRecordEvent.Start -> isRecording = true
                            else -> {}
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .size(80.dp),
            containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                contentDescription = if (isRecording) "Detener" else "Grabar",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        if (isRecording) {
            // Indicador REC rojo
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color.Black.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text("REC", color = Color.White)
            }
        }
    }
}
