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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.time.Instant
import kotlin.math.abs

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

    // Axis calibration
    private val calibrator = ImuAxisCalibrator()
    private var axisMappingFile: File? = null
    private var calibrationSaved = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelerometerData = event.values.clone()
                    // Feed calibrator on every accelerometer sample
                    if (_status.value.isRecording && !calibrationSaved) {
                        calibrator.onNewSample(
                            accelerometerData[0], accelerometerData[1], accelerometerData[2],
                            gyroscopeData[0], gyroscopeData[1], gyroscopeData[2],
                            System.currentTimeMillis()
                        )
                        checkAndSaveCalibration()
                    }
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
                    // Will be UTC, not local time
                    val isoTimestamp = Instant.ofEpochMilli(imuData.timestamp).toString()
                    val line = "$isoTimestamp,${imuData.accelerometerX},${imuData.accelerometerY}," +
                            "${imuData.accelerometerZ},${imuData.gyroscopeX},${imuData.gyroscopeY}," +
                            "${imuData.gyroscopeZ}\n"
                    fileWriter?.write(line)
                    fileWriter?.flush()
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

    fun startRecording(outputFile: File, axisMappingOutputFile: File? = null) {
        if (!checkIMUAvailability()) {
            Log.e("IMUSensorManager", "IMU sensors not available")
            return
        }

        imuLogFile = outputFile
        axisMappingFile = axisMappingOutputFile
        calibrationSaved = false
        calibrator.reset()

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

        // Best-effort final save if confidence threshold not yet reached during recording
        if (!calibrationSaved) checkAndSaveCalibration()

        calibrator.reset()
        calibrationSaved = false
        axisMappingFile = null

        _status.value = _status.value.copy(isRecording = false)
        Log.i("IMUSensorManager", "IMU recording stopped")
    }

    private fun checkAndSaveCalibration() {
        val mapping = calibrator.currentMapping ?: return
        val file = axisMappingFile ?: run {
            Log.w("IMUSensorManager", "Axis mapping ready but no output file set — skipping write")
            calibrationSaved = true // prevent repeated log spam
            return
        }
        try {
            file.writeText(mapping.toJson().toString())
            calibrationSaved = true
            Log.i("IMUSensorManager",
                "IMU axis mapping saved [confidence=${
                    String.format("%.2f", mapping.confidenceScore)
                }]: forward=${mapping.forward.index * mapping.forwardSign}, " +
                "lateral(left)=${mapping.lateral.index * mapping.lateralSign}, " +
                "downward=${mapping.downward.index * mapping.downwardSign}")
        } catch (e: Exception) {
            Log.e("IMUSensorManager", "Failed to write IMU axis mapping", e)
        }
    }

    fun reset() {
        accelerometerData = FloatArray(3)
        gyroscopeData = FloatArray(3)
        _status.value = IMUStatus(isAvailable = _status.value.isAvailable)
    }

    // =========================================================================
    // ImuAxisCalibrator — determines which physical axis maps to forward /
    // lateral / downward, AND the sign convention for each direction.
    //
    // Axis identity strategies:
    //   1. Gravity detection  → downward axis (fast, ~1 s)
    //   2. Variance ratio     → forward axis  (passive, accumulates over time)
    //   3. Straight-driving   → forward axis  (5-second window, clearest signal)
    //
    // Sign detection strategies:
    //   Downward sign  : sign of the gravity reading (±9.81) — certain.
    //   Forward sign   : dominant sign of large isolated linear-accel events
    //                    (large forward axis value + quiet lateral = pure
    //                    braking/accelerating with no turning component).
    //   Lateral sign   : gyroscope yaw × lateral accel correlation.
    //                    Gyro component along the downward axis = yaw rate.
    //                    Left turn → yaw_rate > 0 (right-hand rule with Z up).
    //                    Centripetal accel during left turn acts leftward, so
    //                    if lateral_accel > 0 at same time → left = positive.
    //
    // Votes are weighted; a confidence score drives the 0.75 lock-in threshold.
    // Signed output: negative index means the axis produces negative values when
    // moving in that direction. e.g. forward=Z but braking gives negative Z → -3.
    // =========================================================================
    private class ImuAxisCalibrator {

        companion object {
            private const val GRAVITY = 9.81f
            private const val GRAVITY_TOLERANCE = 0.8f       // ±0.8 m/s² from 9.81
            private const val NEAR_ZERO_THRESHOLD = 0.3f     // lateral mean abs during straight driving
            private const val LARGE_ACCEL_THRESHOLD = 0.5f   // forward mean abs during straight driving
            private const val MAX_BUFFER = 300               // ~6 s at ~50 Hz
            private const val MIN_SAMPLES_VERTICAL = 50
            private const val MIN_SAMPLES_FORWARD = 150
            const val CONFIDENCE_THRESHOLD = 0.75f
            private const val STRAIGHT_DRIVE_WINDOW_MS = 5_000L
            // Sign detection
            private const val FORWARD_EVENT_THRESHOLD = 1.0f  // m/s² — clear linear accel spike
            private const val LATERAL_QUIET_THRESHOLD = 0.5f  // m/s² — lateral must be quiet during forward event
            private const val YAW_RATE_THRESHOLD = 0.08f      // rad/s — minimum yaw to count as a turn
            private const val LATERAL_TURN_THRESHOLD = 0.3f   // m/s² — minimum lateral accel during turn
            private const val SIGN_MIN_VOTES = 15             // votes required before sign is trusted
        }

        enum class Axis(val index: Int) { X(1), Y(2), Z(3) }

        data class AxisMapping(
            val forward: Axis,
            val forwardSign: Int,   // +1 or -1: sign of the axis when moving forward
            val lateral: Axis,
            val lateralSign: Int,   // +1 or -1: sign of the axis when moving left
            val downward: Axis,
            val downwardSign: Int,  // +1 or -1: sign of the axis when pointing downward
            val confidenceScore: Float
        ) {
            /**
             * Produces JSON like:
             * {
             *   "imu_axis_mapping": [3, -2, 1]
             * }
             * Each value is axis_index * sign, where index 1=X, 2=Y, 3=Z.
             * Order is [forward, lateral (left), downward].
             * A negative value means that direction produces negative readings on that axis.
             */
            fun toJson(): JSONObject = JSONObject().apply {
                put("imu_axis_mapping", JSONArray().apply {
                    put(forward.index * forwardSign)
                    put(lateral.index * lateralSign)
                    put(downward.index * downwardSign)
                })
            }
        }

        // Accelerometer sample buffers — all stay in sync (same index = same sample)
        private val bufX = ArrayDeque<Float>()
        private val bufY = ArrayDeque<Float>()
        private val bufZ = ArrayDeque<Float>()
        private val bufTs = ArrayDeque<Long>()

        // Latest gyroscope readings, updated alongside each accelerometer sample
        private var latestGx = 0f
        private var latestGy = 0f
        private var latestGz = 0f

        private var downwardAxis: Axis? = null
        private var downwardSign: Float = 1f
        private val forwardVotes = mutableMapOf<Axis, Float>()
        private var confidenceScore: Float = 0f

        // Sign detection accumulators
        private var forwardSignAccum = 0   // running sum of sign() for linear-accel events
        private var forwardSignCount = 0
        private var lateralSignAccum = 0   // running sum of sign(yawRate * lateralAccel)
        private var lateralSignCount = 0

        /** Returns the finalized mapping once confidence ≥ threshold, null otherwise. */
        val currentMapping: AxisMapping?
            get() = if (confidenceScore >= CONFIDENCE_THRESHOLD) buildMapping() else null

        fun reset() {
            bufX.clear(); bufY.clear(); bufZ.clear(); bufTs.clear()
            latestGx = 0f; latestGy = 0f; latestGz = 0f
            downwardAxis = null
            downwardSign = 1f
            forwardVotes.clear()
            confidenceScore = 0f
            forwardSignAccum = 0; forwardSignCount = 0
            lateralSignAccum = 0; lateralSignCount = 0
        }

        fun onNewSample(
            ax: Float, ay: Float, az: Float,
            gx: Float, gy: Float, gz: Float,
            timestampMs: Long
        ) {
            latestGx = gx; latestGy = gy; latestGz = gz
            bufX.addLast(ax); bufY.addLast(ay); bufZ.addLast(az); bufTs.addLast(timestampMs)
            while (bufX.size > MAX_BUFFER) {
                bufX.removeFirst(); bufY.removeFirst()
                bufZ.removeFirst(); bufTs.removeFirst()
            }
            if (downwardAxis == null) {
                tryDetectDownward()
            } else {
                tryDetectForward()
            }
        }

        // -----------------------------------------------------------------------
        // Strategy 1 — Gravity: the axis whose rolling mean ≈ ±9.81 with lowest
        // variance is the downward axis. Sign is the sign of the mean.
        // -----------------------------------------------------------------------
        private fun tryDetectDownward() {
            if (bufX.size < MIN_SAMPLES_VERTICAL) return
            val n = minOf(bufX.size, 100)

            data class Stats(val axis: Axis, val buf: ArrayDeque<Float>) {
                val slice: List<Float> = buf.toList().takeLast(n)
                val mean: Float = slice.average().toFloat()
                val variance: Float = slice.map { (it - mean) * (it - mean) }.average().toFloat()
            }

            val best = listOf(Stats(Axis.X, bufX), Stats(Axis.Y, bufY), Stats(Axis.Z, bufZ))
                .filter { abs(abs(it.mean) - GRAVITY) < GRAVITY_TOLERANCE }
                .minByOrNull { it.variance } ?: return

            downwardAxis = best.axis
            // If mean is positive (+9.81), the axis reads positive when pointing downward → sign = +1.
            // If mean is negative (-9.81), the axis reads negative when pointing downward → sign = -1.
            downwardSign = if (best.mean > 0) 1f else -1f
            confidenceScore += 0.45f
            Log.i("ImuAxisCalibrator",
                "Downward axis: ${best.axis}, sign=${downwardSign.toInt()}, mean=${
                    String.format("%.3f", best.mean)
                }")
        }

        private fun tryDetectForward() {
            val down = downwardAxis ?: return
            val candidates = Axis.entries.filter { it != down }
            strategyVarianceRatio(candidates)
            strategyStraightDriving(candidates)
            // Accumulate sign evidence using current best-estimate axes
            val bestForward = forwardVotes.filter { it.key in candidates }.maxByOrNull { it.value }?.key
            if (bestForward != null) {
                val bestLateral = candidates.first { it != bestForward }
                detectForwardSign(bestForward, bestLateral)
                detectLateralSign(bestLateral, down)
            }
        }

        // -----------------------------------------------------------------------
        // Strategy 2 — Variance ratio: forward axis accumulates more variance
        // than lateral over any driving window (continuous, low-weight).
        // -----------------------------------------------------------------------
        private fun strategyVarianceRatio(candidates: List<Axis>) {
            if (bufX.size < MIN_SAMPLES_FORWARD) return
            val n = minOf(bufX.size, 200)

            fun variance(buf: ArrayDeque<Float>): Float {
                val s = buf.toList().takeLast(n)
                val m = s.average().toFloat()
                return s.map { (it - m) * (it - m) }.average().toFloat()
            }

            val varByAxis = mapOf(Axis.X to variance(bufX), Axis.Y to variance(bufY), Axis.Z to variance(bufZ))
            val sorted = candidates.sortedByDescending { varByAxis[it]!! }
            if (sorted.size >= 2 && varByAxis[sorted[0]]!! > varByAxis[sorted[1]]!! * 2f) {
                castVote(sorted[0], 1f)
            }
        }

        // -----------------------------------------------------------------------
        // Strategy 3 — Straight driving (5-second window): one candidate axis
        // has consistently larger absolute mean than the other (near zero).
        // -----------------------------------------------------------------------
        private fun strategyStraightDriving(candidates: List<Axis>) {
            if (bufTs.isEmpty()) return
            val cutoff = bufTs.last() - STRAIGHT_DRIVE_WINDOW_MS
            val indices = bufTs.indices.filter { bufTs[it] >= cutoff }
            if (indices.size < 50) return

            fun meanAbs(buf: ArrayDeque<Float>): Float =
                indices.map { abs(buf[it]) }.average().toFloat()

            val absMap = mapOf(
                Axis.X to meanAbs(bufX),
                Axis.Y to meanAbs(bufY),
                Axis.Z to meanAbs(bufZ)
            )
            val sorted = candidates.sortedByDescending { absMap[it]!! }
            if (sorted.size >= 2) {
                val large = sorted[0]; val small = sorted[1]
                if (absMap[large]!! > LARGE_ACCEL_THRESHOLD &&
                    absMap[small]!! < NEAR_ZERO_THRESHOLD &&
                    absMap[large]!! > absMap[small]!! * 3f
                ) {
                    castVote(large, 2f)
                }
            }
        }

        // -----------------------------------------------------------------------
        // Forward sign: during a pure linear event (large forward axis, quiet
        // lateral), accumulate the sign of the forward axis value. Hard braking
        // and acceleration both produce clear spikes — the dominant sign across
        // many events reveals the axis convention.
        // -----------------------------------------------------------------------
        private fun detectForwardSign(forwardAxis: Axis, lateralAxis: Axis) {
            if (forwardSignCount >= SIGN_MIN_VOTES * 3) return // already saturated
            val fVal = bufOf(forwardAxis).lastOrNull() ?: return
            val lVal = bufOf(lateralAxis).lastOrNull() ?: return
            if (abs(fVal) > FORWARD_EVENT_THRESHOLD && abs(lVal) < LATERAL_QUIET_THRESHOLD) {
                forwardSignAccum += if (fVal > 0) 1 else -1
                forwardSignCount++
            }
        }

        // -----------------------------------------------------------------------
        // Lateral sign: correlate gyroscope yaw rate with lateral acceleration.
        // The gyro component along the downward axis = vehicle yaw rate.
        // Right-hand rule (with the downward axis corrected for its sign):
        //   yaw_rate > 0 → left turn → centripetal accel acts leftward
        //   If lateral_accel > 0 simultaneously → left = positive → sign = +1
        //   If lateral_accel < 0 simultaneously → left = negative → sign = -1
        // -----------------------------------------------------------------------
        private fun detectLateralSign(lateralAxis: Axis, downAxis: Axis) {
            if (lateralSignCount >= SIGN_MIN_VOTES * 3) return // already saturated
            // Raw gyro component along the downward axis, corrected for downward sign flip
            val rawYaw = when (downAxis) {
                Axis.X -> latestGx
                Axis.Y -> latestGy
                Axis.Z -> latestGz
            }
            val yawRate = rawYaw * downwardSign // positive = left turn regardless of mounting
            val lVal = bufOf(lateralAxis).lastOrNull() ?: return
            if (abs(yawRate) > YAW_RATE_THRESHOLD && abs(lVal) > LATERAL_TURN_THRESHOLD) {
                // yawRate * lVal > 0 means left turn produces positive lateral → left = positive
                lateralSignAccum += if (yawRate * lVal > 0) 1 else -1
                lateralSignCount++
            }
        }

        private fun castVote(axis: Axis, weight: Float) {
            forwardVotes[axis] = (forwardVotes[axis] ?: 0f) + weight
            val total = forwardVotes.values.sum()
            val top = forwardVotes.values.maxOrNull() ?: 0f
            if (total > 0) {
                confidenceScore = minOf(1f, confidenceScore + (top / total) * 0.15f)
            }
        }

        private fun bufOf(axis: Axis): ArrayDeque<Float> = when (axis) {
            Axis.X -> bufX
            Axis.Y -> bufY
            Axis.Z -> bufZ
        }

        private fun buildMapping(): AxisMapping? {
            val down = downwardAxis ?: return null
            val nonDown = Axis.entries.filter { it != down }
            val forward = forwardVotes
                .filter { it.key in nonDown }
                .maxByOrNull { it.value }?.key ?: return null
            val lateral = nonDown.first { it != forward }

            // Forward sign: use dominant sign if enough events observed, else default +1
            val fSign = if (forwardSignCount >= SIGN_MIN_VOTES)
                if (forwardSignAccum >= 0) 1 else -1
            else 1

            // Lateral sign: use dominant correlation if enough events observed, else default +1
            val lSign = if (lateralSignCount >= SIGN_MIN_VOTES)
                if (lateralSignAccum >= 0) 1 else -1
            else 1

            Log.i("ImuAxisCalibrator",
                "Signs — forward: $fSign (${forwardSignCount} events), " +
                "lateral: $lSign (${lateralSignCount} events), " +
                "downward: ${downwardSign.toInt()} (from gravity)")

            return AxisMapping(
                forward = forward, forwardSign = fSign,
                lateral = lateral, lateralSign = lSign,
                downward = down, downwardSign = downwardSign.toInt(),
                confidenceScore = confidenceScore
            )
        }
    }
}
