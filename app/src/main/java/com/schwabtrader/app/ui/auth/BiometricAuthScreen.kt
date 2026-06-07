package com.schwabtrader.app.ui.auth

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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

@Composable
fun BiometricAuthScreen(
    onAuthSuccess: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var authFailed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var triggerCount by remember { mutableStateOf(0) }

    fun showPrompt() {
        val activity = context.findFragmentActivity() ?: return
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onAuthSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        authFailed = true
                        errorMessage = "Authentication failed. Try again."
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authFailed = true
                        errorMessage = if (
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) "Tap Unlock to try again." else errString.toString()
                    }
                }
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock App")
                .setSubtitle("Verify your identity to continue")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(promptInfo)
        } else {
            onAuthSuccess()
        }
    }

    // Auto-trigger on first entry and whenever the user taps Unlock
    LaunchedEffect(triggerCount) {
        showPrompt()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Unlock Required",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (authFailed && errorMessage.isNotEmpty()) errorMessage
            else "Use your biometrics or screen lock to access the app.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                authFailed = false
                errorMessage = ""
                triggerCount++
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("Unlock")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onSignOut) {
            Text("Sign Out", color = TextSecondary)
        }
    }
}
