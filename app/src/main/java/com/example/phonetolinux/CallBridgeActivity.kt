package com.example.phonetolinux

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * Transparent bridge activity that bypasses background activity start restrictions.
 * Instantly triggers system phone call and closes itself.
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
            startActivity(callIntent)
            Log.d("CallBridgeActivity", "Triggered call to $number")
        } catch (e: Exception) {
            Log.e("CallBridgeActivity", "Failed to start call intent: ${e.message}", e)
        }
    }
}