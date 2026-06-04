package com.schwabtrader.app.data.api

import com.schwabtrader.app.data.api.models.SchwabTokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface SchwabAuthService {

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeCodeForToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String
    ): SchwabTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): SchwabTokenResponse
}
