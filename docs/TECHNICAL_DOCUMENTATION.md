# Technical Documentation - PhoneToLinux (Android)

> **Google Play Permissions Declaration:** For the official permission declaration, policy compliance justification, and technical architecture explanation for Google Play review, see [Google Play Permissions Declaration](GOOGLE_PLAY_PERMISSIONS_DECLARATION.md).

## 1. Introduction
PhoneToLinux is an Android application designed to bridge the gap between an Android device and a Linux desktop environment. It serves as a server that exposes phone functionalities (SMS, calls, notifications, contacts, and files) to a desktop client.

## 2. Architecture Overview
The application follows a service-oriented architecture, where a background foreground service handles most of the logic, while a Jetpack Compose-based UI handles onboarding, permissions, and status monitoring.

### Core Modules:
- **UI (`pl.stanislawtlolka.phonetolinux.ui`)**: Manages user interaction using Jetpack Compose.
- **Service (`pl.stanislawtlolka.phonetolinux.service`)**: The heart of the app, running a foreground service with multiple servers.
- **Server (`pl.stanislawtlolka.phonetolinux.server`)**: Contains server implementations (WebDAV).
- **Endpoints (`pl.stanislawtlolka.phonetolinux.endpoints`)**: Modular handlers for specific API requests.
- **Security (`pl.stanislawtlolka.phonetolinux.security`)**: Handles UDP discovery and pairing secrets.

## 3. Key Components

### MainActivity
The entry point of the app. It manages:
- **Runtime Permissions**: Requests extensive permissions required for SMS, Calls, Contacts, and Storage.
- **Onboarding/Pairing**: Handles the initial PIN-based pairing with the Linux client.
- **Dashboard**: Displays the current status of the background service.

### PhoneServerService
A foreground service that ensures the bridge remains active. It manages:
- **Custom HTTP Server (Port 5000)**: A socket-based server handling API requests via a plugin system (Endpoints).
- **SSE Stream (`/sms_stream`)**: Maintains a persistent connection to broadcast real-time events (SMS, call states) to the desktop.
- **WebDAV Server (Port 5001)**: Powered by NanoHTTPD, allowing the desktop to mount the phone's storage.
- **UDP Discovery (Port 8889)**: Broadcasts the device's presence for easy discovery by the desktop client.
- **Telephony Monitoring**: Uses `TelephonyManager` (and `TelephonyCallback` on newer Android versions) to track call states.

### NotificationBridgeService
Extends `NotificationListenerService` to intercept system notifications and forward them to the desktop client.

## 4. Network & Communication

### API Endpoints
The app uses a modular endpoint system. Each endpoint implements a common interface and is registered in `PhoneServerService`.
- `PingEndpoint`: Connectivity check.
- `ContactsEndpoint`: Provides access to the phone's contact list.
- `ConversationsEndpoint` & `MessagesEndpoint`: Handle SMS/MMS history.
- `CallEndpoint`: Handles call-related actions.
- `SendSmsEndpoint`: Triggers sending SMS from the phone.
- `WebDavServer`: Provides file system access.

### Real-time Events (SSE)
Real-time events are pushed via Server-Sent Events (SSE). The desktop client connects to `GET /sms_stream`.
Events include:
- `incoming_sms`
- `incoming_call`
- `call_active`
- `call_ended`

## 5. Security
- **Pairing Secret**: A persistent secret is generated and shared during the initial pairing process.
- **PIN Pairing**: Ensures only authorized desktop clients can connect.
- **Local Network**: The server only listens on local network interfaces.

## 6. Comprehensive Permission Justification

PhoneToLinux operates as a system integration bridge between Android and Linux. To provide seamless desktop mirroring without cloud intermediaries, the app requires specific Android permissions. Each permission group is directly linked to a core architectural component:

### 1. Network & Connectivity Permissions
- `android.permission.INTERNET`: Required to host local HTTP (Port 5000) and WebDAV (Port 5001) servers and handle local socket connections.
- `android.permission.ACCESS_NETWORK_STATE` & `android.permission.ACCESS_WIFI_STATE`: Monitors local network state changes (e.g., Wi-Fi IP address changes) to maintain active desktop connections.
- `android.permission.CHANGE_WIFI_MULTICAST_STATE` & `android.permission.CHANGE_NETWORK_STATE`: Required by `UdpDiscoveryServer` (Port 8889) to send and receive UDP broadcast packets for automatic device discovery on local Wi-Fi networks.

### 2. SMS Messaging Permissions Group
- `android.permission.RECEIVE_SMS` & `android.permission.READ_SMS`: Intercepts incoming SMS messages and streams real-time `incoming_sms` events over the local SSE stream (`/sms_stream`), displaying desktop popups on the Linux PC and providing conversation thread history via `ConversationsEndpoint` and `MessagesEndpoint`.
- `android.permission.SEND_SMS`: Enables sending text messages composed on the Linux desktop via `SendSmsEndpoint` using `SmsManager`.
- `android.permission.WRITE_SMS`: Ensures sent messages are correctly written into the device's SMS database provider to keep message threads in sync across phone and desktop.

### 3. Contacts Permission
- `android.permission.READ_CONTACTS`: Used by `ContactsEndpoint` to fetch contact names and match phone numbers, ensuring the Linux desktop displays contact names instead of raw phone numbers in SMS threads and call alerts.

### 4. Telephony & Call Management Permissions Group
- `android.permission.READ_PHONE_STATE` & `android.permission.READ_CALL_LOG`: Used by `PhoneServerService` via `TelephonyManager` and `TelephonyCallback` to detect call states (ringing, off-hook, ended) and stream `incoming_call`, `call_active`, and `call_ended` events to the Linux desktop.
- `android.permission.CALL_PHONE`: Triggered when the user initiates a call from the Linux desktop interface (`CallEndpoint`) to dial the contact on the phone.
- `android.permission.ANSWER_PHONE_CALLS`: Allows answering ringing calls remotely from the Linux desktop.
- `android.permission.WRITE_CALL_LOG` & `android.permission.MANAGE_OWN_CALLS`: Maintains call log synchronization and self-managed call state handling.

### 5. Storage & File Sharing Permissions Group
- `android.permission.MANAGE_EXTERNAL_STORAGE` (Android 11+ / API 30+): Fundamental requirement for `WebDavServer` (Port 5001). WebDAV protocol allows the Linux desktop file explorer (Nautilus, Dolphin, Thunar) to mount the phone's internal storage as a native network drive. It requires full read/write directory tree traversal across user folders (Documents, Downloads, DCIM, Music) without interactive SAF document pickers for every single network file operation.
- `android.permission.READ_EXTERNAL_STORAGE` & `android.permission.WRITE_EXTERNAL_STORAGE`: Legacy file storage permissions for older Android versions (API <= 29).

### 6. Foreground Service & Notification Mirroring
- `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`: Keeps `PhoneServerService` alive in the background on Android 14+ with an active notification, preventing the Android OS from killing the local HTTP/WebDAV/UDP servers.
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`: Used by `NotificationBridgeService` (`NotificationListenerService`) to intercept status bar notifications from Android apps and mirror them in real time to the Linux desktop notification daemon.
- `android.permission.POST_NOTIFICATIONS`: Displays the required persistent foreground service status notification on Android 13+ (API 33+).

### 7. Audio & Bluetooth Control Permissions
- `android.permission.MODIFY_AUDIO_SETTINGS`: Adjusts phone audio routing during desktop-controlled call events.
- `android.permission.BLUETOOTH`, `android.permission.BLUETOOTH_ADMIN`, `android.permission.BLUETOOTH_CONNECT`, `android.permission.BLUETOOTH_SCAN`, `android.permission.BLUETOOTH_ADVERTISE`: Used by `BluetoothAudioEndpoint` to manage Bluetooth audio SCO connections during desktop calls.

---

## 7. Build and Dependencies
- **Language**: Kotlin 1.9+
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16 candidate)
- **Key Libraries**:
    - Jetpack Compose (UI)
    - NanoHTTPD (WebDAV Server)
    - Retrofit & Gson (Networking/JSON)
    - Kotlin Coroutines (Async operations)
