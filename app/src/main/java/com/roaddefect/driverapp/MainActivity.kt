package com.roaddefect.driverapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.roaddefect.driverapp.models.AppView
import com.roaddefect.driverapp.ui.screens.*
import com.roaddefect.driverapp.ui.theme.DriverAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriverAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DriverApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun DriverApp(viewModel: AppViewModel) {
    val currentView by viewModel.currentView.collectAsState()
    val currentTrip by viewModel.currentTrip.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val sensorStatus by viewModel.sensorStatus.collectAsState()
    val vehicleId by viewModel.vehicleId.collectAsState()
    val isWifiConnected by viewModel.isWifiConnected.collectAsState()

    when (currentView) {
        AppView.DASHBOARD -> DashboardScreen(
            vehicleId = vehicleId,
            sensorStatus = sensorStatus,
            isWifiConnected = isWifiConnected,
            pendingUploads = viewModel.pendingUploads,
            onStartRecording = { viewModel.startRecording() },
            onNavigateToHealth = { viewModel.navigateToView(AppView.HEALTH) },
            onNavigateToHistory = { viewModel.navigateToView(AppView.HISTORY) },
            onNavigateToUploadQueue = { viewModel.navigateToView(AppView.UPLOAD_QUEUE) }
        )

        AppView.RECORDING -> currentTrip?.let { trip ->
            RecordingScreen(
                trip = trip,
                sensorStatus = sensorStatus,
                onCompleteJourney = { completedTrip ->
                    viewModel.completeJourney(completedTrip)
                }
            )
        }

        AppView.TRIP_SUMMARY -> currentTrip?.let { trip ->
            TripSummaryScreen(
                trip = trip,
                onReturnToDashboard = { viewModel.navigateToView(AppView.DASHBOARD) }
            )
        }

        AppView.HEALTH -> SystemHealthScreen(
            sensorStatus = sensorStatus,
            vehicleId = vehicleId,
            onBack = { viewModel.navigateToView(AppView.DASHBOARD) }
        )

        AppView.HISTORY -> TripHistoryScreen(
            trips = trips,
            onBack = { viewModel.navigateToView(AppView.DASHBOARD) }
        )

        AppView.UPLOAD_QUEUE -> UploadQueueScreen(
            trips = trips,
            isWifiConnected = isWifiConnected,
            onBack = { viewModel.navigateToView(AppView.DASHBOARD) },
            onUpdateTrip = { trip -> viewModel.updateTrip(trip) }
        )
    }
}
