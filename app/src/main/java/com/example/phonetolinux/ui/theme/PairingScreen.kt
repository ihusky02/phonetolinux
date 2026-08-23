package com.example.phonetolinux.ui.theme

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phonetolinux.HttpUtils
import com.example.phonetolinux.data.PairingRequest
import com.example.phonetolinux.network.PairingApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Composable screen allowing the user to input the Linux desktop IP address
 * and the 6-digit PIN to establish a secure pairing session.
 */
@Composable
fun PairingScreen(onPairingSuccess: () -> Unit) {
    var ipAddress by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf(HttpUtils.getLocalizedText("enter_ip_pin_hint")) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = HttpUtils.getLocalizedText("pairing_title"),
            fontSize = 24.sp,
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text(HttpUtils.getLocalizedText("ip_label")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pinCode,
            onValueChange = { if (it.length <= 6) pinCode = it.filter { char -> char.isDigit() } },
            label = { Text(HttpUtils.getLocalizedText("pin_label")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (ipAddress.isBlank() || pinCode.length != 6) {
                    Toast.makeText(context, HttpUtils.getLocalizedText("toast_invalid_input"), Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                statusMessage = HttpUtils.getLocalizedText("connecting")

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val retrofit = Retrofit.Builder()
                            .baseUrl("http://$ipAddress:5000/")
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()

                        val api = retrofit.create(PairingApiService::class.java)

                        val androidMac = "02:00:00:00:00:01"
                        val requestPayload = PairingRequest(
                            androidMacAddress = androidMac,
                            pairingPin = pinCode
                        )

                        val response = api.sendPairingRequest("http://$ipAddress:5000/pair/", requestPayload)

                        if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, HttpUtils.getLocalizedText("toast_success"), Toast.LENGTH_LONG).show()
                                onPairingSuccess()
                            }
                        } else {
                            launch(Dispatchers.Main) {
                                statusMessage = HttpUtils.getLocalizedText("pairing_failed")
                                isLoading = false
                            }
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            statusMessage = "${HttpUtils.getLocalizedText("connection_error")} ${e.localizedMessage}"
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(HttpUtils.getLocalizedText("pair_button"))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}