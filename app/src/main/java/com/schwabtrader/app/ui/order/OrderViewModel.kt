package com.schwabtrader.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val showConfirmDialog: Boolean = false
)

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderUiState>(OrderUiState.Idle)
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(OrderFormState())
    val formState: StateFlow<OrderFormState> = _formState.asStateFlow()

    private val _accounts = MutableStateFlow<List<Pair<String, String>>>(emptyList()) // (accountHash, displayName)
    val accounts: StateFlow<List<Pair<String, String>>> = _accounts.asStateFlow()

    private val _selectedAccountIndex = MutableStateFlow(0)
    val selectedAccountIndex: StateFlow<Int> = _selectedAccountIndex.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val result = portfolioRepository.getAccounts()
            if (result.isSuccess) {
                val accountList = result.getOrThrow().mapIndexed { idx, acc ->
                    val hash = acc.securitiesAccount.accountNumber
                    val display = "Account ...${hash.takeLast(4)}"
                    Pair(hash, display)
                }
                _accounts.value = accountList
            }
        }
    }

    fun setQuantity(quantity: String) {
        _formState.value = _formState.value.copy(quantity = quantity)
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

    fun getEstimatedCost(currentPrice: Double): Double {
        val qty = _formState.value.quantity.toDoubleOrNull() ?: 0.0
        val price = if (_formState.value.orderType == OrderType.LIMIT) {
            _formState.value.limitPrice.toDoubleOrNull() ?: currentPrice
        } else currentPrice
        return qty * price
    }
}
