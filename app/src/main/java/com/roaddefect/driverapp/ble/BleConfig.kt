package com.roaddefect.driverapp.ble

import java.util.UUID

/**
 * Configuration for BLE ESP32 connection.
 * Modify these values to match your ESP32 device settings.
 */
object BleConfig {
    /**
     * The advertised name of the ESP32 device to connect to.
     */
    const val ESP32_DEVICE_NAME = "ESP32-SENSOR-FUSION"

    /**
     * The UUID of the BLE service exposed by the ESP32.
     */
    val ESP32_SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

    /**
     * The UUID of the BLE characteristic for receiving sensor data.
     */
    val ESP32_CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    /**
     * Client Characteristic Configuration Descriptor (CCCD) UUID.
     * Standard UUID used for enabling notifications on BLE characteristics.
     */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * MTU (Maximum Transmission Unit) size to request.
     * ESP32 packets are 44 bytes, so 100 provides adequate headroom.
     */
    const val MTU_SIZE = 100

    /**
     * Scan timeout in milliseconds.
     * How long to scan for the ESP32 before giving up.
     */
    const val SCAN_TIMEOUT_MS = 60000L

    /**
     * Reconnection delay in milliseconds.
     * Time to wait before attempting to reconnect after disconnection.
     */
    const val RECONNECT_DELAY_MS = 3000L

    /**
     * Expected packet size from ESP32.
     */
    const val EXPECTED_PACKET_SIZE = 44
}
