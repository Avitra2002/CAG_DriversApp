package com.roaddefect.driverapp.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File

object FileManager {

    private const val TRIP_FOLDER_NAME = "trips"
    private const val VIDEO_FILE_NAME = "video.mp4"
    private const val GPS_FILE_NAME = "gps_data.csv"
    private const val IMU_FILE_NAME = "imu_data.csv"
    private const val METADATA_FILE_NAME = "metadata.json"
    private const val ESP32_GPS_FILE_NAME = "esp32_gps.csv"
    private const val ESP32_IMU_FILE_NAME = "esp32_imu.csv"
    private const val GPS_GPX_FILE_NAME = "gps_data.gpx"
    private const val ESP32_GPS_GPX_FILE_NAME = "esp32_gps.gpx"

    fun getTripDirectory(context: Context, tripId: Long): File {
        val tripDir = File(context.getExternalFilesDir(null), tripId.toString())
//        val tripDir = File(tripsDir, tripId)
        tripDir.mkdirs()
        return tripDir
    }

    fun getVideoFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, VIDEO_FILE_NAME)
    }

    fun getGPSFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, GPS_FILE_NAME)
    }

    fun getGPSGpxFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, GPS_GPX_FILE_NAME)
    }

    fun getIMUFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, IMU_FILE_NAME)
    }

    fun getMetadataFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, METADATA_FILE_NAME)
    }

    fun getESP32GpsFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, ESP32_GPS_FILE_NAME)
    }

    fun getESP32GpsGpxFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, ESP32_GPS_GPX_FILE_NAME)
    }

    fun getESP32ImuFile(context: Context, tripId: Long): File {
        val tripDir = getTripDirectory(context, tripId)
        return File(tripDir, ESP32_IMU_FILE_NAME)
    }

    fun getAllTrips(context: Context): List<File> {
        val tripsDir = File(context.getExternalFilesDir(null), TRIP_FOLDER_NAME)
        if (!tripsDir.exists()) return emptyList()

        return tripsDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteTripDirectory(tripDirectory: File): Boolean {
        return try {
            tripDirectory.deleteRecursively()
            Log.i("FileManager", "Deleted trip directory: ${tripDirectory.name}")
            true
        } catch (e: Exception) {
            Log.e("FileManager", "Failed to delete trip directory", e)
            false
        }
    }

    fun getTripSize(tripDirectory: File): Long {
        return tripDirectory.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    fun getAvailableStorageBytes(context: Context): Long {
        val path = context.getExternalFilesDir(null) ?: return 0
        val stat = StatFs(path.path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun getAvailableStoragePercentage(context: Context): Int {
        val path = context.getExternalFilesDir(null) ?: return 0
        val stat = StatFs(path.path)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong

        if (totalBytes == 0L) return 0
        return ((availableBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
    }

    fun hasEnoughStorageForRecording(context: Context, requiredMinutes: Int = 30): Boolean {
        // Estimate: ~100MB per minute of video + GPS + IMU data
        val requiredBytes = requiredMinutes * 100L * 1024L * 1024L
        val availableBytes = getAvailableStorageBytes(context)
        return availableBytes >= requiredBytes
    }

    fun getEstimatedTripsRemaining(context: Context, avgTripSizeBytes: Long = 3L * 1024L * 1024L * 1024L): Int {
        // Default average trip size: 3GB
        val availableBytes = getAvailableStorageBytes(context)
        return (availableBytes / avgTripSizeBytes).toInt()
    }

    fun cleanupOldestTrips(context: Context, count: Int) {
        val trips = getAllTrips(context)
        trips.sortedBy { it.lastModified() }
            .take(count)
            .forEach { deleteTripDirectory(it) }

        Log.i("FileManager", "Cleaned up $count old trips")
    }
}
