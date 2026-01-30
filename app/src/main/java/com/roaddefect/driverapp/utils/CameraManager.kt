package com.roaddefect.driverapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CameraStatus(
        val isAvailable: Boolean = false,
        val isRecording: Boolean = false,
        val hasPermission: Boolean = false,
        val recordingDurationMs: Long = 0
)

class CameraManager(private val context: Context) {
    private val _status = MutableStateFlow(CameraStatus())
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun checkCameraAvailability(): Boolean {
        val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED

        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        _status.value =
                _status.value.copy(
                        hasPermission = hasPermission,
                        isAvailable = hasCamera && hasPermission
                )

        return hasCamera && hasPermission
    }

    fun setupCamera(lifecycleOwner: LifecycleOwner, onCameraReady: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
                {
                    try {
                        cameraProvider = cameraProviderFuture.get()

                        val recorder =
                                Recorder.Builder()
                                        .setQualitySelector(QualitySelector.from(Quality.HD))
                                        .build()

                        videoCapture = VideoCapture.withOutput(recorder)

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider?.unbindAll()
                        cameraProvider?.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                videoCapture
                        )

                        _status.value = _status.value.copy(isAvailable = true)
                        onCameraReady()

                        Log.i("CameraManager", "Camera setup completed")
                    } catch (e: Exception) {
                        Log.e("CameraManager", "Camera setup failed", e)
                        _status.value = _status.value.copy(isAvailable = false)
                    }
                },
                ContextCompat.getMainExecutor(context)
        )
    }

    fun startRecording(outputFile: File, onRecordingStarted: () -> Unit): Boolean {
        val videoCap =
                videoCapture
                        ?: run {
                            Log.e("CameraManager", "VideoCapture not initialized")
                            return false
                        }

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        recording =
                videoCap.output.prepareRecording(context, outputOptions).start(
                                ContextCompat.getMainExecutor(context)
                        ) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            _status.value = _status.value.copy(isRecording = true)
                            onRecordingStarted()
                            Log.i("CameraManager", "Recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            _status.value = _status.value.copy(isRecording = false)
                            if (event.hasError()) {
                                Log.e("CameraManager", "Recording error: ${event.error}")
                            } else {
                                Log.i(
                                        "CameraManager",
                                        "Recording saved: ${outputFile.absolutePath}"
                                )
                            }
                        }
                    }
                }

        return true
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
        _status.value = _status.value.copy(isRecording = false)
        Log.i("CameraManager", "Recording stopped")
    }

    fun release() {
        recording?.stop()
        recording = null
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
