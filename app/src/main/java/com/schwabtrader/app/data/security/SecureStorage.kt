package com.schwabtrader.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE_NAME = "schwab_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "schwab_access_token"
        private const val KEY_REFRESH_TOKEN = "schwab_refresh_token"
        private const val KEY_EXPIRES_AT = "schwab_token_expires_at"
        private const val KEY_ACCOUNT_HASH = "schwab_account_hash"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSchwabTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Int
    ) {
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getSchwabAccessToken(): String? {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getSchwabRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getTokenExpiresAt(): Long {
        return sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)
    }

    fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiresAt()
        if (expiresAt == 0L) return true
        // Consider expired 60 seconds before actual expiry for safety
        return System.currentTimeMillis() >= (expiresAt - 60_000L)
    }

    fun clearSchwabTokens() {
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_ACCOUNT_HASH)
            .apply()
    }

    fun saveAccountHash(accountHash: String) {
        sharedPreferences.edit()
            .putString(KEY_ACCOUNT_HASH, accountHash)
            .apply()
    }

    fun getAccountHash(): String? {
        return sharedPreferences.getString(KEY_ACCOUNT_HASH, null)
    }

    fun hasSchwabTokens(): Boolean {
        return getSchwabAccessToken() != null && getSchwabRefreshToken() != null
    }
}
