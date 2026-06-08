package com.schwabtrader.app.ui.stockdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.MarketDataRepository
import com.schwabtrader.app.data.repository.StockDetail
import com.schwabtrader.app.data.repository.WatchlistItem
import com.schwabtrader.app.data.repository.WatchlistRepository
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
    private val repository: MarketDataRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<StockDetailUiState>(StockDetailUiState.Loading)
    val uiState: StateFlow<StockDetailUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private var loadedDetail: StockDetail? = null

    fun load(symbol: String) {
        if (_uiState.value is StockDetailUiState.Success) return
        _isFavorite.value = watchlistRepository.isInWatchlist(symbol)
        viewModelScope.launch {
            _uiState.value = StockDetailUiState.Loading
            val result = repository.getStockDetail(symbol)
            if (result.isSuccess) {
                loadedDetail = result.getOrThrow()
                _uiState.value = StockDetailUiState.Success(result.getOrThrow())
            } else {
                _uiState.value = StockDetailUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
    }

    fun retry(symbol: String) {
        _uiState.value = StockDetailUiState.Loading
        viewModelScope.launch {
            val result = repository.getStockDetail(symbol)
            if (result.isSuccess) {
                loadedDetail = result.getOrThrow()
                _uiState.value = StockDetailUiState.Success(result.getOrThrow())
            } else {
                _uiState.value = StockDetailUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
    }

    fun toggleFavorite(symbol: String) {
        val detail = loadedDetail
        val item = WatchlistItem(
            symbol = symbol,
            companyName = detail?.companyName ?: symbol,
            assetType = "EQUITY"
        )
        if (watchlistRepository.isInWatchlist(symbol)) {
            watchlistRepository.removeFromWatchlist(symbol)
        } else {
            watchlistRepository.addToWatchlist(item)
        }
        _isFavorite.value = watchlistRepository.isInWatchlist(symbol)
    }
}
