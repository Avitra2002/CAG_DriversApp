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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roaddefect.driverapp.models.Trip
import com.roaddefect.driverapp.models.UploadStatus

@Composable
fun UploadQueueScreen(
    trips: List<Trip>,
    isWifiConnected: Boolean,
    onBack: () -> Unit,
    onUpdateTrip: (Trip) -> Unit
) {
    val pendingTrips = trips.filter { it.uploadStatus == UploadStatus.PENDING }
    val uploadingTrips = trips.filter { it.uploadStatus == UploadStatus.UPLOADING }
    val completedTrips = trips.filter { it.uploadStatus == UploadStatus.COMPLETED }
    val failedTrips = trips.filter { it.uploadStatus == UploadStatus.FAILED }

    val estimatedTime = pendingTrips.size * 2.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
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
                        tint = Color.White
                    )
                }
                Text(
                    text = "Upload Queue",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "WiFi",
                    tint = if (isWifiConnected) Color(0xFF10B981) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (isWifiConnected) Color(0xFF10B981) else Color(0xFF334155),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isWifiConnected) "Connected" else "Offline",
                        color = Color.White,
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
                            containerColor = Color(0xFF7C2D12).copy(alpha = 0.3f)
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
                                tint = Color(0xFFFB923C),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "No WiFi Connection",
                                    color = Color(0xFFFDBA74),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${pendingTrips.size} trip${if (pendingTrips.size != 1) "s" else ""} waiting to upload. Connect to hub WiFi to begin automatic upload.",
                                    color = Color(0xFFFED7AA).copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                                if (estimatedTime > 0) {
                                    Text(
                                        text = "Estimated upload time: ~${estimatedTime.toInt()} minutes",
                                        color = Color(0xFFFED7AA).copy(alpha = 0.7f),
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
                        color = Color(0xFF94A3B8),
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
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
                items(pendingTrips.withIndex().toList()) { (index, trip) ->
                    PendingTripCard(trip, index + 1)
                }
            }

            // Failed Uploads
            if (failedTrips.isNotEmpty()) {
                item {
                    Text(
                        text = "Failed Uploads",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
                items(failedTrips) { trip ->
                    FailedTripCard(trip) {
                        onUpdateTrip(trip.copy(uploadStatus = UploadStatus.UPLOADING, uploadProgress = 0))
                    }
                }
            }

            // Completed Uploads
            if (completedTrips.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Completed",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
                items(completedTrips.take(5)) { trip ->
                    CompletedTripCard(trip)
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
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No uploads in queue",
                            color = Color(0xFF94A3B8),
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Completed trips will appear here",
                            color = Color(0xFF64748B),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )
            Text(
                text = count.toString(),
                color = Color.White,
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
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
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trip.date} • ${trip.time}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = Color(0xFF3B82F6),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uploading", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = (trip.uploadProgress / 100f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = Color(0xFF3B82F6),
                trackColor = Color(0xFF334155)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${trip.uploadProgress}% complete",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Text(
                    text = "~${(trip.duration / 60 * 45)} MB",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PendingTripCard(trip: Trip, position: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
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
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trip.date} • ${trip.time}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Queue #$position",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "%.1f km • ${trip.duration / 60} min".format(trip.distance),
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Text(
                    text = "~${(trip.duration / 60 * 45)} MB",
                    color = Color(0xFF94A3B8),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
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
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trip.date} • ${trip.time}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = Color(0xFFDC2626),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Failed", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White
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

@Composable
fun CompletedTripCard(trip: Trip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = trip.routeId,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${trip.date} • ${trip.time}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
            Surface(
                color = Color(0xFF10B981),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Uploaded",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Uploaded", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}
