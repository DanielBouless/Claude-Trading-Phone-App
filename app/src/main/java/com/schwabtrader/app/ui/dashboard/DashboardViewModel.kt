package com.schwabtrader.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.AuthRepository
import com.schwabtrader.app.data.repository.Portfolio
import com.schwabtrader.app.data.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DashboardUiState {
    object Idle : DashboardUiState()
    object Loading : DashboardUiState()
    data class Success(val portfolio: Portfolio) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
    object NotConnected : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Idle)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val isSchwabConnected: Boolean get() = authRepository.isSchwabConnected

    init {
        loadPortfolio()
    }

    fun loadPortfolio() {
        if (!authRepository.isSchwabConnected) {
            _uiState.value = DashboardUiState.NotConnected
            return
        }

        _uiState.value = DashboardUiState.Loading

        portfolioRepository.getPortfolio()
            .onEach { result ->
                if (result.isSuccess) {
                    _uiState.value = DashboardUiState.Success(result.getOrThrow())
                } else {
                    _uiState.value = DashboardUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load portfolio"
                    )
                }
            }
            .catch { e ->
                _uiState.value = DashboardUiState.Error(e.message ?: "Unexpected error")
            }
            .launchIn(viewModelScope)
    }

    fun refreshPortfolio() {
        loadPortfolio()
    }

    fun signOut() {
        authRepository.signOut()
    }
}
