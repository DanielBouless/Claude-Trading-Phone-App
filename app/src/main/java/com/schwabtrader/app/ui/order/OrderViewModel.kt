package com.schwabtrader.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwabtrader.app.data.repository.MarketDataRepository
import com.schwabtrader.app.data.repository.PortfolioRepository
import com.schwabtrader.app.data.repository.SellOnFillConfig
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
enum class OrderDirection { BUY, SELL }
enum class SellStrategy { MARKET, LIMIT, STOP_LOSS, TRAILING_STOP }
enum class TrailingStopUnit { PERCENT, DOLLAR }

data class OrderFormState(
    val quantity: String = "1",
    val orderType: OrderType = OrderType.MARKET,
    val limitPrice: String = "",
    val showConfirmDialog: Boolean = false,
    val dollarAmount: String = "",
    val direction: OrderDirection = OrderDirection.BUY,
    val sellStrategy: SellStrategy = SellStrategy.MARKET,
    val stopPrice: String = "",
    val trailingAmount: String = "",
    val trailingUnit: TrailingStopUnit = TrailingStopUnit.PERCENT,
    // Sell-on-fill (bracket order — only used when direction == BUY)
    val addSellOnFill: Boolean = false,
    val sellOnFillStopLossEnabled: Boolean = false,
    val sellOnFillTrailingStopEnabled: Boolean = false,
    val sellOnFillLimitEnabled: Boolean = false,
    val sellOnFillStopPrice: String = "",
    val sellOnFillTrailingAmount: String = "",
    val sellOnFillTrailingUnit: TrailingStopUnit = TrailingStopUnit.PERCENT,
    val sellOnFillLimitPrice: String = ""
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
            portfolioRepository.getAccounts().onSuccess { list ->
                _accounts.value = list.map { acc ->
                    val hash = acc.securitiesAccount.accountNumber
                    Pair(hash, "Account ...${hash.takeLast(4)}")
                }
            }
        }
    }

    fun setDirection(direction: OrderDirection) {
        _formState.value = _formState.value.copy(direction = direction)
    }

    fun setQuantity(quantity: String) {
        val price = _currentPrice.value
        val dollars = if (price > 0) {
            quantity.toDoubleOrNull()?.let { if (it > 0) "%.2f".format(it * price) else "" } ?: ""
        } else _formState.value.dollarAmount
        _formState.value = _formState.value.copy(quantity = quantity, dollarAmount = dollars)
    }

    fun setDollarAmount(amount: String) {
        val price = _currentPrice.value
        val shares = if (price > 0) {
            amount.toDoubleOrNull()?.let { if (it > 0) formatShares(it / price) else "" } ?: ""
        } else _formState.value.quantity
        _formState.value = _formState.value.copy(dollarAmount = amount, quantity = shares)
    }

    fun setOrderType(orderType: OrderType) {
        _formState.value = _formState.value.copy(orderType = orderType)
    }

    fun setSellStrategy(strategy: SellStrategy) {
        _formState.value = _formState.value.copy(sellStrategy = strategy)
    }

    fun setLimitPrice(price: String) {
        _formState.value = _formState.value.copy(limitPrice = price)
    }

    fun setStopPrice(price: String) {
        _formState.value = _formState.value.copy(stopPrice = price)
    }

    fun setTrailingAmount(amount: String) {
        _formState.value = _formState.value.copy(trailingAmount = amount)
    }

    fun setTrailingUnit(unit: TrailingStopUnit) {
        _formState.value = _formState.value.copy(trailingUnit = unit)
    }

    // Sell-on-fill setters
    fun setAddSellOnFill(enabled: Boolean) {
        _formState.value = _formState.value.copy(addSellOnFill = enabled)
    }

    fun toggleSellOnFillStopLoss(enabled: Boolean) {
        _formState.value = _formState.value.copy(sellOnFillStopLossEnabled = enabled)
    }

    fun toggleSellOnFillTrailingStop(enabled: Boolean) {
        _formState.value = _formState.value.copy(sellOnFillTrailingStopEnabled = enabled)
    }

    fun toggleSellOnFillLimit(enabled: Boolean) {
        _formState.value = _formState.value.copy(sellOnFillLimitEnabled = enabled)
    }

    fun setSellOnFillLimitPrice(price: String) {
        _formState.value = _formState.value.copy(sellOnFillLimitPrice = price)
    }

    fun setSellOnFillStopPrice(price: String) {
        _formState.value = _formState.value.copy(sellOnFillStopPrice = price)
    }

    fun setSellOnFillTrailingAmount(amount: String) {
        _formState.value = _formState.value.copy(sellOnFillTrailingAmount = amount)
    }

    fun setSellOnFillTrailingUnit(unit: TrailingStopUnit) {
        _formState.value = _formState.value.copy(sellOnFillTrailingUnit = unit)
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

        val targetAccountHash = if (accountHash.isNotBlank()) accountHash
        else _accounts.value.getOrNull(_selectedAccountIndex.value)?.first ?: ""

        if (targetAccountHash.isBlank()) {
            _uiState.value = OrderUiState.Error("No account selected")
            return
        }

        _uiState.value = OrderUiState.Loading

        viewModelScope.launch {
            val result = if (form.direction == OrderDirection.BUY) {
                placeBuyOrder(symbol, quantity, form, targetAccountHash)
            } else {
                placeSellOrder(symbol, quantity, form, targetAccountHash)
            }
            _uiState.value = if (result.isSuccess) OrderUiState.Success
            else OrderUiState.Error(result.exceptionOrNull()?.message ?: "Order placement failed")
        }
    }

    private suspend fun placeBuyOrder(
        symbol: String,
        quantity: Double,
        form: OrderFormState,
        accountHash: String
    ): Result<Unit> {
        val limitPrice = if (form.orderType == OrderType.LIMIT) {
            form.limitPrice.toDoubleOrNull()
                ?: return Result.failure(Exception("Invalid limit price"))
        } else null

        val sellOnFill = if (form.addSellOnFill) buildSellOnFillConfig(form) else null

        return portfolioRepository.placeOrder(
            accountHash = accountHash,
            symbol = symbol,
            quantity = quantity,
            orderType = if (form.orderType == OrderType.LIMIT) "LIMIT" else "MARKET",
            instruction = "BUY",
            limitPrice = limitPrice,
            sellOnFill = sellOnFill
        )
    }

    private fun buildSellOnFillConfig(form: OrderFormState): SellOnFillConfig {
        val trailingOffset = if (form.sellOnFillTrailingStopEnabled)
            form.sellOnFillTrailingAmount.toDoubleOrNull() else null
        val trailingLinkType = if (trailingOffset != null)
            if (form.sellOnFillTrailingUnit == TrailingStopUnit.PERCENT) "PERCENT" else "VALUE"
        else null

        return SellOnFillConfig(
            stopLossPrice = if (form.sellOnFillStopLossEnabled)
                form.sellOnFillStopPrice.toDoubleOrNull() else null,
            trailingLinkType = trailingLinkType,
            trailingOffset = trailingOffset,
            limitPrice = if (form.sellOnFillLimitEnabled)
                form.sellOnFillLimitPrice.toDoubleOrNull() else null
        )
    }

    private suspend fun placeSellOrder(
        symbol: String,
        quantity: Double,
        form: OrderFormState,
        accountHash: String
    ): Result<Unit> {
        return when (form.sellStrategy) {
            SellStrategy.MARKET -> portfolioRepository.placeOrder(
                accountHash = accountHash,
                symbol = symbol,
                quantity = quantity,
                orderType = "MARKET",
                instruction = "SELL"
            )
            SellStrategy.LIMIT -> {
                val lp = form.limitPrice.toDoubleOrNull()
                    ?: return Result.failure(Exception("Invalid limit price"))
                portfolioRepository.placeOrder(
                    accountHash = accountHash,
                    symbol = symbol,
                    quantity = quantity,
                    orderType = "LIMIT",
                    instruction = "SELL",
                    limitPrice = lp
                )
            }
            SellStrategy.STOP_LOSS -> {
                val sp = form.stopPrice.toDoubleOrNull()
                    ?: return Result.failure(Exception("Invalid stop price"))
                portfolioRepository.placeOrder(
                    accountHash = accountHash,
                    symbol = symbol,
                    quantity = quantity,
                    orderType = "STOP",
                    instruction = "SELL",
                    stopPrice = sp,
                    duration = "GTC"
                )
            }
            SellStrategy.TRAILING_STOP -> {
                val offset = form.trailingAmount.toDoubleOrNull()
                    ?: return Result.failure(Exception("Invalid trailing amount"))
                val linkType = if (form.trailingUnit == TrailingStopUnit.PERCENT) "PERCENT" else "VALUE"
                portfolioRepository.placeOrder(
                    accountHash = accountHash,
                    symbol = symbol,
                    quantity = quantity,
                    orderType = "TRAILING_STOP",
                    instruction = "SELL",
                    trailingStopLinkType = linkType,
                    trailingStopOffset = offset,
                    duration = "GTC"
                )
            }
        }
    }

    fun clearError() {
        if (_uiState.value is OrderUiState.Error) _uiState.value = OrderUiState.Idle
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
