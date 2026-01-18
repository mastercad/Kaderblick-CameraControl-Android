# Kaderblick Camera Control - Android

This Android application controls Kaderblick Dual Camera Wireless via an Android device.

## Features

- **Dual Camera Control**: Simultaneously control two wireless cameras
- **Camera Modes**: Switch between Camera 1, Camera 2, or both cameras
- **Wireless Connection**: Connect to cameras via WiFi/HTTP
- **Photo Capture**: Take photos from either or both cameras
- **Video Recording**: Start/stop video recording on wireless cameras
- **Real-time Preview**: View live camera feeds on your Android device

## Requirements

- Android 7.0 (API 24) or higher
- Camera and Internet permissions
- WiFi connection to Kaderblick camera system

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Build and run on your Android device or emulator

## Usage

1. Launch the app
2. Grant camera and audio permissions when prompted
3. Enter the IP address of your Kaderblick camera system
4. Tap "Connect" to establish connection
5. Use the radio buttons to select camera mode (Camera 1, Camera 2, or Dual)
6. Control your cameras using the buttons:
   - **Capture Photo**: Take a photo from the selected camera(s)
   - **Start/Stop Recording**: Begin or end video recording
   - **Switch Camera**: Cycle through camera modes

## Architecture

- **MainActivity**: Main UI controller
- **DualCameraController**: Manages local camera functionality
- **CameraNetworkClient**: Handles wireless communication with Kaderblick cameras

## Permissions

The app requires the following permissions:
- `CAMERA`: Access device cameras for preview
- `RECORD_AUDIO`: Record audio with video
- `INTERNET`: Communicate with wireless cameras
- `ACCESS_WIFI_STATE`: Check WiFi connection status
- `CHANGE_WIFI_STATE`: Manage WiFi connections

## License

Copyright (c) 2026 Kaderblick