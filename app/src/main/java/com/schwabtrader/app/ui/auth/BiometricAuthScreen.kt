package com.schwabtrader.app.ui.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.schwabtrader.app.ui.theme.AccentBlue
import com.schwabtrader.app.ui.theme.TextPrimary
import com.schwabtrader.app.ui.theme.TextSecondary

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private sealed class BiometricState {
    object Idle : BiometricState()
    data class NeedsSetup(val message: String) : BiometricState()
    data class Failed(val message: String) : BiometricState()
}

@Composable
fun BiometricAuthScreen(
    onAuthSuccess: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var biometricState by remember { mutableStateOf<BiometricState>(BiometricState.Idle) }
    var triggerCount by remember { mutableStateOf(0) }

    fun openSecuritySettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun showPrompt() {
        val activity = context.findFragmentActivity() ?: return
        val biometricManager = BiometricManager.from(context)

        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricState = BiometricState.Idle
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onAuthSuccess()
                        }

                        override fun onAuthenticationFailed() {
                            biometricState = BiometricState.Failed("Authentication failed. Try again.")
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            biometricState = if (
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                BiometricState.Failed("Tap Unlock to try again.")
                            } else {
                                BiometricState.Failed(errString.toString())
                            }
                        }
                    }
                )
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock App")
                    .setSubtitle("Verify your identity to continue")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    .build()
                prompt.authenticate(promptInfo)
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                biometricState = BiometricState.NeedsSetup(
                    "No biometric or screen lock is set up on this device. " +
                        "Set up a fingerprint, face, or PIN to access the app."
                )
            }

            else -> {
                // Hardware not present or unavailable — allow through
                onAuthSuccess()
            }
        }
    }

    LaunchedEffect(triggerCount) {
        showPrompt()
    }

    val state = biometricState
    val needsSetup = state is BiometricState.NeedsSetup

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (needsSetup) Icons.Default.Lock else Icons.Default.Fingerprint,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (needsSetup) "Security Setup Required" else "Unlock Required",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (state) {
                is BiometricState.NeedsSetup -> state.message
                is BiometricState.Failed -> state.message
                else -> "Use your biometrics or screen lock to access the app."
            },
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (needsSetup) {
            Button(
                onClick = { openSecuritySettings() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Open Security Settings")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { triggerCount++ }
            ) {
                Text("I've set it up, try again", color = AccentBlue)
            }
        } else {
            Button(
                onClick = {
                    biometricState = BiometricState.Idle
                    triggerCount++
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Unlock")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onSignOut) {
            Text("Sign Out", color = TextSecondary)
        }
    }
}
