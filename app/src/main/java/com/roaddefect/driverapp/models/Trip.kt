package com.roaddefect.driverapp.models

data class Trip(
    val id: Long,
    val date: String,
    val time: String,
    val duration: Int, // in seconds
    val distance: Float, // in km
    val routeId: String,
    val vehicleId: String,
    val coverage: Int, // percentage 0-100
    val uploadStatus: UploadStatus,
    val uploadProgress: Int = 0 // percentage 0-100
)

enum class UploadStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}
