package com.example.phonetolinux

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * Transparent bridge activity that bypasses background activity start restrictions.
 * Explicitly passes Background Activity Launch (BAL) permissions to the system dialer.
 */
class CallBridgeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent.getStringExtra("EXTRA_PHONE_NUMBER")
        if (!number.isNullOrBlank()) {
            triggerCall(number)
        }

        finish()
    }

    private fun triggerCall(number: String) {
        try {
            val uri = Uri.parse("tel:${Uri.encode(number)}")
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(action, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Explicitly grant Background Activity Launch permission to the dialer intent on Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                startActivity(callIntent, options.toBundle())
            } else {
                startActivity(callIntent)
            }

            Log.d("CallBridgeActivity", "Successfully triggered direct dialer intent for $number")
        } catch (e: Exception) {
            Log.e("CallBridgeActivity", "Failed to start call intent: ${e.message}", e)
        }
    }
}