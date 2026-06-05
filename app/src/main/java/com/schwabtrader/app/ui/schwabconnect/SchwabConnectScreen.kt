package com.schwabtrader.app.ui.schwabconnect

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.schwabtrader.app.ui.theme.AccentBlue
import com.schwabtrader.app.ui.theme.CardBackground
import com.schwabtrader.app.ui.theme.DarkBackground
import com.schwabtrader.app.ui.theme.GainGreen
import com.schwabtrader.app.ui.theme.TextPrimary
import com.schwabtrader.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchwabConnectScreen(
    viewModel: SchwabConnectViewModel,
    onConnected: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val authUrl by viewModel.authUrl.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SchwabConnectUiState.Success -> onConnected()
            is SchwabConnectUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearError()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Schwab Account", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    snackbarData = data
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState is SchwabConnectUiState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting your Schwab account...", color = TextSecondary)
                    }
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Icon
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = AccentBlue
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Connect Your Schwab Account",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "To access your portfolio and place trades, you need to connect your Charles Schwab account using secure OAuth 2.0 authentication.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Security features list
            SecurityFeatureItem(
                icon = Icons.Default.Lock,
                text = "Your credentials are never stored by this app"
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecurityFeatureItem(
                icon = Icons.Default.Security,
                text = "Tokens encrypted with Android Keystore"
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecurityFeatureItem(
                icon = Icons.Default.CheckCircle,
                text = "OAuth 2.0 industry standard authentication"
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    openSchwabAuth(context, authUrl)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue
                )
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "Connect Schwab Account",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You'll be redirected to Schwab's secure login page",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SecurityFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GainGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

private fun openSchwabAuth(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        // Fallback: open in default browser
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                Uri.parse(url)
            )
            context.startActivity(intent)
        } catch (ex: Exception) {
            // Cannot open browser
        }
    }
}
