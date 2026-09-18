# Technical Documentation - PhoneToLinux (Android)

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

## 6. Permissions
The app requires a significant number of permissions to function correctly, including:
- `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`
- `READ_CONTACTS`, `READ_CALL_LOG`, `CALL_PHONE`
- `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`
- `MANAGE_EXTERNAL_STORAGE` (on Android 11+)
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`

## 7. Build and Dependencies
- **Language**: Kotlin 1.9+
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16 candidate)
- **Key Libraries**:
    - Jetpack Compose (UI)
    - NanoHTTPD (WebDAV Server)
    - Retrofit & Gson (Networking/JSON)
    - Kotlin Coroutines (Async operations)
