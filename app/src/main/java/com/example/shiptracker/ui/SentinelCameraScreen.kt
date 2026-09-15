package com.example.shiptracker.ui

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.shiptracker.ai.BoundingBox
import com.example.shiptracker.ai.SentinelAnalyzer
import com.example.shiptracker.ai.SentinelEngine
import com.example.shiptracker.ai.SpoofBusterEngine
import com.example.shiptracker.ai.SpoofBusterAlert
import com.example.shiptracker.data.ShipState
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelCameraScreen(
    ships: List<ShipState>,
    userLat: Double,
    userLon: Double,
    compassHeading: Float,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var liveHeading by remember { mutableStateOf(compassHeading) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    try {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        azimuth = (azimuth + 360) % 360
                        liveHeading = azimuth
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        rotationSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val sentinelEngine = remember { SentinelEngine(context) }
    var detectedBoxes by remember { mutableStateOf<List<BoundingBox>>(emptyList()) }
    var spoofAlerts by remember { mutableStateOf<List<SpoofBusterAlert>>(emptyList()) }

    DisposableEffect(Unit) {
        onDispose {
            sentinelEngine.shutdown()
        }
    }

    LaunchedEffect(detectedBoxes, liveHeading) {
        if (detectedBoxes.isNotEmpty() && ships.isNotEmpty()) {
            spoofAlerts = SpoofBusterEngine.evaluateAnomalies(
                boundingBoxes = detectedBoxes,
                compassHeading = liveHeading,
                cameraFov = 60.0f,
                userLat = userLat,
                userLon = userLon,
                activeShips = ships
            )
        } else {
            spoofAlerts = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sentinel NPU AI & Spoof-Buster", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            // 🚨 CRITICAL CONSTRAINT: .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(
                                cameraExecutor,
                                SentinelAnalyzer(sentinelEngine) { boxes ->
                                    detectedBoxes = boxes
                                }
                            )

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    for (box in detectedBoxes) {
                        val left = box.x1 * canvasWidth
                        val top = box.y1 * canvasHeight
                        val right = box.x2 * canvasWidth
                        val bottom = box.y2 * canvasHeight

                        val boxColor = if (spoofAlerts.isNotEmpty()) Color.Red else Color.Green

                        drawRect(
                            color = boxColor,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }

                if (spoofAlerts.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🚨 COVERT VESSEL ANOMALY DETECTED 🚨",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        spoofAlerts.forEach { alert ->
                            Text(
                                text = alert.warningMessage,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = Color.Green)
                        Text(
                            text = "NPU Accelerated • YOLOv8 Maritime • Heading: ${liveHeading.toInt()}°",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Camera permission is required for Project Sentinel.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Camera Permission")
                        }
                    }
                }
            }
        }
    }
}
