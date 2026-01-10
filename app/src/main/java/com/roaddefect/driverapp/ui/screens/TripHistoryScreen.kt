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
import com.roaddefect.driverapp.ui.theme.AppColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roaddefect.driverapp.models.Trip
import com.roaddefect.driverapp.models.UploadStatus

@Composable
fun TripHistoryScreen(
    trips: List<Trip>,
    onBack: () -> Unit
) {
    val totalDistance = trips.sumOf { it.distance.toDouble() }.toFloat()
    val totalDuration = trips.sumOf { it.duration }
    val uploadedTrips = trips.count { it.uploadStatus == UploadStatus.COMPLETED }

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.Light
                )
            }
            Text(
                text = "Trip History",
                color = AppColors.Light,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Summary Stats
            if (trips.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Total Trips", trips.size.toString(), Modifier.weight(1f))
                    StatCard("Distance", "%.1f km".format(totalDistance), Modifier.weight(1f))
                    StatCard("Uploaded", "$uploadedTrips/${trips.size}", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Trip List
        if (trips.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "No trips",
                    tint = AppColors.MutedStrong,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No trips recorded yet",
                    color = AppColors.Muted,
                    fontSize = 16.sp
                )
                Text(
                    text = "Start your first recording from the dashboard",
                    color = AppColors.Muted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trips) { trip ->
                    TripCard(trip)
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun TripCard(trip: Trip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = trip.routeId,
                        color = AppColors.Light,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trip.date} • ${trip.time}",
                        color = AppColors.Muted,
                        fontSize = 12.sp
                    )
                }
                UploadStatusBadge(trip.uploadStatus)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TripStat(
                    icon = Icons.Default.Timer,
                    label = "Duration",
                    value = formatDuration(trip.duration),
                    modifier = Modifier.weight(1f)
                )
                TripStat(
                    icon = Icons.Default.Navigation,
                    label = "Distance",
                    value = "%.1f km".format(trip.distance),
                    modifier = Modifier.weight(1f)
                )
                TripStat(
                    icon = Icons.Default.LocationOn,
                    label = "Coverage",
                    value = "${trip.coverage}%",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = AppColors.Divider)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Trip ID: ${trip.id}",
                    color = AppColors.Muted,
                    fontSize = 11.sp
                )
                if (trip.uploadStatus == UploadStatus.UPLOADING) {
                    Text(
                        text = "${trip.uploadProgress}%",
                        color = AppColors.Secondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TripStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AppColors.Muted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = AppColors.Muted,
                fontSize = 11.sp
            )
        }
        Text(
            text = value,
            color = AppColors.Light,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun UploadStatusBadge(status: UploadStatus) {
    val (color, icon, text) = when (status) {
        UploadStatus.COMPLETED -> Triple(AppColors.Success, Icons.Default.CheckCircle, "Uploaded")
        UploadStatus.UPLOADING -> Triple(AppColors.Secondary, Icons.Default.CloudUpload, "Uploading")
        UploadStatus.FAILED -> Triple(AppColors.Error, Icons.Default.Error, "Failed")
        UploadStatus.PENDING -> Triple(AppColors.Background, Icons.Default.Upload, "Pending")
    }

    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = AppColors.Light,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = AppColors.Light,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = AppColors.Muted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = AppColors.Light,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
