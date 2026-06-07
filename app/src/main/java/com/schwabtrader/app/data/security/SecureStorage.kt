package com.schwabtrader.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureStorage"
        private const val PREFS_FILE_NAME = "schwab_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "schwab_access_token"
        private const val KEY_REFRESH_TOKEN = "schwab_refresh_token"
        private const val KEY_EXPIRES_AT = "schwab_token_expires_at"
        private const val KEY_ACCOUNT_HASH = "schwab_account_hash"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        createOrRecover()
    }

    private fun buildMasterKey(): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun buildPrefs(key: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun createOrRecover(): SharedPreferences {
        return try {
            buildPrefs(buildMasterKey())
        } catch (e: Exception) {
            // Keystore key invalidated (biometric enrollment changed, reinstall, etc.).
            // Wipe the corrupted keyset file and stale Keystore entry, then start fresh.
            // The user will need to re-link their Schwab account once.
            Log.w(TAG, "EncryptedSharedPreferences keyset corrupted — wiping and recreating", e)
            wipeCorruptedState()
            buildPrefs(buildMasterKey())
        }
    }

    private fun wipeCorruptedState() {
        try {
            context.deleteSharedPreferences(PREFS_FILE_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete corrupted prefs file", e)
        }
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete stale Keystore entry", e)
        }
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

    fun getSchwabAccessToken(): String? =
        sharedPreferences.getString(KEY_ACCESS_TOKEN, null)

    fun getSchwabRefreshToken(): String? =
        sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    fun getTokenExpiresAt(): Long =
        sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)

    fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiresAt()
        if (expiresAt == 0L) return true
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

    fun getAccountHash(): String? =
        sharedPreferences.getString(KEY_ACCOUNT_HASH, null)

    fun hasSchwabTokens(): Boolean =
        getSchwabAccessToken() != null && getSchwabRefreshToken() != null
}
