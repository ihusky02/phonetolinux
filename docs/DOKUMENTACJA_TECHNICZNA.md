# Dokumentacja Techniczna - PhoneToLinux (Android)

## 1. Wstęp
PhoneToLinux to aplikacja na system Android, która służy jako most (bridge) pomiędzy urządzeniem mobilnym a komputerem z systemem Linux. Działa jako serwer udostępniający funkcjonalności telefonu (SMS, połączenia, powiadomienia, kontakty i pliki) klientowi stacjonarnemu.

## 2. Architektura
Aplikacja oparta jest na architekturze zorientowanej na usługi (service-oriented architecture). Główna logika przetwarzana jest w usłudze działającej w tle (foreground service), natomiast interfejs użytkownika (Jetpack Compose) służy do konfiguracji, nadawania uprawnień i monitorowania statusu.

### Główne Moduły:
- **UI (`pl.stanislawtlolka.phonetolinux.ui`)**: Zarządza interakcją z użytkownikiem za pomocą Jetpack Compose.
- **Service (`pl.stanislawtlolka.phonetolinux.service`)**: Serce aplikacji, uruchamiające serwery i nasłuchujące zdarzeń systemowych.
- **Server (`pl.stanislawtlolka.phonetolinux.server`)**: Zawiera implementacje serwerów (np. WebDAV).
- **Endpoints (`pl.stanislawtlolka.phonetolinux.endpoints`)**: Modułowe programy obsługi konkretnych żądań API.
- **Security (`pl.stanislawtlolka.phonetolinux.security`)**: Obsługuje odkrywanie UDP oraz mechanizmy parowania.

## 3. Kluczowe Komponenty

### MainActivity
Punkt wejściowy aplikacji. Odpowiada za:
- **Uprawnienia**: Wymusza nadanie uprawnień do SMS-ów, połączeń, kontaktów i pamięci masowej.
- **Parowanie**: Obsługuje proces parowania z klientem Linux za pomocą kodu PIN.
- **Panel sterowania**: Wyświetla aktualny status serwera i pozwala na otwarcie ustawień systemowych.

### PhoneServerService
Usługa typu foreground, która utrzymuje działanie serwera. Zarządza:
- **Własny Serwer HTTP (Port 5000)**: Serwer oparty na socketach, obsługujący żądania API przez system wtyczek (Endpoints).
- **Strumień SSE (`/sms_stream`)**: Utrzymuje połączenie do przesyłania zdarzeń w czasie rzeczywistym (SMS, status połączeń).
- **Serwer WebDAV (Port 5001)**: Oparty na NanoHTTPD, umożliwia montowanie pamięci telefonu jako dysku sieciowego w systemie Linux.
- **Odkrywanie UDP (Port 8889)**: Rozgłasza obecność urządzenia w sieci lokalnej.
- **Monitorowanie Telefonii**: Wykorzystuje `TelephonyManager` do śledzenia stanów połączeń głosowych.

### NotificationBridgeService
Rozszerza `NotificationListenerService`, aby przechwytywać powiadomienia systemowe i przesyłać je do klienta na komputerze.

## 4. Komunikacja Sieciowa

### Punkty Końcowe API (Endpoints)
Aplikacja wykorzystuje modułowy system punktów końcowych. Każdy z nich implementuje wspólny interfejs:
- `PingEndpoint`: Sprawdzanie łączności.
- `ContactsEndpoint`: Dostęp do listy kontaktów.
- `ConversationsEndpoint` & `MessagesEndpoint`: Historia wiadomości SMS/MMS.
- `CallEndpoint`: Akcje związane z połączeniami.
- `SendSmsEndpoint`: Wysyłanie wiadomości SMS z telefonu.
- `WebDavServer`: Dostęp do systemu plików.

### Zdarzenia w Czasie Rzeczywistym (SSE)
Zdarzenia są przesyłane za pomocą Server-Sent Events (SSE). Klient łączy się z adresem `GET /sms_stream`.
Obsługiwane zdarzenia:
- `incoming_sms`
- `incoming_call`
- `call_active`
- `call_ended`

## 5. Bezpieczeństwo
- **Pairing Secret**: Unikalny klucz generowany podczas pierwszego parowania.
- **Parowanie PIN**: Zapewnia, że tylko autoryzowane urządzenia mogą się połączyć.
- **Sieć Lokalna**: Serwer nasłuchuje wyłącznie na interfejsach sieci lokalnej.

## 6. Uprawnienia
Aplikacja wymaga szerokiego wachlarza uprawnień, w tym:
- `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`
- `READ_CONTACTS`, `READ_CALL_LOG`, `CALL_PHONE`
- `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`
- `MANAGE_EXTERNAL_STORAGE` (od Android 11)
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`

## 7. Technologie i Zależności
- **Język**: Kotlin 1.9+
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16 candidate)
- **Kluczowe Biblioteki**:
    - Jetpack Compose (Interfejs)
    - NanoHTTPD (Serwer WebDAV)
    - Retrofit & Gson (Sieć/JSON)
    - Kotlin Coroutines (Asynchroniczność)
