# CrowdSense

CrowdSense is an Android crowd-pressure prototype. Nearby Bluetooth Low Energy (BLE) devices and cellular signal conditions are combined into a live crowd-pressure score. Readings are shared anonymously through Firebase and shown on a MapTiler-powered map.

It estimates local pressure, not an exact people count. BLE visibility, device settings, buildings, and cellular coverage can affect the result.

## How it works

1. The foreground service scans nearby BLE devices and advertises this app's BLE identity.
2. Devices seen in the last 30 seconds and either within 25 m or stronger than -75 dBm are included.
3. CrowdSense app users are detected through a service UUID/manufacturer payload; all other devices are counted as anonymous devices.
4. RSSI estimates proximity, while RSRP/RSRQ cellular degradation contributes congestion pressure.
5. The score, location, and summary data are uploaded once per minute. The map shows fresh shared readings for up to 10 minutes.

> **Crowd-pressure score**
>
> `BLE = (2.5 x app users) + (1.0 x anonymous devices)`
>
> `RSSI multiplier = 1.4 if average RSSI >= -60 dBm; 1.2 if >= -68 dBm; otherwise 1.0`
>
> `cell pressure = clamp((baseline RSRP - current RSRP) / 5 + RSRQ penalty, 0, 5)`
>
> `RSRQ penalty = 0 when > -10 dB; 1 when -15 < RSRQ <= -10 dB; 2 when <= -15 dB`
>
> `final score = (BLE x RSSI multiplier) + (2 x cell pressure)`
>
> Levels: `Low < 8`, `Medium < 20`, `High < 38`, otherwise `Danger`.

## Requirements

- Android Studio Narwhal (2025.1.1) or newer
- JDK 17
- Android SDK Platform 36 and Build Tools 35.0.0+
- An Android 7.0+ device (API 24) with Bluetooth LE; advertising support is recommended
- A Firebase project and a MapTiler API key

The project uses Android Gradle Plugin 8.11 and Gradle 8.13. Android Studio Narwhal supports AGP 8.11, and AGP 8.11 requires JDK 17. See the [Android Studio compatibility table](https://developer.android.com/build/releases/about-agp) and [AGP 8.11 requirements](https://developer.android.com/build/releases/agp-8-11-0-release-notes).

## Get started

```bash
git clone https://github.com/YashBhadange2006/CrowdSense.git
cd CrowdSense
```

Open the folder in Android Studio, allow the Gradle sync to finish, complete the Firebase and MapTiler setup below, then run the `app` configuration on a real device. Grant Bluetooth and location permissions when prompted.

## Firebase setup

1. Create a project in the [Firebase console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.example.ble`.
3. Download `google-services.json` and place it at `app/google-services.json`. It is intentionally ignored by Git.
4. In **Build > Authentication > Sign-in method**, enable **Anonymous**.
5. In **Build > Realtime Database**, create a Realtime Database. Choose a location, then open the **Rules** tab and publish these prototype rules:

```json
{
  "rules": {
    "readings": { ".read": "auth != null", ".write": "auth != null" },
    "latest": { ".read": "auth != null", ".write": "auth != null" },
    "history": { ".read": "auth != null", ".write": "auth != null" }
  }
}
```

6. Add your database URL to the untracked `local.properties` file. Keep the existing `sdk.dir` line:

```properties
FIREBASE_DB_URL=https://YOUR_PROJECT_ID-default-rtdb.YOUR_REGION.firebasedatabase.app
```

The build reads this value into the app without committing it to Git.

No manual database schema is required. The app creates these paths on its first successful upload:

```text
readings/{geohash}/{push-id}  # individual readings
latest/{geohash}              # most recent reading for each area
history/{geohash}/{date}/{hour} # average score and count
```

These rules are suitable only for a prototype because every authenticated anonymous user can read and write the shared nodes. Tighten them before production.

## MapTiler setup

1. Create a free MapTiler account and API key.
2. In the key settings, allow the user-agent `CrowdSense-Android`.
3. Add this line to your untracked `local.properties` file. Keep the existing `sdk.dir` line.

```properties
MAPTILER_API_KEY=your_maptiler_key_here
```

The build reads this value into the app's map resource. Do not commit `local.properties` or share the key. The app uses the MapTiler Streets tile source with required MapTiler and OpenStreetMap attribution.

## Data and privacy

CrowdSense uploads a timestamp, latitude/longitude, geohash, score, crowd level, nearby-device counts, average RSSI, cellular pressure, and an anonymous Firebase user ID. It does not upload Bluetooth device addresses or names.

## Project stack

- Kotlin, Jetpack Compose, and Material 3
- Android BLE scanning and advertising
- Firebase Authentication (anonymous) and Realtime Database
- MapTiler Streets through osmdroid
- Google Play Services Location

## Troubleshooting

- **Map is blank:** confirm `MAPTILER_API_KEY` is in `local.properties`, the key allows `CrowdSense-Android`, then sync and rebuild.
- **Firebase upload fails:** check that `google-services.json`, the database URL, Anonymous Authentication, and the database rules match the steps above.
- **No nearby devices:** use a physical BLE-capable device, enable Bluetooth and location, and grant the requested permissions. BLE results vary by phone and nearby devices.
