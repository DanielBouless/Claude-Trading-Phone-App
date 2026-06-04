package com.schwabtrader.app.ui.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.AuthRepository
import com.schwabtrader.app.data.repository.HighPeriod
import com.schwabtrader.app.data.repository.IndexType
import com.schwabtrader.app.data.repository.MarketDataRepository
import com.schwabtrader.app.data.repository.ScreenedStock
import com.schwabtrader.app.data.repository.ScreenerCriteria
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

sealed class ScreenerUiState {
    object Idle : ScreenerUiState()
    object Loading : ScreenerUiState()
    data class Running(val resultsCount: Int, val stocks: List<ScreenedStock>) : ScreenerUiState()
    data class Success(val stocks: List<ScreenedStock>) : ScreenerUiState()
    data class Error(val message: String) : ScreenerUiState()
    object NotConnected : ScreenerUiState()
}

@HiltViewModel
class ScreenerViewModel @Inject constructor(
    private val marketDataRepository: MarketDataRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScreenerUiState>(ScreenerUiState.Idle)
    val uiState: StateFlow<ScreenerUiState> = _uiState.asStateFlow()

    private val _criteria = MutableStateFlow(ScreenerCriteria())
    val criteria: StateFlow<ScreenerCriteria> = _criteria.asStateFlow()

    val isSchwabConnected: Boolean get() = authRepository.isSchwabConnected

    fun toggleHighPeriod(period: HighPeriod) {
        val current = _criteria.value.highPeriods.toMutableSet()
        if (current.contains(period)) {
            if (current.size > 1) current.remove(period) // Keep at least one
        } else {
            current.add(period)
        }
        _criteria.value = _criteria.value.copy(highPeriods = current)
    }

    fun setIndex(index: IndexType) {
        _criteria.value = _criteria.value.copy(index = index)
    }

    fun setMinOutperformance(value: Float) {
        _criteria.value = _criteria.value.copy(minOutperformance = value)
    }

    fun runScreener() {
        if (!authRepository.isSchwabConnected) {
            _uiState.value = ScreenerUiState.NotConnected
            return
        }

        _uiState.value = ScreenerUiState.Loading

        marketDataRepository.screenStocks(_criteria.value)
            .onEach { stocks ->
                if (_uiState.value is ScreenerUiState.Loading) {
                    _uiState.value = ScreenerUiState.Running(stocks.size, stocks)
                } else if (_uiState.value is ScreenerUiState.Running) {
                    _uiState.value = ScreenerUiState.Running(stocks.size, stocks)
                }
            }
            .catch { e ->
                _uiState.value = ScreenerUiState.Error(e.message ?: "Screener failed")
            }
            .launchIn(viewModelScope)
    }

    fun finishScreener(stocks: List<ScreenedStock>) {
        _uiState.value = ScreenerUiState.Success(stocks)
    }

    fun resetScreener() {
        _uiState.value = ScreenerUiState.Idle
    }
}
