package com.roaddefect.driverapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roaddefect.driverapp.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AppViewModel : ViewModel() {
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
            camera = true,
            gps = true,
            imu = true,
            storage = 78
        )
    )
    val sensorStatus: StateFlow<SensorStatus> = _sensorStatus.asStateFlow()

    private val _vehicleId = MutableStateFlow("BUS-042")
    val vehicleId: StateFlow<String> = _vehicleId.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    init {
        // Simulate WiFi connection check
        simulateWifiConnection()
    }

    private fun simulateWifiConnection() {
        viewModelScope.launch {
            while (true) {
                delay(60000) // Check every minute
                val now = Calendar.getInstance().get(Calendar.MINUTE)
                _isWifiConnected.value = now % 5 == 0
            }
        }
    }

    fun startRecording() {
        _isRecording.value = true
        _currentView.value = AppView.RECORDING

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val now = Date()

        val newTrip = Trip(
            id = "TRIP-${System.currentTimeMillis()}",
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

    fun completeJourney(completedTrip: Trip) {
        _isRecording.value = false
        _trips.value = listOf(completedTrip) + _trips.value
        _currentTrip.value = completedTrip
        _currentView.value = AppView.TRIP_SUMMARY
    }

    fun navigateToView(view: AppView) {
        _currentView.value = view
        if (view == AppView.DASHBOARD) {
            _currentTrip.value = null
        }
    }

    fun updateTrip(trip: Trip) {
        _trips.value = _trips.value.map { if (it.id == trip.id) trip else it }
    }

    fun updateSensorStatus(status: SensorStatus) {
        _sensorStatus.value = status
    }

    val pendingUploads: Int
        get() = _trips.value.count { it.uploadStatus != UploadStatus.COMPLETED }
}
