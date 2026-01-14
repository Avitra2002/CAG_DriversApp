package com.roaddefect.driverapp.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BlePacket(
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BlePacket
        return data.contentEquals(other.data) && timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        return 31 * data.contentHashCode() + timestamp.hashCode()
    }
}

data class SensorSample(
    val timestampMs: Long,
    val latitude: Float,
    val longitude: Float,
    val altitude: Float,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float
) {
    companion object {
        /**
         * Parses a 44-byte BLE packet into a list of SensorSample objects.
         * Packet format (little-endian):
         * - 1 byte: version
         * - 1 byte: message type
         * - 2 bytes: payload length
         * - 4 bytes: timestamp (unsigned 32-bit ms)
         * - 4 bytes: latitude (float)
         * - 4 bytes: longitude (float)
         * - 4 bytes: altitude (float)
         * - 4 bytes: ax (float)
         * - 4 bytes: ay (float)
         * - 4 bytes: az (float)
         * - 4 bytes: gx (float)
         * - 4 bytes: gy (float)
         * - 4 bytes: gz (float)
         */
        fun parseSamples(value: ByteArray): List<SensorSample> {
            if (value.size < BleConfig.EXPECTED_PACKET_SIZE) {
                return emptyList()
            }

            val buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)

            // 0xFF operations are bitmasks. Ensuring that the bytes are treated as unsigned.
            val version = buf.get().toInt() and 0xFF
            val msgType = buf.get().toInt() and 0xFF
            val payloadLen = buf.short.toInt() and 0xFFFF

            val samples = mutableListOf<SensorSample>()

            val ts = buf.int.toLong() and 0xFFFFFFFFL
            val lat = buf.float
            val lon = buf.float
            val alt = buf.float
            val ax = buf.float
            val ay = buf.float
            val az = buf.float
            val gx = buf.float
            val gy = buf.float
            val gz = buf.float
            samples += SensorSample(ts, lat, lon, alt, ax, ay, az, gx, gy, gz)

            return samples
        }
    }

    /**
     * Returns true if the GNSS coordinates are valid (non-zero and finite).
     */
    fun hasValidGnss(): Boolean {
        return latitude.isFinite() && longitude.isFinite() &&
               !(latitude == 0.0f && longitude == 0.0f)
    }
}
