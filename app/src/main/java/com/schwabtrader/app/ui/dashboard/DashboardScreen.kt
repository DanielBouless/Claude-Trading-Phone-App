package com.schwabtrader.app.ui.dashboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwabtrader.app.data.repository.Portfolio
import com.schwabtrader.app.data.repository.PortfolioPosition
import com.schwabtrader.app.ui.theme.AccentBlue
import com.schwabtrader.app.ui.theme.CardBackground
import com.schwabtrader.app.ui.theme.DarkBackground
import com.schwabtrader.app.ui.theme.GainGreen
import com.schwabtrader.app.ui.theme.LossRed
import com.schwabtrader.app.ui.theme.SurfaceVariant
import com.schwabtrader.app.ui.theme.TextPrimary
import com.schwabtrader.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onConnectSchwab: () -> Unit,
    onNavigateToOrder: (symbol: String, accountHash: String) -> Unit,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshPortfolio()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState !is DashboardUiState.Loading) {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Portfolio",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPortfolio() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                    }
                    IconButton(onClick = {
                        viewModel.signOut()
                        onSignOut()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }

                is DashboardUiState.NotConnected -> {
                    NotConnectedContent(onConnectSchwab = onConnectSchwab)
                }

                is DashboardUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.refreshPortfolio() }
                    )
                }

                is DashboardUiState.Success -> {
                    PortfolioContent(
                        portfolio = state.portfolio,
                        onBuyStock = { symbol ->
                            onNavigateToOrder(symbol, state.portfolio.accountHash)
                        }
                    )
                }

                else -> {}
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = CardBackground,
                contentColor = AccentBlue
            )
        }
    }
}

@Composable
private fun PortfolioContent(
    portfolio: Portfolio,
    onBuyStock: (String) -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Portfolio header card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Total Portfolio Value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currencyFormatter.format(portfolio.totalValue),
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val dayGainColor = if (portfolio.dayGainLoss >= 0) GainGreen else LossRed
                    val dayGainIcon = if (portfolio.dayGainLoss >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = dayGainIcon,
                            contentDescription = null,
                            tint = dayGainColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${if (portfolio.dayGainLoss >= 0) "+" else ""}${currencyFormatter.format(portfolio.dayGainLoss)} " +
                                "(${if (portfolio.dayGainLossPercent >= 0) "+" else ""}${"%.2f".format(portfolio.dayGainLossPercent)}%)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = dayGainColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (portfolio.buyingPower > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Buying Power: ${currencyFormatter.format(portfolio.buyingPower)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        if (portfolio.positions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No positions found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            item {
                Text(
                    text = "Positions",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(portfolio.positions) { position ->
                PositionCard(
                    position = position,
                    onBuyMore = { onBuyStock(position.symbol) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun PositionCard(
    position: PortfolioPosition,
    onBuyMore: () -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
    val gainColor = if (position.gainLoss >= 0) GainGreen else LossRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = position.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (position.companyName.isNotBlank() && position.companyName != position.symbol) {
                        Text(
                            text = position.companyName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${"%.4f".format(position.quantity)} shares @ ${currencyFormatter.format(position.averagePrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currencyFormatter.format(position.marketValue),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (position.gainLoss >= 0) "+" else ""}${currencyFormatter.format(position.gainLoss)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = gainColor
                    )
                    Text(
                        text = "(${if (position.gainLossPercent >= 0) "+" else ""}${"%.2f".format(position.gainLossPercent)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = gainColor
                    )
                }
            }

            if (position.dayGainLoss != 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                val dayGainColor = if (position.dayGainLoss >= 0) GainGreen else LossRed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Today:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${if (position.dayGainLoss >= 0) "+" else ""}${currencyFormatter.format(position.dayGainLoss)} " +
                            "(${if (position.dayGainLossPercent >= 0) "+" else ""}${"%.2f".format(position.dayGainLossPercent)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = dayGainColor
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConnectedContent(onConnectSchwab: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Schwab Account Not Connected",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect your Charles Schwab account to view your portfolio",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onConnectSchwab,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Connect Schwab Account")
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TrendingDown,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = LossRed
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error Loading Portfolio",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Retry")
            }
        }
    }
}
