package com.example.phonetolinux

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phonetolinux.service.PhoneServerService
import com.example.phonetolinux.ui.theme.PairingScreen

/**
 * Main entry point activity for PhoneToLinux Android client.
 * Enforces permission initialization prior to desktop pairing to guarantee
 * background service stability on Android 14/15 (API 34/35).
 *
 * @author Stanisław Tlołka
 */
class MainActivity : ComponentActivity() {

    // Status message state localized based on system language
    var serviceStatusText by mutableStateOf(HttpUtils.getLocalizedText("waiting_status"))

    // State tracking runtime permissions status
    var hasPermissions by mutableStateOf(false)

    // State controlling whether the device is authenticated and paired with Linux
    var isPaired by mutableStateOf(false)

    // Launcher for handling multiple runtime permissions requests
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPermissions = hasAllPermissions()
        if (hasPermissions && isPaired) {
            triggerServiceAndSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read stored pairing state from persistent SharedPreferences
        isPaired = HttpUtils.isPaired(this)

        // Evaluate initial permissions state upon startup
        hasPermissions = hasAllPermissions()

        // If device has all permissions and is already paired, ensure background service starts immediately
        if (hasPermissions && isPaired) {
            triggerServiceAndSettings()
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when {
                    // STEP 1: Force runtime permissions setup first
                    !hasPermissions -> {
                        PermissionRequestScreen(
                            onRequestPermissions = { checkAndRequestPermissions() }
                        )
                    }

                    // STEP 2: Display PIN pairing screen once permissions are secured (if not already paired)
                    !isPaired -> {
                        PairingScreen(
                            onPairingSuccess = { desktopIp ->
                                // Save persistent pairing info to SharedPreferences
                                HttpUtils.savePairing(this@MainActivity, desktopIp)
                                isPaired = true
                                triggerServiceAndSettings()
                            }
                        )
                    }

                    // STEP 3: Main server management dashboard
                    else -> {
                        MainScreen(
                            statusMessage = serviceStatusText,
                            onStartClicked = { checkAndRequestPermissions() },
                            onOpenSettingsClicked = { openAppNotificationSettings() }
                        )
                    }
                }
            }
        }

        handleCallIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) {
            handleCallIntent(intent)
        }
    }

    // Handles incoming call dispatch intents from the desktop bridge
    private fun handleCallIntent(intent: Intent) {
        if (intent.action == "ACTION_MAKE_CALL") {
            val number = intent.getStringExtra("EXTRA_PHONE_NUMBER")
            if (!number.isNullOrBlank()) {
                triggerDirectCall(number)
            }
        }
    }

    // Triggers direct telephony call or opens dialer depending on permissions
    private fun triggerDirectCall(number: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(callIntent)
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(dialIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error triggering phone call: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermissions = hasAllPermissions()
        isPaired = HttpUtils.isPaired(this)

        if (hasPermissions && isPaired) {
            serviceStatusText = HttpUtils.getLocalizedText("running_status")
        }
    }

    // Evaluates whether all required Android 15 runtime permissions are granted
    private fun hasAllPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Verifies missing permissions and launches system permission dialogs
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            serviceStatusText = HttpUtils.getLocalizedText("requesting_status")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            hasPermissions = true
            if (isPaired) {
                triggerServiceAndSettings()
            }
        }
    }

    // Opens system notification settings page for current application
    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val intentFallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intentFallback)
        }
    }

    // Triggers background server initialization and battery optimization prompt
    private fun triggerServiceAndSettings() {
        startPhoneService()
        checkNotificationListenerPermission()
        requestBatteryOptimizationExemption()
        serviceStatusText = HttpUtils.getLocalizedText("running_status")
    }

    // Safely starts foreground HTTP server service
    private fun startPhoneService() {
        try {
            val serviceIntent = Intent(this, PhoneServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start PhoneServerService: ${e.message}", e)
        }
    }

    // Verifies notification listener service access
    private fun checkNotificationListenerPermission() {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabledListeners == null || !enabledListeners.contains(packageName)) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
    }

    // Prompts user to exempt application from battery optimization policies
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(settingsIntent)
                }
            }
        }
    }
}

/**
 * Initial onboarding Composable screen requesting mandatory runtime permissions.
 */
@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To sync SMS messages, contacts, and phone calls with Linux, PhoneToLinux requires system permissions before desktop pairing.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermissions) {
            Text(text = "Grant Permissions")
        }
    }
}

/**
 * Main dashboard Composable screen displayed after authentication and setup completion.
 */
@Composable
fun MainScreen(statusMessage: String, onStartClicked: () -> Unit, onOpenSettingsClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PhonetoLinux Server",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartClicked) {
            Text(text = HttpUtils.getLocalizedText("start_service_btn"))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenSettingsClicked) {
            Text(text = HttpUtils.getLocalizedText("open_settings_btn"))
        }
    }
}