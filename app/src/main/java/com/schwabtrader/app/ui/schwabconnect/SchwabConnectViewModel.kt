package com.schwabtrader.app.ui.schwabconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SchwabConnectUiState {
    object Idle : SchwabConnectUiState()
    object Loading : SchwabConnectUiState()
    object Success : SchwabConnectUiState()
    data class Error(val message: String) : SchwabConnectUiState()
}

@HiltViewModel
class SchwabConnectViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SchwabConnectUiState>(SchwabConnectUiState.Idle)
    val uiState: StateFlow<SchwabConnectUiState> = _uiState.asStateFlow()

    private val _authUrl = MutableStateFlow("")
    val authUrl: StateFlow<String> = _authUrl.asStateFlow()

    init {
        buildAuthUrl()
    }

    fun buildAuthUrl() {
        _authUrl.value = authRepository.buildSchwabAuthUrl()
    }

    fun handleOAuthCallback(code: String) {
        viewModelScope.launch {
            _uiState.value = SchwabConnectUiState.Loading
            val result = authRepository.exchangeCodeForTokens(code)
            if (result.isSuccess) {
                _uiState.value = SchwabConnectUiState.Success
            } else {
                _uiState.value = SchwabConnectUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to exchange OAuth code"
                )
            }
        }
    }

    fun clearError() {
        if (_uiState.value is SchwabConnectUiState.Error) {
            _uiState.value = SchwabConnectUiState.Idle
        }
    }

    fun disconnect() {
        authRepository.disconnectSchwab()
        _uiState.value = SchwabConnectUiState.Idle
        buildAuthUrl()
    }
}
