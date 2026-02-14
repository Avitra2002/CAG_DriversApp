package com.roaddefect.driverapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.roaddefect.driverapp.R
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
        private const val CONNECTION_TIMEOUT_MS = 60_000
        private const val SOCKET_TIMEOUT_MS = 300_000
        private const val MAX_S3_ERROR_RETRY = 5
        private const val MAX_UPLOAD_ATTEMPTS = 3
        private const val BASE_RETRY_DELAY_MS = 4_000L
        private const val PREFS_NAME = "s3_transfer_recovery"
        private const val META_PREFIX = "transfer_meta_"
    }

    private data class UploadTask(
        val file: File,
        val s3Key: String,
        val tripId: String?,
        val attempt: Int = 0
    )

    private val uploadQueue = ConcurrentLinkedQueue<UploadTask>()
    @Volatile private var isUploading = false
    private var transferUtility: TransferUtility? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private lateinit var transferPrefs: SharedPreferences
    private val attachedTransferIds = ConcurrentHashMap.newKeySet<Int>()
    private val activeTransferIds = ConcurrentHashMap.newKeySet<Int>()

    override fun onCreate() {
        super.onCreate()
        Log.i("S3UploadService", "=== SERVICE CREATED ===")
        transferPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ensureChannel()
        startForeground(NOTIF_ID, buildNotif("Preparing upload service…"))

        initS3Client()
        recoverPersistedTransfers()
    }

    private fun initS3Client() {
        try {
            TransferNetworkLossHandler.getInstance(applicationContext)
            val credentialsProvider = CognitoCachingCredentialsProvider(
                applicationContext,
                COGNITO_POOL_ID,
                REGION
            )

            val clientConfig = ClientConfiguration().apply {
                connectionTimeout = CONNECTION_TIMEOUT_MS
                socketTimeout = SOCKET_TIMEOUT_MS
                maxErrorRetry = MAX_S3_ERROR_RETRY
            }

            val s3Client = AmazonS3Client(credentialsProvider, clientConfig)
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
            pauseActiveTransfers()
            stopSelf()
            return START_NOT_STICKY
        }

        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        val s3Key = intent?.getStringExtra(EXTRA_S3_KEY)
        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID)

        if (!filePath.isNullOrBlank() && !s3Key.isNullOrBlank()) {
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
            if (activeTransferIds.isNotEmpty()) return
            Log.i("S3UploadService", "Queue empty, all uploads finished.")
            updateNotif("All uploads complete")
            stopSelf()
            return
        }

        isUploading = true
        performUpload(task)
    }

    private fun performUpload(task: UploadTask) {
        val (file, key, tripId, attempt) = task
        Log.i("S3UploadService", "Starting upload: ${file.name} -> $key")
        updateNotif("Uploading ${file.name}… (attempt ${attempt + 1}/$MAX_UPLOAD_ATTEMPTS)")

        val utility = transferUtility
        if (utility == null) {
             Log.e("S3UploadService", "TransferUtility is null, cannot upload")
             sendFailureBroadcast(tripId, key, "S3 Client not initialized")
             isUploading = false
             processNext()
             return
        }

        val existingTransferId = findExistingTransferId(task)
        if (existingTransferId != null) {
            Log.i("S3UploadService", "Found existing transferId=$existingTransferId for key=$key. Attaching/resuming.")
            attachAndMaybeResumeTransfer(existingTransferId, task)
            return
        }

        val observer = utility.upload(BUCKET_NAME, key, file)
        saveTransferMetadata(observer.id, task)
        attachTransferListener(observer, task, shouldResumeIfNeeded = false)
    }

    private fun handleUploadFailure(task: UploadTask, transferId: Int, reason: String) {
        activeTransferIds.remove(transferId)
        attachedTransferIds.remove(transferId)
        removeTransferMetadata(transferId)
        val nextAttempt = task.attempt + 1
        if (nextAttempt < MAX_UPLOAD_ATTEMPTS) {
            val delayMs = BASE_RETRY_DELAY_MS * (1L shl task.attempt)
            Log.w(
                "S3UploadService",
                "Upload failed for ${task.s3Key}: $reason. Retrying in ${delayMs}ms (attempt ${nextAttempt + 1}/$MAX_UPLOAD_ATTEMPTS)"
            )
            updateNotif("Retrying ${task.file.name} in ${delayMs / 1000}s…")
            isUploading = false
            retryHandler.postDelayed({
                uploadQueue.add(task.copy(attempt = nextAttempt))
                processNext()
            }, delayMs)
            processNext()
            return
        }

        Log.e("S3UploadService", "❌ Upload failed after $MAX_UPLOAD_ATTEMPTS attempts for ${task.s3Key}: $reason")
        sendFailureBroadcast(task.tripId, task.s3Key, reason)
        isUploading = false
        processNext()
    }

    private fun recoverPersistedTransfers() {
        val utility = transferUtility ?: return
        try {
            val observers = utility.getTransfersWithType(TransferType.UPLOAD)
            if (observers.isEmpty()) return

            var recovered = 0
            for (observer in observers) {
                val transferId = observer.id
                val stateName = observer.state?.name ?: "UNKNOWN"
                if (!isRecoverableState(stateName)) continue

                val task = readTransferMetadata(transferId) ?: continue
                recovered++
                attachTransferListener(observer, task, shouldResumeIfNeeded = true)
            }
            if (recovered > 0) {
                Log.i("S3UploadService", "Recovered $recovered persisted upload(s)")
                updateNotif("Recovered $recovered in-progress upload(s)…")
            }
        } catch (e: Exception) {
            Log.e("S3UploadService", "Failed to recover persisted transfers", e)
        }
    }

    private fun attachAndMaybeResumeTransfer(transferId: Int, task: UploadTask) {
        val utility = transferUtility ?: run {
            handleUploadFailure(task, transferId, "S3 Client not initialized")
            return
        }
        try {
            val observer = utility.getTransferById(transferId)
            if (observer == null) {
                removeTransferMetadata(transferId)
                performUpload(task)
                return
            }
            attachTransferListener(observer, task, shouldResumeIfNeeded = true)
        } catch (e: Exception) {
            Log.e("S3UploadService", "Failed to attach existing transfer=$transferId", e)
            handleUploadFailure(task, transferId, e.message ?: "Attach transfer failed")
        }
    }

    private fun attachTransferListener(
        observer: TransferObserver,
        task: UploadTask,
        shouldResumeIfNeeded: Boolean
    ) {
        val transferId = observer.id
        saveTransferMetadata(transferId, task)
        activeTransferIds.add(transferId)
        attachedTransferIds.add(transferId)
        val utility = transferUtility

        observer.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                Log.d("S3UploadService", "Transfer[$id] state changed: $state")
                when (state) {
                    TransferState.COMPLETED -> {
                        Log.i("S3UploadService", "✅ Upload success: ${task.s3Key}")
                        activeTransferIds.remove(id)
                        attachedTransferIds.remove(id)
                        removeTransferMetadata(id)
                        sendSuccessBroadcast(task.tripId, task.s3Key)
                        isUploading = false
                        processNext()
                    }
                    TransferState.FAILED -> {
                        handleUploadFailure(task, id, "Transfer State: FAILED")
                    }
                    else -> Unit
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                if (bytesTotal <= 0L) return
                val progress = ((bytesCurrent * 100) / bytesTotal).toInt()
                if (progress % 10 == 0) {
                    updateNotif("Uploading ${task.file.name}… $progress%")
                }
            }

            override fun onError(id: Int, ex: java.lang.Exception?) {
                Log.e("S3UploadService", "Upload Exception: ${ex?.message}", ex)
                handleUploadFailure(task, id, ex?.message ?: "Unknown Error")
            }
        })

        if (shouldResumeIfNeeded && shouldResumeState(observer.state?.name)) {
            try {
                utility?.resume(transferId)
                Log.i("S3UploadService", "Resumed transferId=$transferId for key=${task.s3Key}")
            } catch (e: Exception) {
                Log.e("S3UploadService", "Failed to resume transferId=$transferId", e)
                handleUploadFailure(task, transferId, e.message ?: "Resume failed")
            }
        }
    }

    private fun isRecoverableState(stateName: String): Boolean {
        return stateName == "WAITING" ||
            stateName == "IN_PROGRESS" ||
            stateName == "RESUMED_WAITING" ||
            stateName == "PAUSED" ||
            stateName == "FAILED" ||
            stateName == "WAITING_FOR_NETWORK"
    }

    private fun shouldResumeState(stateName: String?): Boolean {
        return stateName == "PAUSED" || stateName == "FAILED" || stateName == "WAITING_FOR_NETWORK"
    }

    private fun pauseActiveTransfers() {
        val utility = transferUtility ?: return
        for (transferId in activeTransferIds) {
            try {
                utility.pause(transferId)
            } catch (e: Exception) {
                Log.w("S3UploadService", "Failed to pause transferId=$transferId", e)
            }
        }
        isUploading = false
    }

    private fun findExistingTransferId(task: UploadTask): Int? {
        transferPrefs.all.keys
            .filter { it.startsWith(META_PREFIX) }
            .forEach { key ->
                val transferId = key.removePrefix(META_PREFIX).toIntOrNull() ?: return@forEach
                val meta = readTransferMetadata(transferId) ?: return@forEach
                if (meta.file.absolutePath == task.file.absolutePath &&
                    meta.s3Key == task.s3Key &&
                    meta.tripId == task.tripId
                ) {
                    return transferId
                }
            }
        return null
    }

    private fun saveTransferMetadata(transferId: Int, task: UploadTask) {
        val json = JSONObject().apply {
            put("filePath", task.file.absolutePath)
            put("s3Key", task.s3Key)
            put("tripId", task.tripId ?: "")
            put("attempt", task.attempt)
        }
        transferPrefs.edit().putString("$META_PREFIX$transferId", json.toString()).apply()
    }

    private fun readTransferMetadata(transferId: Int): UploadTask? {
        val raw = transferPrefs.getString("$META_PREFIX$transferId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            val filePath = json.optString("filePath")
            val s3Key = json.optString("s3Key")
            val tripIdRaw = json.optString("tripId")
            val attempt = json.optInt("attempt", 0)
            if (filePath.isBlank() || s3Key.isBlank()) return null
            UploadTask(
                file = File(filePath),
                s3Key = s3Key,
                tripId = tripIdRaw.ifBlank { null },
                attempt = attempt
            )
        } catch (e: Exception) {
            Log.e("S3UploadService", "Failed parsing transfer metadata for id=$transferId", e)
            null
        }
    }

    private fun removeTransferMetadata(transferId: Int) {
        transferPrefs.edit().remove("$META_PREFIX$transferId").apply()
    }

    private fun sendSuccessBroadcast(tripId: String?, key: String) {
        val successIntent = Intent(ACTION_UPLOAD_COMPLETE).apply {
            setPackage(packageName)
            putExtra(EXTRA_TRIP_ID, tripId)
            putExtra(EXTRA_S3_KEY, key)
        }
        sendBroadcast(successIntent)
    }

    private fun sendFailureBroadcast(tripId: String?, key: String, error: String) {
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
