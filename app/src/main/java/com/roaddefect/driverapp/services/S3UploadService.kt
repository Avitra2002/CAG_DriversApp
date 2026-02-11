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
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.roaddefect.driverapp.R
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class S3UploadService : Service() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_S3_KEY = "extra_s3_key"
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val ACTION_STOP = "action_stop_upload"
        const val ACTION_UPLOAD_COMPLETE = "com.roaddefect.driverapp.UPLOAD_COMPLETE"
        const val ACTION_UPLOAD_FAILED = "com.roaddefect.driverapp.UPLOAD_FAILED"

        // AWS Config - Extracted from amplifyconfiguration.json
        private const val COGNITO_POOL_ID = "ap-southeast-1:a6eaaf29-5595-413c-93a0-a9bb66195ffc"
        private const val BUCKET_NAME = "road-safety-dashboard-dev-storag-rawbucket0c3ee094-06krq7xkrooi"
        private val REGION = Regions.AP_SOUTHEAST_1

        private const val CHANNEL_ID = "upload_channel"
        private const val NOTIF_ID = 1001
    }

    private data class UploadTask(
        val file: File,
        val s3Key: String,
        val tripId: Long
    )

    private val uploadQueue = ConcurrentLinkedQueue<UploadTask>()
    @Volatile private var isUploading = false
    private var transferUtility: TransferUtility? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("S3UploadService", "=== SERVICE CREATED ===")
        ensureChannel()
        startForeground(NOTIF_ID, buildNotif("Preparing upload service…"))

        initS3Client()
    }

    private fun initS3Client() {
        try {
            val credentialsProvider = CognitoCachingCredentialsProvider(
                applicationContext,
                COGNITO_POOL_ID,
                REGION
            )

            val s3Client = AmazonS3Client(credentialsProvider)
            s3Client.setRegion(Region.getRegion(REGION))

            // Attempt to build TransferUtility
            try {
                transferUtility = TransferUtility.builder()
                    .context(applicationContext)
                    .s3Client(s3Client)
                    .build()
            } catch (e: Exception) {
                if (e.message?.contains("awstransfer already exists") == true || e.cause?.message?.contains("awstransfer already exists") == true) {
                    Log.e("S3UploadService", "Database schema conflict detected. Deleting old transfer database and retrying.", e)
                    // Delete the old database to fix the schema conflict
                    applicationContext.deleteDatabase("awstransfer.db")

                    // Retry initialization
                    transferUtility = TransferUtility.builder()
                        .context(applicationContext)
                        .s3Client(s3Client)
                        .build()
                } else {
                    throw e
                }
            }

            Log.i("S3UploadService", "S3 Client and TransferUtility initialized")
        } catch (e: Exception) {
            Log.e("S3UploadService", "Error initializing S3 Client or TransferUtility", e)
            // If initialization fails, we cannot proceed with uploads.
            // Notifications or broadcasts should reflect this fatal error.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("S3UploadService", "=== onStartCommand called ===")

        if (intent?.action == ACTION_STOP) {
            Log.i("S3UploadService", "Stopping upload due to request")
            uploadQueue.clear()
            stopSelf()
            return START_NOT_STICKY
        }

        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        val s3Key = intent?.getStringExtra(EXTRA_S3_KEY)
        val tripId = intent?.getLongExtra(EXTRA_TRIP_ID, 0L) ?: 0L

        if (!filePath.isNullOrBlank() && !s3Key.isNullOrBlank() && tripId != 0L) {
            val file = File(filePath)
            if (file.exists()) {
                val task = UploadTask(file, s3Key, tripId)
                uploadQueue.add(task)
                Log.i("S3UploadService", "Added to queue: ${file.name}. Queue size: ${uploadQueue.size}")
                processNext()
            } else {
                Log.e("S3UploadService", "File not found: $filePath")
            }
        } else {
            Log.w("S3UploadService", "Invalid intent extras or missing data")
        }

        return START_STICKY
    }

    private fun processNext() {
        if (isUploading) return

        val task = uploadQueue.poll()
        if (task == null) {
            Log.i("S3UploadService", "Queue empty, all uploads finished.")
            updateNotif("All uploads complete")
            stopSelf()
            return
        }

        isUploading = true
        performUpload(task)
    }

    private fun performUpload(task: UploadTask) {
        val (file, key, tripId) = task
        Log.i("S3UploadService", "Starting upload: ${file.name} -> $key")
        updateNotif("Uploading ${file.name}…")

        val utility = transferUtility
        if (utility == null) {
             Log.e("S3UploadService", "TransferUtility is null, cannot upload")
             sendFailureBroadcast(tripId, key, "S3 Client not initialized")
             isUploading = false
             processNext()
             return
        }

        val observer = utility.upload(BUCKET_NAME, key, file)

        observer.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                Log.d("S3UploadService", "Transfer state changed: $state")
                if (state == TransferState.COMPLETED) {
                    Log.i("S3UploadService", "✅ Upload success: $key")
                    sendSuccessBroadcast(tripId, key)
                    isUploading = false
                    processNext()
                } else if (state == TransferState.FAILED) {
                    Log.e("S3UploadService", "❌ Upload failed state for $key")
                     // observer.cleanTransferListener()
                     // TransferUtility persists uploads in DB, we might need to rely on that or handle retries.
                     // For now, treat as fail.
                    sendFailureBroadcast(tripId, key, "Transfer State: FAILED")
                    isUploading = false
                    processNext()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                 // Optional: update notification progress
            }

            override fun onError(id: Int, ex: java.lang.Exception?) {
                Log.e("S3UploadService", "❌ Upload Exception: ${ex?.message}", ex)
                sendFailureBroadcast(tripId, key, ex?.message ?: "Unknown Error")
                isUploading = false
                processNext()
            }
        })
    }

    private fun sendSuccessBroadcast(tripId: Long, key: String) {
        val successIntent = Intent(ACTION_UPLOAD_COMPLETE).apply {
            setPackage(packageName)
            putExtra(EXTRA_TRIP_ID, tripId)
            putExtra(EXTRA_S3_KEY, key)
        }
        sendBroadcast(successIntent)
    }

    private fun sendFailureBroadcast(tripId: Long, key: String, error: String) {
        val failIntent = Intent(ACTION_UPLOAD_FAILED).apply {
            setPackage(packageName)
            putExtra(EXTRA_TRIP_ID, tripId)
            putExtra(EXTRA_S3_KEY, key)
            putExtra("error", error)
        }
        sendBroadcast(failIntent)
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


