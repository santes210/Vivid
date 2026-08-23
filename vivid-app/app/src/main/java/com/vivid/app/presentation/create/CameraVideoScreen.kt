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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.vivid.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vivid.app.theme.VividSpace
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay

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
            modifier = Modifier.fillMaxSize().padding(VividSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Vivid necesita cámara y micrófono para grabar Reels.")
            Spacer(Modifier.height(VividSpace.m))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }) { Text("Conceder permisos") }
            Spacer(Modifier.height(VividSpace.xs))
            TextButton(onClick = { navController.popBackStack() }) { Text("Cancelar") }
        }
        return
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    val recordInteraction = remember { MutableInteractionSource() }
    val recordPressed by recordInteraction.collectIsPressedAsState()
    val recordScale by animateFloatAsState(if (recordPressed) 0.9f else 1f, label = "recordScale")

    LaunchedEffect(isRecording) {
        recordingSeconds = 0
        while (isRecording) {
            delay(1_000)
            recordingSeconds++
        }
    }

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
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
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
                .padding(VividSpace.m)
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
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
                .padding(VividSpace.m)
        ) {
            Icon(Icons.Default.Cameraswitch, contentDescription = stringResource(R.string.cd_switch_camera), tint = Color.White)
        }

        // Anillo de grabación: 72dp, responde a la presión y el progreso gira
        // continuamente mientras el Recorder está activo.
        Surface(
            onClick = {
                val capture = videoCapture ?: return@Surface
                if (isRecording) {
                    currentRecording?.stop()
                    currentRecording = null
                    isRecording = false
                } else {
                    val name = "vivid_reel_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
                    val outFile = File(context.cacheDir, name)
                    val output = FileOutputOptions.Builder(outFile).build()
                    currentRecording = capture.output
                        .prepareRecording(context, output)
                        .withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(context)) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> isRecording = true
                                is VideoRecordEvent.Finalize -> {
                                    isRecording = false
                                    currentRecording = null
                                    if (!event.hasError()) {
                                        onVideoRecorded(Uri.fromFile(outFile))
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }
                }
            },
            interactionSource = recordInteraction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .size(72.dp)
                .graphicsLayer { scaleX = recordScale; scaleY = recordScale },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            contentColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
            border = androidx.compose.foundation.BorderStroke(3.dp, Color.White)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(if (isRecording) 38.dp else 56.dp),
                    shape = if (isRecording) MaterialTheme.shapes.extraSmall else CircleShape,
                    color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                ) {}
                if (isRecording) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 3.dp,
                        color = Color.Red
                    )
                }
            }
        }

        if (isRecording) {
            // Indicador REC rojo
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = VividSpace.l)
                    .background(Color.Black.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = VividSpace.s, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(VividSpace.xs))
                Text("REC  ${recordingSeconds / 60}:${(recordingSeconds % 60).toString().padStart(2, '0')}", color = Color.White)
            }
        }
    }
}
