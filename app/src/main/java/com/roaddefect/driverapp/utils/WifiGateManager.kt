package com.roaddefect.driverapp.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiGateStatus(
    val ssid: String = "Unknown",
    val rssi: Int = 0,
    val isOnTargetWifi: Boolean = false,
    val isStrongEnough: Boolean = false,
    val stableMs: Long = 0,
    val gatePassed: Boolean = false
)

class WifiGateManager(
    private val context: Context,
    private val targetSsid: String = "SUTD_Wifi",
    private val rssiThresholdDbm: Int = -90,
    private val requiredStableMs: Long = 10_000L,
    private val sampleIntervalMs: Long = 1_000L,
    private val allowedDipMs: Long = 3_000L
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _status = MutableStateFlow(WifiGateStatus())
    val status: StateFlow<WifiGateStatus> = _status.asStateFlow()

    private var goodStartAt: Long? = null
    private var lastGoodAt: Long? = null
    private var isMonitoring = false

    private fun cleanSsid(raw: String): String =
        raw.trim().removePrefix("\"").removeSuffix("\"")

    suspend fun startMonitoring() {
        _status.value = WifiGateStatus(gatePassed = true)

//        if (isMonitoring) return
//        isMonitoring = true
//
//        // Reset state
//        goodStartAt = null
//        lastGoodAt = null
//        _status.value = WifiGateStatus()

//        while (isMonitoring) {
//            val info = wifiManager.connectionInfo
//            val ssidRaw = info.ssid ?: "Unknown"
//            val rssi = info.rssi
//            val now = android.os.SystemClock.elapsedRealtime()
//
//            val ssidClean = cleanSsid(ssidRaw)
//            val onTargetWifi = ssidClean.equals(targetSsid, ignoreCase = true)
//            val strongEnough = rssi >= rssiThresholdDbm
//            val isGood = onTargetWifi && strongEnough
//
//            val currentStatus = _status.value
//
//            if (!currentStatus.gatePassed) {
//                if (isGood) {
//                    if (goodStartAt == null) goodStartAt = now
//                    lastGoodAt = now
//                    val stable = now - (goodStartAt ?: now)
//
//                    _status.value = currentStatus.copy(
//                        ssid = ssidClean,
//                        rssi = rssi,
//                        isOnTargetWifi = onTargetWifi,
//                        isStrongEnough = strongEnough,
//                        stableMs = stable,
//                        gatePassed = stable >= requiredStableMs
//                    )
//                } else {
//                    // Check if it's a brief dip or a real disconnect
//                    val last = lastGoodAt
//                    if (last == null || (now - last) > allowedDipMs) {
//                        // Reset
//                        goodStartAt = null
//                        lastGoodAt = null
//                        _status.value = WifiGateStatus(
//                            ssid = ssidClean,
//                            rssi = rssi,
//                            isOnTargetWifi = onTargetWifi,
//                            isStrongEnough = strongEnough
//                        )
//                    } else {
//                        // Just update display but keep progress
//                        _status.value = currentStatus.copy(
//                            ssid = ssidClean,
//                            rssi = rssi,
//                            isOnTargetWifi = onTargetWifi,
//                            isStrongEnough = strongEnough
//                        )
//                    }
//                }
//            } else {
//                // Gate already passed, just update info
//                _status.value = currentStatus.copy(
//                    ssid = ssidClean,
//                    rssi = rssi,
//                    isOnTargetWifi = onTargetWifi,
//                    isStrongEnough = strongEnough
//                )
//            }
//
//            delay(sampleIntervalMs)
//        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        goodStartAt = null
        lastGoodAt = null
    }

    fun reset() {
        goodStartAt = null
        lastGoodAt = null
        _status.value = WifiGateStatus()
    }
}
