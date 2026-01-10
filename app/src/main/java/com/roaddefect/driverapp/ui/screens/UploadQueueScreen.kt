package com.roaddefect.driverapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roaddefect.driverapp.models.Trip
import com.roaddefect.driverapp.models.UploadStatus
import com.roaddefect.driverapp.ui.theme.AppColors

@Composable
fun UploadQueueScreen(
    trips: List<Trip>,
    isWifiConnected: Boolean,
    onBack: () -> Unit,
    onUpdateTrip: (Trip) -> Unit,
    onTripClick: (Trip) -> Unit
) {
    val pendingTrips = trips.filter { it.uploadStatus == UploadStatus.PENDING }
    val uploadingTrips = trips.filter { it.uploadStatus == UploadStatus.UPLOADING }
    val completedTrips = trips.filter { it.uploadStatus == UploadStatus.COMPLETED }
    val failedTrips = trips.filter { it.uploadStatus == UploadStatus.FAILED }

    val estimatedTime = pendingTrips.size * 2.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = AppColors.Light
                    )
                }
                Text(
                    text = "Upload Queue",
                    color = AppColors.Light,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "WiFi",
                    tint = if (isWifiConnected) AppColors.Success else AppColors.Muted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (isWifiConnected) AppColors.Success else AppColors.Background,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isWifiConnected) "Connected" else "Offline",
                        color = AppColors.Light,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Queue Summary
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueStatCard("Pending", pendingTrips.size, Modifier.weight(1f))
                    QueueStatCard("Uploading", uploadingTrips.size, Modifier.weight(1f))
                    QueueStatCard("Complete", completedTrips.size, Modifier.weight(1f))
                    QueueStatCard("Failed", failedTrips.size, Modifier.weight(1f))
                }
            }

            // WiFi Alert
            if (!isWifiConnected && pendingTrips.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AppColors.AccentGold.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = "No WiFi",
                                tint = AppColors.AccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "No WiFi Connection",
                                    color = AppColors.Light,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${pendingTrips.size} trip${if (pendingTrips.size != 1) "s" else ""} waiting to upload. Connect to hub WiFi to begin automatic upload.",
                                    color = AppColors.Light.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                                if (estimatedTime > 0) {
                                    Text(
                                        text = "Estimated upload time: ~${estimatedTime.toInt()} minutes",
                                        color = AppColors.Light.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Currently Uploading
            if (uploadingTrips.isNotEmpty()) {
                item {
                    Text(
                        text = "Currently Uploading",
                        color = AppColors.Muted,
                        fontSize = 13.sp
                    )
                }
                items(uploadingTrips) { trip ->
                    UploadingTripCard(trip)
                }
            }

            // Pending Queue
            if (pendingTrips.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Queue",
                        color = AppColors.Muted,
                        fontSize = 13.sp
                    )
                }
                items(pendingTrips.withIndex().toList()) { (index, trip) ->
                    PendingTripCard(trip, index + 1, onClick = { onTripClick(trip) })
                }
            }

            // Failed Uploads
            if (failedTrips.isNotEmpty()) {
                item {
                    Text(
                        text = "Failed Uploads",
                        color = AppColors.Muted,
                        fontSize = 13.sp
                    )
                }
                items(failedTrips) { trip ->
                    FailedTripCard(trip) {
                        onUpdateTrip(trip.copy(uploadStatus = UploadStatus.UPLOADING, uploadProgress = 0))
                    }
                }
            }

            // Empty State
            if (trips.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "No uploads",
                            tint = AppColors.MutedStrong,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No uploads in queue",
                            color = AppColors.Muted,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Completed trips will appear here",
                            color = AppColors.Muted,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun QueueStatCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = AppColors.Muted,
                fontSize = 10.sp
            )
            Text(
                text = count.toString(),
                color = AppColors.Light,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun UploadingTripCard(trip: Trip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = trip.routeId,
                        color = AppColors.Light,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trip.date} • ${trip.time}",
                        color = AppColors.Muted,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = AppColors.Secondary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = AppColors.Light,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uploading", color = AppColors.Light, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = (trip.uploadProgress / 100f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AppColors.Secondary,
                trackColor = AppColors.Background
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${trip.uploadProgress}% complete",
                    color = AppColors.Muted,
                    fontSize = 11.sp
                )
                Text(
                    text = "~${(trip.duration / 60 * 45)} MB",
                    color = AppColors.Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PendingTripCard(trip: Trip, position: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = trip.routeId,
                        color = AppColors.Light,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trip.date} • ${trip.time}",
                        color = AppColors.Muted,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = AppColors.Background,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Queue #$position",
                        color = AppColors.Light,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = AppColors.Divider)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "%.1f km • ${trip.duration / 60} min".format(trip.distance),
                    color = AppColors.Muted,
                    fontSize = 11.sp
                )
                Text(
                    text = "~${(trip.duration / 60 * 45)} MB",
                    color = AppColors.Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun FailedTripCard(trip: Trip, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = trip.routeId,
                        color = AppColors.Light,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trip.date} • ${trip.time}",
                        color = AppColors.Muted,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = AppColors.Error,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = AppColors.Light,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Failed", color = AppColors.Light, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppColors.Background,
                    contentColor = AppColors.Light
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Upload", fontSize = 13.sp)
            }
        }
    }
}

