# PhoneToLinux (Android)

PhoneToLinux to usługa umożliwiająca bezobsługowe połączenie i integrację telefonu z systemem Android z komputerem stacjonarnym Linux w sieci lokalnej.

## Główne Funkcje / Key Features
- **Powiadomienia / Notification Sync**: Przesyłanie powiadomień z telefonu bezpośrednio na pulpit Linuksa.
- **SMS-y i Połączenia / SMS & Calls**: Odczytywanie i wysyłanie wiadomości SMS, powiadomienia o połączeniach przychodzących oraz wykonywanie połączeń z komputera.
- **Wymiana Plików / File Sharing**: Bezpośredni dostęp do plików w telefonie poprzez wbudowany serwer WebDAV.
- **Automatyczne Wykrywanie / Auto-Discovery**: Automatyczne odnajdywanie urządzeń w sieci lokalnej (UDP).

---

## 💻 Aplikacja Towarzysząca dla Linux (Fedora)

> **Ważne:** Dla uzyskania pełnej funkcjonalności wymagana jest aplikacja towarzysząca dla systemu **Fedora Linux**.

Możesz ją zainstalować wykonując poniższe polecenie w terminalu (po włączeniu repozytorium COPR):

```bash
sudo dnf copr enable stanislav1988/phonetolinuxdesktop
sudo dnf install phonetolinuxdesktop
```

---

## Technical Documentation & Google Play Compliance
For detailed technical documentation and permission declarations for Google Play submission:
- [Google Play Permissions Justification (English)](docs/GOOGLE_PLAY_PERMISSIONS_DECLARATION.md)
- [Technical Documentation (English)](docs/TECHNICAL_DOCUMENTATION.md)
- [Dokumentacja Techniczna (Polski)](docs/DOKUMENTACJA_TECHNICZNA.md)

## Current Version
**V3.2.0**
- Added support for sending incoming call events to the desktop client.
- Improved foreground service stability on Android 14+.
