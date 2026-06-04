package com.schwabtrader.app.data.api

import com.schwabtrader.app.data.api.models.AccountResponse
import com.schwabtrader.app.data.api.models.OrderRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SchwabTraderService {

    @GET("accounts")
    suspend fun getAccounts(
        @Query("fields") fields: String = "positions"
    ): List<AccountResponse>

    @GET("accounts/{accountHash}")
    suspend fun getAccount(
        @Path("accountHash") accountHash: String,
        @Query("fields") fields: String = "positions"
    ): AccountResponse

    @POST("accounts/{accountHash}/orders")
    suspend fun placeOrder(
        @Path("accountHash") accountHash: String,
        @Body orderRequest: OrderRequest
    ): Response<Unit>
}
