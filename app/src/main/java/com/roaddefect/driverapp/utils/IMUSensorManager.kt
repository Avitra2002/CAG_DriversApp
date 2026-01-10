package com.roaddefect.driverapp.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter

data class IMUData(
    val timestamp: Long,
    val accelerometerX: Float,
    val accelerometerY: Float,
    val accelerometerZ: Float,
    val gyroscopeX: Float,
    val gyroscopeY: Float,
    val gyroscopeZ: Float
)

data class IMUStatus(
    val isAvailable: Boolean = false,
    val isRecording: Boolean = false,
    val currentData: IMUData? = null
)

class IMUSensorManager(private val context: Context) {
    private val _status = MutableStateFlow(IMUStatus())
    val status: StateFlow<IMUStatus> = _status.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var imuLogFile: File? = null
    private var fileWriter: FileWriter? = null

    private var accelerometerData = FloatArray(3)
    private var gyroscopeData = FloatArray(3)

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelerometerData = event.values.clone()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroscopeData = event.values.clone()
                }
            }

            // Update current data and log to file
            val imuData = IMUData(
                timestamp = System.currentTimeMillis(),
                accelerometerX = accelerometerData[0],
                accelerometerY = accelerometerData[1],
                accelerometerZ = accelerometerData[2],
                gyroscopeX = gyroscopeData[0],
                gyroscopeY = gyroscopeData[1],
                gyroscopeZ = gyroscopeData[2]
            )

            _status.value = _status.value.copy(currentData = imuData)

            // Log to file if recording
            if (_status.value.isRecording) {
                try {
                    val line = "${imuData.timestamp},${imuData.accelerometerX},${imuData.accelerometerY}," +
                            "${imuData.accelerometerZ},${imuData.gyroscopeX},${imuData.gyroscopeY}," +
                            "${imuData.gyroscopeZ}\n"
                    fileWriter?.write(line)
                } catch (e: Exception) {
                    Log.e("IMUSensorManager", "Failed to write IMU data", e)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // Not used
        }
    }

    fun checkIMUAvailability(): Boolean {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val isAvailable = accelerometer != null && gyroscope != null

        _status.value = _status.value.copy(isAvailable = isAvailable)

        return isAvailable
    }

    fun startRecording(outputFile: File) {
        if (!checkIMUAvailability()) {
            Log.e("IMUSensorManager", "IMU sensors not available")
            return
        }

        imuLogFile = outputFile

        try {
            fileWriter = FileWriter(outputFile, true)
            // Write CSV header
            fileWriter?.write("timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z\n")
            fileWriter?.flush()
        } catch (e: Exception) {
            Log.e("IMUSensorManager", "Failed to create IMU log file", e)
            return
        }

        // Register listeners
        accelerometer?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                SensorManager.SENSOR_DELAY_GAME // ~20ms intervals
            )
        }

        gyroscope?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        _status.value = _status.value.copy(isRecording = true)
        Log.i("IMUSensorManager", "IMU recording started")
    }

    fun stopRecording() {
        sensorManager.unregisterListener(sensorListener)

        try {
            fileWriter?.close()
        } catch (e: Exception) {
            Log.e("IMUSensorManager", "Failed to close IMU log file", e)
        }
        fileWriter = null

        _status.value = _status.value.copy(isRecording = false)
        Log.i("IMUSensorManager", "IMU recording stopped")
    }

    fun reset() {
        accelerometerData = FloatArray(3)
        gyroscopeData = FloatArray(3)
        _status.value = IMUStatus(isAvailable = _status.value.isAvailable)
    }
}
