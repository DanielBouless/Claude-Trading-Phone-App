package com.schwabtrader.app.data.api.models

import com.google.gson.annotations.SerializedName

data class SchwabTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("expires_in")
    val expiresIn: Int,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("scope")
    val scope: String? = null,
    @SerializedName("id_token")
    val idToken: String? = null
)

data class SchwabTokenRequest(
    val grantType: String,
    val code: String? = null,
    val redirectUri: String? = null,
    val refreshToken: String? = null
)
