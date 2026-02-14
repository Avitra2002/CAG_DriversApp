package com.roaddefect.driverapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.storage.s3.AWSS3StoragePlugin

class DriverApplication : Application() {
    companion object {
        private const val TRANSFER_CHANNEL_ID = "aws_transfer_channel"
        private const val TRANSFER_NOTIFICATION_ID = 3201
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSS3StoragePlugin())
            Amplify.configure(applicationContext)
            Log.i("AMPLIFY", "Amplify initialized")
        } catch (e: AmplifyException) {
            Log.e("AMPLIFY", "Failed to initialize Amplify", e)
        }

        startAwsTransferService()
    }

    private fun startAwsTransferService() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    TRANSFER_CHANNEL_ID,
                    "AWS Transfer Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                mgr.createNotificationChannel(channel)

                val notification = NotificationCompat.Builder(this, TRANSFER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Driver App Uploads")
                    .setContentText("Preparing background transfer service")
                    .setOngoing(true)
                    .build()

                val transferIntent = Intent(this, TransferService::class.java).apply {
                    putExtra(TransferService.INTENT_KEY_NOTIFICATION, notification)
                    putExtra(TransferService.INTENT_KEY_NOTIFICATION_ID, TRANSFER_NOTIFICATION_ID)
                    putExtra(TransferService.INTENT_KEY_REMOVE_NOTIFICATION, true)
                }
                ContextCompat.startForegroundService(this, transferIntent)
            } else {
                startService(Intent(this, TransferService::class.java))
            }
            TransferNetworkLossHandler.getInstance(applicationContext)
            Log.i("DriverApplication", "AWS TransferService initialized")
        } catch (e: Exception) {
            Log.e("DriverApplication", "Failed to initialize AWS TransferService", e)
        }
    }
}
