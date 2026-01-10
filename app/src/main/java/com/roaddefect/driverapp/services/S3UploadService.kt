package com.roaddefect.driverapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.roaddefect.driverapp.R
import java.io.File

class S3UploadService : Service() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_S3_KEY = "extra_s3_key"
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val ACTION_STOP = "action_stop_upload"
        const val ACTION_UPLOAD_COMPLETE = "com.roaddefect.driverapp.UPLOAD_COMPLETE"
        const val ACTION_UPLOAD_FAILED = "com.roaddefect.driverapp.UPLOAD_FAILED"

        private const val CHANNEL_ID = "upload_channel"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("S3UploadService", "=== SERVICE CREATED ===")
        ensureChannel()
        startForeground(NOTIF_ID, buildNotif("Preparing upload…"))
        Log.i("S3UploadService", "Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("S3UploadService", "=== onStartCommand called ===")
        Log.i("S3UploadService", "Intent: $intent")

        if (intent?.action == ACTION_STOP) {
            Log.i("S3UploadService", "Stopping upload due to geofence exit")
            stopSelf()
            return START_NOT_STICKY
        }

        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        val s3Key = intent?.getStringExtra(EXTRA_S3_KEY)
        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID)

        Log.i("S3UploadService", "filePath: $filePath")
        Log.i("S3UploadService", "s3Key: $s3Key")
        Log.i("S3UploadService", "tripId: $tripId")

        if (filePath.isNullOrBlank() || s3Key.isNullOrBlank()) {
            Log.e("S3UploadService", "Missing filePath or s3Key")
            stopSelf()
            return START_NOT_STICKY
        }

        val file = File(filePath)
        Log.i("S3UploadService", "File exists: ${file.exists()}, path: ${file.absolutePath}")

        if (!file.exists()) {
            Log.e("S3UploadService", "File not found: $filePath")
            updateNotif("File not found")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i("S3UploadService", "Starting upload for file: ${file.name}, size: ${file.length()} bytes")
        uploadFile(file, s3Key, tripId)
        return START_NOT_STICKY
    }

    private fun uploadFile(file: File, key: String, tripId: String?) {
        Log.i("S3UploadService", "=== uploadFile called ===")
        Log.i("S3UploadService", "File: ${file.absolutePath}, Key: $key")
        updateNotif("Uploading ${file.name}…")

        try {
            val options = com.amplifyframework.storage.options.StorageUploadFileOptions.defaultInstance()
            Log.i("S3UploadService", "Calling Amplify.Storage.uploadFile...")

            com.amplifyframework.core.Amplify.Storage.uploadFile(
                key,
                file,
                options,
                { result ->
                    Log.i("S3UploadService", "✅ Upload success: ${result.key}")
                    updateNotif("Upload complete: ${file.name}")

                    // Send broadcast for upload completion
                    tripId?.let {
                        val successIntent = Intent(ACTION_UPLOAD_COMPLETE).apply {
                            setPackage(packageName)  // Make it an explicit broadcast
                            putExtra(EXTRA_TRIP_ID, it)
                            putExtra(EXTRA_S3_KEY, key)
                        }
                        sendBroadcast(successIntent)
                        Log.i("S3UploadService", "Broadcast sent: Upload complete for trip $it")
                    }

                    stopSelf()
                },
                { error ->
                    Log.e("S3UploadService", "❌ Upload failed: ${error.message}", error)
                    updateNotif("Upload failed: ${file.name}")

                    // Send broadcast for upload failure
                    tripId?.let {
                        val failIntent = Intent(ACTION_UPLOAD_FAILED).apply {
                            setPackage(packageName)  // Make it an explicit broadcast
                            putExtra(EXTRA_TRIP_ID, it)
                            putExtra(EXTRA_S3_KEY, key)
                            putExtra("error", error.message)
                        }
                        sendBroadcast(failIntent)
                        Log.i("S3UploadService", "Broadcast sent: Upload failed for trip $it")
                    }

                    stopSelf()
                }
            )
            Log.i("S3UploadService", "Amplify.Storage.uploadFile call completed")
        } catch (e: Exception) {
            Log.e("S3UploadService", "Exception in uploadFile", e)
            updateNotif("Upload error: ${e.message}")
            stopSelf()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Uploads",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Driver App Upload")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun updateNotif(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotif(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
