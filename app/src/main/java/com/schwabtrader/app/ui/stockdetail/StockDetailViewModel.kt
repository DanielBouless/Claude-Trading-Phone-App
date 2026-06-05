package com.schwabtrader.app.ui.stockdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.MarketDataRepository
import com.schwabtrader.app.data.repository.StockDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StockDetailUiState {
    object Loading : StockDetailUiState()
    data class Success(val detail: StockDetail) : StockDetailUiState()
    data class Error(val message: String) : StockDetailUiState()
}

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    private val repository: MarketDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<StockDetailUiState>(StockDetailUiState.Loading)
    val uiState: StateFlow<StockDetailUiState> = _uiState.asStateFlow()

    fun load(symbol: String) {
        if (_uiState.value is StockDetailUiState.Success) return
        viewModelScope.launch {
            _uiState.value = StockDetailUiState.Loading
            val result = repository.getStockDetail(symbol)
            _uiState.value = if (result.isSuccess) {
                StockDetailUiState.Success(result.getOrThrow())
            } else {
                StockDetailUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
    }

    fun retry(symbol: String) {
        _uiState.value = StockDetailUiState.Loading
        viewModelScope.launch {
            val result = repository.getStockDetail(symbol)
            _uiState.value = if (result.isSuccess) {
                StockDetailUiState.Success(result.getOrThrow())
            } else {
                StockDetailUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
    }
}
