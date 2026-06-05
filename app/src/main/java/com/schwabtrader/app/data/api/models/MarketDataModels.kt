package com.schwabtrader.app.data.api.models

import com.google.gson.annotations.SerializedName

data class PriceHistoryResponse(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("candles")
    val candles: List<Candle>,
    @SerializedName("empty")
    val empty: Boolean = false
)

data class Candle(
    @SerializedName("open")
    val open: Double,
    @SerializedName("high")
    val high: Double,
    @SerializedName("low")
    val low: Double,
    @SerializedName("close")
    val close: Double,
    @SerializedName("volume")
    val volume: Long,
    @SerializedName("datetime")
    val datetime: Long
)

// Schwab /quotes response: price fields are nested under a "quote" sub-object.
// description and assetType sit at the top-level symbol wrapper.
data class QuoteData(
    @SerializedName("lastPrice")     val lastPrice: Double = 0.0,
    @SerializedName("netChange")     val netChange: Double = 0.0,
    @SerializedName("netPercentChange") val netPercentChange: Double = 0.0,
    @SerializedName("52WkHigh")      val week52High: Double = 0.0,
    @SerializedName("52WkLow")       val week52Low: Double = 0.0,
    @SerializedName("openPrice")     val openPrice: Double = 0.0,
    @SerializedName("highPrice")     val highPrice: Double = 0.0,
    @SerializedName("lowPrice")      val lowPrice: Double = 0.0,
    @SerializedName("closePrice")    val closePrice: Double = 0.0,
    @SerializedName("totalVolume")   val totalVolume: Long = 0L,
    @SerializedName("bidPrice")      val bidPrice: Double = 0.0,
    @SerializedName("askPrice")      val askPrice: Double = 0.0,
    @SerializedName("mark")          val mark: Double = 0.0
)

data class QuoteDetail(
    @SerializedName("description") val description: String = "",
    @SerializedName("assetType")   val assetType: String = "",
    @SerializedName("quote")       val quoteData: QuoteData? = null
) {
    val lastPrice: Double        get() = quoteData?.lastPrice        ?: 0.0
    val netChange: Double        get() = quoteData?.netChange        ?: 0.0
    val netPercentChange: Double get() = quoteData?.netPercentChange ?: 0.0
    val week52High: Double       get() = quoteData?.week52High       ?: 0.0
    val week52Low: Double        get() = quoteData?.week52Low        ?: 0.0
    val openPrice: Double        get() = quoteData?.openPrice        ?: 0.0
    val highPrice: Double        get() = quoteData?.highPrice        ?: 0.0
    val lowPrice: Double         get() = quoteData?.lowPrice         ?: 0.0
    val closePrice: Double       get() = quoteData?.closePrice       ?: 0.0
    val totalVolume: Long        get() = quoteData?.totalVolume      ?: 0L
    val bidPrice: Double         get() = quoteData?.bidPrice         ?: 0.0
    val askPrice: Double         get() = quoteData?.askPrice         ?: 0.0
    val mark: Double             get() = quoteData?.mark             ?: 0.0
}

data class InstrumentsResponse(
    @SerializedName("instruments")
    val instruments: List<InstrumentDetail> = emptyList()
)

data class InstrumentDetail(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("fundamental") val fundamental: FundamentalData? = null
)

data class FundamentalData(
    @SerializedName("high52") val high52: Double = 0.0,
    @SerializedName("low52") val low52: Double = 0.0,
    @SerializedName("peRatio") val peRatio: Double = 0.0,
    @SerializedName("pbRatio") val pbRatio: Double = 0.0,
    @SerializedName("epsTTM") val epsTTM: Double = 0.0,
    @SerializedName("dividendYield") val dividendYield: Double = 0.0,
    @SerializedName("dividendAmount") val dividendAmount: Double = 0.0,
    @SerializedName("beta") val beta: Double = 0.0,
    @SerializedName("marketCap") val marketCap: Double = 0.0,
    @SerializedName("returnOnEquity") val returnOnEquity: Double = 0.0,
    @SerializedName("vol10DayAvg") val vol10DayAvg: Double = 0.0
)
