package com.roaddefect.driverapp.models

data class SensorStatus(
    val camera: Boolean = false,
    val gps: Boolean = false,
    val imu: Boolean = false,
    val storage: Int = 0 // percentage 0-100
)
