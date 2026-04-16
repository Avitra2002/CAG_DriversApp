# Architecture and key classes

## What `AppViewModel` does

`AppViewModel` is the app's central state holder. It owns the screen state, trip state, recording progress, sensor health, BLE connection state, and trip-upload counts.

### Responsibilities

- Tracks which screen is visible through `currentView`.
- Holds the active `Trip` and the in-memory list of recorded trips.
- Exposes sensor health through `sensorStatus`.
- Tracks whether recording is active and how long / far the current recording has progressed.
- Tracks Bluetooth connection state and Wi-Fi connectivity.
- Coordinates transitions such as dashboard -> recording, recording -> trip summary, and trip summary -> upload queue.
- Receives data from the bound `RecordingService` and forwards BLE packets into it.

### Why `AndroidViewModel` here

This project uses `AndroidViewModel` rather than a plain `ViewModel` because the ViewModel needs application context to build and own long-lived helpers such as `CameraManager`, `GPSTracker`, `IMUSensorManager`, `WifiGateManager`, `GeofenceManager`, `BleManager`, and `FileManager` calls that need a `Context`.

That keeps the helpers out of the UI layer while still letting them access Android APIs.

### Architectural choice: manual wiring instead of a DI framework

There is no Hilt, Koin, or Dagger setup in this repo. Instead, the app uses manual composition:

- `MainActivity` creates the `AppViewModel` with `by viewModels()`.
- The activity binds to `RecordingService` and passes the binder-backed service instance into `AppViewModel`.
- Composables receive state and callbacks as parameters.
- Utility classes are created directly inside the ViewModel or services.

That is a simple choice for a small app because it reduces setup overhead, makes Android lifecycle ownership explicit, keeps the service and camera lifecycle easy to reason about, and avoids a large DI graph for only a few long-lived objects.

The trade-off is that some orchestration ends up split between UI, activity, and ViewModel. The TODO comments in `AppViewModel` reflect that the code already feels this coupling.

## What `RecordingService` does

`RecordingService` is the foreground service that performs the actual trip capture work.

### Responsibilities

- Starts and stops video capture through `CameraManager`.
- Starts and stops GPS logging through `GPSTracker`.
- Starts and stops IMU logging through `IMUSensorManager`.
- Receives BLE sensor packets from the ViewModel and stores them for later export.
- Writes trip metadata and ESP32-derived data to files.
- Keeps the device awake with a partial wake lock during recording.
- Runs as a foreground service so recording can continue when the UI is backgrounded.

### Why a service instead of only ViewModel logic

This work needs to survive UI changes and continue while the user is on a different screen or the app is backgrounded. A foreground service is the right fit because it gives a longer-lived lifecycle than a composable or activity, access to camera/location/sensor work that should continue while recording, a user-visible notification for an ongoing capture task, and better resilience when the screen changes state.

The ViewModel only coordinates state. The service owns the actual recording side effects.

### Why the service is bound

The activity binds to `RecordingService` so the UI can query live status and reuse the camera manager for preview mode. That binding gives the ViewModel a concrete service instance so it can observe `RecordingStatus`, forward BLE data to the service, and let the recording screen access the camera manager for preview binding and unbinding.

## Why the services are split

The app has three major background services:

- `RecordingService` for local capture
- `BleService` for ESP32 communication
- `S3UploadService` for uploads

This split makes each responsibility independent. Recording can keep running even if upload later fails, BLE can reconnect without restarting camera capture, and uploads can retry or resume without touching the record pipeline.

That separation is especially useful in Android because each workflow has a different lifecycle and foreground-service type requirement.

## Supporting state models

A few small models carry state between layers:

- `AppView` selects the current screen.
- `Trip` holds the trip metadata and upload status.
- `SensorStatus` exposes dashboard health indicators.
- `BleConnectionState` tracks BLE connection progress.
- `RecordingStatus` tracks elapsed time, distance, and camera state.
- `UploadStatus` tracks whether a trip is pending, uploading, completed, or failed.

These models keep the UI simple and make the state transitions explicit.

