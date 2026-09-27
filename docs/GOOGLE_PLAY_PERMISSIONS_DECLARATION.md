# Google Play Permissions Declaration & Functional Justification

**Application Name:** PhoneToLinux  
**Package Name:** `pl.stanislawtlolka.phonetolinux`  
**Primary Category:** Companion Device App / Desktop Integration Utility  
**Companion Linux App Repository (COPR):** `stanislav1988/phonetolinuxdesktop`  

---

## 1. Executive Summary & Core App Purpose

**PhoneToLinux** is a **Companion Device / System Bridge application** engineered to integrate an Android smartphone with a Linux desktop environment (Fedora Linux) over a local Wi-Fi network (LAN). 

The app operates as a local background server exposing real-time phone functionalities (SMS texting, call alerts & control, notification mirroring, contact sync, and WebDAV remote file access) directly to the user's Linux desktop application (`phonetolinuxdesktop`).

> **Crucial Privacy Notice:** All data transfers (SMS, calls, notifications, and files) occur **100% locally** between the Android phone and the Linux desktop on the same Wi-Fi network. **No user data is uploaded to any external server or third-party cloud service.**

---

## 2. Linux-Android Communication Architecture

The Android application operates a multi-protocol local server architecture listening on local network ports:

1. **UDP Local Auto-Discovery (Port 8889):** Broadcasts device presence on the local subnet to allow the Linux desktop client to discover the phone automatically without manual IP configuration.
2. **HTTP API & Event Stream (Port 5000):** 
   - **REST API Endpoints:** Handles request-response actions from the Linux desktop (e.g., fetching contacts, fetching conversation history, initiating calls, sending SMS).
   - **Server-Sent Events (SSE Stream `/sms_stream`):** Maintains a persistent local TCP connection to stream real-time events (incoming SMS, incoming call alerts, call state changes) to the Linux desktop.
3. **WebDAV File Server (Port 5001):** Powered by NanoHTTPD, allowing the Linux desktop file manager (Nautilus, Dolphin, Thunar) to mount the Android device storage as a native network drive.
4. **Notification Listener Service:** Intercepts status bar notifications and relays them to the Linux desktop notification daemon.

---

## 3. Justification of High-Risk / Sensitive Permissions

To comply with Google Play Policies regarding **SMS & Call Log Permissions** and **All Files Access (`MANAGE_EXTERNAL_STORAGE`)**, below is the detailed technical justification for each requested permission group.

---

### A. SMS Permissions Group
- `android.permission.READ_SMS`
- `android.permission.SEND_SMS`
- `android.permission.RECEIVE_SMS`
- `android.permission.WRITE_SMS`

#### Core Functionality Justification:
- **Core Feature:** Remote SMS Messaging & Sync on Linux Desktop.
- **Why it is required:** The primary purpose of PhoneToLinux is to allow users to view their SMS conversations and send/reply to SMS text messages directly from their Linux computer without picking up their phone.
- **How it works:**
  - `RECEIVE_SMS` & `READ_SMS`: Triggers an `incoming_sms` event over the local SSE stream to instantly display a desktop popup notification on Linux when a text message arrives.
  - `SEND_SMS`: Allows the user to compose and send an SMS from the Linux desktop app, which calls the local Android `SendSmsEndpoint` to transmit the message via `SmsManager`.
  - `WRITE_SMS`: Ensures sent messages are properly saved into the Android system SMS provider database so thread histories remain synchronized.
- **Impact of removal:** Removing any SMS permission completely breaks the SMS synchronization feature, defeating the core purpose of the companion app.

---

### B. Call & Telephony Permissions Group
- `android.permission.READ_CALL_LOG`
- `android.permission.WRITE_CALL_LOG`
- `android.permission.CALL_PHONE`
- `android.permission.ANSWER_PHONE_CALLS`
- `android.permission.READ_PHONE_STATE`

#### Core Functionality Justification:
- **Core Feature:** Desktop Call Alerts, Remote Dialing & Call Termination.
- **Why it is required:** PhoneToLinux alerts the user on their Linux screen when their phone receives a phone call, shows caller information, allows starting calls from desktop contact lists, and enables hanging up or answering calls remotely.
- **How it works:**
  - `READ_PHONE_STATE` & `READ_CALL_LOG`: Detects call state changes (ringing, off-hook, idle) and broadcasts `incoming_call`, `call_active`, and `call_ended` events to the Linux desktop.
  - `CALL_PHONE`: Triggered when a user clicks "Call" on a contact within the Linux desktop interface (`CallEndpoint`), instructing Android to dial the specified number.
  - `ANSWER_PHONE_CALLS`: Allows answering ringing calls remotely from the desktop.
- **Impact of removal:** Without telephony permissions, call notifications and desktop dialing cannot function, disabling remote call management.

---

### C. All Files Access Permission (`MANAGE_EXTERNAL_STORAGE`)
- `android.permission.MANAGE_EXTERNAL_STORAGE`

#### Core Functionality Justification:
- **Core Feature:** Local WebDAV Network File Server (`WebDavServer` on Port 5001).
- **Policy Exception Category:** Network File Manager / Storage Access Server.
- **Why it is required:** The app embeds a full WebDAV server that allows the Linux desktop file explorer (e.g., GNOME Files / KDE Dolphin) to mount the phone's storage over Wi-Fi.
- **Technical Requirement:** WebDAV protocol requires full read/write access across all user directories (Documents, Downloads, DCIM, Music, custom folders) as requested by the connected WebDAV client on the Linux PC.
- **Why Storage Access Framework (SAF) is insufficient:** SAF cannot be integrated into a standard background WebDAV server protocol, as WebDAV operates on POSIX-style file paths and directory tree traversal without user UI picker interactions for every single file operation.
- **Impact of removal:** The WebDAV file server cannot serve or write files, breaking remote file management on Linux.

---

### D. Foreground Service & Notification Listener
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
- `android.permission.POST_NOTIFICATIONS`

#### Core Functionality Justification:
- **Foreground Service:** Keeps the local HTTP/WebDAV/UDP server alive in the background so the connection to the Linux desktop remains active even when the screen is turned off. A persistent notification is displayed informing the user that the server is running.
- **Notification Listener:** Reads incoming app notifications (e.g., messaging apps, system alerts) and forwards them to the Linux desktop notification daemon.

---

## 4. Privacy, Security & Data Handling Compliance

1. **Zero External Data Transmission:**
   - Data never leaves the user's local network (LAN).
   - No cloud servers, no remote analytics, no advertising SDKs.
2. **User Control & Transparency:**
   - The user must perform an explicit **PIN-based pairing process** before any connection from a desktop is accepted.
   - The foreground service displays a persistent notification whenever the server is active, allowing the user to stop the service at any time.
   - Full disclosure is provided in the app's Privacy Policy.

---

## 5. Summary for Google Play Review Team

PhoneToLinux is a legitimate **Device Companion Application** for Linux users. The requested permissions (`SMS`, `CALL_LOG`, `CALL_PHONE`, `MANAGE_EXTERNAL_STORAGE`) are strictly required to perform local device-to-desktop synchronization. All features operate locally, transparently, and securely without third-party data collection.
