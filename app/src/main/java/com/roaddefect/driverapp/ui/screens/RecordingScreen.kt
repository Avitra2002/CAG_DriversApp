package com.roaddefect.driverapp.ui.screens

import android.content.Intent
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.roaddefect.driverapp.AppViewModel
import com.roaddefect.driverapp.MainActivity
import com.roaddefect.driverapp.services.RecordingService
import com.roaddefect.driverapp.ui.theme.AppColors
import java.util.Locale

@Composable
fun RecordingScreen(viewModel: AppViewModel, activity: MainActivity) {
        val context = LocalContext.current

        val currentTrip by viewModel.currentTrip.collectAsState()
        val sensorStatus by viewModel.sensorStatus.collectAsState()
        val elapsedTime by viewModel.recordingElapsedTime.collectAsState()
        val distance by viewModel.recordingDistance.collectAsState()

        val trip = currentTrip ?: return

        // Track whether we're in preview mode or recording mode
        val isPreviewMode by viewModel.isPreviewMode.collectAsState()
        var previewView by remember { mutableStateOf<PreviewView?>(null) }

        // Setup camera preview when preview view is ready
        LaunchedEffect(previewView, isPreviewMode) {
                if (isPreviewMode && previewView != null) {
                        val service = viewModel.getRecordingService()
                        service?.getCameraManager()?.setupCameraWithPreview(
                                lifecycleOwner = activity,
                                previewView = previewView!!,
                                onCameraReady = {}
                        )
                }
        }

        val elapsedSeconds = (elapsedTime / 1000).toInt()
        val distanceKm = distance / 1000.0
        val currentSpeed = 0 // Could be calculated from GPS data

        Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(24.dp)
                                        .padding(bottom = 100.dp)
                ) {
                        // Header
                        Column {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by
                                        infiniteTransition.animateFloat(
                                                initialValue = 1f,
                                                targetValue = 0.3f,
                                                animationSpec =
                                                        infiniteRepeatable(
                                                                animation = tween(1000),
                                                                repeatMode = RepeatMode.Reverse
                                                        ),
                                                label = "alpha"
                                        )

                                Surface(
                                        color = if (isPreviewMode) AppColors.Secondary.copy(alpha = 0.8f) else AppColors.Error.copy(alpha = alpha),
                                        shape = RoundedCornerShape(12.dp)
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(AppColors.Light)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        text = if (isPreviewMode) "Preview" else "Recording",
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
                                Text(text = trip.routeId, color = AppColors.Muted, fontSize = 14.sp)
                        }

                        // ESP32 Warning Banner - shown when Bluetooth is not connected
                        if (!sensorStatus.bluetooth) {
                                Spacer(modifier = Modifier.height(16.dp))
                                ESP32WarningBanner()
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                colors =
                                        CardDefaults.cardColors(containerColor = AppColors.Surface),
                                shape = RoundedCornerShape(16.dp)
                        ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                        // Show preview or placeholder based on mode
                                        if (isPreviewMode) {
                                                // Camera preview
                                                AndroidView(
                                                        factory = { ctx ->
                                                                PreviewView(ctx).apply {
                                                                        layoutParams = ViewGroup.LayoutParams(
                                                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                                                ViewGroup.LayoutParams.MATCH_PARENT
                                                                        )
                                                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                                                        previewView = this
                                                                }
                                                        },
                                                        modifier = Modifier.fillMaxSize()
                                                )
                                        } else {
                                                // Placeholder for when recording in background
                                                Column(
                                                        modifier = Modifier.fillMaxSize(),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Videocam,
                                                                contentDescription = "Recording",
                                                                tint = AppColors.Muted,
                                                                modifier = Modifier.size(64.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                                text = "Recording in background",
                                                                color = AppColors.Muted,
                                                                fontSize = 14.sp
                                                        )
                                                }
                                        }

                                        // Overlay badges
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Surface(
                                                        color =
                                                                AppColors.Background.copy(
                                                                        alpha = 0.7f
                                                                ),
                                                        shape = RoundedCornerShape(8.dp)
                                                ) {
                                                        Text(
                                                                text = formatTime(elapsedSeconds),
                                                                color = AppColors.Light,
                                                                fontSize = 14.sp,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 12.dp,
                                                                                vertical = 6.dp
                                                                        )
                                                        )
                                                }
                                                Surface(
                                                        color =
                                                                AppColors.Background.copy(
                                                                        alpha = 0.7f
                                                                ),
                                                        shape = RoundedCornerShape(8.dp)
                                                ) {
                                                        Text(
                                                                text = "$currentSpeed km/h",
                                                                color = AppColors.Light,
                                                                fontSize = 14.sp,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 12.dp,
                                                                                vertical = 6.dp
                                                                        )
                                                        )
                                                }
                                        }

                                        // Recording indicator - only show when actually recording
                                        if (!isPreviewMode) {
                                                Surface(
                                                        modifier =
                                                                Modifier.align(Alignment.BottomStart)
                                                                        .padding(16.dp),
                                                        color = AppColors.Background.copy(alpha = 0.7f),
                                                        shape = RoundedCornerShape(8.dp)
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 12.dp,
                                                                                vertical = 6.dp
                                                                        ),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(12.dp)
                                                                                        .clip(CircleShape)
                                                                                        .background(
                                                                                                AppColors
                                                                                                        .Error
                                                                                        )
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
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Sensor Indicators
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                SensorCard("Cam", sensorStatus.camera, Modifier.weight(1f))
                                SensorCard("GPS", sensorStatus.gps, Modifier.weight(1f))
                                SensorCard("IMU", sensorStatus.imu, Modifier.weight(1f))
                                SensorCard("ESP32", sensorStatus.bluetooth, Modifier.weight(1f))
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

                // Bottom Button - Start Recording or Complete Journey
                Column(
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
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
                                        if (isPreviewMode) {
                                                // Start actual recording
                                                // 1. Unbind preview from camera
                                                val service = viewModel.getRecordingService()
                                                service?.getCameraManager()?.unbindPreview()

                                                // 2. Start camera recording (video only)
                                                val intent =
                                                        Intent(context, RecordingService::class.java)
                                                                .apply {
                                                                        action =
                                                                                RecordingService
                                                                                        .ACTION_START_RECORDING
                                                                }
                                                context.startService(intent)

                                                // 3. Switch to recording mode
                                                viewModel.setIsPreviewMode(false)
                                        } else {
                                                // Stop recording service (also stops camera)
                                                val intent =
                                                        Intent(context, RecordingService::class.java)
                                                                .apply {
                                                                        action =
                                                                                RecordingService
                                                                                        .ACTION_STOP_RECORDING
                                                                }
                                                context.startService(intent)

                                                // Switch back to preview mode
                                                viewModel.setIsPreviewMode(true)

                                                // Complete journey in ViewModel
                                                viewModel.completeJourney()
                                        }
                                },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = if (isPreviewMode) AppColors.Success else AppColors.Error
                                        ),
                                shape = RoundedCornerShape(16.dp)
                        ) {
                                Icon(
                                        imageVector = if (isPreviewMode) Icons.Default.PlayArrow else Icons.Default.Stop,
                                        contentDescription = if (isPreviewMode) "Start Recording" else "Stop",
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = if (isPreviewMode) "Start Recording" else "Complete Journey",
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
                                        modifier =
                                                Modifier.size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (status) AppColors.Success
                                                                else AppColors.Error
                                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = name, color = AppColors.Muted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                                imageVector =
                                        if (status) Icons.Default.CheckCircle
                                        else Icons.Default.Error,
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
                                Text(text = title, color = AppColors.MutedStrong, fontSize = 16.sp)
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
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
}

@Composable
fun ESP32WarningBanner() {
        val infiniteTransition = rememberInfiniteTransition(label = "esp32_warning_pulse")
        val borderAlpha by
                infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(800),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "border_alpha"
                )

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .border(
                                        width = 2.dp,
                                        color = AppColors.AccentGold.copy(alpha = borderAlpha),
                                        shape = RoundedCornerShape(12.dp)
                                ),
                colors =
                        CardDefaults.cardColors(
                                containerColor = Color(0xFF3D2800) // Dark orange/amber background
                        ),
                shape = RoundedCornerShape(12.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Warning icon with pulsing background
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        AppColors.AccentGold.copy(alpha = 0.2f)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.BluetoothDisabled,
                                        contentDescription = "ESP32 Disconnected",
                                        tint = AppColors.AccentGold,
                                        modifier = Modifier.size(28.dp)
                                )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = "ESP32 NOT CONNECTED",
                                        color = AppColors.AccentGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text =
                                                "External sensor data will not be recorded for this trip",
                                        color = AppColors.AccentGold.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                )
                        }
                }
        }
}
