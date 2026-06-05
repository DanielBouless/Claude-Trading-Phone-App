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
    NASDAQ("QQQ", "NASDAQ 100"),
    DOW("DIA", "Dow Jones 30")
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
        // Full S&P 500 constituent list (sourced from datasets/s-and-p-500-companies, June 2026)
        val SP500_STOCKS = listOf(
            "MMM", "AOS", "ABT", "ABBV", "ACN", "ADBE", "AMD", "AES", "AFL", "A",
            "APD", "ABNB", "AKAM", "ALB", "ARE", "ALGN", "ALLE", "LNT", "ALL", "GOOGL",
            "GOOG", "MO", "AMZN", "AMCR", "AEE", "AEP", "AXP", "AIG", "AMT", "AWK",
            "AMP", "AME", "AMGN", "APH", "ADI", "AON", "APA", "APO", "AAPL", "AMAT",
            "APP", "APTV", "ACGL", "ADM", "ARES", "ANET", "AJG", "AIZ", "T", "ATO",
            "ADSK", "ADP", "AZO", "AVB", "AVY", "AXON", "BKR", "BALL", "BAC", "BAX",
            "BDX", "BRK.B", "BBY", "TECH", "BIIB", "BLK", "BX", "BNY", "BA", "BKNG",
            "BSX", "BMY", "AVGO", "BR", "BRO", "BF.B", "BLDR", "BG", "BXP", "CHRW",
            "CDNS", "CPT", "CPB", "COF", "CAH", "CCL", "CARR", "CVNA", "CASY", "CAT",
            "CBOE", "CBRE", "CDW", "COR", "CNC", "CNP", "CF", "CRL", "SCHW", "CHTR",
            "CVX", "CMG", "CB", "CHD", "CIEN", "CI", "CINF", "CTAS", "CSCO", "C",
            "CFG", "CLX", "CME", "CMS", "KO", "CTSH", "COHR", "COIN", "CL", "CMCSA",
            "FIX", "CAG", "COP", "ED", "STZ", "CEG", "COO", "CPRT", "GLW", "CPAY",
            "CTVA", "CSGP", "COST", "CRH", "CRWD", "CCI", "CSX", "CMI", "CVS", "DHR",
            "DRI", "DDOG", "DVA", "DECK", "DE", "DELL", "DAL", "DVN", "DXCM", "FANG",
            "DLR", "DG", "DLTR", "D", "DPZ", "DASH", "DOV", "DOW", "DHI", "DTE",
            "DUK", "DD", "ETN", "EBAY", "SATS", "ECL", "EIX", "EW", "EA", "ELV",
            "EME", "EMR", "ETR", "EOG", "EQT", "EFX", "EQIX", "EQR", "ERIE", "ESS",
            "EL", "EG", "EVRG", "ES", "EXC", "EXE", "EXPE", "EXPD", "EXR", "XOM",
            "FFIV", "FDS", "FICO", "FAST", "FRT", "FDX", "FIS", "FITB", "FSLR", "FE",
            "FISV", "F", "FTNT", "FTV", "FOXA", "FOX", "BEN", "FCX", "GRMN", "IT",
            "GE", "GEHC", "GEV", "GEN", "GNRC", "GD", "GIS", "GM", "GPC", "GILD",
            "GPN", "GL", "GDDY", "GS", "HAL", "HIG", "HAS", "HCA", "DOC", "HSIC",
            "HSY", "HPE", "HLT", "HD", "HON", "HRL", "HST", "HWM", "HPQ", "HUBB",
            "HUM", "HBAN", "HII", "IBM", "IEX", "IDXX", "ITW", "INCY", "IR", "PODD",
            "INTC", "IBKR", "ICE", "IFF", "IP", "INTU", "ISRG", "IVZ", "INVH", "IQV",
            "IRM", "JBHT", "JBL", "JKHY", "J", "JNJ", "JCI", "JPM", "KVUE", "KDP",
            "KEY", "KEYS", "KMB", "KIM", "KMI", "KKR", "KLAC", "KHC", "KR", "LHX",
            "LH", "LRCX", "LVS", "LDOS", "LEN", "LII", "LLY", "LIN", "LYV", "LMT",
            "L", "LOW", "LULU", "LITE", "LYB", "MTB", "MPC", "MAR", "MRSH", "MLM",
            "MAS", "MA", "MKC", "MCD", "MCK", "MDT", "MRK", "META", "MET", "MTD",
            "MGM", "MCHP", "MU", "MSFT", "MAA", "MRNA", "TAP", "MDLZ", "MPWR", "MNST",
            "MCO", "MS", "MOS", "MSI", "MSCI", "NDAQ", "NTAP", "NFLX", "NEM", "NWSA",
            "NWS", "NEE", "NKE", "NI", "NDSN", "NSC", "NTRS", "NOC", "NCLH", "NRG",
            "NUE", "NVDA", "NVR", "NXPI", "ORLY", "OXY", "ODFL", "OMC", "ON", "OKE",
            "ORCL", "OTIS", "PCAR", "PKG", "PLTR", "PANW", "PH", "PAYX", "PYPL", "PNR",
            "PEP", "PFE", "PCG", "PM", "PSX", "PNW", "PNC", "POOL", "PPG", "PPL",
            "PFG", "PG", "PGR", "PLD", "PRU", "PEG", "PTC", "PSA", "PHM", "PWR",
            "QCOM", "DGX", "RL", "RJF", "RTX", "O", "REG", "REGN", "RF", "RSG",
            "RMD", "RVTY", "HOOD", "ROK", "ROL", "ROP", "ROST", "RCL", "SPGI", "CRM",
            "SNDK", "SBAC", "SLB", "STX", "SRE", "NOW", "SHW", "SPG", "SWKS", "SJM",
            "SW", "SNA", "SOLV", "SO", "LUV", "SWK", "SBUX", "STT", "STLD", "STE",
            "SYK", "SMCI", "SYF", "SNPS", "SYY", "TMUS", "TROW", "TTWO", "TPR", "TRGP",
            "TGT", "TEL", "TDY", "TER", "TSLA", "TXN", "TPL", "TXT", "TMO", "TJX",
            "TKO", "TSCO", "TT", "TDG", "TRV", "TRMB", "TFC", "TYL", "TSN", "USB",
            "UBER", "UDR", "ULTA", "UNP", "UAL", "UPS", "URI", "UNH", "UHS", "VLO",
            "VEEV", "VTR", "VLTO", "VRSN", "VRSK", "VZ", "VRTX", "VRT", "VTRS", "VICI",
            "V", "VST", "VMC", "WRB", "GWW", "WAB", "WMT", "DIS", "WBD", "WM",
            "WAT", "WEC", "WFC", "WELL", "WST", "WDC", "WY", "WSM", "WMB", "WTW",
            "WDAY", "WYNN", "XEL", "XYL", "YUM", "ZBRA", "ZBH", "ZTS"
        )

        // NASDAQ-100 constituents (post December 2025 annual reconstitution)
        // Added: ALNY, FER, INSM, MPWR, STX, WDC  |  Removed: BIIB, CDW, GFS, LULU, ON, TTD
        val NASDAQ100_STOCKS = listOf(
            "AAPL", "MSFT", "NVDA", "AMZN", "META", "GOOGL", "GOOG", "TSLA", "AVGO", "COST",
            "ASML", "NFLX", "AZN", "AMD", "ADBE", "QCOM", "INTU", "CSCO", "TMUS", "APP",
            "TXN", "AMAT", "AMGN", "ISRG", "BKNG", "HON", "VRTX", "GILD", "REGN", "ADI",
            "SBUX", "MDLZ", "LRCX", "INTC", "KLAC", "SNPS", "MU", "CDNS", "MRVL", "PANW",
            "FTNT", "ABNB", "PYPL", "MAR", "MELI", "KDP", "ORLY", "AEP", "PAYX", "MNST",
            "CTAS", "ROST", "WDAY", "NXPI", "DXCM", "PCAR", "CEG", "EXC", "IDXX", "BKR",
            "CHTR", "CMCSA", "FAST", "ODFL", "VRSK", "EA", "DLTR", "CPRT", "TTWO", "GEHC",
            "XEL", "KHC", "CTSH", "ANSS", "FANG", "WBD", "EBAY", "ARM", "GRMN", "MCHP",
            "ZS", "TEAM", "DDOG", "CRWD", "OKTA", "MDB", "PLTR", "ADP", "ADSK", "PEP",
            "ALNY", "FER", "INSM", "MPWR", "STX", "WDC"
        )

        // Dow Jones Industrial Average — 30 components (SHW replaced DOW Inc. in late 2025)
        val DOW30_STOCKS = listOf(
            "AAPL", "AMGN", "AMZN", "AXP", "BA", "CAT", "CRM", "CSCO", "CVX", "DIS",
            "GS", "HD", "HON", "IBM", "JNJ", "JPM", "KO", "MCD", "MMM", "MRK",
            "MSFT", "NKE", "NVDA", "PG", "SHW", "TRV", "UNH", "V", "VZ", "WMT"
        )

        val SECTOR_GROUPS: Map<String, List<String>> = mapOf(
            "Technology" to listOf(
                "AAPL", "MSFT", "NVDA", "AVGO", "ORCL", "CSCO", "ACN", "IBM", "AMD", "QCOM",
                "TXN", "INTC", "ADBE", "INTU", "AMAT", "KLAC", "LRCX", "MRVL", "MU", "SNPS",
                "CDNS", "FTNT", "PANW", "NXPI", "ADI", "WDAY", "ZS", "CRWD", "DDOG", "OKTA",
                "PLTR", "MDB", "TEAM", "APP", "ARM", "MPWR", "STX", "WDC", "ANSS", "CTSH",
                "IT", "AKAM", "ZBRA", "VRSN", "NTAP", "CDW", "HPQ", "HPE", "KEYS", "TER",
                "ADSK", "ADP", "PAYX", "GRMN", "MCHP", "GDDY", "GEN", "MSI", "COHR", "DELL",
                "GLW", "APH", "FFIV", "EPAM", "LDOS", "PTC", "ROP", "PAYC", "SMCI", "SNDK"
            ),
            "Communication Services" to listOf(
                "GOOGL", "GOOG", "META", "NFLX", "CMCSA", "DIS", "T", "VZ", "TMUS",
                "CHTR", "EA", "LYV", "PARA", "WBD", "OMC", "IPG", "FOXA", "FOX",
                "NWS", "NWSA", "TTWO", "SATS", "TKO"
            ),
            "Consumer Discretionary" to listOf(
                "AMZN", "TSLA", "HD", "MCD", "NKE", "LOW", "SBUX", "TJX", "BKNG", "CMG",
                "ORLY", "AZO", "GM", "F", "APTV", "DHI", "LEN", "PHM", "NVR", "TOL",
                "ROST", "BBY", "DRI", "YUM", "HLT", "MAR", "MGM", "WYNN", "LVS",
                "RCL", "CCL", "NCLH", "EXPE", "ABNB", "EBAY", "CPRT", "MELI",
                "DLTR", "DECK", "TPR", "RL", "LULU", "TSCO", "WSM", "ULTA", "CVNA"
            ),
            "Consumer Staples" to listOf(
                "WMT", "COST", "PG", "KO", "PEP", "PM", "MO", "MDLZ", "CL", "KHC",
                "GIS", "SJM", "CAG", "HRL", "MKC", "CPB", "CHD", "CLX", "EL",
                "KR", "SYY", "ADM", "BG", "TSN", "CVS", "KDP", "MNST", "KVUE", "TAP"
            ),
            "Healthcare" to listOf(
                "LLY", "JNJ", "UNH", "MRK", "ABBV", "ABT", "TMO", "DHR", "BMY", "AMGN",
                "PFE", "GILD", "ISRG", "VRTX", "REGN", "BIIB", "IDXX", "IQV", "ZBH",
                "BDX", "BSX", "EW", "SYK", "MDT", "HOLX", "DXCM", "ALGN", "RMD",
                "HSIC", "CNC", "HUM", "MOH", "CI", "MCK", "CAH", "HCA", "DVA",
                "GEHC", "AZN", "MRNA", "ALNY", "INSM", "INCY", "PODD", "RVTY",
                "BAX", "LH", "DGX", "COO", "TECH", "VTRS", "VEEV", "SOLV"
            ),
            "Financials" to listOf(
                "BRK.B", "JPM", "BAC", "WFC", "GS", "MS", "C", "AXP", "BLK", "SCHW",
                "CB", "MRSH", "AON", "MET", "PRU", "AFL", "ALL", "TRV", "AIG", "PGR",
                "BNY", "STT", "NTRS", "USB", "PNC", "TFC", "FITB", "HBAN", "KEY", "CFG",
                "RF", "MTB", "V", "MA", "PYPL", "FIS", "FI", "GPN", "AMP", "IVZ",
                "BEN", "TROW", "NDAQ", "ICE", "CME", "CBOE", "KKR", "APO", "ARES",
                "ACGL", "AJG", "AIZ", "RJF", "CINF", "GL", "PFG", "IBKR", "HOOD",
                "COIN", "SPGI", "MCO", "MSCI", "FDX"
            ),
            "Energy" to listOf(
                "XOM", "CVX", "COP", "EOG", "SLB", "MPC", "PSX", "VLO", "OXY",
                "HES", "DVN", "FANG", "APA", "BKR", "HAL", "OKE", "WMB", "KMI",
                "CTRA", "EQT", "TRGP", "VST", "CEG", "EXE"
            ),
            "Materials" to listOf(
                "LIN", "APD", "SHW", "ECL", "FCX", "NEM", "NUE", "STLD", "CF", "MOS",
                "ALB", "PPG", "IFF", "EMN", "AVY", "PKG", "IP", "MLM", "VMC", "CRH",
                "DOW", "DD", "CTVA", "FMC", "CE", "SW", "BALL"
            ),
            "Industrials" to listOf(
                "GE", "GEV", "CAT", "HON", "RTX", "LMT", "BA", "NOC", "GD", "LHX",
                "TDG", "UPS", "FDX", "NSC", "UNP", "CSX", "WAB", "GWW", "MMM", "EMR",
                "ETN", "PH", "ROK", "AME", "CARR", "OTIS", "XYL", "IEX", "GNRC", "TT",
                "JCI", "FAST", "SNA", "SWK", "MAS", "ALLE", "IR", "RSG", "WM", "CTAS",
                "VRSK", "EFX", "PCAR", "ODFL", "JBHT", "CHRW", "EXPD", "J", "FTV",
                "HWM", "TXT", "LDOS", "HUBB", "ITW", "DOV", "NDSN", "TRMB", "URI",
                "PWR", "EME", "FIX", "CPAY", "BLDR", "CSGP", "ROL", "ROP", "AXON"
            ),
            "Real Estate" to listOf(
                "PLD", "AMT", "EQIX", "CCI", "PSA", "EXR", "SBAC", "DLR", "O", "WELL",
                "VTR", "ARE", "BXP", "KIM", "REG", "FRT", "SPG", "EQR", "AVB",
                "ESS", "MAA", "UDR", "CPT", "INVH", "VICI", "DOC"
            ),
            "Utilities" to listOf(
                "NEE", "DUK", "SO", "D", "AEP", "EXC", "XEL", "SRE", "ED", "ETR",
                "PPL", "FE", "ES", "EIX", "CNP", "NI", "AEE", "WEC", "LNT", "EVRG",
                "AWK", "CMS", "DTE", "NRG", "PCG", "AES", "PNW"
            )
        )

        val SYMBOL_TO_SECTOR: Map<String, String> = SECTOR_GROUPS
            .flatMap { (sector, symbols) -> symbols.map { it to sector } }
            .toMap()

        fun stocksForIndex(index: IndexType): List<String> = when (index) {
            IndexType.SP500  -> SP500_STOCKS
            IndexType.NASDAQ -> NASDAQ100_STOCKS
            IndexType.DOW    -> DOW30_STOCKS
        }
    }

    private val semaphore = Semaphore(10)

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
        val stocks = stocksForIndex(criteria.index)
        val total = stocks.size

        coroutineScope {
            val deferreds = stocks.map { symbol ->
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

        val priorOneYearHigh   = priorCandles.filter { it.datetime >= now - oneYearMs     }.maxOfOrNull { it.close } ?: 0.0
        val priorThreeYearHigh = priorCandles.filter { it.datetime >= now - 3 * oneYearMs }.maxOfOrNull { it.close } ?: 0.0
        val priorFiveYearHigh  = priorCandles.filter { it.datetime >= now - 5 * oneYearMs }.maxOfOrNull { it.close } ?: 0.0

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
            val peerSemaphore = Semaphore(5)
            val peerComparison: List<PeerStock> = try {
                coroutineScope {
                    val deferreds = peerSymbols.take(20).map { peer ->
                        peer to async {
                            peerSemaphore.withPermit {
                                runCatching {
                                    val h = marketDataService.getPriceHistory(peer, "year", 1, "weekly", 1)
                                    val c = h.candles
                                    if (c.size >= 2 && c.first().close > 0)
                                        ((c.last().close - c.first().close) / c.first().close) * 100.0
                                    else 0.0
                                }.getOrElse { 0.0 }
                            }
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
