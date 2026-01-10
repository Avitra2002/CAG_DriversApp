package com.roaddefect.driverapp.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.roaddefect.driverapp.AppViewModel
import com.roaddefect.driverapp.MainActivity
import com.roaddefect.driverapp.models.UploadStatus
import com.roaddefect.driverapp.services.S3UploadService
import com.roaddefect.driverapp.ui.theme.AppColors
import com.roaddefect.driverapp.utils.FileManager
import kotlinx.coroutines.launch

@Composable
fun TripSummaryScreen(
    viewModel: AppViewModel,
    activity: MainActivity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentTrip by viewModel.currentTrip.collectAsState()
    val tripDirectory by viewModel.tripDirectory.collectAsState()

    val trip = currentTrip ?: return

    // WiFi and Geofence status
    val wifiGateStatus by viewModel.wifiGateManager.status.collectAsState()
    val geofenceStatus by viewModel.geofenceManager.status.collectAsState()

    val gatesReady = wifiGateStatus.gatePassed && geofenceStatus.isInsideGeofence
    var uploadTriggered by remember { mutableStateOf(false) }
    var uploadedFilesCount by remember { mutableStateOf(0) }
    val totalFilesToUpload = 3 // video, GPS, IMU

    // Broadcast receiver for upload completion
    DisposableEffect(trip.id) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                android.util.Log.i("TripSummaryScreen", "Broadcast received: ${intent?.action}")
                when (intent?.action) {
                    S3UploadService.ACTION_UPLOAD_COMPLETE -> {
                        val tripId = intent.getStringExtra(S3UploadService.EXTRA_TRIP_ID)
                        android.util.Log.i("TripSummaryScreen", "Received upload complete for trip: $tripId, current trip: ${trip.id}")
                        if (tripId == trip.id) {
                            uploadedFilesCount++
                            android.util.Log.i("TripSummaryScreen", "Upload complete: $uploadedFilesCount/$totalFilesToUpload")

                            // All files uploaded
                            if (uploadedFilesCount >= totalFilesToUpload) {
                                viewModel.updateTrip(trip.copy(uploadStatus = UploadStatus.COMPLETED))

                                uploadedFilesCount = 0
                            }
                        }
                    }
                    S3UploadService.ACTION_UPLOAD_FAILED -> {
                        val tripId = intent.getStringExtra(S3UploadService.EXTRA_TRIP_ID)
                        if (tripId == trip.id) {
                            android.util.Log.e("TripSummaryScreen", "Upload failed for trip $tripId")
                            viewModel.updateTrip(trip.copy(uploadStatus = UploadStatus.FAILED))
                            uploadTriggered = false
                            uploadedFilesCount = 0
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(S3UploadService.ACTION_UPLOAD_COMPLETE)
            addAction(S3UploadService.ACTION_UPLOAD_FAILED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Start monitoring WiFi and geofence when screen appears
    LaunchedEffect(Unit) {
        scope.launch {
            viewModel.geofenceManager.startMonitoring()
            viewModel.wifiGateManager.startMonitoring()
        }
    }

    // Stop service if user exits geofence
    LaunchedEffect(geofenceStatus.isInsideGeofence) {
        if (!geofenceStatus.isInsideGeofence && uploadTriggered) {
            val stopIntent = Intent(context, S3UploadService::class.java).apply {
                action = S3UploadService.ACTION_STOP
            }
            context.stopService(stopIntent)
            uploadTriggered = false
        }
    }

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
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(AppColors.Success, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = AppColors.Light,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Journey Complete!",
                color = AppColors.Light,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    trip.uploadStatus == UploadStatus.COMPLETED -> "Upload complete ✓"
                    uploadTriggered -> "Upload in progress..."
                    else -> "Ready to upload"
                },
                color = when {
                    trip.uploadStatus == UploadStatus.COMPLETED -> AppColors.Success
                    uploadTriggered -> AppColors.Secondary
                    else -> AppColors.Muted
                },
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Trip Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Trip Details",
                        color = AppColors.MutedStrong,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailRow("Trip ID", trip.id)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Vehicle", trip.vehicleId)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Route", trip.routeId)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Date & Time", "${trip.date} ${trip.time}")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Duration",
                                tint = AppColors.Secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Duration",
                                color = AppColors.Muted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatDuration(trip.duration),
                            color = AppColors.Light,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Distance",
                                tint = AppColors.Success,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Distance",
                                color = AppColors.Muted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "%.2f km".format(trip.distance),
                            color = AppColors.Light,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Geofence Gate Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (geofenceStatus.isInsideGeofence) AppColors.Success.copy(alpha = 0.12f)
                    else AppColors.Surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Geofence",
                            tint = if (geofenceStatus.isInsideGeofence) AppColors.Success else AppColors.Muted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Geofence Gate",
                                color = AppColors.Light,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            geofenceStatus.distanceToCenter?.let { distance ->
                                Text(
                                    text = "Distance: %.1f m".format(distance),
                                    color = AppColors.Muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (geofenceStatus.isInsideGeofence) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Passed",
                                tint = AppColors.Success,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // WiFi Gate Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (wifiGateStatus.gatePassed) AppColors.Success.copy(alpha = 0.12f)
                    else AppColors.Surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "WiFi",
                            tint = if (wifiGateStatus.gatePassed) AppColors.Success else AppColors.Muted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WiFi Gate",
                                color = AppColors.Light,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "SSID: ${wifiGateStatus.ssid} | ${wifiGateStatus.rssi} dBm",
                                color = AppColors.Muted,
                                fontSize = 12.sp
                            )
                            if (!wifiGateStatus.gatePassed && wifiGateStatus.isOnTargetWifi) {
                                Text(
                                    text = "Stable: ${formatMs(wifiGateStatus.stableMs)} / 10s",
                                    color = AppColors.Muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (wifiGateStatus.gatePassed) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Passed",
                                tint = AppColors.Success,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Upload in progress card
            if (trip.uploadStatus == UploadStatus.UPLOADING) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Secondary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AppColors.Secondary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Uploading files...",
                                color = AppColors.Light,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (uploadedFilesCount > 0) {
                                Text(
                                    text = "$uploadedFilesCount of $totalFilesToUpload files completed",
                                    color = AppColors.Muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Upload complete card
            if (trip.uploadStatus == UploadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Success.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = AppColors.Success,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Upload successful!",
                                color = AppColors.Light,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "All files uploaded to S3",
                                color = AppColors.Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Upload button (only shown when gates passed and not already uploading/completed)
            if (gatesReady && trip.uploadStatus == UploadStatus.PENDING) {
                Button(
                    onClick = {
                        uploadTriggered = true

                        // Upload all files from trip directory
                        val videoFile = FileManager.getVideoFile(context, trip.id)
                        val gpsFile = FileManager.getGPSFile(context, trip.id)
                        val imuFile = FileManager.getIMUFile(context, trip.id)

                        android.util.Log.i("TripSummaryScreen", "Upload button clicked!")
                        android.util.Log.i("TripSummaryScreen", "Video file exists: ${videoFile.exists()}")
                        android.util.Log.i("TripSummaryScreen", "GPS file exists: ${gpsFile.exists()}")
                        android.util.Log.i("TripSummaryScreen", "IMU file exists: ${imuFile.exists()}")

                        // Create empty GPS and IMU files if they don't exist
                        if (!gpsFile.exists()) {
                            try {
                                gpsFile.createNewFile()
                                gpsFile.writeText("timestamp,latitude,longitude,altitude,speed,accuracy\n")
                                android.util.Log.i("TripSummaryScreen", "Created empty GPS file")
                            } catch (e: Exception) {
                                android.util.Log.e("TripSummaryScreen", "Failed to create GPS file", e)
                            }
                        }

                        if (!imuFile.exists()) {
                            try {
                                imuFile.createNewFile()
                                imuFile.writeText("timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z\n")
                                android.util.Log.i("TripSummaryScreen", "Created empty IMU file")
                            } catch (e: Exception) {
                                android.util.Log.e("TripSummaryScreen", "Failed to create IMU file", e)
                            }
                        }

                        uploadedFilesCount = 0

                        // Upload video
                        if (videoFile.exists()) {
                            android.util.Log.i("TripSummaryScreen", "Starting video upload service...")
                            val videoIntent = Intent(context, S3UploadService::class.java).apply {
                                putExtra(S3UploadService.EXTRA_FILE_PATH, videoFile.absolutePath)
                                putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${trip.id}/video.mp4")
                                putExtra(S3UploadService.EXTRA_TRIP_ID, trip.id)
                            }
                            ContextCompat.startForegroundService(context, videoIntent)
                        }

                        // Upload GPS data (always exists now)
                        android.util.Log.i("TripSummaryScreen", "Starting GPS upload service...")
                        val gpsIntent = Intent(context, S3UploadService::class.java).apply {
                            putExtra(S3UploadService.EXTRA_FILE_PATH, gpsFile.absolutePath)
                            putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${trip.id}/gps_data.csv")
                            putExtra(S3UploadService.EXTRA_TRIP_ID, trip.id)
                        }
                        ContextCompat.startForegroundService(context, gpsIntent)

                        // Upload IMU data (always exists now)
                        android.util.Log.i("TripSummaryScreen", "Starting IMU upload service...")
                        val imuIntent = Intent(context, S3UploadService::class.java).apply {
                            putExtra(S3UploadService.EXTRA_FILE_PATH, imuFile.absolutePath)
                            putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${trip.id}/imu_data.csv")
                            putExtra(S3UploadService.EXTRA_TRIP_ID, trip.id)
                        }
                        ContextCompat.startForegroundService(context, imuIntent)

                        // Update trip status
                        viewModel.updateTrip(trip.copy(uploadStatus = UploadStatus.UPLOADING))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Upload to S3",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Return to Dashboard button
            OutlinedButton(
                onClick = {
                    viewModel.wifiGateManager.stopMonitoring()
                    viewModel.geofenceManager.stopMonitoring()
                    viewModel.navigateToView(com.roaddefect.driverapp.models.AppView.DASHBOARD)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.Light
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Return to Dashboard",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = AppColors.Muted,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = AppColors.Light,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatDuration(seconds: Int): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hrs > 0) {
        "%dh %dm".format(hrs, mins)
    } else {
        "%dm".format(mins)
    }
}

fun formatMs(ms: Long): String {
    val seconds = ms / 1000
    return "${seconds}s"
}
