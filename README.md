# BachelorProjektV2 Camera & Sensor Logger

## Overview

BachelorProjektV2 is an Android application that captures images every 10 seconds and logs a range of sensor data. Data includes readings from Accelerometer, Euler Angles, GNSS Coordinates, GNSS Raw Measurements, Gravity, Gyroscope, and Magnetometer.

## APK Location

To install the app on your device, find the APK file in the repository under the `BachelorProjectV2\app\build\outputs\apk` directory. 

## Output Details

All captured images and sensor data logs will be stored in the following directory on your device:

```
/storage/emulated/0/Android/data/com.example.bachelorprojectv2/files/Download/BachelorProjektV2_[TIMESTAMP]/
```

Files generated include:

- `DATA_[TIMESTAMP]_Accelerometer.csv`
- `DATA_[TIMESTAMP]_Euler-Angles.csv`
- `DATA_[TIMESTAMP]_GNSS-Coordinates.csv`
- `DATA_[TIMESTAMP]_GNSS-Raw-Measurements.csv`
- `DATA_[TIMESTAMP]_Gravity.csv`
- `DATA_[TIMESTAMP]_Gyroscop.csv`
- `DATA_[TIMESTAMP]_Magnetometer.csv`

## Permissions

To ensure the app functions correctly, please grant all requested permissions. Denying any essential permissions may cause the app to crash.

## Opening in Android Studio

1. Clone the repository to your local environment.
2. Launch Android Studio.
3. Select "Open an existing Android Studio project".
4. Browse to the repository's location on your machine and select it.
5. Android Studio will then load and sync the project.

## License

The BachelorProjektV2 application is freely available for use.

