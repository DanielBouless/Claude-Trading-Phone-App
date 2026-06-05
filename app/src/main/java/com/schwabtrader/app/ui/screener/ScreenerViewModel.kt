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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

sealed class ScreenerUiState {
    object Idle : ScreenerUiState()
    object Loading : ScreenerUiState()
    data class Running(
        val processedCount: Int,
        val totalCount: Int,
        val stocks: List<ScreenedStock>
    ) : ScreenerUiState()
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

    private var screenerJob: Job? = null

    fun toggleHighPeriod(period: HighPeriod) {
        val current = _criteria.value.highPeriods.toMutableSet()
        if (current.contains(period)) {
            if (current.size > 1) current.remove(period)
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
        screenerJob?.cancel()
        _uiState.value = ScreenerUiState.Loading

        screenerJob = marketDataRepository.screenStocks(_criteria.value)
            .onEach { progress ->
                _uiState.value = ScreenerUiState.Running(
                    progress.processedCount,
                    progress.totalCount,
                    progress.matches
                )
            }
            .catch { e ->
                _uiState.value = ScreenerUiState.Error(e.message ?: "Screener failed")
            }
            .onCompletion { cause ->
                if (cause == null) {
                    val current = _uiState.value
                    if (current is ScreenerUiState.Running) {
                        _uiState.value = ScreenerUiState.Success(current.stocks)
                    } else if (current is ScreenerUiState.Loading) {
                        _uiState.value = ScreenerUiState.Success(emptyList())
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun stopScreener() {
        screenerJob?.cancel()
        screenerJob = null
        val current = _uiState.value
        _uiState.value = when (current) {
            is ScreenerUiState.Running -> ScreenerUiState.Success(current.stocks)
            else -> ScreenerUiState.Idle
        }
    }

    fun resetScreener() {
        screenerJob?.cancel()
        screenerJob = null
        _uiState.value = ScreenerUiState.Idle
    }
}
