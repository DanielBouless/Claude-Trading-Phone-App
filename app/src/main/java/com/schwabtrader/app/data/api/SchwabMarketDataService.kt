package com.schwabtrader.app.data.api

import com.schwabtrader.app.data.api.models.InstrumentsResponse
import com.schwabtrader.app.data.api.models.PriceHistoryResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SchwabMarketDataService {

    @GET("pricehistory")
    suspend fun getPriceHistory(
        @Query("symbol") symbol: String,
        @Query("periodType") periodType: String = "year",
        @Query("period") period: Int = 5,
        @Query("frequencyType") frequencyType: String = "weekly",
        @Query("frequency") frequency: Int = 1,
        @Query("startDate") startDate: Long? = null,
        @Query("endDate") endDate: Long? = null,
        @Query("needExtendedHoursData") needExtendedHoursData: Boolean = false
    ): PriceHistoryResponse

    @GET("quotes")
    suspend fun getQuotes(
        @Query("symbols") symbols: String,
        @Query("fields") fields: String = "quote",
        @Query("indicative") indicative: Boolean = false
    ): Map<String, com.schwabtrader.app.data.api.models.QuoteDetail>

    @GET("instruments")
    suspend fun getInstrumentFundamentals(
        @Query("symbol") symbol: String,
        @Query("projection") projection: String = "fundamental"
    ): InstrumentsResponse
}
