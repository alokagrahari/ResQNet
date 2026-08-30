# ResQNet

Offline-first emergency mesh network. Nearby phones can relay SOS alerts when cellular service is unreliable. A Node.js API stores emergencies and locations in MongoDB when a device later has internet.

This folder is the **single master project**. Android and backend live side by side here — not nested under another `ResQNet` directory.

## Architecture

```text
Frontend (android/app)
    ↓
Location (android/location)     GPS: lat, lon, accuracy, timestamp
    ↓
Emergency / SOS data
    ↓
Mesh (android/mesh)             messageId, nodeId, TTL, hopCount, validation, dedup, forward
    ↓
Transport interface
    ↓
MockTransport                   (mesh unit tests)
    ↓
BleTransport                    (android/ble — Packet over GATT RX/TX)
    ↓
Storage                         (android/storage, Room)
    ↓
HTTP API  →  Node.js backend  →  MongoDB
```

Mesh does not depend on Bluetooth APIs. `:ble` implements `Transport` as `BleTransport` and sends `Packet` on the existing GATT RX/TX path (`MESH:` codec). HELLO/ACK and PING/PONG/DATA are unchanged. Multi-hop routing beyond MeshEngine's existing flood is not added.

Storage uses Room on device and syncs through the backend API. Do not put MongoDB inside Android.

## Layout

```text
SIH ResQNet/
├── android/                 Android Gradle project
│   ├── app/                 Frontend (screens, navigation, SOS UI)
│   ├── core/                Shared Android helpers only
│   ├── mesh/                Mesh engine + MockTransport + unit tests
│   ├── location/            GPS / SOS location data
│   ├── storage/             Room + backend sync
│   ├── ble/                 Proven BLE GATT stack + unit tests
│   ├── settings.gradle.kts
│   └── gradlew.bat
├── backend/                 Node.js + Express (MongoDB behind this API)
└── README.md
```

| Area | Path |
|------|------|
| Frontend | `android/app/` |
| Mesh | `android/mesh/` |
| Location | `android/location/` |
| Shared Android | `android/core/` |
| Storage | `android/storage/` |
| BLE | `android/ble/` |
| Backend | `backend/` |

## Android setup

1. Open **`android/`** in Android Studio (the folder that contains `settings.gradle.kts`).
2. Let Gradle sync. SDK 35 and JDK 17 are required.
3. Run the **app** configuration on a device or emulator.

### Build from the command line

```powershell
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat :mesh:testDebugUnitTest
.\gradlew.bat :ble:testDebugUnitTest
```

Modules:

```text
app → core
app → mesh
app → location
app → storage
app → ble
ble → mesh
```

There are no circular dependencies. Mesh does not depend on `app`. Location does not depend on `app`.

Packages: `com.resqnet.app`, `com.resqnet.core`, `com.resqnet.mesh`, `com.resqnet.location`, `com.resqnet.storage`, `com.resqnet.ble`.

## Backend setup

MongoDB is **backend only**. The Android app does not contain database credentials.

```powershell
cd backend
copy .env.example .env
npm install
npm run dev
```

Production-style start:

```powershell
npm start
```

Default health check: `http://localhost:5000/api/health`

Copy `.env.example` to `.env` and set `PORT` / `MONGO_URI` locally. Do not commit `.env`.

API details: `backend/docs/API.md`.

## What is not wired yet

- **Multi-hop routing** — A → B packet transfer uses MeshEngine's existing receive/validate/dedup/TTL/forward pipeline. Extra routing algorithms are not implemented. Prove A → B on two phones (Network status → Open BLE test → handshake → Send Packet) before adding hops.

Offline mesh SOS works without the backend. Backend sync is for a later Storage module.
