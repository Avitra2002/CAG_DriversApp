package com.roaddefect.driverapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
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
        val isPreviewActive: Boolean = false,
        val hasPermission: Boolean = false,
        val recordingDurationMs: Long = 0
)

class CameraManager(private val context: Context) {
    private val _status = MutableStateFlow(CameraStatus())
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
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
        setupCameraVideoOnly(lifecycleOwner, onCameraReady)
    }

    /**
     * Setup camera with both Preview and VideoCapture use cases.
     * Call this when you want to show the camera preview in the UI.
     */
    fun setupCameraWithPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraReady: () -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
                {
                    try {
                        cameraProvider = cameraProviderFuture.get()
                        currentLifecycleOwner = lifecycleOwner

                        val recorder =
                                Recorder.Builder()
                                        .setQualitySelector(QualitySelector.from(Quality.HD))
                                        .build()

                        videoCapture = VideoCapture.withOutput(recorder)

                        preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider?.unbindAll()
                        cameraProvider?.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                videoCapture
                        )

                        _status.value = _status.value.copy(isAvailable = true, isPreviewActive = true)
                        onCameraReady()

                        Log.i("CamManager", "Camera setup with preview completed")
                    } catch (e: Exception) {
                        Log.e("CamManager", "Camera setup with preview failed", e)
                        _status.value = _status.value.copy(isAvailable = false, isPreviewActive = false)
                    }
                },
                ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Unbind preview while keeping VideoCapture bound.
     * Call this when starting recording to allow screen-off recording.
     */
    fun unbindPreview() {
        val provider = cameraProvider ?: return
        val owner = currentLifecycleOwner ?: return
        val videoCap = videoCapture ?: return

        try {
            provider.unbindAll()

            // Rebind only VideoCapture
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            provider.bindToLifecycle(owner, cameraSelector, videoCap)

            preview = null
            _status.value = _status.value.copy(isPreviewActive = false)
            Log.i("CamManager", "Preview unbound, VideoCapture still active")
        } catch (e: Exception) {
            Log.e("CamManager", "Failed to unbind preview", e)
        }
    }

    /**
     * Setup camera with only VideoCapture (no preview).
     * Use this for background recording.
     */
    fun setupCameraVideoOnly(lifecycleOwner: LifecycleOwner, onCameraReady: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
                {
                    try {
                        cameraProvider = cameraProviderFuture.get()
                        currentLifecycleOwner = lifecycleOwner

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

                        _status.value = _status.value.copy(isAvailable = true, isPreviewActive = false)
                        onCameraReady()

                        Log.i("CamManager", "Camera setup (video only) completed")
                    } catch (e: Exception) {
                        Log.e("CamManager", "Camera setup failed", e)
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
                            Log.e("CamManager", "VideoCapture not initialized")
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
                            Log.i("CamManager", "Recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            _status.value = _status.value.copy(isRecording = false)
                            if (event.hasError()) {
                                Log.e("CamManager", "Recording error: ${event.error}")
                            } else {
                                Log.i(
                                        "CamManager",
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
        Log.i("CamManager", "Recording stopped")
    }

    fun release() {
        recording?.stop()
        recording = null
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}

