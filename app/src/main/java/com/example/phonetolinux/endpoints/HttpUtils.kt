package com.example.phonetolinux

import android.content.Context
import java.net.URLDecoder
import java.util.Locale

/**
 * Helper utilities for processing HTTP requests, URL parameters,
 * multi-language support based on system locale, and persistent pairing storage.
 */
object HttpUtils {

    private const val PREFS_NAME = "phonetolinux_prefs"
    private const val KEY_DESKTOP_IP = "desktop_ip"
    private const val KEY_IS_PAIRED = "is_paired"

    /**
     * Persistently saves pairing status and desktop IP to Android SharedPreferences.
     */
    fun savePairing(context: Context, desktopIp: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DESKTOP_IP, desktopIp.trim())
            .putBoolean(KEY_IS_PAIRED, true)
            .apply()
    }

    /**
     * Retrieves the stored desktop IP address from SharedPreferences.
     */
    fun getDesktopIp(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DESKTOP_IP, null)
    }

    /**
     * Checks whether the application has a saved, active pairing status.
     */
    fun isPaired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_PAIRED, false)
    }

    /**
     * Clears saved pairing status and stored IP address (unpairs device).
     */
    fun clearPairing(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_DESKTOP_IP)
            .putBoolean(KEY_IS_PAIRED, false)
            .apply()
    }

    /**
     * Returns localized text based on the user's current system language.
     * Supported languages: Polish (pl), German (de), French (fr), Russian (ru),
     * and English (en) as default fallback.
     */
    fun getLocalizedText(key: String): String {
        val lang = Locale.getDefault().language

        return when (key) {
            "pairing_title" -> when (lang) {
                "pl" -> "Sparuj z komputerem Linux"
                "de" -> "Mit Linux-PC koppeln"
                "fr" -> "Jumeler avec le PC Linux"
                "ru" -> "Сопряжение с ПК Linux"
                else -> "Pair with Linux Desktop"
            }
            "ip_label" -> when (lang) {
                "pl" -> "Adres IP komputera (np. 192.168.100.92)"
                "de" -> "Desktop IP-Adresse (z.B. 192.168.100.92)"
                "fr" -> "Adresse IP du PC (ex: 192.168.100.92)"
                "ru" -> "IP-адрес ПК (например, 192.168.100.92)"
                else -> "Desktop IP Address (e.g., 192.168.100.92)"
            }
            "pin_label" -> when (lang) {
                "pl" -> "6-cyfrowy PIN (np. 922653)"
                "de" -> "6-stellige PIN (z.B. 922653)"
                "fr" -> "PIN à 6 chiffres (ex: 922653)"
                "ru" -> "6-значный PIN (например, 922653)"
                else -> "6-Digit PIN (e.g., 922653)"
            }
            "pair_button" -> when (lang) {
                "pl" -> "Sparuj urządzenie"
                "de" -> "Gerät koppeln"
                "fr" -> "Jumeler l'appareil"
                "ru" -> "Сопрячь устройство"
                else -> "Pair Device"
            }
            "connecting" -> when (lang) {
                "pl" -> "Łączenie z komputerem..."
                "de" -> "Verbindung zum Desktop..."
                "fr" -> "Connexion au bureau..."
                "ru" -> "Подключение к ПК..."
                else -> "Connecting to desktop..."
            }
            "waiting_status" -> when (lang) {
                "pl" -> "Status: Oczekiwanie na uruchomienie..."
                "de" -> "Status: Warten auf Start..."
                "fr" -> "Status: En attente de démarrage..."
                "ru" -> "Статус: Ожидание запуска..."
                else -> "Status: Waiting to start..."
            }
            "running_status" -> when (lang) {
                "pl" -> "Status: Usługa w tle uruchomiona!"
                "de" -> "Status: Hintergrunddienst läuft!"
                "fr" -> "Status: Service d'arrière-plan actif !"
                "ru" -> "Статус: Фоновая служба запущенна!"
                else -> "Status: Background service running!"
            }
            "requesting_status" -> when (lang) {
                "pl" -> "Status: Prośba o uprawnienia..."
                "de" -> "Status: Berechtigungen anfordern..."
                "fr" -> "Status: Demande d'autorisations..."
                "ru" -> "Статус: Запрос разрешений..."
                else -> "Status: Requesting permissions..."
            }
            "start_service_btn" -> when (lang) {
                "pl" -> "Uruchom Usługę i Uprawnienia"
                "de" -> "Dienst & Berechtigungen starten"
                "fr" -> "Démarrer le service et permissions"
                "ru" -> "Запустить службу и права"
                else -> "Start Service & Permissions"
            }
            "open_settings_btn" -> when (lang) {
                "pl" -> "Otwórz Ustawienia Powiadomień"
                "de" -> "Benachrichtigungseinstellungen öffnen"
                "fr" -> "Ouvrir les paramètres de notification"
                "ru" -> "Открыть настройки уведомлений"
                else -> "Open Notification Settings"
            }
            else -> ""
        }
    }

    /**
     * Parses HTTP request lines to extract query parameters.
     */
    fun extractQueryParam(requestLine: String, paramName: String): String {
        try {
            val parts = requestLine.split(" ")
            if (parts.size < 2) return ""
            val pathAndQuery = parts[1]
            val queryParts = pathAndQuery.split("?")
            if (queryParts.size < 2) return ""

            val query = queryParts[1]
            val params = query.split("&")
            for (param in params) {
                val keyValue = param.split("=")
                if (keyValue.size == 2 && keyValue[0] == paramName) {
                    return URLDecoder.decode(keyValue[1], "UTF-8")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}