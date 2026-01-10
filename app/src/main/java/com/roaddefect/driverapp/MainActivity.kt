package com.roaddefect.driverapp

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.roaddefect.driverapp.models.AppView
import com.roaddefect.driverapp.services.RecordingService
import com.roaddefect.driverapp.ui.screens.*
import com.roaddefect.driverapp.ui.theme.DriverAppTheme
import com.roaddefect.driverapp.utils.PermissionsManager

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var recordingService: RecordingService? = null
    private var isServiceBound = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Update sensor status after permissions are granted/denied
        viewModel.updateSensorStatus()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.RecordingBinder
            recordingService = binder.getService()
            isServiceBound = true
            recordingService?.let { viewModel.bindRecordingService(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isServiceBound = false
            viewModel.unbindRecordingService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions on startup
        if (!PermissionsManager.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionsManager.REQUIRED_PERMISSIONS)
        }

        setContent {
            DriverAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DriverApp(viewModel, this)
                }
            }
        }
    }

    fun bindToRecordingService() {
        val intent = Intent(this, RecordingService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    fun unbindFromRecordingService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindFromRecordingService()
    }
}

@Composable
fun DriverApp(viewModel: AppViewModel, activity: MainActivity) {
    val currentView by viewModel.currentView.collectAsState()
    val currentTrip by viewModel.currentTrip.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val sensorStatus by viewModel.sensorStatus.collectAsState()
    val vehicleId by viewModel.vehicleId.collectAsState()
    val isWifiConnected by viewModel.isWifiConnected.collectAsState()
    val tripSummarySource by viewModel.tripSummarySource.collectAsState()

    when (currentView) {
        AppView.DASHBOARD -> DashboardScreen(
            vehicleId = vehicleId,
            sensorStatus = sensorStatus,
            isWifiConnected = isWifiConnected,
            pendingUploads = viewModel.pendingUploads,
            onStartRecording = {
                activity.bindToRecordingService()
                viewModel.startRecording()
            },
            onNavigateToHealth = { viewModel.navigateToView(AppView.HEALTH) },
            onNavigateToHistory = { viewModel.navigateToView(AppView.HISTORY) },
            onNavigateToUploadQueue = { viewModel.navigateToView(AppView.UPLOAD_QUEUE) }
        )

        AppView.RECORDING -> currentTrip?.let { trip ->
            RecordingScreen(
                viewModel = viewModel,
                activity = activity
            )
        }

        AppView.TRIP_SUMMARY -> currentTrip?.let { trip ->
            TripSummaryScreen(
                viewModel = viewModel,
                activity = activity,
                sourceView = tripSummarySource,
                onNavigateToDashboard = {
                    viewModel.navigateToView(AppView.DASHBOARD)
                },
                onNavigateToQueue = {
                    viewModel.navigateToView(AppView.UPLOAD_QUEUE)
                }
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
            onUpdateTrip = { trip -> viewModel.updateTrip(trip) },
            onTripClick = { trip ->
                viewModel.setCurrentTrip(trip)
                viewModel.navigateToView(AppView.TRIP_SUMMARY)
            }
        )
    }
}
