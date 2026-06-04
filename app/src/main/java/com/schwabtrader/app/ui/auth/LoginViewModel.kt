package com.schwabtrader.app.ui.auth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.schwabtrader.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val isLoggedIn: Boolean get() = authRepository.isLoggedIn
    val isSchwabConnected: Boolean get() = authRepository.isSchwabConnected

    // Web client ID from google-services.json - this should be the web OAuth client ID
    // not the Android client ID, for Firebase Auth to work properly
    val webClientId: String = "YOUR_WEB_CLIENT_ID" // Replace with actual web client ID from Firebase console

    fun signInWithGoogle(launcher: ActivityResultLauncher<Intent>) {
        _uiState.value = LoginUiState.Loading
        val signInClient = authRepository.getGoogleSignInClient(webClientId)
        launcher.launch(signInClient.signInIntent)
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val result = authRepository.firebaseSignInWithGoogle(account)
                if (result.isSuccess) {
                    _uiState.value = LoginUiState.Success
                } else {
                    _uiState.value = LoginUiState.Error(
                        result.exceptionOrNull()?.message ?: "Sign-in failed"
                    )
                }
            } catch (e: ApiException) {
                val errorMessage = when (e.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign-in cancelled"
                    GoogleSignInStatusCodes.NETWORK_ERROR -> "Network error. Please check your connection."
                    GoogleSignInStatusCodes.INVALID_ACCOUNT -> "Invalid account"
                    else -> "Google sign-in failed: ${e.localizedMessage}"
                }
                _uiState.value = LoginUiState.Error(errorMessage)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
