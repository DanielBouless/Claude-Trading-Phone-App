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

data class QuoteResponse(
    // Map of symbol -> QuoteDetail
    val quotes: Map<String, QuoteDetail> = emptyMap()
)

data class QuoteDetail(
    @SerializedName("lastPrice")
    val lastPrice: Double = 0.0,
    @SerializedName("netChange")
    val netChange: Double = 0.0,
    @SerializedName("netPercentChange")
    val netPercentChange: Double = 0.0,
    @SerializedName("52WkHigh")
    val week52High: Double = 0.0,
    @SerializedName("52WkLow")
    val week52Low: Double = 0.0,
    @SerializedName("openPrice")
    val openPrice: Double = 0.0,
    @SerializedName("highPrice")
    val highPrice: Double = 0.0,
    @SerializedName("lowPrice")
    val lowPrice: Double = 0.0,
    @SerializedName("closePrice")
    val closePrice: Double = 0.0,
    @SerializedName("totalVolume")
    val totalVolume: Long = 0L,
    @SerializedName("description")
    val description: String = "",
    @SerializedName("assetType")
    val assetType: String = "",
    @SerializedName("bidPrice")
    val bidPrice: Double = 0.0,
    @SerializedName("askPrice")
    val askPrice: Double = 0.0,
    @SerializedName("mark")
    val mark: Double = 0.0
)
