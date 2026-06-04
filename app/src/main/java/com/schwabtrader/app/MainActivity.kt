package com.schwabtrader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.schwabtrader.app.ui.navigation.AppNavigation
import com.schwabtrader.app.ui.theme.SchwabTraderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingOAuthCode: String? = null
    private var pendingOAuthState: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle intent that started the activity (OAuth callback from cold start)
        handleIntent(intent)

        setContent {
            SchwabTraderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        pendingOAuthCode = pendingOAuthCode,
                        pendingOAuthState = pendingOAuthState,
                        onOAuthConsumed = {
                            pendingOAuthCode = null
                            pendingOAuthState = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val isAppLink = data.scheme == "https" &&
            data.host == "danielbouless.github.io" &&
            data.path?.startsWith("/Claude-Trading-Phone-App/oauth") == true
        val isFallbackScheme = data.scheme == "schwabtrader" && data.host == "oauth"
        if (isAppLink || isFallbackScheme) {
            pendingOAuthCode = data.getQueryParameter("code")
            pendingOAuthState = data.getQueryParameter("state")
        }
    }
}
