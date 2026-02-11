package com.roaddefect.driverapp.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class BleService : Service() {

    companion object {
        private const val TAG = "BleService"
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "ble_channel"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _incomingData = MutableSharedFlow<BlePacket>(extraBufferCapacity = 64)
    val incomingData = _incomingData.asSharedFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null

    private var reconnectJob: Job? = null
    private var isReconnecting = false

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App closing, BleService is shutting down cleaning up...")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG," BleService stopped")
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        initializeBluetooth()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ESP32 Sensor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    @SuppressLint("MissingPermission")
    fun startConnection() {
        if (_connectionState.value == BleConnectionState.Connected ||
            _connectionState.value == BleConnectionState.Connecting) {
            return
        }

        _connectionState.value = BleConnectionState.Connecting
        updateNotification("Scanning for ${BleConfig.ESP32_DEVICE_NAME}...")
        startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceName(BleConfig.ESP32_DEVICE_NAME)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)

        // Stop scan after timeout
        serviceScope.launch {
            delay(BleConfig.SCAN_TIMEOUT_MS)
            stopScan()
            if (_connectionState.value == BleConnectionState.Connecting && targetDevice == null) {
                _connectionState.value = BleConnectionState.Error("Device not found")
                updateNotification("Device not found")
                scheduleReconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                Log.d(TAG, "Found device: ${device.name}")
                stopScan()
                targetDevice = device
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _connectionState.value = BleConnectionState.Error("Scan failed: $errorCode")
            updateNotification("Scan failed")
            scheduleReconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        updateNotification("Connecting to ${device.name}...")
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    updateNotification("BLE Sensors Connected")
                    isReconnecting = false
                    reconnectJob?.cancel()
                    gatt?.requestMtu(BleConfig.MTU_SIZE)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = BleConnectionState.Disconnected
                    updateNotification("Disconnected")
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu, status=$status")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(BleConfig.ESP32_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConfig.ESP32_CHARACTERISTIC_UUID)

                if (characteristic != null) {
                    // Enable notifications
                    gatt.setCharacteristicNotification(characteristic, true)

                    // Enable notifications by writing to the Client Characteristic Configuration Descriptor (CCCD)
                    val descriptor = characteristic.getDescriptor(BleConfig.CCCD_UUID)
                    descriptor?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(it)
                        }
                    }

                    _connectionState.value = BleConnectionState.Connected
                    updateNotification("Connected to ${BleConfig.ESP32_DEVICE_NAME}")
                    Log.d(TAG, "BLE fully connected and notifications enabled")
                } else {
                    _connectionState.value = BleConnectionState.Error("Characteristic not found")
                    updateNotification("Service not found")
                    scheduleReconnect()
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { data ->
                handleIncomingData(data)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingData(value)
        }
    }

    private fun handleIncomingData(data: ByteArray) {
        if (data.size == BleConfig.EXPECTED_PACKET_SIZE) {
            val packet = BlePacket(data.copyOf())
            serviceScope.launch {
                _incomingData.emit(packet)
            }
//            Log.d(TAG, "Received ${BleConfig.EXPECTED_PACKET_SIZE}-byte packet")
        } else {
            Log.w(TAG, "Received unexpected packet size: ${data.size}")
        }
    }

    private fun scheduleReconnect() {
        if (isReconnecting) return
        isReconnecting = true

        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(BleConfig.RECONNECT_DELAY_MS)
            Log.d(TAG, "Attempting to reconnect...")
            targetDevice?.let {
                _connectionState.value = BleConnectionState.Connecting
                updateNotification("Reconnecting...")
                connectToDevice(it)
            } ?: run {
                startConnection()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        reconnectJob?.cancel()
        isReconnecting = false
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.Disconnected
//        updateNotification("Disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        serviceScope.cancel()
    }
}
