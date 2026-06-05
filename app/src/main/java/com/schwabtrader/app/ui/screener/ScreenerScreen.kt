package com.schwabtrader.app.ui.screener

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwabtrader.app.data.repository.HighPeriod
import com.schwabtrader.app.data.repository.IndexType
import com.schwabtrader.app.data.repository.ScreenedStock
import com.schwabtrader.app.data.repository.ScreenerCriteria
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenerScreen(
    viewModel: ScreenerViewModel = hiltViewModel(),
    onNavigateToOrder: (symbol: String, accountHash: String) -> Unit,
    onConnectSchwab: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val criteria by viewModel.criteria.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Stock Screener",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        when (val state = uiState) {
            is ScreenerUiState.NotConnected -> {
                NotConnectedState(onConnectSchwab = onConnectSchwab)
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    item {
                        ScreenerFiltersSection(
                            criteria = criteria,
                            onToggleHighPeriod = { viewModel.toggleHighPeriod(it) },
                            onSetIndex = { viewModel.setIndex(it) },
                            onSetOutperformance = { viewModel.setMinOutperformance(it) }
                        )
                    }

                    item {
                        Button(
                            onClick = { viewModel.runScreener() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            enabled = state !is ScreenerUiState.Loading && state !is ScreenerUiState.Running
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state is ScreenerUiState.Loading || state is ScreenerUiState.Running)
                                    "Screening..." else "Run Screener",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    when (state) {
                        is ScreenerUiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = AccentBlue)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Fetching price histories...",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "This may take a minute",
                                            color = TextSecondary.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        is ScreenerUiState.Running -> {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Text(
                                        "Screening stocks... Found ${state.resultsCount} matches so far",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = AccentBlue,
                                        trackColor = SurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            if (state.stocks.isNotEmpty()) {
                                item {
                                    Text(
                                        "Results so far (${state.stocks.size})",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                items(state.stocks) { stock ->
                                    ScreenedStockCard(
                                        stock = stock,
                                        onBuy = { onNavigateToOrder(stock.symbol, "") }
                                    )
                                }
                            }
                        }

                        is ScreenerUiState.Success -> {
                            if (state.stocks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No stocks matched your criteria.\nTry adjusting the filters.",
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                item {
                                    Text(
                                        "${state.stocks.size} stocks found",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                items(state.stocks) { stock ->
                                    ScreenedStockCard(
                                        stock = stock,
                                        onBuy = { onNavigateToOrder(stock.symbol, "") }
                                    )
                                }
                            }
                        }

                        is ScreenerUiState.Error -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "Error running screener",
                                            color = LossRed,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            state.message,
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        else -> {}
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenerFiltersSection(
    criteria: ScreenerCriteria,
    onToggleHighPeriod: (HighPeriod) -> Unit,
    onSetIndex: (IndexType) -> Unit,
    onSetOutperformance: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Screen Criteria",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // High period selection
            Text(
                "Near Historical High",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighPeriod.values().forEach { period ->
                    val selected = criteria.highPeriods.contains(period)
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleHighPeriod(period) },
                        label = {
                            Text(
                                when (period) {
                                    HighPeriod.ONE_YEAR -> "1Y High"
                                    HighPeriod.THREE_YEAR -> "3Y High"
                                    HighPeriod.FIVE_YEAR -> "5Y High"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Index selection
            Text(
                "Compare vs Index",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IndexType.values().forEach { index ->
                    val selected = criteria.index == index
                    FilterChip(
                        selected = selected,
                        onClick = { onSetIndex(index) },
                        label = { Text(index.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Outperformance threshold
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Min Outperformance vs Index",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "+${"%.0f".format(criteria.minOutperformance)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = GainGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = criteria.minOutperformance,
                onValueChange = onSetOutperformance,
                valueRange = 0f..50f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = SurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ScreenedStockCard(
    stock: ScreenedStock,
    onBuy: () -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

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
                        text = stock.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (stock.companyName.isNotBlank() && stock.companyName != stock.symbol) {
                        Text(
                            text = stock.companyName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currencyFormatter.format(stock.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    val returnColor = if (stock.oneYearReturn >= 0) GainGreen else LossRed
                    Text(
                        text = "${if (stock.oneYearReturn >= 0) "+" else ""}${"%.1f".format(stock.oneYearReturn)}% (1Y)",
                        style = MaterialTheme.typography.bodySmall,
                        color = returnColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // High period badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stock.meetsHighCriteria.forEach { period ->
                    val label = when (period) {
                        HighPeriod.ONE_YEAR -> "Near 1Y High"
                        HighPeriod.THREE_YEAR -> "Near 3Y High"
                        HighPeriod.FIVE_YEAR -> "Near 5Y High"
                    }
                    val pctFromHigh = when (period) {
                        HighPeriod.ONE_YEAR -> stock.percentFromOneYearHigh
                        HighPeriod.THREE_YEAR -> stock.percentFromThreeYearHigh
                        HighPeriod.FIVE_YEAR -> stock.percentFromFiveYearHigh
                    }
                    Text(
                        text = "$label (${"%.1f".format(pctFromHigh)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        modifier = Modifier
                            .then(
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "vs Index:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    val outperfColor = if (stock.outperformance >= 0) GainGreen else LossRed
                    Text(
                        text = "${if (stock.outperformance >= 0) "+" else ""}${"%.1f".format(stock.outperformance)}% outperformance",
                        style = MaterialTheme.typography.bodySmall,
                        color = outperfColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = GainGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Buy",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConnectedState(onConnectSchwab: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                text = "Connect Schwab to Run Screener",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The screener requires access to real-time market data via your Schwab API credentials",
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
