# Recording and upload workflow

This document follows the user-visible screens and shows how they trigger the background pipeline.

## Dashboard -> Recording screen

`DashboardScreen` is the launch point for a trip.

When the user taps **Start Recording**:

1. `MainActivity.bindToRecordingService()` binds the foreground recording service.
2. `AppViewModel.startRecording()` creates a `Trip` with a timestamp-based id.
3. The app switches to `AppView.RECORDING`.
4. `RecordingScreen` displays the camera UI and live trip metrics.

## Preview mode vs recording mode

`RecordingScreen` uses `AppViewModel.isPreviewMode` to switch between two states:

- **Preview mode**: the camera preview is visible and the user can check alignment.
- **Recording mode**: the preview is removed and the service keeps recording in the background.

The UI uses the bound `RecordingService` to access `CameraManager` for preview setup.

## Starting capture

When the user taps the in-screen **Start Recording** button:

1. The preview is unbound from the camera.
2. The screen sends `RecordingService.ACTION_START_RECORDING` with the trip id.
3. `RecordingService.onStartCommand()` routes the intent to `startCameraRecording()`.
4. The service starts camera, GPS, IMU, metadata, timer, and wake-lock work.
5. `AppViewModel` updates the UI state via the bound service status flow.

## Stopping capture

When the user taps **Complete Journey**:

1. The screen sends `RecordingService.ACTION_STOP_RECORDING`.
2. `RecordingService.stopRecording()` stops all capture sources.
3. ESP32 packets collected during the session are exported into CSV/GPX-like files.
4. `AppViewModel.completeJourney()` calculates final duration and distance.
5. The app shows `TripSummaryScreen`.

## Trip summary and upload trigger

`TripSummaryScreen` is the handoff from recording to upload.

### Preconditions

The upload button appears only when the trip is still `PENDING` and the Wi-Fi gate and geofence gate have both passed.

### Upload steps

When the user taps Upload:

1. The screen calls `TripApiService.startTrip(...)`.
2. The API returns a server trip id and the expected S3 keys.
3. The screen ensures the local files exist and creates placeholders if needed.
4. It merges IMU axis mapping into `metadata.json` when present.
5. It starts `S3UploadService` once per file.
6. The upload service queues those files and uploads them sequentially.
7. Broadcasts from `S3UploadService` update the screen's upload counter.
8. When all files are done, the screen calls `TripApiService.completeTrip(...)`.
9. The trip is marked as `COMPLETED`.

## Upload queue screen

`UploadQueueScreen` is a status and retry screen, not the uploader itself.

It shows pending trips, trips currently uploading, completed trips, and failed trips that can be retried.

Selecting a pending trip opens its summary, where the upload can be triggered.

## Implementation notes

- `S3UploadService` is foreground because the upload can take a while and must keep running.
- It uses AWS TransferUtility directly and keeps transfer metadata so uploads can resume after interruption.
- The UI is updated with broadcasts instead of direct service callbacks.

