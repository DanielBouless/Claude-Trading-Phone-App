package com.schwabtrader.app.data.repository

import com.schwabtrader.app.data.api.SchwabMarketDataService
import com.schwabtrader.app.data.api.models.Candle
import com.schwabtrader.app.data.api.models.QuoteDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

data class ScreenerProgress(
    val processedCount: Int,
    val totalCount: Int,
    val matches: List<ScreenedStock>
)

data class PeerStock(
    val symbol: String,
    val oneYearReturn: Double,
    val isCurrentStock: Boolean = false
)

data class StockDetail(
    val symbol: String,
    val companyName: String,
    val currentPrice: Double,
    val priceChange: Double,
    val priceChangePercent: Double,
    val volume: Long,
    val high52Week: Double,
    val low52Week: Double,
    val openPrice: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val bid: Double,
    val ask: Double,
    val marketCap: Double,
    val peRatio: Double,
    val eps: Double,
    val dividendYield: Double,
    val beta: Double,
    val pbRatio: Double,
    val roe: Double,
    val sma50: Double,
    val sma200: Double,
    val rsi14: Double,
    val oneMonthReturn: Double,
    val threeMonthReturn: Double,
    val sixMonthReturn: Double,
    val oneYearReturn: Double,
    val threeYearReturn: Double,
    val fiveYearReturn: Double,
    val dailyCandles: List<Candle>,
    val weeklyCandles: List<Candle>,
    val sector: String = "",
    val peerComparison: List<PeerStock> = emptyList()
)

@Singleton
class MarketDataRepository @Inject constructor(
    private val marketDataService: SchwabMarketDataService
) {
    companion object {
        val SP500_TOP_50 = listOf(
            "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "BRK.B", "AVGO",
            "TSLA", "JPM", "LLY", "V", "UNH", "XOM", "MA", "COST", "HD",
            "PG", "JNJ", "MRK", "ABBV", "BAC", "KO", "NFLX", "CRM", "CVX",
            "AMD", "WMT", "MCD", "ABT", "ACN", "PEP", "ADBE", "LIN", "TMO",
            "WFC", "DHR", "NKE", "TXN", "NEE", "PM", "INTC", "MS", "RTX",
            "QCOM", "UPS", "AMGN", "IBM", "CAT", "GE"
        )
        val SECTOR_GROUPS: Map<String, List<String>> = mapOf(
            "Technology" to listOf("AAPL", "MSFT", "NVDA", "AVGO", "AMD", "ADBE", "INTC", "QCOM", "IBM", "ACN", "CRM", "TXN"),
            "Communication Services" to listOf("GOOGL", "META", "NFLX"),
            "Consumer Discretionary" to listOf("AMZN", "TSLA", "MCD", "NKE", "HD"),
            "Healthcare" to listOf("LLY", "JNJ", "MRK", "ABBV", "ABT", "UNH", "TMO", "DHR", "AMGN"),
            "Financials" to listOf("JPM", "BAC", "V", "MA", "WFC", "MS", "BRK.B"),
            "Energy" to listOf("XOM", "CVX"),
            "Consumer Staples" to listOf("COST", "WMT", "KO", "PG", "PEP", "PM"),
            "Industrials" to listOf("CAT", "GE", "RTX", "UPS"),
            "Utilities" to listOf("NEE"),
            "Materials" to listOf("LIN")
        )
        val SYMBOL_TO_SECTOR: Map<String, String> = SECTOR_GROUPS
            .flatMap { (sector, symbols) -> symbols.map { it to sector } }
            .toMap()
    }

    private val semaphore = Semaphore(5)

    fun screenStocks(criteria: ScreenerCriteria): Flow<ScreenerProgress> = flow {
        val now = System.currentTimeMillis()
        val indexHistory = runCatching {
            marketDataService.getPriceHistory(criteria.index.symbol, "year", 5, "weekly", 1)
        }.getOrNull()

        val indexOneYearReturn = if (indexHistory != null && indexHistory.candles.isNotEmpty()) {
            val candles = indexHistory.candles
            val idx = candles.indexOfLast { it.datetime < now - 365L * 24 * 60 * 60 * 1000 }
            if (idx >= 0 && idx < candles.size - 1) {
                val p = candles[idx].close
                if (p > 0) ((candles.last().close - p) / p) * 100.0 else 0.0
            } else 0.0
        } else 0.0

        val results = mutableListOf<ScreenedStock>()
        val total = SP500_TOP_50.size

        coroutineScope {
            val deferreds = SP500_TOP_50.map { symbol ->
                async { semaphore.withPermit { screenSingleStock(symbol, criteria, indexOneYearReturn, now) } }
            }
            deferreds.forEachIndexed { i, deferred ->
                val result = runCatching { deferred.await() }.getOrNull()
                if (result != null) results.add(result)
                emit(ScreenerProgress(i + 1, total, results.toList()))
            }
        }
    }

    private suspend fun screenSingleStock(
        symbol: String,
        criteria: ScreenerCriteria,
        indexOneYearReturn: Double,
        now: Long
    ): ScreenedStock? {
        val priceHistory = runCatching {
            marketDataService.getPriceHistory(symbol, "year", 5, "weekly", 1)
        }.getOrNull() ?: return null

        val candles = priceHistory.candles
        if (candles.isEmpty()) return null
        val currentPrice = candles.last().close
        if (currentPrice <= 0) return null

        val oneYearMs = 365L * 24 * 60 * 60 * 1000
        // Exclude the most recent candle so current price can genuinely exceed prior high
        val priorCandles = candles.dropLast(1)

        val priorOneYearHigh   = priorCandles.filter { it.datetime >= now - oneYearMs       }.maxOfOrNull { it.close } ?: 0.0
        val priorThreeYearHigh = priorCandles.filter { it.datetime >= now - 3 * oneYearMs   }.maxOfOrNull { it.close } ?: 0.0
        val priorFiveYearHigh  = priorCandles.filter { it.datetime >= now - 5 * oneYearMs   }.maxOfOrNull { it.close } ?: 0.0

        // pct > 0 means stock is trading ABOVE the prior period high (breakout)
        fun pctAbove(priorHigh: Double) = if (priorHigh > 0) ((currentPrice - priorHigh) / priorHigh) * 100.0 else -999.0
        val pct1Y = pctAbove(priorOneYearHigh)
        val pct3Y = pctAbove(priorThreeYearHigh)
        val pct5Y = pctAbove(priorFiveYearHigh)

        val oneYearAgoCandle = candles.lastOrNull { it.datetime < now - oneYearMs }
        val oneYearReturn = if (oneYearAgoCandle != null && oneYearAgoCandle.close > 0)
            ((currentPrice - oneYearAgoCandle.close) / oneYearAgoCandle.close) * 100.0 else 0.0
        val outperformance = oneYearReturn - indexOneYearReturn

        val meetsHighCriteria = mutableSetOf<HighPeriod>()
        if (criteria.highPeriods.contains(HighPeriod.ONE_YEAR)   && pct1Y >= 0.0) meetsHighCriteria.add(HighPeriod.ONE_YEAR)
        if (criteria.highPeriods.contains(HighPeriod.THREE_YEAR) && pct3Y >= 0.0) meetsHighCriteria.add(HighPeriod.THREE_YEAR)
        if (criteria.highPeriods.contains(HighPeriod.FIVE_YEAR)  && pct5Y >= 0.0) meetsHighCriteria.add(HighPeriod.FIVE_YEAR)

        if (meetsHighCriteria.isEmpty()) return null
        if (outperformance < criteria.minOutperformance) return null

        val quoteDetail = runCatching { marketDataService.getQuotes(symbols = symbol) }.getOrNull()?.get(symbol)

        return ScreenedStock(
            symbol = symbol,
            companyName = quoteDetail?.description?.takeIf { it.isNotBlank() } ?: symbol,
            currentPrice = currentPrice,
            percentFromOneYearHigh = pct1Y,
            percentFromThreeYearHigh = pct3Y,
            percentFromFiveYearHigh = pct5Y,
            oneYearReturn = oneYearReturn,
            indexOneYearReturn = indexOneYearReturn,
            outperformance = outperformance,
            meetsHighCriteria = meetsHighCriteria
        )
    }

    suspend fun getStockDetail(symbol: String): Result<StockDetail> {
        return try {
            val dailyHistory = runCatching {
                marketDataService.getPriceHistory(symbol, "year", 1, "daily", 1)
            }.getOrNull()
            val weeklyHistory = runCatching {
                marketDataService.getPriceHistory(symbol, "year", 5, "weekly", 1)
            }.getOrNull()
            val quote = runCatching {
                marketDataService.getQuotes(symbols = symbol)
            }.getOrNull()?.get(symbol)
            val fundamentals = runCatching {
                marketDataService.getInstrumentFundamentals(symbol)
            }.getOrNull()?.instruments?.firstOrNull()

            val dailyCandles  = dailyHistory?.candles ?: emptyList()
            val weeklyCandles = weeklyHistory?.candles ?: emptyList()
            val currentPrice  = quote?.lastPrice?.takeIf { it > 0 }
                ?: dailyCandles.lastOrNull()?.close ?: 0.0

            val dailyCloses = dailyCandles.map { it.close }
            val sma50  = if (dailyCloses.size >= 50)  dailyCloses.takeLast(50).average()  else 0.0
            val sma200 = if (dailyCloses.size >= 200) dailyCloses.takeLast(200).average() else 0.0
            val rsi14  = calculateRSI(dailyCloses, 14)

            val now = System.currentTimeMillis()
            fun ret(candles: List<Candle>, msAgo: Long): Double {
                val ago = candles.lastOrNull { it.datetime < now - msAgo } ?: return 0.0
                return if (ago.close > 0 && currentPrice > 0) ((currentPrice - ago.close) / ago.close) * 100.0 else 0.0
            }
            val ms1  = 30L  * 86_400_000L
            val ms3  = 90L  * 86_400_000L
            val ms6  = 180L * 86_400_000L
            val ms1y = 365L * 86_400_000L
            val ms3y = 3 * ms1y
            val ms5y = 5 * ms1y

            val oneYearReturn = ret(dailyCandles, ms1y)

            // Sector peer comparison
            val sector = SYMBOL_TO_SECTOR[symbol] ?: ""
            val peerSymbols = SECTOR_GROUPS[sector]?.filter { it != symbol } ?: emptyList()
            val peerComparison: List<PeerStock> = try {
                coroutineScope {
                    val deferreds = peerSymbols.map { peer ->
                        peer to async {
                            runCatching {
                                val h = marketDataService.getPriceHistory(peer, "year", 1, "weekly", 1)
                                val c = h.candles
                                if (c.size >= 2 && c.first().close > 0)
                                    ((c.last().close - c.first().close) / c.first().close) * 100.0
                                else 0.0
                            }.getOrElse { 0.0 }
                        }
                    }
                    (deferreds.map { (sym, d) -> PeerStock(sym, d.await(), false) } +
                        PeerStock(symbol, oneYearReturn, true))
                        .sortedByDescending { it.oneYearReturn }
                }
            } catch (e: Exception) { listOf(PeerStock(symbol, oneYearReturn, true)) }

            Result.success(StockDetail(
                symbol = symbol,
                companyName = fundamentals?.description?.takeIf { it.isNotBlank() }
                    ?: quote?.description?.takeIf { it.isNotBlank() }
                    ?: symbol,
                currentPrice = currentPrice,
                priceChange = quote?.netChange ?: 0.0,
                priceChangePercent = quote?.netPercentChange ?: 0.0,
                volume = quote?.totalVolume ?: 0L,
                high52Week = quote?.week52High ?: fundamentals?.fundamental?.high52 ?: 0.0,
                low52Week = quote?.week52Low ?: fundamentals?.fundamental?.low52 ?: 0.0,
                openPrice = quote?.openPrice ?: 0.0,
                dayHigh = quote?.highPrice ?: 0.0,
                dayLow = quote?.lowPrice ?: 0.0,
                bid = quote?.bidPrice ?: 0.0,
                ask = quote?.askPrice ?: 0.0,
                marketCap = fundamentals?.fundamental?.marketCap ?: 0.0,
                peRatio = fundamentals?.fundamental?.peRatio ?: 0.0,
                eps = fundamentals?.fundamental?.epsTTM ?: 0.0,
                dividendYield = fundamentals?.fundamental?.dividendYield ?: 0.0,
                beta = fundamentals?.fundamental?.beta ?: 0.0,
                pbRatio = fundamentals?.fundamental?.pbRatio ?: 0.0,
                roe = fundamentals?.fundamental?.returnOnEquity ?: 0.0,
                sma50 = sma50,
                sma200 = sma200,
                rsi14 = rsi14,
                oneMonthReturn    = ret(dailyCandles, ms1),
                threeMonthReturn  = ret(dailyCandles, ms3),
                sixMonthReturn    = ret(dailyCandles, ms6),
                oneYearReturn     = oneYearReturn,
                threeYearReturn   = ret(weeklyCandles, ms3y),
                fiveYearReturn    = ret(weeklyCandles, ms5y),
                dailyCandles  = dailyCandles,
                weeklyCandles = weeklyCandles,
                sector = sector,
                peerComparison = peerComparison
            ))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getQuotes(symbols: List<String>): Result<Map<String, QuoteDetail>> {
        return try {
            Result.success(marketDataService.getQuotes(symbols = symbols.joinToString(",")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateRSI(closes: List<Double>, period: Int): Double {
        if (closes.size < period + 1) return 50.0
        val changes = closes.zipWithNext { a, b -> b - a }
        var avgGain = changes.take(period).filter { it > 0 }.sumOf { it } / period
        var avgLoss = changes.take(period).filter { it < 0 }.sumOf { -it } / period
        for (i in period until changes.size) {
            avgGain = (avgGain * (period - 1) + maxOf(changes[i], 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-changes[i], 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }
}
