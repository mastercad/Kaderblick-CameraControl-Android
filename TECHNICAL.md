# Kaderblick Camera Control - Technical Documentation

## Project Overview

This Android application provides wireless control for the Kaderblick Dual Camera system. It enables users to remotely control, preview, and capture content from two cameras simultaneously via a WiFi connection.

## System Architecture

### Components

1. **MainActivity** (`MainActivity.kt`)
   - Main entry point and UI controller
   - Manages permissions, connections, and user interactions
   - Coordinates between camera controller and network client

2. **DualCameraController** (`DualCameraController.kt`)
   - Manages local camera functionality using Android CameraX API
   - Handles camera preview, switching between cameras, and capture operations
   - Supports three modes: Camera 1, Camera 2, or Both cameras

3. **CameraNetworkClient** (`CameraNetworkClient.kt`)
   - Handles HTTP communication with wireless Kaderblick cameras
   - Uses OkHttp3 for reliable network operations
   - Sends commands for photo capture, video recording, and camera switching

### Communication Protocol

The app communicates with Kaderblick cameras using HTTP POST requests with JSON payloads:

**Connection Test:**
```
GET http://<camera-ip>:8080/status
```

**Capture Photo:**
```
POST http://<camera-ip>:8080/capture
Body: {"action":"capture","camera":1}
```

**Start Recording:**
```
POST http://<camera-ip>:8080/record
Body: {"action":"start_recording","camera":1}
```

**Stop Recording:**
```
POST http://<camera-ip>:8080/record
Body: {"action":"stop_recording","camera":1}
```

**Switch Camera:**
```
POST http://<camera-ip>:8080/switch
Body: {"action":"switch","camera":2}
```

Camera IDs:
- `1`: Camera 1
- `2`: Camera 2
- `0`: Both cameras (dual mode)

## UI Design

### Layout Structure

The main activity layout (`activity_main.xml`) consists of:

1. **Connection Panel** (Top)
   - IP address input field
   - Connect/Disconnect button

2. **Status Bar**
   - Displays connection status

3. **Camera Preview Area** (Center)
   - Split-screen view showing both cameras
   - Each camera preview labeled with "Camera 1" or "Camera 2"

4. **Camera Selector** (Radio Buttons)
   - Camera 1: Show only first camera
   - Camera 2: Show only second camera
   - Dual Camera Mode: Show both cameras

5. **Control Panel** (Bottom)
   - Capture Photo button
   - Start/Stop Recording button
   - Switch Camera button

## Dependencies

### Core Android Libraries
- `androidx.core:core-ktx:1.12.0`
- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`

### Camera Libraries
- `androidx.camera:camera-core:1.3.1`
- `androidx.camera:camera-camera2:1.3.1`
- `androidx.camera:camera-lifecycle:1.3.1`
- `androidx.camera:camera-view:1.3.1`

### Network Libraries
- `com.squareup.okhttp3:okhttp:4.12.0`

## Build Configuration

### Minimum Requirements
- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 34 (Android 14)
- **compileSdk**: 34

### Required Permissions
- `CAMERA`: Access device cameras
- `RECORD_AUDIO`: Record audio with video
- `INTERNET`: Network communication
- `ACCESS_WIFI_STATE`: Check WiFi status
- `ACCESS_NETWORK_STATE`: Check network status
- `CHANGE_WIFI_STATE`: Manage WiFi connections
- `WRITE_EXTERNAL_STORAGE`: Save photos/videos (API ≤ 28)
- `READ_EXTERNAL_STORAGE`: Read photos/videos (API ≤ 32)

## Usage Flow

1. **Startup**
   - App requests camera and audio permissions
   - Initializes camera controller
   - Displays camera preview (defaults to Camera 1)

2. **Connection**
   - User enters Kaderblick camera IP address
   - User taps "Connect"
   - App tests connection to camera system
   - On success, enables control buttons

3. **Operation**
   - User selects camera mode (1, 2, or both)
   - User can:
     - Capture photos locally and remotely
     - Start/stop video recording
     - Switch between camera modes
   - All operations are sent to both local device and wireless cameras

4. **Disconnection**
   - User taps "Disconnect"
   - Control buttons are disabled
   - Camera preview continues on local device

## Error Handling

- **Permission Denied**: App displays error message and exits
- **Connection Failed**: Shows error toast with failure reason
- **Network Errors**: Displays error messages but allows retry
- **Camera Errors**: Logs errors and notifies user

## Future Enhancements

Potential improvements for future versions:

1. **Multi-Camera Support**: Support for more than 2 cameras
2. **Settings Screen**: Configure camera quality, frame rate, etc.
3. **Gallery**: View captured photos and videos
4. **WiFi Direct**: Direct peer-to-peer connection without router
5. **Streaming**: Real-time video streaming from wireless cameras
6. **Recording to Device**: Save videos locally from wireless cameras
7. **Authentication**: Secure connection with password/token
8. **Camera Discovery**: Automatic detection of Kaderblick cameras on network

## Development Notes

### Testing
- The app can be tested on physical devices with cameras
- Emulator testing is limited (no actual camera hardware)
- Network functionality can be tested against mock HTTP server

### Debugging
- Enable verbose logging in CameraNetworkClient for network debugging
- Use Android Studio's Layout Inspector to debug UI issues
- Camera issues can be diagnosed through logcat output

### Known Limitations
- Simultaneous dual camera binding may not work on all Android devices
- Some devices have hardware limitations for concurrent camera access
- Network timeout is set to 30 seconds (may need adjustment for slower networks)

## License

Copyright (c) 2026 Kaderblick
