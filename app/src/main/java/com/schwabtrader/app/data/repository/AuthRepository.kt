package com.schwabtrader.app.data.repository

import android.content.Context
import android.util.Base64
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.schwabtrader.app.BuildConfig
import com.schwabtrader.app.data.api.SchwabAuthService
import com.schwabtrader.app.data.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val schwabAuthService: SchwabAuthService,
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val SCHWAB_AUTH_URL = "https://api.schwabapi.com/v1/oauth/authorize"
        private const val SCHWAB_REDIRECT_URI = BuildConfig.SCHWAB_REDIRECT_URI
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isLoggedIn: Boolean
        get() = firebaseAuth.currentUser != null

    val isSchwabConnected: Boolean
        get() = secureStorage.getSchwabRefreshToken() != null

    fun getGoogleSignInClient(webClientId: String): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    suspend fun firebaseSignInWithGoogle(account: GoogleSignInAccount): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Authentication failed")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildSchwabAuthUrl(state: String = generateState()): String {
        return buildString {
            append(SCHWAB_AUTH_URL)
            append("?client_id=").append(BuildConfig.SCHWAB_CLIENT_ID)
            append("&redirect_uri=").append(android.net.Uri.encode(SCHWAB_REDIRECT_URI))
            append("&response_type=code")
            append("&scope=readonly%20trading")
            append("&state=").append(state)
        }
    }

    suspend fun exchangeCodeForTokens(code: String): Result<Unit> {
        return try {
            val basicAuth = createBasicAuth()
            val tokenResponse = schwabAuthService.exchangeCodeForToken(
                basicAuth = basicAuth,
                code = code,
                redirectUri = SCHWAB_REDIRECT_URI
            )
            secureStorage.saveSchwabTokens(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresIn = tokenResponse.expiresIn
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(): Result<String> {
        return try {
            val refreshToken = secureStorage.getSchwabRefreshToken()
                ?: return Result.failure(Exception("No refresh token available"))
            val basicAuth = createBasicAuth()
            val tokenResponse = schwabAuthService.refreshToken(
                basicAuth = basicAuth,
                refreshToken = refreshToken
            )
            secureStorage.saveSchwabTokens(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresIn = tokenResponse.expiresIn
            )
            Result.success(tokenResponse.accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAccessToken(): String? = secureStorage.getSchwabAccessToken()

    fun isAccessTokenExpired(): Boolean = secureStorage.isTokenExpired()

    fun signOut() {
        firebaseAuth.signOut()
        secureStorage.clearSchwabTokens()
    }

    fun disconnectSchwab() {
        secureStorage.clearSchwabTokens()
    }

    private fun createBasicAuth(): String {
        val credentials = "${BuildConfig.SCHWAB_CLIENT_ID}:${BuildConfig.SCHWAB_CLIENT_SECRET}"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private fun generateState(): String {
        return java.util.UUID.randomUUID().toString().replace("-", "")
    }
}
