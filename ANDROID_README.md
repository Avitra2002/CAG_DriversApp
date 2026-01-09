# Driver App - Android (Kotlin + Jetpack Compose)

A native Android app for road defect monitoring with sensor integration, built with Kotlin and Jetpack Compose.

## Features

- **Dashboard**: View system status, sensor health, and quick access to all features
- **Recording**: Real-time trip recording with camera, GPS, and IMU sensor data
- **Trip Summary**: Detailed view of completed trips with statistics
- **System Health**: Monitor sensor status, storage, and device information
- **Trip History**: Browse all recorded trips with upload status
- **Upload Queue**: Automatic upload management when connected to WiFi

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+ (Android 8.0 Oreo)
- Target SDK 34 (Android 14)
- Kotlin 1.9.20+

## Getting Started

### 1. Open Project

Open Android Studio and select "Open an Existing Project", then navigate to this directory.

### 2. Sync Gradle

Android Studio will automatically sync Gradle dependencies. If not, click "Sync Project with Gradle Files" in the toolbar.

### 3. Build and Run

1. Connect an Android device via USB or start an Android emulator
2. Click the "Run" button (green play icon) or press Shift+F10
3. Select your device/emulator and click OK

## Project Structure

```
app/src/main/java/com/roaddefect/driverapp/
├── MainActivity.kt                 # Entry point
├── AppViewModel.kt                 # State management
├── models/
│   ├── Trip.kt                     # Trip data model
│   ├── SensorStatus.kt            # Sensor status model
│   └── AppView.kt                 # Navigation enum
├── ui/
│   ├── screens/
│   │   ├── DashboardScreen.kt     # Main dashboard
│   │   ├── RecordingScreen.kt     # Trip recording view
│   │   ├── TripSummaryScreen.kt   # Trip completion screen
│   │   ├── SystemHealthScreen.kt  # System diagnostics
│   │   ├── TripHistoryScreen.kt   # Trip history list
│   │   └── UploadQueueScreen.kt   # Upload management
│   └── theme/
│       └── Theme.kt                # App theming
```

## Permissions

The app requires the following permissions (declared in AndroidManifest.xml):

- **Camera**: For recording road conditions
- **Location**: GPS tracking for trip routes
- **Storage**: Saving trip data locally
- **Internet**: Uploading data to cloud
- **Sensors**: IMU (accelerometer/gyroscope) data collection
- **Foreground Service**: Background recording capability

## Key Dependencies

- **Jetpack Compose**: Modern Android UI toolkit
- **Material 3**: Material Design components
- **Navigation Compose**: Screen navigation
- **ViewModel**: State management
- **CameraX**: Camera integration
- **Location Services**: GPS functionality
- **Coroutines**: Asynchronous programming

## Building for Release

To create a release APK:

```bash
./gradlew assembleRelease
```

The APK will be located at:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Notes

- This is a conversion from the original React web app to native Android
- Sensor integration code is simulated; real implementation requires platform-specific sensor APIs
- Upload functionality simulates WiFi detection and background uploads
- Camera preview shows a placeholder; real implementation requires CameraX configuration

## Customization

### Change Vehicle ID
Edit the default vehicle ID in `AppViewModel.kt`:
```kotlin
private val _vehicleId = MutableStateFlow("BUS-042")
```

### Adjust Upload Settings
Modify upload simulation in `AppViewModel.kt`:
```kotlin
// WiFi connection check interval (milliseconds)
delay(60000)  // Currently checks every minute
```

### Theme Colors
Customize app colors in `ui/theme/Theme.kt`

## Troubleshooting

**Gradle sync fails**: Ensure you have internet connection and try File → Invalidate Caches / Restart

**Build errors**: Check that Android SDK 34 is installed in SDK Manager

**App crashes on launch**: Verify minimum SDK version (26) is met by your device/emulator

## License

This project is developed as part of a road defect monitoring system capstone project.
