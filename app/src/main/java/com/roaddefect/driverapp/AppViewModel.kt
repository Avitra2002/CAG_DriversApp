package com.roaddefect.driverapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roaddefect.driverapp.ble.BleConnectionState
import com.roaddefect.driverapp.ble.BleManager
import com.roaddefect.driverapp.models.*
import com.roaddefect.driverapp.utils.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.text.format

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
            bluetooth = false,
            storage = 0
        )
    )
    val sensorStatus: StateFlow<SensorStatus> = _sensorStatus.asStateFlow()

    private val _vehicleId = MutableStateFlow("BUS-042")
    val vehicleId: StateFlow<String> = _vehicleId.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _isPreviewMode = MutableStateFlow(true)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

    private val _tripSummarySource = MutableStateFlow(TripSummarySource.FROM_RECORDING)
    val tripSummarySource: StateFlow<TripSummarySource> = _tripSummarySource.asStateFlow()

    // Sensor managers
    private val cameraManager = CameraManager(context)
    private val gpsTracker = GPSTracker(context)
    private val imuSensorManager = IMUSensorManager(context)

    // WiFi and Geofence managers
    val wifiGateManager = WifiGateManager(context)
    val geofenceManager = GeofenceManager(context)

    // BLE Manager for ESP32 connection
    private val bleManager = BleManager(application)
    private var bleDataCollectionJob: Job? = null

    private val _bleConnectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val bleConnectionState: StateFlow<BleConnectionState> = _bleConnectionState.asStateFlow()

    // Recording service connection
    // TODO: clarify whether the recording service needed to be created in the MainActivity. Why not
    // just in this viewmodel?
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
        startWifiMonitoring()

        // Initialize BLE for ESP32 connection
        initializeBle()
    }

    private fun initializeBle() {
        bleManager.bind()
        viewModelScope.launch {
            delay(500) // Allow service to start
            bleManager.startConnection()
        }

        // Monitor BLE connection state
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _bleConnectionState.value = state
                updateSensorStatus()
            }
        }
    }

    private fun startSensorMonitoring() {
        viewModelScope.launch {
            while (true) {
                updateSensorStatus()
                delay(5000) // Check every 5 seconds
            }
        }
    }

    private fun startWifiMonitoring() {
        viewModelScope.launch {

            while (true) {
                try {
                    val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE)
                        as android.net.wifi.WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    val ssid = wifiInfo.ssid?.replace("\"", "") ?: ""

                    // Update isWifiConnected if connected to AndroidWifi
                    _isWifiConnected.value = (ssid == "AndroidWifi")
                } catch (e: Exception) {
                    _isWifiConnected.value = false
                }
                delay(2000) // Check every 2 seconds
            }
        }
    }

    fun updateSensorStatus() {
        val cameraAvailable = cameraManager.checkCameraAvailability()
        val gpsAvailable = gpsTracker.checkGPSAvailability()
        val imuAvailable = imuSensorManager.checkIMUAvailability()
        val storagePercentage = FileManager.getAvailableStoragePercentage(context)
        val bluetoothConnected = _bleConnectionState.value is BleConnectionState.Connected

        _sensorStatus.value = SensorStatus(
            camera = cameraAvailable,
            gps = gpsAvailable,
            imu = imuAvailable,
            bluetooth = bluetoothConnected,
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

    //TODO: Ugly to have fragmented startRecording logic here and in RecordingScreen.
    //TODO: Even uglier that startRecording logic is called in the MainActivity. Leaking logic into the UI.
    fun startRecording(tripId: Long? = null) {
        _isRecording.value = true
        _currentView.value = AppView.RECORDING

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val now = Date()

        val finalTripId = tripId ?: SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(now).toLong()

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

        // Start BLE data collection
        startBleDataCollection()
    }

    private fun startBleDataCollection() {
        bleDataCollectionJob?.cancel()
        bleDataCollectionJob = viewModelScope.launch {
            bleManager.incomingData.collect { packet ->
                recordingServiceBinder?.onBleData(packet)
            }
        }
    }

    private fun stopBleDataCollection() {
        bleDataCollectionJob?.cancel()
        bleDataCollectionJob = null
    }

    fun completeJourney() {
        val trip = _currentTrip.value ?: return

        // Stop BLE data collection
        stopBleDataCollection()

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
        _tripSummarySource.value = TripSummarySource.FROM_RECORDING
        _currentView.value = AppView.TRIP_SUMMARY
    }

    fun setCurrentTrip(trip: Trip) {
        _currentTrip.value = trip
        _tripSummarySource.value = TripSummarySource.FROM_QUEUE
    }

    fun setTripDirectory(directory: File) {
        _tripDirectory.value = directory
    }

    fun navigateToView(view: AppView) {
        _currentView.value = view
        if (view == AppView.DASHBOARD) {
            _currentTrip.value = null
            _tripSummarySource.value = TripSummarySource.FROM_RECORDING
        }
    }

    fun updateTrip(trip: Trip) {
        _trips.value = _trips.value.map { if (it.id == trip.id) trip else it }
        // Also update currentTrip if it's the same trip
        if (_currentTrip.value?.id == trip.id) {
            _currentTrip.value = trip
        }
    }

    fun getRecordingService(): com.roaddefect.driverapp.services.RecordingService? =
            recordingServiceBinder

    fun setIsPreviewMode(value: Boolean) {
        _isPreviewMode.value = value
    }

    val pendingUploads: Int
        get() = _trips.value.count { it.uploadStatus != UploadStatus.COMPLETED }

    override fun onCleared() {
        super.onCleared()
        stopBleDataCollection()
        bleManager.unbind()
        wifiGateManager.stopMonitoring()
        geofenceManager.stopMonitoring()
    }
}
