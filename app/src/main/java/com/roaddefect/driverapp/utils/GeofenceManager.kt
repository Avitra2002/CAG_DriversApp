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

data class GeofenceStatus(
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val distanceToCenter: Double? = null,
    val isInsideGeofence: Boolean = false,
    val hasPermission: Boolean = false
)

class GeofenceManager(
    private val context: Context,
    private val centerLat: Double = 13.7621,
    private val centerLng: Double = 100.5372,
    private val radiusMeters: Double = 500.0
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _status = MutableStateFlow(GeofenceStatus())
    val status: StateFlow<GeofenceStatus> = _status.asStateFlow()

    private var locationCallback: LocationCallback? = null

    fun startMonitoring() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _status.value = _status.value.copy(hasPermission = hasPermission)

        if (!hasPermission) {
            Log.w("GeofenceManager", "Location permission not granted")
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L // Update every 5 seconds
        ).setMinUpdateIntervalMillis(2_000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return

                val center = Location("center").apply {
                    latitude = centerLat
                    longitude = centerLng
                }

                val distance = loc.distanceTo(center).toDouble()
                val isInside = distance <= radiusMeters

                _status.value = GeofenceStatus(
                    currentLat = loc.latitude,
                    currentLng = loc.longitude,
                    distanceToCenter = distance,
                    isInsideGeofence = isInside,
                    hasPermission = true
                )
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("GeofenceManager", "Security exception when requesting location updates", e)
        }
    }

    fun stopMonitoring() {
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    fun reset() {
        _status.value = GeofenceStatus(hasPermission = _status.value.hasPermission)
    }
}
