package com.schwabtrader.app.data.repository

import com.schwabtrader.app.data.api.SchwabMarketDataService
import com.schwabtrader.app.data.api.models.QuoteDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class HighPeriod { ONE_YEAR, THREE_YEAR, FIVE_YEAR }

enum class IndexType(val symbol: String, val displayName: String) {
    SP500("SPY", "S&P 500"),
    NASDAQ("QQQ", "NASDAQ"),
    DOW("DIA", "Dow Jones")
}

data class ScreenerCriteria(
    val highPeriods: Set<HighPeriod> = setOf(HighPeriod.ONE_YEAR),
    val index: IndexType = IndexType.SP500,
    val minOutperformance: Float = 5.0f
)

data class ScreenedStock(
    val symbol: String,
    val companyName: String,
    val currentPrice: Double,
    val percentFromOneYearHigh: Double,
    val percentFromThreeYearHigh: Double,
    val percentFromFiveYearHigh: Double,
    val oneYearReturn: Double,
    val indexOneYearReturn: Double,
    val outperformance: Double,
    val meetsHighCriteria: Set<HighPeriod>
)

@Singleton
class MarketDataRepository @Inject constructor(
    private val marketDataService: SchwabMarketDataService
) {
    companion object {
        // Top 50 S&P 500 stocks by market cap
        val SP500_TOP_50 = listOf(
            "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "BRK.B", "AVGO",
            "TSLA", "JPM", "LLY", "V", "UNH", "XOM", "MA", "COST", "HD",
            "PG", "JNJ", "MRK", "ABBV", "BAC", "KO", "NFLX", "CRM", "CVX",
            "AMD", "WMT", "MCD", "ABT", "ACN", "PEP", "ADBE", "LIN", "TMO",
            "WFC", "DHR", "NKE", "TXN", "NEE", "PM", "INTC", "MS", "RTX",
            "QCOM", "UPS", "AMGN", "IBM", "CAT", "GE"
        )
    }

    private val semaphore = Semaphore(5)

    fun screenStocks(criteria: ScreenerCriteria): Flow<List<ScreenedStock>> = flow {
        val now = System.currentTimeMillis()
        val fiveYearsAgo = now - (5L * 365 * 24 * 60 * 60 * 1000)

        // Fetch index 5yr history
        val indexHistory = runCatching {
            marketDataService.getPriceHistory(
                symbol = criteria.index.symbol,
                periodType = "year",
                period = 5,
                frequencyType = "weekly",
                frequency = 1
            )
        }.getOrNull()

        val indexOneYearReturn = if (indexHistory != null && indexHistory.candles.isNotEmpty()) {
            val candles = indexHistory.candles
            val oneYearAgoIndex = candles.indexOfLast {
                it.datetime < now - (365L * 24 * 60 * 60 * 1000)
            }
            if (oneYearAgoIndex >= 0 && oneYearAgoIndex < candles.size - 1) {
                val priceOneYearAgo = candles[oneYearAgoIndex].close
                val currentPrice = candles.last().close
                if (priceOneYearAgo > 0) ((currentPrice - priceOneYearAgo) / priceOneYearAgo) * 100.0 else 0.0
            } else 0.0
        } else 0.0

        val results = mutableListOf<ScreenedStock>()

        coroutineScope {
            val deferreds = SP500_TOP_50.map { symbol ->
                async {
                    semaphore.withPermit {
                        screenSingleStock(symbol, criteria, indexOneYearReturn, now)
                    }
                }
            }
            deferreds.forEach { deferred ->
                val result = runCatching { deferred.await() }.getOrNull()
                if (result != null) {
                    results.add(result)
                    emit(results.toList())
                }
            }
        }

        emit(results.toList())
    }

    private suspend fun screenSingleStock(
        symbol: String,
        criteria: ScreenerCriteria,
        indexOneYearReturn: Double,
        now: Long
    ): ScreenedStock? {
        val priceHistory = runCatching {
            marketDataService.getPriceHistory(
                symbol = symbol,
                periodType = "year",
                period = 5,
                frequencyType = "weekly",
                frequency = 1
            )
        }.getOrNull() ?: return null

        val candles = priceHistory.candles
        if (candles.isEmpty()) return null

        val currentPrice = candles.last().close
        if (currentPrice <= 0) return null

        val oneYearMs = 365L * 24 * 60 * 60 * 1000
        val threeYearMs = 3 * oneYearMs
        val fiveYearMs = 5 * oneYearMs

        val oneYearCutoff = now - oneYearMs
        val threeYearCutoff = now - threeYearMs
        val fiveYearCutoff = now - fiveYearMs

        val oneYearCandles = candles.filter { it.datetime >= oneYearCutoff }
        val threeYearCandles = candles.filter { it.datetime >= threeYearCutoff }
        val fiveYearCandles = candles.filter { it.datetime >= fiveYearCutoff }

        val oneYearHigh = oneYearCandles.maxOfOrNull { it.high } ?: currentPrice
        val threeYearHigh = threeYearCandles.maxOfOrNull { it.high } ?: currentPrice
        val fiveYearHigh = fiveYearCandles.maxOfOrNull { it.high } ?: currentPrice

        val percentFromOneYearHigh = if (oneYearHigh > 0) ((currentPrice - oneYearHigh) / oneYearHigh) * 100.0 else 0.0
        val percentFromThreeYearHigh = if (threeYearHigh > 0) ((currentPrice - threeYearHigh) / threeYearHigh) * 100.0 else 0.0
        val percentFromFiveYearHigh = if (fiveYearHigh > 0) ((currentPrice - fiveYearHigh) / fiveYearHigh) * 100.0 else 0.0

        // Calculate one year return
        val oneYearAgoCandle = candles.lastOrNull { it.datetime < oneYearCutoff }
        val oneYearReturn = if (oneYearAgoCandle != null && oneYearAgoCandle.close > 0) {
            ((currentPrice - oneYearAgoCandle.close) / oneYearAgoCandle.close) * 100.0
        } else 0.0

        val outperformance = oneYearReturn - indexOneYearReturn

        // Determine which high criteria are met (within 5% of high = "near high")
        val meetsHighCriteria = mutableSetOf<HighPeriod>()
        if (criteria.highPeriods.contains(HighPeriod.ONE_YEAR) && abs(percentFromOneYearHigh) <= 5.0) {
            meetsHighCriteria.add(HighPeriod.ONE_YEAR)
        }
        if (criteria.highPeriods.contains(HighPeriod.THREE_YEAR) && abs(percentFromThreeYearHigh) <= 5.0) {
            meetsHighCriteria.add(HighPeriod.THREE_YEAR)
        }
        if (criteria.highPeriods.contains(HighPeriod.FIVE_YEAR) && abs(percentFromFiveYearHigh) <= 5.0) {
            meetsHighCriteria.add(HighPeriod.FIVE_YEAR)
        }

        // Must meet at least one selected high criterion AND outperform the index
        if (meetsHighCriteria.isEmpty()) return null
        if (outperformance < criteria.minOutperformance) return null

        // Get company name from quotes
        val quoteDetail = runCatching {
            marketDataService.getQuotes(symbols = symbol)
        }.getOrNull()?.get(symbol)

        return ScreenedStock(
            symbol = symbol,
            companyName = quoteDetail?.description ?: symbol,
            currentPrice = currentPrice,
            percentFromOneYearHigh = percentFromOneYearHigh,
            percentFromThreeYearHigh = percentFromThreeYearHigh,
            percentFromFiveYearHigh = percentFromFiveYearHigh,
            oneYearReturn = oneYearReturn,
            indexOneYearReturn = indexOneYearReturn,
            outperformance = outperformance,
            meetsHighCriteria = meetsHighCriteria
        )
    }

    suspend fun getQuotes(symbols: List<String>): Result<Map<String, QuoteDetail>> {
        return try {
            val symbolsStr = symbols.joinToString(",")
            val quotes = marketDataService.getQuotes(symbols = symbolsStr)
            Result.success(quotes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
