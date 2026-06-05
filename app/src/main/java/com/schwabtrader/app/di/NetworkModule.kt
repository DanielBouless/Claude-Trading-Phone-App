package com.schwabtrader.app.di

import com.schwabtrader.app.BuildConfig
import com.schwabtrader.app.data.api.SchwabAuthService
import com.schwabtrader.app.data.api.SchwabMarketDataService
import com.schwabtrader.app.data.api.SchwabTraderService
import com.schwabtrader.app.data.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.util.Base64
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("api")
    fun provideApiOkHttpClient(
        secureStorage: SecureStorage
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val accessToken = secureStorage.getSchwabAccessToken()

            if (accessToken == null) {
                return@Interceptor chain.proceed(originalRequest)
            }

            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = chain.proceed(authenticatedRequest)

            // Handle 401: attempt token refresh once
            if (response.code == 401) {
                response.close()
                val refreshToken = secureStorage.getSchwabRefreshToken()
                if (refreshToken != null) {
                    try {
                        val credentials = "${BuildConfig.SCHWAB_CLIENT_ID}:${BuildConfig.SCHWAB_CLIENT_SECRET}"
                        val basicAuth = "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"

                        // Build a simple synchronous refresh call using OkHttp directly
                        val refreshRequest = okhttp3.Request.Builder()
                            .url("${BuildConfig.SCHWAB_AUTH_BASE_URL}oauth/token")
                            .header("Authorization", basicAuth)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .post(
                                okhttp3.FormBody.Builder()
                                    .add("grant_type", "refresh_token")
                                    .add("refresh_token", refreshToken)
                                    .build()
                            )
                            .build()

                        val refreshResponse = OkHttpClient().newCall(refreshRequest).execute()
                        if (refreshResponse.isSuccessful) {
                            val body = refreshResponse.body?.string()
                            refreshResponse.close()
                            if (body != null) {
                                val gson = com.google.gson.Gson()
                                val tokenResponse = gson.fromJson<com.schwabtrader.app.data.api.models.SchwabTokenResponse>(
                                    body, com.schwabtrader.app.data.api.models.SchwabTokenResponse::class.java
                                )
                                secureStorage.saveSchwabTokens(
                                    accessToken = tokenResponse.accessToken,
                                    refreshToken = tokenResponse.refreshToken,
                                    expiresIn = tokenResponse.expiresIn
                                )
                                // Retry original request with new token
                                val retryRequest = originalRequest.newBuilder()
                                    .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                                    .build()
                                return@Interceptor chain.proceed(retryRequest)
                            }
                        } else {
                            refreshResponse.close()
                        }
                    } catch (e: Exception) {
                        // Refresh failed, return original 401
                    }
                }
                // Re-execute to get a fresh 401 response
                return@Interceptor chain.proceed(authenticatedRequest)
            }

            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSchwabAuthService(
        @Named("auth") okHttpClient: OkHttpClient
    ): SchwabAuthService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SCHWAB_AUTH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SchwabAuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideSchwabMarketDataService(
        @Named("api") okHttpClient: OkHttpClient
    ): SchwabMarketDataService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SCHWAB_MARKET_DATA_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SchwabMarketDataService::class.java)
    }

    @Provides
    @Singleton
    fun provideSchwabTraderService(
        @Named("api") okHttpClient: OkHttpClient
    ): SchwabTraderService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SCHWAB_TRADER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SchwabTraderService::class.java)
    }
}
