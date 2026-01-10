package com.roaddefect.driverapp.ui.screens

import android.content.Intent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.roaddefect.driverapp.AppViewModel
import com.roaddefect.driverapp.MainActivity
import com.roaddefect.driverapp.services.RecordingService
import com.roaddefect.driverapp.ui.theme.AppColors
import com.roaddefect.driverapp.utils.FileManager

@Composable
fun RecordingScreen(
    viewModel: AppViewModel,
    activity: MainActivity
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentTrip by viewModel.currentTrip.collectAsState()
    val sensorStatus by viewModel.sensorStatus.collectAsState()
    val elapsedTime by viewModel.recordingElapsedTime.collectAsState()
    val distance by viewModel.recordingDistance.collectAsState()

    val trip = currentTrip ?: return

    // Start recording service when screen appears
    LaunchedEffect(Unit) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
            putExtra(RecordingService.EXTRA_TRIP_ID, trip.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    // Setup camera preview
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(previewView) {
        val cameraManager = viewModel.getCameraManager()
        cameraManager.setupCamera(lifecycleOwner, previewView) {
            // Camera ready
            val videoFile = FileManager.getVideoFile(context, trip.id)
            cameraManager.startRecording(videoFile) {
                // Recording started
            }
        }
    }

    val elapsedSeconds = (elapsedTime / 1000).toInt()
    val distanceKm = distance / 1000.0
    val currentSpeed = 0 // Could be calculated from GPS data

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 100.dp)
        ) {
            // Header
            Column {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Surface(
                    color = AppColors.Error.copy(alpha = alpha),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AppColors.Light)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording",
                            color = AppColors.Light,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Trip ${trip.id}",
                    color = AppColors.Light,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = trip.routeId,
                    color = AppColors.Muted,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Camera Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Real camera preview
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = AppColors.Background.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = formatTime(elapsedSeconds),
                                color = AppColors.Light,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            color = AppColors.Background.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$currentSpeed km/h",
                                color = AppColors.Light,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Recording indicator
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        color = AppColors.Background.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.Error)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REC",
                                color = AppColors.Light,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sensor Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SensorCard("Camera", sensorStatus.camera, Modifier.weight(1f))
                SensorCard("GPS", sensorStatus.gps, Modifier.weight(1f))
                SensorCard("IMU", sensorStatus.imu, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trip Stats
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    title = "Duration",
                    value = formatTime(elapsedSeconds),
                    icon = Icons.Default.Timer,
                    iconTint = AppColors.Secondary
                )
                StatCard(
                    title = "Distance Covered",
                    value = "%.2f km".format(distanceKm),
                    icon = Icons.Default.Navigation,
                    iconTint = AppColors.Success
                )
            }
        }

        // Complete Journey Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AppColors.Background,
                            AppColors.Background
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Button(
                onClick = {
                    // Stop camera recording
                    viewModel.getCameraManager().stopRecording()

                    // Stop recording service
                    val intent = Intent(context, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_STOP_RECORDING
                    }
                    context.startService(intent)

                    // Complete journey in ViewModel
                    viewModel.completeJourney()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Complete Journey",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SensorCard(name: String, status: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (status) AppColors.Success else AppColors.Error)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = name,
                    color = AppColors.Muted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                imageVector = if (status) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = name,
                tint = if (status) AppColors.Success else AppColors.Error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = AppColors.MutedStrong,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = AppColors.Light,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatTime(seconds: Int): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}
