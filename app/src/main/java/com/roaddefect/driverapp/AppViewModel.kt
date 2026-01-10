package com.roaddefect.driverapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roaddefect.driverapp.models.*
import com.roaddefect.driverapp.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _currentView = MutableStateFlow(AppView.DASHBOARD)
    val currentView: StateFlow<AppView> = _currentView.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentTrip = MutableStateFlow<Trip?>(null)
    val currentTrip: StateFlow<Trip?> = _currentTrip.asStateFlow()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _sensorStatus = MutableStateFlow(
        SensorStatus(
            camera = false,
            gps = false,
            imu = false,
            storage = 0
        )
    )
    val sensorStatus: StateFlow<SensorStatus> = _sensorStatus.asStateFlow()

    private val _vehicleId = MutableStateFlow("BUS-042")
    val vehicleId: StateFlow<String> = _vehicleId.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    // Sensor managers
    private val cameraManager = CameraManager(context)
    private val gpsTracker = GPSTracker(context)
    private val imuSensorManager = IMUSensorManager(context)

    // WiFi and Geofence managers
    val wifiGateManager = WifiGateManager(context)
    val geofenceManager = GeofenceManager(context)

    // Recording service connection
    private var recordingServiceBinder: com.roaddefect.driverapp.services.RecordingService? = null

    private val _recordingElapsedTime = MutableStateFlow(0L)
    val recordingElapsedTime: StateFlow<Long> = _recordingElapsedTime.asStateFlow()

    private val _recordingDistance = MutableStateFlow(0.0)
    val recordingDistance: StateFlow<Double> = _recordingDistance.asStateFlow()

    private val _tripDirectory = MutableStateFlow<File?>(null)
    val tripDirectory: StateFlow<File?> = _tripDirectory.asStateFlow()

    init {
        // Check sensor availability periodically
        updateSensorStatus()
        startSensorMonitoring()
    }

    private fun startSensorMonitoring() {
        viewModelScope.launch {
            while (true) {
                updateSensorStatus()
                delay(5000) // Check every 5 seconds
            }
        }
    }

    fun updateSensorStatus() {
        val cameraAvailable = cameraManager.checkCameraAvailability()
        val gpsAvailable = gpsTracker.checkGPSAvailability()
        val imuAvailable = imuSensorManager.checkIMUAvailability()
        val storagePercentage = FileManager.getAvailableStoragePercentage(context)

        _sensorStatus.value = SensorStatus(
            camera = cameraAvailable,
            gps = gpsAvailable,
            imu = imuAvailable,
            storage = storagePercentage
        )
    }

    fun bindRecordingService(service: com.roaddefect.driverapp.services.RecordingService) {
        recordingServiceBinder = service

        // Monitor recording service status
        viewModelScope.launch {
            service.status.collect { status ->
                _recordingElapsedTime.value = status.elapsedTimeMs
                _recordingDistance.value = status.distance
                _isRecording.value = status.isRecording
            }
        }
    }

    fun unbindRecordingService() {
        recordingServiceBinder = null
    }

    fun startRecording(tripId: String? = null) {
        _isRecording.value = true
        _currentView.value = AppView.RECORDING

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val now = Date()

        val finalTripId = tripId ?: "trip_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(now)}"

        val newTrip = Trip(
            id = finalTripId,
            date = dateFormat.format(now),
            time = timeFormat.format(now),
            duration = 0,
            distance = 0f,
            routeId = "ROUTE-A12",
            vehicleId = _vehicleId.value,
            coverage = 0,
            uploadStatus = UploadStatus.PENDING
        )
        _currentTrip.value = newTrip
    }

    fun completeJourney() {
        val trip = _currentTrip.value ?: return

        val elapsedSeconds = (_recordingElapsedTime.value / 1000).toInt()
        val distanceKm = (_recordingDistance.value / 1000).toFloat()

        val completedTrip = trip.copy(
            duration = elapsedSeconds,
            distance = distanceKm,
            uploadStatus = UploadStatus.PENDING
        )

        _isRecording.value = false
        _trips.value = listOf(completedTrip) + _trips.value
        _currentTrip.value = completedTrip
        _currentView.value = AppView.TRIP_SUMMARY
    }

    fun setTripDirectory(directory: File) {
        _tripDirectory.value = directory
    }

    fun navigateToView(view: AppView) {
        _currentView.value = view
        if (view == AppView.DASHBOARD) {
            _currentTrip.value = null
        }
    }

    fun updateTrip(trip: Trip) {
        _trips.value = _trips.value.map { if (it.id == trip.id) trip else it }
        // Also update currentTrip if it's the same trip
        if (_currentTrip.value?.id == trip.id) {
            _currentTrip.value = trip
        }
    }

    fun getCameraManager() = cameraManager

    val pendingUploads: Int
        get() = _trips.value.count { it.uploadStatus != UploadStatus.COMPLETED }

    override fun onCleared() {
        super.onCleared()
        wifiGateManager.stopMonitoring()
        geofenceManager.stopMonitoring()
    }
}
