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

        // Full Nasdaq exchange listings (~3,083 common stocks, sourced June 2026)
        val NASDAQ_ALL_STOCKS = listOf(
            "AACB", "AACG", "AACI", "AACO", "AACP", "AAL", "AAME", "AAOI", "AAON", "AAPG",
            "AAPL", "AARD", "ABAT", "ABCL", "ABEO", "ABLV", "ABNB", "ABOS", "ABSI", "ABTC",
            "ABTS", "ABUS", "ABVC", "ABVE", "ABVX", "ACAA", "ACAD", "ACB", "ACCL", "ACDC",
            "ACET", "ACFN", "ACGC", "ACGL", "ACHC", "ACHV", "ACIC", "ACLS", "ACNB", "ACNT",
            "ACOG", "ACON", "ACRS", "ACRV", "ACT", "ACTG", "ACXP", "ADAC", "ADAG", "ADBE",
            "ADEA", "ADGM", "ADI", "ADIL", "ADMA", "ADP", "ADPT", "ADSE", "ADSK", "ADTN",
            "ADTX", "ADUS", "ADV", "ADVB", "ADXN", "AEAQ", "AEBI", "AEC", "AEHL", "AEI",
            "AEIS", "AEMD", "AENT", "AEP", "AERT", "AEVA", "AEYE", "AFBI", "AFCG", "AFJK",
            "AFRI", "AFRM", "AFYA", "AGCC", "AGEN", "AGIO", "AGMB", "AGMH", "AGNC", "AGNT",
            "AGRZ", "AGYS", "AHCO", "AHG", "AHMA", "AIAI", "AIDX", "AIFA", "AIFC", "AIFF",
            "AIHS", "AIIO", "AIMD", "AIOS", "AIOT", "AIP", "AIRE", "AIRG", "AIRJ", "AIRO",
            "AIRS", "AIRT", "AISP", "AIXC", "AIXI", "AKAM", "AKAN", "AKBA", "AKTS", "AKTX",
            "ALAB", "ALBT", "ALCO", "ALDF", "ALDX", "ALEC", "ALF", "ALGM", "ALGN", "ALGS",
            "ALGT", "ALHC", "ALIS", "ALKS", "ALKT", "ALLO", "ALLT", "ALM", "ALMS", "ALNT",
            "ALNY", "ALOT", "ALOV", "ALOY", "ALP", "ALPS", "ALRM", "ALRS", "ALT", "ALTI",
            "ALTO", "ALVO", "ALXO", "ALZN", "AMAL", "AMAN", "AMAT", "AMBA", "AMCI", "AMCX",
            "AMD", "AMGN", "AMIX", "AMLX", "AMOD", "AMPG", "AMPH", "AMPL", "AMRN", "AMRX",
            "AMSC", "AMSF", "AMSS", "AMST", "AMTX", "AMZN", "ANAB", "ANDE", "ANGH", "ANGI",
            "ANGO", "ANIK", "ANIP", "ANIX", "ANL", "ANNA", "ANNX", "ANPA", "ANSC", "ANTA",
            "ANTX", "ANY", "AOSL", "AOUT", "APA", "APAC", "APC", "APEI", "APGE", "API",
            "APLD", "APLM", "APM", "APOG", "APP", "APPF", "APPN", "APPS", "APRE", "APVO",
            "APWC", "APXT", "APYX", "AQB", "AQMS", "AQST", "ARAI", "ARAY", "ARBB", "ARBE",
            "ARBK", "ARCB", "ARCI", "ARCL", "ARCT", "ARDX", "AREC", "ARGX", "ARHS", "ARKO",
            "ARM", "ARQ", "ARQQ", "ARQT", "ARRY", "ARTC", "ARTL", "ARTNA", "ARTV", "ARVN",
            "ARXS", "ASBP", "ASLE", "ASMB", "ASML", "ASND", "ASO", "ASPC", "ASPI", "ASPS",
            "ASRT", "ASRV", "ASST", "ASTC", "ASTE", "ASTH", "ASTI", "ASTL", "ASTS", "ASYS",
            "ATAI", "ATAT", "ATCX", "ATEC", "ATEX", "ATGL", "ATHE", "ATII", "ATLC", "ATLN",
            "ATLO", "ATLX", "ATNI", "ATOM", "ATOS", "ATPC", "ATRA", "ATRC", "ATRO", "ATXG",
            "AUBN", "AUC", "AUDC", "AUGO", "AUID", "AUPH", "AURA", "AURE", "AUTL", "AUUD",
            "AVAH", "AVAV", "AVBH", "AVBP", "AVGO", "AVLN", "AVO", "AVPT", "AVT", "AVTX",
            "AVX", "AVXL", "AWRE", "AXG", "AXGN", "AXIN", "AXON", "AXSM", "AXTI", "AYA",
            "AZ", "AZI", "AZTA", "BACC", "BAFN", "BAND", "BANF", "BANL", "BAOS", "BATRA",
            "BATRK", "BAYA", "BBCP", "BBCQ", "BBGI", "BBIO", "BBLG", "BBNX", "BBOT", "BBSI",
            "BCAB", "BCAL", "BCAX", "BCBP", "BCDA", "BCG", "BCML", "BCPC", "BCRX", "BCTX",
            "BCYC", "BDCI", "BDMD", "BDRX", "BDSX", "BDTX", "BEAG", "BEAM", "BEAT", "BEEM",
            "BEEP", "BELFA", "BELFB", "BENF", "BFC", "BFRG", "BFRI", "BFST", "BGC", "BGDE",
            "BGIN", "BGL", "BGLC", "BGM", "BGMS", "BHAV", "BHF", "BHRB", "BHST", "BIAF",
            "BIB", "BIIB", "BILI", "BIOA", "BIOX", "BIRD", "BIS", "BIVI", "BIXI", "BIYA",
            "BJDX", "BJRI", "BKHA", "BKNG", "BL", "BLBD", "BLDP", "BLFS", "BLIN", "BLIV",
            "BLKB", "BLLN", "BLMN", "BLNE", "BLNK", "BLRK", "BLRX", "BLTE", "BLZE", "BMBL",
            "BMEA", "BMGL", "BMHL", "BMM", "BMRA", "BMRC", "BMRN", "BNAI", "BNBX", "BNC",
            "BNGO", "BNKK", "BNRG", "BNTC", "BNTX", "BNZI", "BODI", "BOF", "BOKF", "BOLD",
            "BOLT", "BON", "BOOM", "BOSC", "BOT", "BOTJ", "BOXL", "BPAC", "BPOP", "BPRN",
            "BRAG", "BRAI", "BRBI", "BRCB", "BRFH", "BRID", "BRLS", "BRLT", "BRNS", "BRTX",
            "BRUN", "BRZE", "BSAA", "BSBK", "BSET", "BSVN", "BSY", "BTAI", "BTBD", "BTBT",
            "BTCS", "BTCT", "BTMD", "BTOC", "BTOG", "BTQ", "BTSG", "BTTC", "BULL", "BUSE",
            "BVC", "BVFL", "BVS", "BWAY", "BWB", "BWEN", "BWFG", "BWIN", "BWMN", "BYAH",
            "BYFC", "BYND", "BYRN", "BYSI", "BZ", "BZAI", "BZFD", "BZUN", "CAAS", "CABA",
            "CAC", "CACC", "CADL", "CAI", "CAKE", "CALC", "CALM", "CAMP", "CAMT", "CAN",
            "CAPN", "CAPS", "CAQ", "CARE", "CARG", "CARL", "CART", "CASH", "CASS", "CAST",
            "CASY", "CATY", "CBAT", "CBC", "CBFV", "CBIO", "CBK", "CBLL", "CBNK", "CBRL",
            "CBRS", "CBSH", "CBUS", "CCAP", "CCAQ", "CCB", "CCBG", "CCC", "CCCC", "CCEC",
            "CCEP", "CCG", "CCHH", "CCII", "CCIX", "CCLD", "CCNE", "CCOI", "CCRN", "CCSI",
            "CCTG", "CCXI", "CD", "CDIO", "CDLX", "CDNA", "CDNL", "CDNS", "CDRO", "CDT",
            "CDTG", "CDXS", "CDZI", "CECO", "CEG", "CELC", "CELH", "CELZ", "CENN", "CENT",
            "CENTA", "CENX", "CEPF", "CEPO", "CEPS", "CEPT", "CEPV", "CERS", "CERT", "CETX",
            "CETY", "CEVA", "CFBK", "CFFI", "CFFN", "CG", "CGC", "CGCT", "CGEM", "CGEN",
            "CGNT", "CGNX", "CGON", "CGTL", "CGTX", "CHA", "CHAI", "CHCI", "CHCO", "CHDN",
            "CHEC", "CHEF", "CHKP", "CHMG", "CHPG", "CHRD", "CHRN", "CHRS", "CHSN", "CHYM",
            "CIGI", "CIIT", "CINF", "CING", "CISO", "CISS", "CIVB", "CJMB", "CLBK", "CLBT",
            "CLDX", "CLFD", "CLGN", "CLIK", "CLLS", "CLMB", "CLMT", "CLNE", "CLNN", "CLOV",
            "CLPS", "CLPT", "CLRB", "CLRO", "CLSK", "CLST", "CLWT", "CLYM", "CMCO", "CMCSA",
            "CMCT", "CME", "CMII", "CMMB", "CMND", "CMPS", "CMPX", "CMRC", "CMTL", "CMTV",
            "CNCK", "CNDT", "CNET", "CNEY", "CNOB", "CNSP", "CNTA", "CNTB", "CNTN", "CNTX",
            "CNTY", "CNVS", "CNXC", "CNXN", "COAG", "COCH", "COCO", "COCP", "CODA", "CODX",
            "COFS", "COGT", "COIN", "COKE", "COLA", "COLB", "COLL", "COLM", "COO", "COOT",
            "CORT", "CORZ", "COSM", "COST", "COYA", "CPB", "CPBI", "CPHC", "CPIX", "CPOP",
            "CPRT", "CPRX", "CPSH", "CPSS", "CRAC", "CRAI", "CRAN", "CRAQ", "CRBP", "CRCT",
            "CRDF", "CRDL", "CRDO", "CRE", "CREG", "CRESY", "CREX", "CRGO", "CRIS", "CRMD",
            "CRML", "CRMT", "CRNC", "CRNT", "CRNX", "CRON", "CROX", "CRSP", "CRTO", "CRUS",
            "CRVL", "CRVO", "CRVS", "CRWD", "CRWS", "CRWV", "CSAI", "CSCO", "CSGP", "CSIQ",
            "CSPI", "CSTE", "CSTL", "CSWC", "CSX", "CTAA", "CTAS", "CTKB", "CTMX", "CTNM",
            "CTNT", "CTRM", "CTRN", "CTSH", "CTSO", "CUB", "CUE", "CULP", "CURI", "CURX",
            "CV", "CVBF", "CVCO", "CVGI", "CVKD", "CVLT", "CVRX", "CVV", "CWBC", "CWCO",
            "CWD", "CWST", "CXAI", "CXDO", "CYAB", "CYCN", "CYN", "CYPH", "CYRX", "CYTK",
            "CZFS", "CZNC", "CZWI", "DAAQ", "DAIC", "DAIO", "DAKT", "DARE", "DASH", "DAVE",
            "DBCA", "DBGI", "DBVT", "DBX", "DCBO", "DCGO", "DCOY", "DCTH", "DCX", "DDI",
            "DDOG", "DEFT", "DERM", "DETX", "DEVS", "DFDV", "DFLI", "DFNS", "DFSC", "DFTX",
            "DGICA", "DGICB", "DGII", "DGNX", "DGXX", "DH", "DIBS", "DIOD", "DJCO", "DJT",
            "DKI", "DKNG", "DLHC", "DLO", "DLPN", "DLTH", "DLXY", "DMAA", "DMAC", "DMII",
            "DMRA", "DMRC", "DNLI", "DNMX", "DNTH", "DNUT", "DOGZ", "DOMH", "DOMO", "DOO",
            "DORM", "DOX", "DPRO", "DPZ", "DRCT", "DRDB", "DRH", "DRIO", "DRMA", "DRS",
            "DRTS", "DRUG", "DRVN", "DSAC", "DSGN", "DSGX", "DSP", "DSWL", "DSY", "DTCX",
            "DTI", "DTIL", "DTSQ", "DTSS", "DTST", "DUO", "DUOL", "DUOT", "DVLT", "DWSN",
            "DWTX", "DXCM", "DXLG", "DXPE", "DXST", "DYAI", "DYN", "DYNC", "EA", "EBAY",
            "EBC", "EBMT", "EBON", "ECBK", "ECPG", "ECX", "EDAP", "EDBL", "EDHL", "EDIT",
            "EDRY", "EDSA", "EDTK", "EDUC", "EEFT", "EEIQ", "EFOI", "EFSC", "EFSI", "EFTY",
            "EGAN", "EGBN", "EGHA", "EGHT", "EH", "EHGO", "EHLD", "EHTH", "EIKN", "EJH",
            "ELAB", "ELBM", "ELDN", "ELE", "ELMT", "ELOG", "ELSE", "ELTK", "ELTX", "ELUT",
            "ELVA", "ELVN", "ELWT", "EMAT", "EMBC", "EMIS", "EML", "EMPD", "EMPG", "ENGN",
            "ENGS", "ENLT", "ENLV", "ENPH", "ENSC", "ENSG", "ENTA", "ENTG", "ENTX", "ENVB",
            "ENVX", "EOLS", "EOSE", "EPRX", "EPSM", "EPSN", "EQ", "EQIX", "EQPT", "ERAS",
            "ERIC", "ERIE", "ERII", "ERNA", "ESCA", "ESEA", "ESLA", "ESLT", "ESOA", "ESQ",
            "ESTA", "ETON", "ETS", "EUDA", "EURK", "EVAX", "EVCM", "EVGN", "EVGO", "EVLV",
            "EVO", "EVOX", "EVRG", "EVTV", "EWBC", "EWTX", "EXC", "EXE", "EXEL", "EXFY",
            "EXLS", "EXOZ", "EXPE", "EXPO", "EXYN", "EYE", "EYPT", "EZGO", "EZRA", "FA",
            "FABC", "FACT", "FAMI", "FANG", "FAST", "FATE", "FATN", "FBGL", "FBIO", "FBIZ",
            "FBLA", "FBLG", "FBNC", "FBRX", "FBYD", "FCAP", "FCBC", "FCCO", "FCEL", "FCFS",
            "FCHL", "FCNCA", "FCUV", "FDBC", "FDMT", "FDSB", "FEAM", "FEBO", "FEED", "FEIM",
            "FELE", "FEMY", "FENC", "FERA", "FFAI", "FFBC", "FFIC", "FFIN", "FFIV", "FGBI",
            "FGI", "FGII", "FGL", "FGMC", "FGNX", "FHB", "FHTX", "FIBK", "FIEE", "FIGX",
            "FIP", "FISI", "FISV", "FITB", "FIVE", "FIVN", "FIZZ", "FKWL", "FLD", "FLEX",
            "FLGT", "FLL", "FLNA", "FLNC", "FLNT", "FLUX", "FLWS", "FLX", "FLXS", "FLY",
            "FLYE", "FMAC", "FMAO", "FMBH", "FMFC", "FMNB", "FMST", "FNKO", "FNLC", "FNRN",
            "FNUC", "FNWB", "FNWD", "FOFO", "FORM", "FORTY", "FOSL", "FOX", "FOXA", "FOXF",
            "FOXX", "FRAF", "FRBA", "FRD", "FRGT", "FRHC", "FRME", "FRMI", "FRMM", "FROG",
            "FRPH", "FRPT", "FRSH", "FRST", "FRSX", "FRVO", "FSBC", "FSEA", "FSHP", "FSLY",
            "FSUN", "FSV", "FTAI", "FTCI", "FTEK", "FTFT", "FTHM", "FTLF", "FTNT", "FTRE",
            "FTRK", "FULC", "FULT", "FUSB", "FUSE", "FVAV", "FVCB", "FVN", "FWDI", "FWONA",
            "FWONK", "FWRD", "FWRG", "FXNC", "GABC", "GAIA", "GAIN", "GALT", "GAMB", "GAME",
            "GANX", "GASS", "GAUZ", "GBFH", "GBLI", "GCBC", "GCL", "GCMG", "GCT", "GCTK",
            "GDC", "GDEV", "GDHG", "GDRX", "GDS", "GDTC", "GDYN", "GEG", "GEHC", "GELS",
            "GEMI", "GEN", "GENB", "GENK", "GEOS", "GERN", "GEVO", "GFAI", "GFS", "GGAL",
            "GGRP", "GH", "GHRS", "GIBO", "GIFT", "GIGM", "GIII", "GILD", "GILT", "GITS",
            "GIX", "GLBE", "GLBS", "GLE", "GLIBA", "GLIBK", "GLMD", "GLND", "GLNG", "GLOO",
            "GLPI", "GLRE", "GLSI", "GLUE", "GLXG", "GLXY", "GMAB", "GMEX", "GMHS", "GMM",
            "GNLN", "GNLX", "GNPX", "GNSS", "GNTA", "GNTX", "GO", "GOAI", "GOCO", "GOGO",
            "GOOG", "GOOGL", "GOSS", "GOVX", "GP", "GPAC", "GPAT", "GPRE", "GPRO", "GRAB",
            "GRAL", "GRAN", "GRCE", "GRDX", "GREE", "GRFS", "GRI", "GRML", "GRNQ", "GRPN",
            "GRVY", "GRWG", "GSAT", "GSBC", "GSHD", "GSIT", "GSM", "GSRF", "GSUN", "GT",
            "GTBP", "GTEC", "GTEN", "GTERA", "GTIM", "GTLB", "GTM", "GTX", "GURE", "GUTS",
            "GV", "GVH", "GWAV", "GWRS", "GXAI", "GYRE", "GYRO", "HACQ", "HAFC", "HAIN",
            "HALO", "HAO", "HAS", "HAVA", "HBAN", "HBCP", "HBIO", "HBNB", "HBNC", "HBT",
            "HCAC", "HCAI", "HCAT", "HCHL", "HCIC", "HCKT", "HCM", "HCMA", "HCSG", "HCTI",
            "HCWB", "HDL", "HDRN", "HDSN", "HELE", "HELP", "HEPS", "HERE", "HFBL", "HFFG",
            "HFWA", "HGBL", "HHS", "HIFS", "HIHO", "HIMX", "HIND", "HIT", "HITI", "HIVE",
            "HKIT", "HKPD", "HLIT", "HLMN", "HLNE", "HLP", "HLXC", "HMH", "HNNA", "HNRG",
            "HNST", "HOFT", "HOLO", "HON", "HOOD", "HOPE", "HOWL", "HPAI", "HPK", "HQ",
            "HQI", "HQY", "HRMY", "HRTX", "HRZN", "HSAI", "HSCS", "HSDT", "HSIC", "HSPT",
            "HST", "HSTM", "HTCO", "HTFL", "HTHT", "HTLD", "HTLM", "HTO", "HTOO", "HTZ",
            "HUBC", "HUBG", "HUDI", "HUIZ", "HUMA", "HURA", "HURC", "HURN", "HUT", "HVII",
            "HVMC", "HWBK", "HWC", "HWH", "HWKN", "HXHX", "HYFM", "HYFT", "HYMC", "HYNE",
            "HYPD", "IAC", "IACO", "IART", "IBAC", "IBCP", "IBEX", "IBG", "IBIO", "IBOC",
            "IBRX", "ICCC", "ICCM", "ICFI", "ICG", "ICMB", "ICON", "ICUI", "IDAI", "IDCC",
            "IDN", "IDXX", "IDYA", "IEAG", "IESC", "IFBD", "IFRX", "IGAC", "IGIC", "IHRT",
            "III", "IIIV", "IKT", "ILAG", "ILMN", "IMA", "IMCC", "IMDX", "IMKTA", "IMMP",
            "IMMX", "IMNM", "IMNN", "IMOS", "IMPP", "IMRN", "IMRX", "IMTE", "IMTX", "IMUX",
            "IMVT", "IMXI", "INAB", "INAC", "INBK", "INBS", "INBX", "INCY", "INDB", "INDI",
            "INDP", "INDV", "INEO", "INGN", "INHD", "INKT", "INLF", "INM", "INMB", "INMD",
            "INNV", "INO", "INOD", "INSE", "INSG", "INSM", "INTA", "INTC", "INTG", "INTJ",
            "INTS", "INTZ", "INV", "INVA", "INVE", "INVZ", "IONS", "IOSP", "IOVA", "IPCX",
            "IPDN", "IPEX", "IPFX", "IPGP", "IPHA", "IPM", "IPSC", "IPST", "IPX", "IQ",
            "IQST", "IRD", "IRDM", "IREN", "IRHO", "IRIX", "IRMD", "IRON", "IRTC", "IRWD",
            "ISBA", "ISPC", "ISRG", "ISSC", "ITHA", "ITIC", "ITOC", "ITRI", "ITRN", "IVA",
            "IVDA", "IVF", "IVVD", "IXHL", "IZEA", "IZM", "JACK", "JAGX", "JAKK", "JANX",
            "JATT", "JAZZ", "JBDI", "JBHT", "JBIO", "JBSS", "JCAP", "JCSE", "JCTC", "JD",
            "JDZG", "JEM", "JF", "JFB", "JFIN", "JG", "JJSF", "JKHY", "JL", "JLHL",
            "JMSB", "JOUT", "JOYY", "JRSH", "JTAI", "JUNS", "JVA", "JWEL", "JXG", "JYD",
            "JYNT", "JZ", "JZXN", "KALA", "KALV", "KARO", "KBON", "KBSX", "KC", "KCHV",
            "KDK", "KDP", "KE", "KEEL", "KELYA", "KELYB", "KFFB", "KFII", "KG", "KGEI",
            "KHC", "KIDS", "KIDZ", "KINS", "KITT", "KLAC", "KLIC", "KLRA", "KLRS", "KLXE",
            "KMB", "KMDA", "KMRK", "KMTS", "KNDI", "KNSA", "KOD", "KOPN", "KOSS", "KOYN",
            "KPLT", "KPRX", "KPTI", "KRAQ", "KRMD", "KRNT", "KRNY", "KROS", "KRRO", "KRT",
            "KRUS", "KRYS", "KSCP", "KSPI", "KTCC", "KTOS", "KTTA", "KTWO", "KURA", "KUST",
            "KVAC", "KVHI", "KWM", "KXIN", "KYIV", "KYNB", "KYTX", "KZIA", "LAB", "LABT",
            "LAES", "LAFA", "LAKE", "LAND", "LARK", "LASE", "LATA", "LBGJ", "LBRDA", "LBRDK",
            "LBRX", "LBTYA", "LBTYB", "LBTYK", "LCCC", "LCFY", "LCID", "LCNB", "LCUT", "LE",
            "LECO", "LEDS", "LEE", "LEGH", "LEGN", "LENZ", "LESL", "LEXX", "LFAC", "LFMD",
            "LFS", "LFST", "LFUS", "LFVN", "LFWD", "LGCL", "LGHL", "LGIH", "LGN", "LGND",
            "LGO", "LGVN", "LHAI", "LI", "LICN", "LIEN", "LIF", "LIFE", "LILA", "LILAK",
            "LIMN", "LIN", "LINC", "LIND", "LINE", "LINK", "LIQT", "LITE", "LITS", "LIVE",
            "LIVN", "LIXT", "LKFN", "LKFT", "LKQ", "LKSP", "LLYVA", "LLYVK", "LMAT", "LMB",
            "LMRI", "LNAI", "LNKS", "LNT", "LNTH", "LNZA", "LOAN", "LOBO", "LOCO", "LOGI",
            "LOKV", "LONA", "LOOP", "LOPE", "LOT", "LOVE", "LPAA", "LPBB", "LPCN", "LPCV",
            "LPLA", "LPRO", "LPSN", "LPTH", "LQDA", "LQDT", "LRCX", "LRE", "LRHC", "LSAK",
            "LSBK", "LSCC", "LSE", "LSH", "LSTA", "LTRN", "LTRX", "LUCD", "LUCY", "LUNG",
            "LVO", "LWAC", "LWAY", "LWLG", "LX", "LXEH", "LXEO", "LXRX", "LYEL", "LYFT",
            "LYTS", "LZ", "LZMH", "MAAS", "MACI", "MAGH", "MAKO", "MAMA", "MAMK", "MAMO",
            "MANH", "MARA", "MASI", "MASK", "MASS", "MAT", "MATH", "MAYS", "MAZE", "MB",
            "MBAI", "MBAV", "MBBC", "MBIN", "MBIO", "MBLY", "MBOT", "MBRX", "MBVI", "MBWM",
            "MBX", "MCBS", "MCFT", "MCGA", "MCHB", "MCHP", "MCHX", "MCRB", "MCRI", "MCTA",
            "MDAI", "MDB", "MDBH", "MDCX", "MDGL", "MDIA", "MDLN", "MDLZ", "MDWD", "MDXG",
            "MDXH", "MEDP", "MEGL", "MEHA", "MELI", "MENS", "MEOH", "MERC", "MESH", "MESO",
            "META", "METC", "METCB", "MEVO", "MFI", "MFIN", "MGEE", "MGIH", "MGN", "MGNI",
            "MGNX", "MGPI", "MGRC", "MGRT", "MGRX", "MGTX", "MGX", "MIDD", "MIMI", "MIND",
            "MIRA", "MIRM", "MIST", "MITK", "MKLY", "MKSI", "MKTX", "MLAA", "MLAB", "MLAC",
            "MLCI", "MLCO", "MLEC", "MLGO", "MLKN", "MLTX", "MLYS", "MMED", "MMSI", "MMTX",
            "MMYT", "MNDO", "MNDY", "MNKD", "MNOV", "MNRO", "MNSB", "MNST", "MNTK", "MNTS",
            "MNY", "MOB", "MOBI", "MOBX", "MODD", "MOLN", "MOMO", "MORN", "MOVE", "MPAA",
            "MPB", "MPLT", "MQ", "MRAM", "MRBK", "MRCY", "MRDN", "MREO", "MRLN", "MRM",
            "MRNA", "MRNO", "MRTN", "MRVI", "MRVL", "MRX", "MSAI", "MSBI", "MSEX", "MSFT",
            "MSGM", "MSGY", "MSLE", "MSS", "MTC", "MTCH", "MTEK", "MTEN", "MTEX", "MTLS",
            "MTRX", "MTSI", "MTVA", "MUZE", "MVBF", "MVIS", "MVST", "MWC", "MWH", "MWYN",
            "MXCT", "MXL", "MYGN", "MYPS", "MYRG", "MYSE", "MYSZ", "MYX", "MZTI", "NA",
            "NAAS", "NAGE", "NAII", "NAKA", "NAMI", "NAMM", "NAMS", "NATH", "NAUT", "NAVI",
            "NAVN", "NB", "NBBK", "NBIS", "NBIX", "NBN", "NBP", "NBRG", "NBTB", "NBTX",
            "NCEL", "NCI", "NCMI", "NCNA", "NCNO", "NCPL", "NCRA", "NCSM", "NCT", "NCTY",
            "NDAQ", "NDLS", "NDRA", "NDSN", "NECB", "NEGG", "NEO", "NEOG", "NEON", "NEOV",
            "NEPH", "NERV", "NEUP", "NEWT", "NEXM", "NEXN", "NEXT", "NFBK", "NFE", "NGEN",
            "NGNE", "NHIC", "NHP", "NHTC", "NICE", "NICM", "NIPG", "NIVF", "NIXX", "NKSH",
            "NKTX", "NMFC", "NMIH", "NMP", "NMRA", "NMRK", "NMTC", "NN", "NNDM", "NNE",
            "NNNN", "NNOX", "NODK", "NOEM", "NOMA", "NOTV", "NOVT", "NPAC", "NPCE", "NPT",
            "NRC", "NRDS", "NRIM", "NRIX", "NRSN", "NRXP", "NSIT", "NSSC", "NSTS", "NSYS",
            "NTAP", "NTCL", "NTCT", "NTES", "NTHI", "NTIC", "NTLA", "NTNX", "NTRA", "NTRB",
            "NTRP", "NTSK", "NTWK", "NTWO", "NUAI", "NUCL", "NUTX", "NUVL", "NUWE", "NVA",
            "NVAX", "NVCT", "NVDA", "NVEC", "NVMI", "NVNI", "NVNO", "NVTS", "NVVE", "NVX",
            "NWBI", "NWE", "NWFL", "NWGL", "NWL", "NWPX", "NWS", "NWSA", "NWTG", "NXGL",
            "NXL", "NXPI", "NXPL", "NXST", "NXT", "NXTC", "NXTS", "NXTT", "NXXT", "NYAX",
            "NYXH", "OABI", "OACC", "OBA", "OBAI", "OBIO", "OBT", "OCC", "OCFC", "OCG",
            "OCGN", "OCS", "OCTV", "OCUL", "ODD", "ODFL", "ODTX", "ODYS", "OESX", "OFAL",
            "OFIX", "OFLX", "OGI", "OIM", "OIO", "OKTA", "OKYO", "OLB", "OLED", "OLLI",
            "OLMA", "OLOX", "OLPX", "OM", "OMAB", "OMCL", "OMDA", "OMEX", "OMH", "OMSE",
            "ON", "ONB", "ONC", "ONCH", "ONCO", "ONCY", "ONDS", "ONEG", "ONFO", "ONMD",
            "OPAL", "OPBK", "OPCH", "OPEN", "OPK", "OPRA", "OPRT", "OPRX", "OPTH", "OPTX",
            "OPXS", "ORBS", "ORGN", "ORGO", "ORIC", "ORIO", "ORIQ", "ORIS", "ORKA", "ORKT",
            "ORLY", "ORMP", "ORRF", "OSBC", "OSIS", "OSPN", "OSRH", "OSS", "OST", "OTEX",
            "OTGA", "OTLK", "OTLY", "OUST", "OVBC", "OVID", "OVLY", "OWLS", "OYSE", "OZK",
            "PAAC", "PACB", "PACH", "PAGP", "PAHC", "PAL", "PALI", "PALO", "PAMT", "PANL",
            "PARK", "PASG", "PATK", "PAVM", "PAVS", "PAX", "PAYO", "PAYP", "PAYS", "PAYX",
            "PBFS", "PBHC", "PBM", "PBYI", "PC", "PCAP", "PCB", "PCLA", "PCRX", "PCSA",
            "PCSC", "PCT", "PCTY", "PCVX", "PCYO", "PDC", "PDD", "PDEX", "PDFS", "PDLB",
            "PDSB", "PDYN", "PEBK", "PEBO", "PECO", "PEGA", "PENG", "PENN", "PEP", "PEPG",
            "PERI", "PESI", "PETS", "PETZ", "PFAI", "PFG", "PFIS", "PFSA", "PFX", "PGAC",
            "PGC", "PGEN", "PGNY", "PGY", "PHAT", "PHIO", "PHOE", "PHUN", "PHVS", "PI",
            "PICS", "PIII", "PKBK", "PKOH", "PLAB", "PLAY", "PLBC", "PLBL", "PLBY", "PLCE",
            "PLMK", "PLPC", "PLRX", "PLRZ", "PLSE", "PLSM", "PLTK", "PLTS", "PLUG", "PLUS",
            "PLUT", "PLXS", "PLYX", "PMAX", "PMCB", "PMEC", "PMN", "PMTS", "PMVP", "PN",
            "PNBK", "PNRG", "PNTG", "POCI", "PODC", "PODD", "POET", "POLA", "POLE", "POM",
            "PONO", "PONY", "POOL", "POWI", "POWL", "PPBT", "PPC", "PPCB", "PPHC", "PPIH",
            "PPSI", "PPTA", "PRAA", "PRAX", "PRCH", "PRCT", "PRDO", "PRE", "PRFX", "PRGS",
            "PRHI", "PRLD", "PRME", "PROF", "PROK", "PROP", "PROV", "PRPL", "PRPO", "PRSO",
            "PRTA", "PRTH", "PRTS", "PRVA", "PRZO", "PSHG", "PSIG", "PSIX", "PSKY", "PSMT",
            "PSNL", "PSNY", "PSTV", "PTC", "PTCT", "PTEN", "PTGX", "PTLE", "PTLO", "PTN",
            "PTNM", "PTON", "PTRN", "PUBM", "PULM", "PUSA", "PVLA", "PWP", "PWRL", "PXS",
            "PYPD", "PYPL", "PYXS", "PZZA", "QCLS", "QCOM", "QCRH", "QDEL", "QETA", "QFIN",
            "QH", "QLYS", "QMCO", "QMMM", "QNCX", "QNRX", "QNST", "QNTM", "QRVO", "QS",
            "QSEA", "QSI", "QTEX", "QTI", "QTRX", "QTTB", "QUBT", "QUCY", "QUIK", "QUMS",
            "QURE", "RAAQ", "RACC", "RADX", "RAIL", "RAIN", "RANG", "RANI", "RAPP", "RARE",
            "RAVE", "RAY", "RAYA", "RBB", "RBBN", "RBCAA", "RBKB", "RBNE", "RCAT", "RCEL",
            "RCKT", "RCKY", "RCMT", "RCON", "RCT", "RDAC", "RDAG", "RDCM", "RDGT", "RDHL",
            "RDI", "RDIB", "RDNT", "RDVT", "RDZN", "REAL", "REAX", "REBN", "RECT", "REE",
            "REFI", "REG", "REGN", "RELL", "RELY", "RENT", "RENX", "REPL", "RETO", "REVB",
            "REYN", "RFAI", "RFAM", "RFIL", "RGC", "RGCO", "RGEN", "RGLD", "RGNX", "RGP",
            "RGS", "RGTI", "RIBB", "RICK", "RIGL", "RILY", "RIME", "RIOT", "RIVN", "RJET",
            "RKDA", "RKLB", "RKTO", "RLAY", "RLMD", "RLYB", "RMBI", "RMBS", "RMCF", "RMCO",
            "RMIX", "RMNI", "RMSG", "RMTI", "RNA", "RNAC", "RNAZ", "RNGT", "RNTX", "RNXT",
            "ROAD", "ROC", "ROCK", "ROIV", "ROMA", "ROOT", "ROP", "ROST", "RPAY", "RPD",
            "RPGL", "RPID", "RPRX", "RRBI", "RREV", "RRGB", "RSSS", "RTAC", "RTB", "RUBI",
            "RUM", "RUN", "RUSHA", "RUSHB", "RVMD", "RVSB", "RVSN", "RWAY", "RXRX", "RXST",
            "RXT", "RYAAY", "RYET", "RYM", "RYOJ", "RYTM", "RZLT", "RZLV", "SAAQ", "SABS",
            "SAFT", "SAFX", "SAGT", "SAIA", "SAIC", "SAIH", "SAIL", "SAMG", "SANA", "SANG",
            "SANM", "SATL", "SATS", "SBAC", "SBC", "SBCF", "SBET", "SBFG", "SBFM", "SBGI",
            "SBLK", "SBRA", "SBUX", "SCAG", "SCHL", "SCII", "SCKT", "SCLX", "SCNI", "SCNX",
            "SCPQ", "SCSC", "SCVL", "SCWO", "SCYX", "SCZM", "SDA", "SDHI", "SDM", "SDOT",
            "SDST", "SEAT", "SEDG", "SEED", "SEGG", "SEIC", "SELF", "SELX", "SENEA", "SENEB",
            "SENS", "SEPN", "SERA", "SERV", "SEV", "SEZL", "SFBC", "SFD", "SFHG", "SFIX",
            "SFM", "SFNC", "SFST", "SFWL", "SGA", "SGC", "SGHT", "SGLY", "SGML", "SGMT",
            "SGP", "SGRP", "SGRY", "SHAZ", "SHBI", "SHC", "SHEN", "SHFS", "SHIM", "SHIP",
            "SHLS", "SHMD", "SHOO", "SHOP", "SHPH", "SIBN", "SIEB", "SIFY", "SIGA", "SIGI",
            "SILC", "SILO", "SIMA", "SIMO", "SINT", "SION", "SIRI", "SITM", "SJ", "SKBL",
            "SKIN", "SKK", "SKWD", "SKYA", "SKYE", "SKYQ", "SKYT", "SKYX", "SLAB", "SLDB",
            "SLDE", "SLDP", "SLE", "SLGB", "SLGL", "SLM", "SLMT", "SLN", "SLNG", "SLNH",
            "SLP", "SLS", "SLSN", "SLXN", "SMBC", "SMCI", "SMID", "SMMT", "SMPL", "SMSI",
            "SMTC", "SMTI", "SMTK", "SMX", "SMXT", "SNAL", "SND", "SNDK", "SNDL", "SNDX",
            "SNES", "SNEX", "SNFCA", "SNGX", "SNOA", "SNPS", "SNSE", "SNT", "SNTG", "SNTI",
            "SNWV", "SNY", "SOCA", "SOFI", "SOGP", "SOLS", "SONM", "SONO", "SOPH", "SORA",
            "SORN", "SOTK", "SOUN", "SOWG", "SPAI", "SPCB", "SPEG", "SPFI", "SPHL", "SPKL",
            "SPOK", "SPPL", "SPRB", "SPRC", "SPRO", "SPRY", "SPSC", "SPT", "SPTX", "SPWH",
            "SRAD", "SRBK", "SRCE", "SRPT", "SRRK", "SRTA", "SRTS", "SRZN", "SSAC", "SSBI",
            "SSEA", "SSII", "SSM", "SSNC", "SSP", "SSRM", "SSTI", "SSYS", "STAA", "STAK",
            "STBA", "STEP", "STEX", "STFS", "STHO", "STI", "STIM", "STKE", "STKH", "STKS",
            "STLD", "STNE", "STOK", "STRA", "STRL", "STRO", "STRS", "STRT", "STRZ", "STTK",
            "STX", "SUGP", "SUIG", "SUJA", "SUMA", "SUNE", "SUPN", "SUPX", "SURG", "SUUN",
            "SVA", "SVAC", "SVAQ", "SVCC", "SVCO", "SVIV", "SVRA", "SVRE", "SVRN", "SWAG",
            "SWBI", "SWIM", "SWKS", "SWVL", "SXTC", "SXTP", "SY", "SYBT", "SYM", "SYNA",
            "SYRE", "SZZL", "TACH", "TACO", "TACT", "TALK", "TANH", "TAOP", "TAOX", "TARA",
            "TARS", "TASK", "TATT", "TAVI", "TAYD", "TBBK", "TBCH", "TBH", "TBLA", "TBPH",
            "TBRG", "TC", "TCBI", "TCBK", "TCBS", "TCMD", "TCOM", "TCRT", "TCRX", "TCX",
            "TDAC", "TDIC", "TDTH", "TDUP", "TDWD", "TEAD", "TEAM", "TECH", "TECX", "TELA",
            "TELO", "TEM", "TENB", "TENX", "TFSL", "TGHL", "TGL", "TGTX", "TH", "THCH",
            "THFF", "THH", "THRM", "THRY", "TIGO", "TIL", "TILE", "TIPT", "TITN", "TJGC",
            "TKLF", "TKNO", "TLF", "TLIH", "TLN", "TLNC", "TLPH", "TLRY", "TLS", "TLSA",
            "TLSI", "TLX", "TMC", "TMCI", "TMDX", "TMTS", "TMUS", "TNDM", "TNGX", "TNMG",
            "TNON", "TNXP", "TNYA", "TOI", "TOMZ", "TONX", "TOP", "TORO", "TOWN", "TOYO",
            "TPCS", "TPG", "TPST", "TRAX", "TRDA", "TREE", "TRGS", "TRI", "TRIB", "TRIN",
            "TRIP", "TRMB", "TRMD", "TRNS", "TRON", "TROO", "TRS", "TRSG", "TRUG", "TRUP",
            "TRVG", "TRVI", "TSAT", "TSBK", "TSCO", "TSEM", "TSHA", "TSLA", "TSSI", "TTAN",
            "TTD", "TTEC", "TTEK", "TTGT", "TTMI", "TTRX", "TTWO", "TULP", "TURB", "TUSK",
            "TVA", "TVAI", "TVGN", "TVRD", "TVTX", "TWAV", "TWFG", "TWG", "TWIN", "TWLV",
            "TWST", "TXG", "TXMD", "TXN", "TXRH", "TYGO", "TYRA", "TZOO", "UAL", "UBCP",
            "UBSI", "UBXG", "UCFI", "UCL", "UCTT", "UCYB", "UEIC", "UFCS", "UFG", "UFPI",
            "UFPT", "UG", "UGRO", "UK", "ULBI", "ULCC", "ULH", "ULTA", "UMBF", "UNB",
            "UNCY", "UNIT", "UNTY", "UONE", "UONEK", "UPB", "UPBD", "UPC", "UPLD", "UPST",
            "UPWK", "UPXI", "URBN", "URGN", "UROY", "USCB", "USEA", "USEG", "USGO", "USIO",
            "USLM", "UTMD", "UTSI", "UVSP", "UXIN", "UYSC", "UZX", "VABK", "VACH", "VALN",
            "VANI", "VBIO", "VBNK", "VC", "VCEL", "VCIG", "VCYT", "VECO", "VEEA", "VEEE",
            "VELO", "VEON", "VERA", "VERI", "VERX", "VFF", "VFS", "VGAS", "VHC", "VHCP",
            "VHUB", "VIAV", "VINP", "VIOT", "VIRC", "VISN", "VITL", "VIVK", "VIVO", "VIVS",
            "VKTX", "VLGEA", "VLY", "VMD", "VMET", "VNCE", "VNDA", "VNET", "VNME", "VNOM",
            "VOD", "VRA", "VRAX", "VRCA", "VRDN", "VREX", "VRM", "VRME", "VRNS", "VRRM",
            "VRSK", "VRSN", "VRTX", "VS", "VSA", "VSAT", "VSEC", "VSEE", "VSME", "VSNT",
            "VSTD", "VSTM", "VTGN", "VTIX", "VTRS", "VTSI", "VTVT", "VUZI", "VVOS", "VWAV",
            "VYNE", "WABC", "WAFD", "WAI", "WALD", "WATT", "WAVE", "WAY", "WB", "WBD",
            "WBTN", "WBUY", "WCT", "WDAY", "WDC", "WDFC", "WEN", "WENN", "WERN", "WEST",
            "WETH", "WETO", "WEYS", "WFCF", "WFF", "WFRD", "WGRX", "WGS", "WHWK", "WILC",
            "WIMI", "WINA", "WING", "WIX", "WKEY", "WKHS", "WKSP", "WLDN", "WLDS", "WLFC",
            "WLII", "WLTH", "WMG", "WMT", "WNEB", "WOK", "WOOF", "WPRT", "WRAP", "WRD",
            "WRLD", "WSBC", "WSBF", "WSBK", "WSC", "WSE", "WSFS", "WSHP", "WSTN", "WTBA",
            "WTF", "WTG", "WTO", "WULF", "WVE", "WVVI", "WWD", "WXM", "WYFI", "WYHG",
            "WYNN", "XBIO", "XBIT", "XBP", "XCBE", "XCH", "XE", "XEL", "XELB", "XENE",
            "XERS", "XGN", "XHG", "XHLD", "XLO", "XMAX", "XNET", "XOMA", "XOS", "XP",
            "XPEL", "XPON", "XRAY", "XRPN", "XRTX", "XRX", "XSLL", "XTIA", "XTLB", "XWEL",
            "XXII", "YAAS", "YB", "YDDL", "YDES", "YDKG", "YHC", "YHGJ", "YHNA", "YI",
            "YIBO", "YJ", "YMAT", "YMT", "YOOV", "YOUL", "YQ", "YSWY", "YSXT", "YTRA",
            "YXT", "YYAI", "YYGH", "Z", "ZBAO", "ZBIO", "ZBRA", "ZD", "ZDAI", "ZENA",
            "ZEO", "ZG", "ZION", "ZJK", "ZJYL", "ZKIN", "ZKP", "ZLAB", "ZM", "ZNB",
            "ZNTL", "ZOOZ", "ZS", "ZSTK", "ZTEK", "ZTG", "ZUMZ", "ZURA", "ZVRA", "ZYBT",
            "ZYME"
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
            IndexType.NASDAQ -> NASDAQ_ALL_STOCKS
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
