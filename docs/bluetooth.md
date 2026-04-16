# Bluetooth / ESP32 workflow

This app uses Bluetooth Low Energy to receive sensor packets from an ESP32 device.

## Main pieces

- `BleService` owns the BLE connection and notification subscription.
- `BleManager` binds to `BleService` and exposes its flows to the rest of the app.
- `BlePacket` wraps raw packet bytes.
- `SensorSample` parses one BLE packet into sensor values.
- `BleConnectionState` tells the UI whether BLE is disconnected, connecting, connected, or in error.

## Connection flow

1. `AppViewModel.init` calls `initializeBle()`.
2. `BleManager.bind()` starts `BleService` and binds to it.
3. `BleService.startConnection()` scans for the ESP32 device name from `BleConfig`.
4. When the device is found, the service connects over GATT.
5. The service discovers the custom service and characteristic UUIDs.
6. Notification support is enabled through the CCCD descriptor.
7. Incoming characteristic values are emitted through `incomingData`.

## Packet format

`BleConfig.EXPECTED_PACKET_SIZE` is 44 bytes.

The parser expects a little-endian packet with:

- version
- message type
- payload length
- timestamp
- latitude
- longitude
- altitude
- accelerometer X/Y/Z
- gyroscope X/Y/Z

`SensorSample.parseSamples()` converts the packet into a typed sample object.

## How BLE data reaches recording

The data path is:

`BleService` -> `BleManager.incomingData` -> `AppViewModel.startBleDataCollection()` -> `RecordingService.onBleData(...)`

That means BLE packets do not go straight to disk.
Instead, the ViewModel forwards them into the active recording session only while recording is active.

## What `RecordingService` does with BLE data

Inside `RecordingService.onBleData(...)`:

- the packet bytes are parsed into `SensorSample` objects,
- samples are stored in memory for later export,
- the first sample is used to establish a time reference between ESP32 uptime and phone wall-clock time,
- when recording stops, the ESP32 samples are written out to CSV and GPX-like files.

## Why BLE is a separate foreground service

BLE scanning and GATT connection handling are long-lived operations that can be interrupted if they are tied too tightly to UI state. Keeping BLE in its own service gives:

- a stable lifecycle,
- automatic reconnect handling,
- separate connection state reporting,
- less coupling to the recording UI.

## UI visibility

The dashboard and recording screen both display BLE state indirectly through `SensorStatus`:

- `AppViewModel.updateSensorStatus()` maps BLE connection state to the dashboard status model.
- The dashboard shows whether the ESP32 is connected.
- The recording screen shows the BLE indicator alongside the other sensors.

