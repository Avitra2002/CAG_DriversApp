package com.roaddefect.driverapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.os.Build as AndroidBuild
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.roaddefect.driverapp.MainActivity
import com.roaddefect.driverapp.R
import com.roaddefect.driverapp.ble.BlePacket
import com.roaddefect.driverapp.ble.SensorSample
import com.roaddefect.driverapp.utils.CameraManager
import com.roaddefect.driverapp.utils.FileManager
import com.roaddefect.driverapp.utils.GPSTracker
import com.roaddefect.driverapp.utils.IMUSensorManager
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingStatus(
        val isRecording: Boolean = false,
        val tripId: String = "",
        val elapsedTimeMs: Long = 0,
        val distance: Double = 0.0,
        val isCameraRecording: Boolean = false
)

class RecordingService : LifecycleService() {

    companion object {
        const val ACTION_START_RECORDING = "action_start_camera_recording"
        const val ACTION_STOP_RECORDING = "action_stop_recording"
        const val EXTRA_TRIP_ID = "extra_trip_id"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIF_ID = 1002
    }

    private val binder = RecordingBinder()
    private lateinit var gpsTracker: GPSTracker
    private lateinit var imuSensorManager: IMUSensorManager
    private lateinit var cameraManager: CameraManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null

    private val _status = MutableStateFlow(RecordingStatus())
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private var startTimeMs: Long = 0

    // ESP32 sensor data collection
    private val esp32SensorSamples = mutableListOf<SensorSample>()

    private var wakeLock: PowerManager.WakeLock? = null

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onCreate() {
        super.onCreate()
        gpsTracker = GPSTracker(this)
        imuSensorManager = IMUSensorManager(this)
        cameraManager = CameraManager(this)
        ensureChannel()
        Log.i("RecordingService", "Recording Service created")
    }

    // Basically everytime you want this service to move to another state, you create an Intent and call startService()
    // multiple calls to startService() will just call onStartCommand() again with the new Intent
    // Won't create multiple instances of the service.
    // Feels like a hackjob, but whatever.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: generateTripId()
                startCameraRecording(tripId)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("RecordingService", "App closing, RecordingService is shutting down cleaning up...")
        stopRecording()
    }

    private fun generateTripId(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "trip_${dateFormat.format(Date())}"
    }

    /**
     * Start camera recording after preview has been unbound.
     * This should be called after the user clicks "Start Recording" in the UI.
     */
    fun startCameraRecording(tripId: String) {

        if (_status.value.isCameraRecording) {
            Log.w("RecordingService", "Camera already recording")
            return
        }

        startForeground(NOTIF_ID, buildNotif("Begin Recording."))

        acquireWakeLock()

        startTimeMs = System.currentTimeMillis()
        val metadataFile = FileManager.getMetadataFile(this, tripId)
        try {
            val metaJson = JSONObject().apply {
                put("video_start_epoch_ms", System.currentTimeMillis())
                put("device_info", JSONObject().apply {
                    put("model", "${AndroidBuild.MANUFACTURER} ${AndroidBuild.MODEL}")
                })
                put("app_version", applicationContext.packageManager
                    .getPackageInfo(applicationContext.packageName, 0).versionName ?: "1.0.0")
            }
            metadataFile.writeText(metaJson.toString(2))
            android.util.Log.i("TripSummaryScreen", "Created trip_meta.json")
        } catch (e: Exception) {
            android.util.Log.e("TripSummaryScreen", "Failed to create trip_meta.json", e)
        }


        // Start GPS tracking
        val gpsFile = FileManager.getGPSFile(this, tripId)
        val gpsGpxFile = FileManager.getGPSGpxFile(this, tripId)
        gpsTracker.startTracking(gpsFile, gpsGpxFile)

        // Start IMU recording
        val imuFile = FileManager.getIMUFile(this, tripId)
        imuSensorManager.startRecording(imuFile)

        _status.value =
            RecordingStatus(
                isRecording = true,
                tripId = tripId,
                elapsedTimeMs = 0,
                distance = 0.0,
                isCameraRecording = true
            )

        // Start timer for elapsed time
        timerJob =
            serviceScope.launch {
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    val distance = gpsTracker.status.value.totalDistance

                    _status.value =
                        _status.value.copy(elapsedTimeMs = elapsed, distance = distance)

                    updateNotif(
                        "Recording: ${formatTime(elapsed)} | ${formatDistance(distance)}"
                    )
                    delay(1000)
                }
            }

        // Setup camera (video only, no preview) and start recording
        cameraManager.setupCameraVideoOnly(this) {
            // Camera ready, start recording
            val videoFile = FileManager.getVideoFile(this, tripId)
            cameraManager.startRecording(videoFile) {
                Log.i("RecordingService", "Camera recording started")
            }
        }
    }

    /**
     * Called by AppViewModel to pass ESP32 BLE packets to the recording service.
     * Packets are parsed and stored for later CSV export.
     */
    fun onBleData(packet: BlePacket) {
        if (!_status.value.isRecording) return

        val samples = SensorSample.parseSamples(packet.data)
        synchronized(esp32SensorSamples) {
            esp32SensorSamples.addAll(samples)
        }
    }

    fun stopRecording() {
        if (!_status.value.isRecording) {
            Log.w("RecordingService", "Not currently recording")
        }

        timerJob?.cancel()
        timerJob = null

        // Stop camera recording
        cameraManager.stopRecording()
        _status.value = _status.value.copy(isCameraRecording = false)
        Log.i("RecordingService", "Camera recording stopped")

        gpsTracker.stopTracking()
        imuSensorManager.stopRecording()

        // Save ESP32 data if any was collected
        saveESP32Data()

        _status.value = _status.value.copy(isRecording = false)

        releaseWakeLock()

        updateNotif("Recording completed")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i("RecordingService", "Recording stopped")
    }

    private fun saveESP32Data() {
        val samples: List<SensorSample>
        synchronized(esp32SensorSamples) {
            if (esp32SensorSamples.isEmpty()) {
                Log.i("RecordingService", "No ESP32 data to save")
                return
            }
            samples = esp32SensorSamples.toList()
            esp32SensorSamples.clear()
        }

        // Save ESP32 GPS data
        try {
            val esp32GpsFile = FileManager.getESP32GpsFile(this, _status.value.tripId)
            FileWriter(esp32GpsFile).use { writer ->
                writer.write("timestamp,latitude,longitude,altitude\n")
                samples.forEach { sample ->
                    if (sample.hasValidGnss()) {
                        writer.write("${sample.timestampMs},${sample.latitude},${sample.longitude},${sample.altitude}\n")
                    }
                }
            }
            Log.i("RecordingService", "ESP32 GPS data saved: ${esp32GpsFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("RecordingService", "Failed to save ESP32 GPS data", e)
        }

        // Save ESP32 GPS data in GPX format
        try {
            val esp32GpsGpxFile = FileManager.getESP32GpsGpxFile(this, _status.value.tripId)
            FileWriter(esp32GpsGpxFile).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<gpx version=\"1.1\" creator=\"CAG_DriversApp\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                writer.write("<trk>\n")
                writer.write("<trkseg>\n")

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")

                samples.forEach { sample ->
                    if (sample.hasValidGnss()) {
                        val timeStr = dateFormat.format(Date(sample.timestampMs))
                        writer.write("<trkpt lat=\"${sample.latitude}\" lon=\"${sample.longitude}\">" +
                                "<ele>${sample.altitude}</ele>" +
                                "<time>$timeStr</time>" +
                                "</trkpt>\n")
                    }
                }

                writer.write("</trkseg>\n")
                writer.write("</trk>\n")
                writer.write("</gpx>\n")
            }
            Log.i("RecordingService", "ESP32 GPS GPX data saved: ${esp32GpsGpxFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("RecordingService", "Failed to save ESP32 GPS GPX data", e)
        }

        // Save ESP32 IMU data
        try {
            val esp32ImuFile = FileManager.getESP32ImuFile(this, _status.value.tripId)
            FileWriter(esp32ImuFile).use { writer ->
                writer.write("timestamp,ax,ay,az,gx,gy,gz\n")
                samples.forEach { sample ->
                    writer.write("${sample.timestampMs},${sample.ax},${sample.ay},${sample.az},${sample.gx},${sample.gy},${sample.gz}\n")
                }
            }
            Log.i("RecordingService", "ESP32 IMU data saved: ${esp32ImuFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("RecordingService", "Failed to save ESP32 IMU data", e)
        }
    }


    fun getCameraManager(): CameraManager = cameraManager

    fun checkCameraAvailability(): Boolean = cameraManager.checkCameraAvailability()

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            String.format(Locale.getDefault(), "%.0f m", meters)
        } else {
            String.format(Locale.getDefault(), "%.2f km", meters / 1000)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotif(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Driver App")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotif(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotif(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cameraManager.release()
        gpsTracker.stopTracking()
        imuSensorManager.stopRecording()
        Log.i("RecordingService", "Service destroyed")
    }

    private fun acquireWakeLock() {
        wakeLock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RecordingService::WakeLock")
                            .apply { acquire(60 * 60 * 1000L /*60 minutes*/) }
                }
        Log.i("RecordingService", "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        Log.i("RecordingService", "Wake lock released")
        wakeLock = null
    }
}
