package com.schwabtrader.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.MarketDataRepository
import com.schwabtrader.app.data.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OrderUiState {
    object Idle : OrderUiState()
    object Loading : OrderUiState()
    object Success : OrderUiState()
    data class Error(val message: String) : OrderUiState()
}

enum class OrderType { MARKET, LIMIT }

data class OrderFormState(
    val quantity: String = "1",
    val orderType: OrderType = OrderType.MARKET,
    val limitPrice: String = "",
    val showConfirmDialog: Boolean = false,
    val dollarAmount: String = ""
)

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val marketDataRepository: MarketDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderUiState>(OrderUiState.Idle)
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(OrderFormState())
    val formState: StateFlow<OrderFormState> = _formState.asStateFlow()

    private val _accounts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val accounts: StateFlow<List<Pair<String, String>>> = _accounts.asStateFlow()

    private val _selectedAccountIndex = MutableStateFlow(0)
    val selectedAccountIndex: StateFlow<Int> = _selectedAccountIndex.asStateFlow()

    private val _currentPrice = MutableStateFlow(0.0)
    val currentPrice: StateFlow<Double> = _currentPrice.asStateFlow()

    init {
        loadAccounts()
    }

    fun loadPrice(symbol: String) {
        viewModelScope.launch {
            marketDataRepository.getQuotes(listOf(symbol)).onSuccess { quotes ->
                val price = quotes[symbol]?.lastPrice ?: 0.0
                _currentPrice.value = price
                // Sync dollar amount for the default quantity once price arrives
                val qty = _formState.value.quantity.toDoubleOrNull() ?: 0.0
                if (qty > 0 && price > 0) {
                    _formState.value = _formState.value.copy(
                        dollarAmount = "%.2f".format(qty * price)
                    )
                }
            }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val result = portfolioRepository.getAccounts()
            if (result.isSuccess) {
                val accountList = result.getOrThrow().map { acc ->
                    val hash = acc.securitiesAccount.accountNumber
                    Pair(hash, "Account ...${hash.takeLast(4)}")
                }
                _accounts.value = accountList
            }
        }
    }

    fun setQuantity(quantity: String) {
        val price = _currentPrice.value
        val dollars = if (price > 0) {
            quantity.toDoubleOrNull()?.let { qty ->
                if (qty > 0) "%.2f".format(qty * price) else ""
            } ?: ""
        } else _formState.value.dollarAmount
        _formState.value = _formState.value.copy(quantity = quantity, dollarAmount = dollars)
    }

    fun setDollarAmount(amount: String) {
        val price = _currentPrice.value
        val shares = if (price > 0) {
            amount.toDoubleOrNull()?.let { d ->
                if (d > 0) formatShares(d / price) else ""
            } ?: ""
        } else _formState.value.quantity
        _formState.value = _formState.value.copy(dollarAmount = amount, quantity = shares)
    }

    fun setOrderType(orderType: OrderType) {
        _formState.value = _formState.value.copy(orderType = orderType)
    }

    fun setLimitPrice(price: String) {
        _formState.value = _formState.value.copy(limitPrice = price)
    }

    fun setSelectedAccountIndex(index: Int) {
        _selectedAccountIndex.value = index
    }

    fun showConfirmDialog() {
        _formState.value = _formState.value.copy(showConfirmDialog = true)
    }

    fun dismissConfirmDialog() {
        _formState.value = _formState.value.copy(showConfirmDialog = false)
    }

    fun placeOrder(symbol: String, accountHash: String) {
        dismissConfirmDialog()
        val form = _formState.value
        val quantity = form.quantity.toDoubleOrNull()
        if (quantity == null || quantity <= 0) {
            _uiState.value = OrderUiState.Error("Invalid quantity")
            return
        }

        val orderTypeStr = when (form.orderType) {
            OrderType.MARKET -> "MARKET"
            OrderType.LIMIT -> "LIMIT"
        }

        val limitPrice = if (form.orderType == OrderType.LIMIT) {
            form.limitPrice.toDoubleOrNull()
        } else null

        if (form.orderType == OrderType.LIMIT && limitPrice == null) {
            _uiState.value = OrderUiState.Error("Invalid limit price")
            return
        }

        val targetAccountHash = if (accountHash.isNotBlank()) {
            accountHash
        } else {
            _accounts.value.getOrNull(_selectedAccountIndex.value)?.first ?: ""
        }

        if (targetAccountHash.isBlank()) {
            _uiState.value = OrderUiState.Error("No account selected")
            return
        }

        _uiState.value = OrderUiState.Loading

        viewModelScope.launch {
            val result = portfolioRepository.placeOrder(
                accountHash = targetAccountHash,
                symbol = symbol,
                quantity = quantity,
                orderType = orderTypeStr,
                limitPrice = limitPrice
            )
            if (result.isSuccess) {
                _uiState.value = OrderUiState.Success
            } else {
                _uiState.value = OrderUiState.Error(
                    result.exceptionOrNull()?.message ?: "Order placement failed"
                )
            }
        }
    }

    fun clearError() {
        if (_uiState.value is OrderUiState.Error) {
            _uiState.value = OrderUiState.Idle
        }
    }

    fun getEstimatedCost(): Double {
        val qty = _formState.value.quantity.toDoubleOrNull() ?: 0.0
        val price = if (_formState.value.orderType == OrderType.LIMIT) {
            _formState.value.limitPrice.toDoubleOrNull() ?: _currentPrice.value
        } else _currentPrice.value
        return qty * price
    }

    private fun formatShares(d: Double): String {
        val s = if (d >= 1.0) "%.4f".format(d) else "%.6f".format(d)
        return s.trimEnd('0').trimEnd('.')
    }
}
