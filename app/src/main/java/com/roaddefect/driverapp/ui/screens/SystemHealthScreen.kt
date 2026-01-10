package com.roaddefect.driverapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.roaddefect.driverapp.models.SensorStatus

@Composable
fun SystemHealthScreen(
    sensorStatus: SensorStatus,
    vehicleId: String,
    onBack: () -> Unit
) {
    val allSensorsOnline = sensorStatus.camera && sensorStatus.gps && sensorStatus.imu
    val systemHealthy = sensorStatus.storage > 20 && allSensorsOnline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
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
                text = "System Health",
                color = AppColors.Light,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Overall Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall System Status",
                            color = AppColors.MutedStrong,
                            fontSize = 16.sp
                        )
                        Surface(
                            color = if (systemHealthy) AppColors.Success else AppColors.AccentGold,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (systemHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = "Status",
                                    tint = AppColors.Light,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (systemHealthy) "Healthy" else "Warning",
                                    color = AppColors.Light,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (systemHealthy)
                            "All systems operational and ready for data collection"
                        else
                            "Some systems need attention before starting a trip",
                        color = AppColors.Muted,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sensors Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Sensors & Devices",
                        color = AppColors.MutedStrong,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SensorStatusRow("Front Camera", sensorStatus.camera, Icons.Default.Camera)
                    Spacer(modifier = Modifier.height(12.dp))
                    SensorStatusRow("GPS Tracker", sensorStatus.gps, Icons.Default.LocationOn)
                    Spacer(modifier = Modifier.height(12.dp))
                    SensorStatusRow("IMU Sensors", sensorStatus.imu, Icons.Default.Sensors)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Storage Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Storage",
                            tint = AppColors.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Storage",
                            color = AppColors.MutedStrong,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Available Space",
                            color = AppColors.Muted,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${sensorStatus.storage}%",
                            color = AppColors.Light,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = sensorStatus.storage / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = AppColors.Secondary,
                        trackColor = AppColors.Background
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "~${(sensorStatus.storage * 1.28f).toInt()} GB free",
                        color = AppColors.Muted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = AppColors.Background,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Estimated trips remaining",
                                    color = AppColors.Muted,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${sensorStatus.storage / 15}",
                                    color = AppColors.Light,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Auto-cleanup policy",
                                    color = AppColors.Muted,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "FIFO",
                                    color = AppColors.Light,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Device",
                            tint = AppColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Device Information",
                            color = AppColors.MutedStrong,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailRow("Vehicle ID", vehicleId)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("App Version", "v2.4.1")
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Last Sync", "2 hours ago")
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("System Uptime", "4h 23m")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
