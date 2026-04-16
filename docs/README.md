# Documentation Index

This folder contains the architectural and workflow notes for the CAG Driver App.

## Docs

- [Architecture and key classes](./architecture.md)
- [End-to-end app data flow](./data-flow.md)
- [Recording and upload workflow](./recording-and-upload.md)
- [Bluetooth / ESP32 workflow](./bluetooth.md)

## Short version

The app is built around a single `AppViewModel`, a foreground `RecordingService` for camera/GPS/IMU capture, a foreground `BleService` for ESP32 sensor packets, and a foreground `S3UploadService` for file transfers.

The UI is composed in `MainActivity`, which binds those pieces together with state flows and callbacks rather than a heavyweight DI framework.


