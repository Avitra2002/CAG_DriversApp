package com.roaddefect.driverapp.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.roaddefect.driverapp.services.TripApiService
import com.roaddefect.driverapp.ui.theme.AppColors
import com.roaddefect.driverapp.utils.FileManager
import kotlinx.coroutines.launch
import android.os.Build as AndroidBuild

@Composable
fun TripSummaryScreen(
    viewModel: AppViewModel,
    activity: MainActivity,
    sourceView: com.roaddefect.driverapp.models.TripSummarySource,
    onNavigateToDashboard: () -> Unit,
    onNavigateToQueue: () -> Unit
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
    val totalFilesToUpload = 6 // video, GPS, IMU

    // Broadcast receiver for upload completion
    DisposableEffect(trip.id) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                android.util.Log.i("TripSummaryScreen", "Broadcast received: ${intent?.action}")
                when (intent?.action) {
                    S3UploadService.ACTION_UPLOAD_COMPLETE -> {
                        val s3TripId = intent.getStringExtra(S3UploadService.EXTRA_TRIP_ID)
                        android.util.Log.i("TripSummaryScreen", "Received upload complete for s3tripId: $s3TripId, current trip: ${trip.id}")
                        uploadedFilesCount++
                        android.util.Log.i("TripSummaryScreen", "Upload complete: $uploadedFilesCount/$totalFilesToUpload")

                        // All files uploaded
                        if (uploadedFilesCount >= totalFilesToUpload) {
                            android.util.Log.i("TripSummaryScreen", "All files uploaded! Calling POST /trips/complete API...")

                            // Step 3: Call POST /trips/{trip_id}/complete API
                            scope.launch {
                                val apiService = TripApiService()

                                // Get file metadata for API call
                                val ctx = context ?: return@launch
                                val videoFile = FileManager.getVideoFile(ctx, trip.id)
                                val gpsFile = FileManager.getGPSGpxFile(ctx, trip.id)
                                val imuFile = FileManager.getIMUFile(ctx, trip.id)

                                // Count GPS and IMU points
                                val gpsPointCount = if (gpsFile.exists()) gpsFile.readLines().size - 1 else 0
                                val imuSampleCount = if (imuFile.exists()) imuFile.readLines().size - 1 else 0

                                val completeResult = apiService.completeTrip(
                                    tripId = s3TripId,
                                    videoKey = "trips/${s3TripId}/video.mp4",
                                    gpsKey = "trips/${s3TripId}/gps_data.xml",
                                    imuKey = "trips/${s3TripId}/imu_data.json",
                                    videoSize = videoFile.length(),
                                    videoDuration = (trip.duration / 1000).toInt(), // Convert ms to seconds
                                    gpsPointCount = gpsPointCount.coerceAtLeast(0),
                                    imuSampleCount = imuSampleCount.coerceAtLeast(0)
                                )

                                if (completeResult.isSuccess) {
                                    val response = completeResult.getOrNull()
                                    android.util.Log.i("TripSummaryScreen", "Trip completed via API: ${response?.message}")
                                    android.util.Log.i("TripSummaryScreen", "Step Functions execution ARN: ${response?.execution_arn}")
                                } else {
                                    android.util.Log.e("TripSummaryScreen", "Failed to complete trip via API: ${completeResult.exceptionOrNull()?.message}")
                                    // TODO: Show error to user - API call failed
                                }

                            viewModel.updateTrip(trip.copy(uploadStatus = UploadStatus.COMPLETED))

                            uploadedFilesCount = 0
                            }
                        }
                    }
                    S3UploadService.ACTION_UPLOAD_FAILED -> {
                        val tripId = intent.getStringExtra(S3UploadService.EXTRA_TRIP_ID)
                        if (tripId == trip.id.toString()) {
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
        }
//        else {
//            context.registerReceiver(receiver, filter)
//        }

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
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP SECTION: Gate statuses (most important) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Geofence Gate Status
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (geofenceStatus.isInsideGeofence) AppColors.Success.copy(alpha = 0.12f)
                        else AppColors.Surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Geofence",
                            tint = if (geofenceStatus.isInsideGeofence) AppColors.Success else AppColors.Muted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Geofence Gate",
                                color = AppColors.Light,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            geofenceStatus.distanceToCenter?.let { distance ->
                                Text(
                                    text = "Distance: %.1f m".format(distance),
                                    color = AppColors.Muted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (geofenceStatus.isInsideGeofence) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Passed",
                                tint = AppColors.Success,
                                modifier = Modifier.size(22.dp)
                            )
                        } else if (geofenceStatus.distanceToCenter != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AppColors.Secondary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // WiFi Gate Status
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (wifiGateStatus.gatePassed) AppColors.Success.copy(alpha = 0.12f)
                        else AppColors.Surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "WiFi",
                            tint = if (wifiGateStatus.gatePassed) AppColors.Success else AppColors.Muted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WiFi Gate",
                                color = AppColors.Light,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "SSID: ${wifiGateStatus.ssid} | ${wifiGateStatus.rssi} dBm",
                                color = AppColors.Muted,
                                fontSize = 11.sp
                            )
                            if (!wifiGateStatus.gatePassed && wifiGateStatus.isOnTargetWifi) {
                                Text(
                                    text = "Stable: ${formatMs(wifiGateStatus.stableMs)} / 10s",
                                    color = AppColors.Muted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (wifiGateStatus.gatePassed) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Passed",
                                tint = AppColors.Success,
                                modifier = Modifier.size(22.dp)
                            )
                        } else if (wifiGateStatus.isOnTargetWifi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AppColors.Secondary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            // Upload Prerequisites hint
            if (trip.uploadStatus == UploadStatus.PENDING) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Both gates must pass before uploading",
                    color = AppColors.Muted,
                    fontSize = 11.sp
                )
            }

            // Upload status indicators (compact inline)
            if (trip.uploadStatus == UploadStatus.UPLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Secondary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AppColors.Secondary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (uploadedFilesCount > 0)
                                "Uploading… $uploadedFilesCount/$totalFilesToUpload"
                            else "Uploading files…",
                            color = AppColors.Light,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (trip.uploadStatus == UploadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Success.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = AppColors.Success,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Upload successful – all files on S3",
                            color = AppColors.Light,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // --- MIDDLE SECTION: Trip Details (3/5) + Stats (2/5) ---

//            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Trip Details Card – 3/5 width
                Card(
                    modifier = Modifier.weight(3f),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Trip Details",
                            color = AppColors.MutedStrong,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Trip ID", trip.id.toString())
                        Spacer(modifier = Modifier.height(4.dp))
                        DetailRow("Vehicle", trip.vehicleId)
                        Spacer(modifier = Modifier.height(4.dp))
                        DetailRow("Route", trip.routeId)
                        Spacer(modifier = Modifier.height(4.dp))
                        DetailRow("Date", "${trip.date} ${trip.time}")
                    }
                }

                // Duration + Distance cards – 2/5 width, stacked vertically
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Duration",
                                    tint = AppColors.Secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Duration",
                                    color = AppColors.Muted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatDuration(trip.duration),
                                color = AppColors.Light,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Distance",
                                    tint = AppColors.Success,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Distance",
                                    color = AppColors.Muted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.2f km".format(trip.distance),
                                color = AppColors.Light,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Action Buttons
        Row(
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Return button (text and action depend on source)
            OutlinedButton(
                onClick = {
                    viewModel.wifiGateManager.stopMonitoring()
                    viewModel.geofenceManager.stopMonitoring()
                    when (sourceView) {
                        com.roaddefect.driverapp.models.TripSummarySource.FROM_RECORDING -> onNavigateToDashboard()
                        com.roaddefect.driverapp.models.TripSummarySource.FROM_QUEUE -> onNavigateToQueue()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.Light
                )
            ) {
                Icon(
                    imageVector = when (sourceView) {
                        com.roaddefect.driverapp.models.TripSummarySource.FROM_RECORDING -> Icons.Default.Home
                        com.roaddefect.driverapp.models.TripSummarySource.FROM_QUEUE -> Icons.Default.ArrowBack
                    },
                    contentDescription = "Return",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (sourceView) {
                        com.roaddefect.driverapp.models.TripSummarySource.FROM_RECORDING -> "Return"
                        com.roaddefect.driverapp.models.TripSummarySource.FROM_QUEUE -> "Return"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Upload button (only shown when gates passed and not already uploading/completed)
            if (gatesReady && trip.uploadStatus == UploadStatus.PENDING) {
                Button(
                    onClick = {
                        uploadTriggered = true

                        // Launch coroutine to call API first, then upload files
                        scope.launch {
                            // Step 1: Call POST /trips/start API
                            android.util.Log.i("TripSummaryScreen", "Calling POST /trips/start API...")
                            val apiService = TripApiService()
                            val deviceInfo = "${AndroidBuild.MANUFACTURER} ${AndroidBuild.MODEL}, Android ${AndroidBuild.VERSION.RELEASE}"
                            val userId = "driver_${trip.vehicleId}" // Use vehicle ID as user identifier

                            val startResult = apiService.startTrip(userId, deviceInfo)

                            if (startResult.isFailure) {
                                android.util.Log.e("TripSummaryScreen", "Failed to start trip via API: ${startResult.exceptionOrNull()?.message}")
                                // TODO: Show error to user - API call failed, but we'll continue with upload for now
                            } else {
                                val apiResponse = startResult.getOrNull()
                                android.util.Log.i("TripSummaryScreen", "Trip started via API: trip_id=${apiResponse?.trip_id}")
                                android.util.Log.i("TripSummaryScreen", "Expected S3 keys: ${apiResponse?.expected_keys}")
                                // TODO: Store apiResponse.trip_id in trip model for later use in complete API call
                            }
                            val expectedKeys = startResult.getOrNull()?.expected_keys
                            val s3TripId = startResult.getOrNull()?.trip_id?.toString() ?: trip.id.toString()

                            // Step 2: Upload all files from trip directory
                            val videoFile = FileManager.getVideoFile(context, trip.id)
                            val gpsFile = FileManager.getGPSGpxFile(context, trip.id)
                            val imuFile = FileManager.getIMUFile(context, trip.id)
                            val gpsEsp32File = FileManager.getESP32GpsGpxFile(context, trip.id)
                            val imuEsp32File = FileManager.getESP32ImuFile(context, trip.id)
                            val metaFile = FileManager.getMetadataFile(context, trip.id)

                            android.util.Log.i("TripSummaryScreen", "Upload button clicked!")

                            android.util.Log.i("TripSummaryScreen", "Trip ID: ${trip.id}")
                            android.util.Log.i("TripSummaryScreen", "Video file path: ${videoFile.absolutePath}")
                            android.util.Log.i("TripSummaryScreen", "GPS file path: ${gpsFile.absolutePath}")

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

                            if(!imuEsp32File.exists()) {
                                try {
                                    imuEsp32File.createNewFile()
                                    imuEsp32File.writeText("timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z\n")
                                    android.util.Log.i("TripSummaryScreen", "Created empty ESP32 IMU file")
                                } catch (e: Exception) {
                                    android.util.Log.e("TripSummaryScreen", "Failed to create ESP32 IMU file", e)
                                }
                            }

                            if(!gpsEsp32File.exists()) {
                                try {
                                    gpsEsp32File.createNewFile()
                                    imuEsp32File.writeText("timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z\n")
                                    android.util.Log.i("TripSummaryScreen", "Created empty ESP32 GPX file")
                                } catch (e: Exception) {
                                    android.util.Log.e("TripSummaryScreen", "Failed to create ESP32 GPX file", e)
                                }
                            }

                            uploadedFilesCount = 0

                            // Upload video
                            if (videoFile.exists()) {
                                android.util.Log.i("TripSummaryScreen", "Starting video upload service...")
                                val videoIntent = Intent(context, S3UploadService::class.java).apply {
                                    putExtra(S3UploadService.EXTRA_FILE_PATH, videoFile.absolutePath)
//                                    putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${trip.id}/video.mp4")
                                    putExtra(S3UploadService.EXTRA_S3_KEY, expectedKeys?.video_key)
                                    putExtra(S3UploadService.EXTRA_TRIP_ID, s3TripId)
                                }
                                ContextCompat.startForegroundService(context, videoIntent)
                            }

                            // Upload GPS GPX data (always exists now)
                            android.util.Log.i("TripSummaryScreen", "Starting GPX upload service...")
                            val gpsIntent = Intent(context, S3UploadService::class.java).apply {
                                putExtra(S3UploadService.EXTRA_FILE_PATH, gpsFile.absolutePath)
//                                putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${trip.id}/gps_data.csv")
                                putExtra(S3UploadService.EXTRA_S3_KEY, expectedKeys?.gps_key)
                                putExtra(S3UploadService.EXTRA_TRIP_ID, s3TripId)
                            }
                            ContextCompat.startForegroundService(context, gpsIntent)

                            // Upload ESP32 GPS GPX data (always exists now)
                            android.util.Log.i("TripSummaryScreen", "Starting ESP32 GPX upload service...")
                            val gpsEsp32Intent = Intent(context, S3UploadService::class.java).apply {
                                putExtra(S3UploadService.EXTRA_FILE_PATH, gpsEsp32File.absolutePath)
//                                putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${trip.id}/esp32_gps.csv")
                                putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${s3TripId}/esp32_gps.gpx")
                                putExtra(S3UploadService.EXTRA_TRIP_ID, s3TripId)
                            }
                            ContextCompat.startForegroundService(context, gpsEsp32Intent)

                            // Upload IMU data (always exists now)
                            android.util.Log.i("TripSummaryScreen", "Starting IMU upload service...")
                            val imuIntent = Intent(context, S3UploadService::class.java).apply {
                                putExtra(S3UploadService.EXTRA_FILE_PATH, imuFile.absolutePath)
                                putExtra(S3UploadService.EXTRA_S3_KEY, expectedKeys?.imu_key)
                                putExtra(S3UploadService.EXTRA_TRIP_ID, s3TripId)
                            }
                            ContextCompat.startForegroundService(context, imuIntent)

                            // Upload ESP32 IMU data (always exists now)
                            android.util.Log.i("TripSummaryScreen", "Starting ESP32 IMU upload service...")
                            val imuEsp32Intent = Intent(context, S3UploadService::class.java).apply {
                                putExtra(S3UploadService.EXTRA_FILE_PATH, imuEsp32File.absolutePath)
                                putExtra(S3UploadService.EXTRA_S3_KEY, "trips/${s3TripId}/imu_esp32.csv")
                                putExtra(S3UploadService.EXTRA_TRIP_ID, s3TripId)
                            }
                            ContextCompat.startForegroundService(context, imuEsp32Intent)

                            // Upload trip_meta.json
                            if (metaFile.exists()) {
                                android.util.Log.i("TripSummaryScreen", "Starting trip_meta upload service...")
                                val metaIntent = Intent(context, S3UploadService::class.java).apply {
                                    putExtra(S3UploadService.EXTRA_FILE_PATH, metaFile.absolutePath)
                                    putExtra(S3UploadService.EXTRA_S3_KEY, "trips/$s3TripId/trip_meta.json")
                                    putExtra(S3UploadService.EXTRA_TRIP_ID, s3TripId)
                                }
                                ContextCompat.startForegroundService(context, metaIntent)
                            }

                            // Update trip status
                            viewModel.updateTrip(trip.copy(uploadStatus = UploadStatus.UPLOADING))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Upload",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (trip.uploadStatus != UploadStatus.PENDING) {
                // If it's uploading or completed, we might want to show a disabled button or just let the return button take full width.
                // For now, we'll let the return button take full width if the upload button is hidden.
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