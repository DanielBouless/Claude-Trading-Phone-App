package com.schwabtrader.app.ui.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.AuthRepository
import com.schwabtrader.app.data.repository.IndexType
import com.schwabtrader.app.data.repository.MarketDataRepository
import com.schwabtrader.app.data.repository.WilliamsDmiResult
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

sealed class WilliamsDmiUiState {
    object Idle : WilliamsDmiUiState()
    object Loading : WilliamsDmiUiState()
    data class Running(
        val processedCount: Int,
        val totalCount: Int,
        val results: List<WilliamsDmiResult>
    ) : WilliamsDmiUiState()
    data class Success(val results: List<WilliamsDmiResult>) : WilliamsDmiUiState()
    data class Error(val message: String) : WilliamsDmiUiState()
    object NotConnected : WilliamsDmiUiState()
}

@HiltViewModel
class WilliamsDmiViewModel @Inject constructor(
    private val marketDataRepository: MarketDataRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WilliamsDmiUiState>(WilliamsDmiUiState.Idle)
    val uiState: StateFlow<WilliamsDmiUiState> = _uiState.asStateFlow()

    private val _index = MutableStateFlow(IndexType.SP500)
    val index: StateFlow<IndexType> = _index.asStateFlow()

    private var screenerJob: Job? = null

    fun setIndex(index: IndexType) {
        _index.value = index
    }

    fun runScreener() {
        if (!authRepository.isSchwabConnected) {
            _uiState.value = WilliamsDmiUiState.NotConnected
            return
        }
        screenerJob?.cancel()
        _uiState.value = WilliamsDmiUiState.Loading

        screenerJob = marketDataRepository.screenWilliamsDmi(_index.value)
            .onEach { progress ->
                _uiState.value = WilliamsDmiUiState.Running(
                    progress.processedCount,
                    progress.totalCount,
                    progress.matches
                )
            }
            .catch { e ->
                _uiState.value = WilliamsDmiUiState.Error(e.message ?: "Screener failed")
            }
            .onCompletion { cause ->
                if (cause == null) {
                    _uiState.value = when (val c = _uiState.value) {
                        is WilliamsDmiUiState.Running -> WilliamsDmiUiState.Success(c.results)
                        is WilliamsDmiUiState.Loading -> WilliamsDmiUiState.Success(emptyList())
                        else -> c
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun stopScreener() {
        screenerJob?.cancel()
        screenerJob = null
        _uiState.value = when (val c = _uiState.value) {
            is WilliamsDmiUiState.Running -> WilliamsDmiUiState.Success(c.results)
            else -> WilliamsDmiUiState.Idle
        }
    }

    fun resetScreener() {
        screenerJob?.cancel()
        screenerJob = null
        _uiState.value = WilliamsDmiUiState.Idle
    }
}
