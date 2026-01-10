package com.roaddefect.driverapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.roaddefect.driverapp.MainActivity
import com.roaddefect.driverapp.R
import com.roaddefect.driverapp.utils.GPSTracker
import com.roaddefect.driverapp.utils.IMUSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingStatus(
    val isRecording: Boolean = false,
    val tripId: String = "",
    val elapsedTimeMs: Long = 0,
    val distance: Double = 0.0
)

class RecordingService : Service() {

    companion object {
        const val ACTION_START_RECORDING = "action_start_recording"
        const val ACTION_STOP_RECORDING = "action_stop_recording"
        const val EXTRA_TRIP_ID = "extra_trip_id"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIF_ID = 1002
    }

    private val binder = RecordingBinder()
    private lateinit var gpsTracker: GPSTracker
    private lateinit var imuSensorManager: IMUSensorManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null

    private val _status = MutableStateFlow(RecordingStatus())
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private var startTimeMs: Long = 0
    private var tripDirectory: File? = null

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onCreate() {
        super.onCreate()
        gpsTracker = GPSTracker(this)
        imuSensorManager = IMUSensorManager(this)
        ensureChannel()
        Log.i("RecordingService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: generateTripId()
                startRecording(tripId)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun generateTripId(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "trip_${dateFormat.format(Date())}"
    }

    fun startRecording(tripId: String) {
        if (_status.value.isRecording) {
            Log.w("RecordingService", "Already recording")
            return
        }

        startForeground(NOTIF_ID, buildNotif("Recording trip..."))

        // Create trip directory
        tripDirectory = File(getExternalFilesDir(null), tripId).apply {
            mkdirs()
        }

        startTimeMs = System.currentTimeMillis()

        // Start GPS tracking
        val gpsFile = File(tripDirectory, "gps_data.csv")
        gpsTracker.startTracking(gpsFile)

        // Start IMU recording
        val imuFile = File(tripDirectory, "imu_data.csv")
        imuSensorManager.startRecording(imuFile)

        // Start timer for elapsed time
        timerJob = serviceScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTimeMs
                val distance = gpsTracker.status.value.totalDistance

                _status.value = RecordingStatus(
                    isRecording = true,
                    tripId = tripId,
                    elapsedTimeMs = elapsed,
                    distance = distance
                )

                updateNotif("Recording: ${formatTime(elapsed)} | ${formatDistance(distance)}")
                delay(1000)
            }
        }

        Log.i("RecordingService", "Recording started for trip: $tripId")
    }

    fun stopRecording(): File? {
        if (!_status.value.isRecording) {
            Log.w("RecordingService", "Not currently recording")
            return null
        }

        timerJob?.cancel()
        timerJob = null

        gpsTracker.stopTracking()
        imuSensorManager.stopRecording()

        _status.value = _status.value.copy(isRecording = false)

        updateNotif("Recording completed")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i("RecordingService", "Recording stopped")

        return tripDirectory
    }

    fun getTripDirectory(): File? = tripDirectory

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
        gpsTracker.stopTracking()
        imuSensorManager.stopRecording()
        Log.i("RecordingService", "Service destroyed")
    }
}
