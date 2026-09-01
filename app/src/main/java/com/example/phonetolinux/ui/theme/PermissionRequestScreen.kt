package com.example.phonetolinux.ui.theme

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen responsible for checking and requesting necessary Android permissions
 * before allowing the user to proceed to the PairingScreen.
 *
 * @author Stanisław Tlołka
 */
@Composable
fun PermissionCheckScreen(onPermissionsGranted: @Composable () -> Unit) {
    val context = LocalContext.current

    // List of permissions required based on Android version
    val permissionsToRequest = mutableListOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    var hasAllPermissions by remember {
        mutableStateOf(
            permissionsToRequest.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasAllPermissions = permissionsMap.values.all { it }
    }

    if (hasAllPermissions) {
        // If all permissions are granted, render the actual pairing screen
        onPermissionsGranted()
    } else {
        // Otherwise, show a clean screen asking for permissions first
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Wymagane uprawnienia",
                fontSize = 24.sp,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Aby aplikacja mogła bezpiecznie połączyć się z Linuksem, zarządzać SMS-ami oraz uruchomić serwer w tle, potrzebuje dostępu do podstawowych funkcji telefonu.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    launcher.launch(permissionsToRequest.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Przyznaj uprawnienia")
            }
        }
    }
}