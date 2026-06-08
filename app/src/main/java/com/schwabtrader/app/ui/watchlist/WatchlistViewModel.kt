package com.schwabtrader.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.api.models.InstrumentDetail
import com.schwabtrader.app.data.api.models.QuoteDetail
import com.schwabtrader.app.data.repository.MarketDataRepository
import com.schwabtrader.app.data.repository.WatchlistItem
import com.schwabtrader.app.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val marketDataRepository: MarketDataRepository
) : ViewModel() {

    private val _watchlistItems = MutableStateFlow<List<WatchlistItem>>(emptyList())
    val watchlistItems: StateFlow<List<WatchlistItem>> = _watchlistItems.asStateFlow()

    private val _watchlistQuotes = MutableStateFlow<Map<String, QuoteDetail>>(emptyMap())
    val watchlistQuotes: StateFlow<Map<String, QuoteDetail>> = _watchlistQuotes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<InstrumentDetail>>(emptyList())
    val searchResults: StateFlow<List<InstrumentDetail>> = _searchResults.asStateFlow()

    private val _searchQuotes = MutableStateFlow<Map<String, QuoteDetail>>(emptyMap())
    val searchQuotes: StateFlow<Map<String, QuoteDetail>> = _searchQuotes.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadWatchlist()
    }

    fun loadWatchlist() {
        val items = watchlistRepository.getWatchlist()
        _watchlistItems.value = items
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                marketDataRepository.getQuotes(items.map { it.symbol }).onSuccess { quotes ->
                    _watchlistQuotes.value = quotes
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _isSearching.value = true
            marketDataRepository.searchSymbols(query).onSuccess { results ->
                val filtered = results.take(20)
                _searchResults.value = filtered
                val syms = filtered.map { it.symbol }.filter { it.isNotBlank() }
                if (syms.isNotEmpty()) {
                    marketDataRepository.getQuotes(syms).onSuccess { quotes ->
                        _searchQuotes.value = quotes
                    }
                }
            }
            _isSearching.value = false
        }
    }

    fun toggleWatchlist(item: WatchlistItem) {
        if (watchlistRepository.isInWatchlist(item.symbol)) {
            watchlistRepository.removeFromWatchlist(item.symbol)
        } else {
            watchlistRepository.addToWatchlist(item)
        }
        loadWatchlist()
    }

    fun isInWatchlist(symbol: String): Boolean = watchlistRepository.isInWatchlist(symbol)
}
