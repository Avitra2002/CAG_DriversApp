# End-to-end app data flow

This is the simplest path through the app, from launch to recording to upload.

## 1) App launch

1. Android launches `MainActivity` from `AndroidManifest.xml`.
2. `DriverApplication.onCreate()` runs first and initializes Amplify plus the AWS transfer service.
3. `MainActivity.onCreate()` checks runtime permissions through `PermissionsManager`.
4. `MainActivity` composes the UI by calling `DriverApp(viewModel, this)`.
5. `AppViewModel` starts sensor monitoring, Wi-Fi monitoring, and BLE initialization in its `init` block.

## 2) Dashboard state is assembled

`DriverApp` reads state flows from `AppViewModel` and renders `DashboardScreen`.

The dashboard is populated from:

- `sensorStatus` for camera/GPS/IMU/BLE/storage health
- `isWifiConnected` for hub Wi-Fi state
- `pendingUploads` for queue count
- `vehicleId` for the current vehicle label

## 3) User starts a recording session

From the dashboard:

1. The Start Recording button binds `RecordingService` through `MainActivity.bindToRecordingService()`.
2. `AppViewModel.startRecording()` creates a new `Trip`, sets the current screen to `RECORDING`, and starts BLE data collection.
3. `RecordingScreen` appears and uses the bound service's `CameraManager` for preview mode.
4. The user presses Start Recording on the recording screen.
5. The screen unbinds the preview, sends `ACTION_START_RECORDING` to `RecordingService`, and toggles preview mode off.

## 4) Recording happens in the foreground service

`RecordingService.startCameraRecording()` does the actual work:

- starts a foreground notification,
- acquires a wake lock,
- creates trip metadata,
- starts GPS tracking,
- starts IMU logging,
- starts the timer loop that updates elapsed time and distance,
- starts camera video capture.

`AppViewModel` keeps observing the service status and mirrors that into UI state flows.

## 5) User ends the trip

When the user stops the session:

1. `RecordingScreen` sends `ACTION_STOP_RECORDING` to `RecordingService`.
2. `RecordingService.stopRecording()` stops camera, GPS, IMU, and ESP32 data collection.
3. `AppViewModel.completeJourney()` calculates final duration/distance, stores the completed `Trip`, and opens the trip summary screen.

## 6) Uploading happens from trip summary

On `TripSummaryScreen`:

1. Wi-Fi gate and geofence are monitored.
2. The Upload button becomes available only when both gates pass and the trip is still pending.
3. The screen first calls `TripApiService.startTrip(...)` to get a server trip id and expected S3 keys.
4. It then starts `S3UploadService` once per file.
5. The upload service processes files sequentially, retries failures, and broadcasts progress or completion.
6. After all files succeed, `TripSummaryScreen` calls `TripApiService.completeTrip(...)`.
7. The trip status is updated to `COMPLETED`.

## Main state holders

- `AppViewModel` owns the overall UI state.
- `RecordingService` owns live capture.
- `BleService` owns BLE packets.
- `S3UploadService` owns uploads.
- `TripSummaryScreen` is the orchestration point for upload triggering and completion.

