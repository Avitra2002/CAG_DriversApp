package com.roaddefect.driverapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GPSData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val accuracy: Float,
    val timestamp: Long
)

data class GPSStatus(
    val isAvailable: Boolean = false,
    val hasPermission: Boolean = false,
    val isTracking: Boolean = false,
    val currentLocation: GPSData? = null,
    val totalDistance: Double = 0.0
)

class GPSTracker(private val context: Context) {
    private val _status = MutableStateFlow(GPSStatus())
    val status: StateFlow<GPSStatus> = _status.asStateFlow()

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    private var gpsLogFile: File? = null
    private var fileWriter: FileWriter? = null
    private var gpxFile: File? = null
    private var gpxWriter: FileWriter? = null
    private var lastLocation: Location? = null
    private var totalDistance: Double = 0.0

    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun checkGPSAvailability(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _status.value = _status.value.copy(
            hasPermission = hasPermission,
            isAvailable = hasPermission
        )

        return hasPermission
    }

    fun startTracking(outputFile: File, gpxOutputFile: File? = null) {
        if (!checkGPSAvailability()) {
            Log.e("GPSTracker", "GPS not available or no permission")
            return
        }

        gpsLogFile = outputFile
        gpxFile = gpxOutputFile
        totalDistance = 0.0
        lastLocation = null

        try {
            fileWriter = FileWriter(outputFile, true)
            // Write CSV header
            fileWriter?.write("timestamp,latitude,longitude,altitude,speed,accuracy\n")
            fileWriter?.flush()
        } catch (e: Exception) {
            Log.e("GPSTracker", "Failed to create GPS log file", e)
            return
        }

        if (gpxFile != null) {
            try {
                gpxWriter = FileWriter(gpxFile, true)
                gpxWriter?.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                gpxWriter?.write("<gpx version=\"1.1\" creator=\"CAG_DriversApp\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                gpxWriter?.write("<trk>\n")
                gpxWriter?.write("<trkseg>\n")
                gpxWriter?.flush()
            } catch (e: Exception) {
                Log.e("GPSTracker", "Failed to create GPX log file", e)
            }
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1_000L // Update every 1 second
        ).setMinUpdateIntervalMillis(500L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return

                // Calculate distance
                lastLocation?.let { last ->
                    val distance = last.distanceTo(loc).toDouble()
                    totalDistance += distance
                }
                lastLocation = loc

                val gpsData = GPSData(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude,
                    speed = loc.speed,
                    accuracy = loc.accuracy,
                    timestamp = System.currentTimeMillis()
                )

                _status.value = _status.value.copy(
                    currentLocation = gpsData,
                    totalDistance = totalDistance,
                    isTracking = true
                )

                // Log to file
                try {
                    val line = "${gpsData.timestamp},${gpsData.latitude},${gpsData.longitude}," +
                            "${gpsData.altitude},${gpsData.speed},${gpsData.accuracy}\n"
                    fileWriter?.write(line)
                    fileWriter?.flush()
                } catch (e: Exception) {
                    Log.e("GPSTracker", "Failed to write GPS data", e)
                }

                // Log to GPX
                if (gpxWriter != null) {
                    try {
                        val timeStr = iso8601Format.format(Date(gpsData.timestamp))
                        val line = "<trkpt lat=\"${gpsData.latitude}\" lon=\"${gpsData.longitude}\">" +
                                "<ele>${gpsData.altitude}</ele>" +
                                "<time>$timeStr</time>" +
                                "</trkpt>\n"
                        gpxWriter?.write(line)
                        gpxWriter?.flush()
                    } catch (e: Exception) {
                        Log.e("GPSTracker", "Failed to write GPX data", e)
                    }
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.i("GPSTracker", "GPS tracking started")
        } catch (e: SecurityException) {
            Log.e("GPSTracker", "Security exception when requesting location updates", e)
        }
    }

    fun stopTracking() {
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
        }
        locationCallback = null

        try {
            fileWriter?.close()
        } catch (e: Exception) {
            Log.e("GPSTracker", "Failed to close GPS log file", e)
        }
        fileWriter = null

        try {
            gpxWriter?.write("</trkseg>\n")
            gpxWriter?.write("</trk>\n")
            gpxWriter?.write("</gpx>\n")
            gpxWriter?.close()
        } catch (e: Exception) {
            Log.e("GPSTracker", "Failed to close GPX log file", e)
        }
        gpxWriter = null

        _status.value = _status.value.copy(isTracking = false)
        Log.i("GPSTracker", "GPS tracking stopped. Total distance: $totalDistance meters")
    }

    fun reset() {
        totalDistance = 0.0
        lastLocation = null
        _status.value = GPSStatus(hasPermission = _status.value.hasPermission)
    }
}
