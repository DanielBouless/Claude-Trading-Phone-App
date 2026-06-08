package com.schwabtrader.app.data.repository

import com.schwabtrader.app.data.api.SchwabMarketDataService
import com.schwabtrader.app.data.api.SchwabTraderService
import com.schwabtrader.app.data.api.models.OrderInstrument
import com.schwabtrader.app.data.api.models.OrderLegCollection
import com.schwabtrader.app.data.api.models.OrderRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class SellOnFillParams(
    val orderType: String,              // "STOP", "TRAILING_STOP", "LIMIT"
    val limitPrice: Double? = null,
    val stopPrice: Double? = null,
    val trailingStopLinkType: String? = null,   // "PERCENT" or "VALUE"
    val trailingStopOffset: Double? = null
)

data class Portfolio(
    val totalValue: Double,
    val dayGainLoss: Double,
    val dayGainLossPercent: Double,
    val positions: List<PortfolioPosition>,
    val accountHash: String = "",
    val buyingPower: Double = 0.0
)

data class PortfolioPosition(
    val symbol: String,
    val quantity: Double,
    val averagePrice: Double,
    val currentPrice: Double,
    val marketValue: Double,
    val gainLoss: Double,
    val gainLossPercent: Double,
    val dayGainLoss: Double = 0.0,
    val dayGainLossPercent: Double = 0.0,
    val companyName: String = ""
)

@Singleton
class PortfolioRepository @Inject constructor(
    private val traderService: SchwabTraderService,
    private val marketDataService: SchwabMarketDataService
) {
    fun getPortfolio(): Flow<Result<Portfolio>> = flow {
        try {
            val accounts = traderService.getAccounts()
            if (accounts.isEmpty()) {
                emit(Result.failure(Exception("No accounts found")))
                return@flow
            }

            val account = accounts.first()
            val securitiesAccount = account.securitiesAccount
            val positions = securitiesAccount.positions

            if (positions.isEmpty()) {
                val totalValue = securitiesAccount.currentBalances?.liquidationValue ?: 0.0
                emit(Result.success(Portfolio(
                    totalValue = totalValue,
                    dayGainLoss = 0.0,
                    dayGainLossPercent = 0.0,
                    positions = emptyList(),
                    accountHash = securitiesAccount.accountNumber,
                    buyingPower = securitiesAccount.currentBalances?.buyingPower ?: 0.0
                )))
                return@flow
            }

            // Get symbols for all equity positions
            val equitySymbols = positions
                .filter { it.instrument.assetType == "EQUITY" }
                .map { it.instrument.symbol }

            // Fetch current quotes
            val quotesMap = if (equitySymbols.isNotEmpty()) {
                runCatching {
                    marketDataService.getQuotes(symbols = equitySymbols.joinToString(","))
                }.getOrDefault(emptyMap())
            } else emptyMap()

            // Build enriched positions
            val portfolioPositions = positions.map { position ->
                val symbol = position.instrument.symbol
                val quoteDetail = quotesMap[symbol]
                val currentPrice = quoteDetail?.lastPrice ?: 0.0
                val quantity = position.longQuantity
                val averagePrice = position.averagePrice
                val marketValue = if (currentPrice > 0) currentPrice * quantity else position.marketValue
                val costBasis = averagePrice * quantity
                val gainLoss = marketValue - costBasis
                val gainLossPercent = if (costBasis > 0) (gainLoss / costBasis) * 100.0 else 0.0
                val dayGainLoss = position.currentDayProfitLoss
                val dayGainLossPercent = position.currentDayProfitLossPercentage

                PortfolioPosition(
                    symbol = symbol,
                    quantity = quantity,
                    averagePrice = averagePrice,
                    currentPrice = currentPrice,
                    marketValue = marketValue,
                    gainLoss = gainLoss,
                    gainLossPercent = gainLossPercent,
                    dayGainLoss = dayGainLoss,
                    dayGainLossPercent = dayGainLossPercent,
                    companyName = quoteDetail?.description ?: position.instrument.description
                )
            }

            val totalValue = securitiesAccount.currentBalances?.liquidationValue
                ?: portfolioPositions.sumOf { it.marketValue }
            val totalDayGainLoss = portfolioPositions.sumOf { it.dayGainLoss }
            val previousTotalValue = totalValue - totalDayGainLoss
            val dayGainLossPercent = if (previousTotalValue > 0) {
                (totalDayGainLoss / previousTotalValue) * 100.0
            } else 0.0

            emit(Result.success(Portfolio(
                totalValue = totalValue,
                dayGainLoss = totalDayGainLoss,
                dayGainLossPercent = dayGainLossPercent,
                positions = portfolioPositions,
                accountHash = securitiesAccount.accountNumber,
                buyingPower = securitiesAccount.currentBalances?.buyingPower ?: 0.0
            )))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun placeOrder(
        accountHash: String,
        symbol: String,
        quantity: Double,
        orderType: String,
        instruction: String = "BUY",
        limitPrice: Double? = null,
        stopPrice: Double? = null,
        trailingStopLinkType: String? = null,
        trailingStopOffset: Double? = null,
        duration: String = "DAY",
        sellOnFill: SellOnFillParams? = null
    ): Result<Unit> {
        return try {
            val childOrder = sellOnFill?.let { s ->
                OrderRequest(
                    orderType = s.orderType,
                    orderStrategyType = "SINGLE",
                    duration = "GTC",
                    price = if (s.orderType == "LIMIT") s.limitPrice else null,
                    stopPrice = if (s.orderType == "STOP") s.stopPrice else null,
                    stopPriceLinkBasis = if (s.orderType == "TRAILING_STOP") "BID" else null,
                    stopPriceLinkType = s.trailingStopLinkType,
                    stopPriceOffset = s.trailingStopOffset,
                    orderLegCollection = listOf(
                        OrderLegCollection(
                            instruction = "SELL",
                            quantity = quantity,
                            instrument = OrderInstrument(symbol = symbol, assetType = "EQUITY")
                        )
                    )
                )
            }
            val orderRequest = OrderRequest(
                orderType = orderType,
                orderStrategyType = if (childOrder != null) "TRIGGER" else "SINGLE",
                duration = duration,
                price = if (orderType == "LIMIT") limitPrice else null,
                stopPrice = if (orderType == "STOP") stopPrice else null,
                stopPriceLinkBasis = if (orderType == "TRAILING_STOP") "BID" else null,
                stopPriceLinkType = trailingStopLinkType,
                stopPriceOffset = trailingStopOffset,
                orderLegCollection = listOf(
                    OrderLegCollection(
                        instruction = instruction,
                        quantity = quantity,
                        instrument = OrderInstrument(symbol = symbol, assetType = "EQUITY")
                    )
                ),
                childOrderStrategies = childOrder?.let { listOf(it) }
            )
            val response = traderService.placeOrder(accountHash, orderRequest)
            if (response.isSuccessful || response.code() == 201) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Order failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAccounts(): Result<List<com.schwabtrader.app.data.api.models.AccountResponse>> {
        return try {
            val accounts = traderService.getAccounts()
            Result.success(accounts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
