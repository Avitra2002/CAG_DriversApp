package com.roaddefect.driverapp.ble

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class BleManager(private val application: Application) {

    companion object {
        private const val TAG = "BleManager"
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var bleService: BleService? = null
    private var isBound = false

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    val incomingData: Flow<BlePacket>
        get() = bleService?.incomingData ?: emptyFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBound = true
            Log.d(TAG, "BleService bound")

            // Forward service connection state
            managerScope.launch {
                bleService?.connectionState?.collect {
                    _connectionState.value = it
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            bleService = null
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    fun bind() {
        Intent(application, BleService::class.java).also { intent ->
            Log.d(TAG, "Starting and binding BleService")
            ContextCompat.startForegroundService(application, intent)
            application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbind() {
        if (isBound) {
            // Disconnect BLE before unbinding
            bleService?.disconnect()
            application.unbindService(connection)
            isBound = false
            bleService = null
        }
        // Stop the foreground service
        application.stopService(Intent(application, BleService::class.java))
    }

    fun startConnection() {
        bleService?.startConnection()
    }

    fun disconnect() {
        bleService?.disconnect()
    }

    fun getServiceConnectionState(): Flow<BleConnectionState> {
        return bleService?.connectionState ?: _connectionState.asStateFlow()
    }
}
