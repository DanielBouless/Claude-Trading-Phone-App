package com.schwabtrader.app.ui.screener

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.schwabtrader.app.data.repository.WilliamsDmiResult
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
    highBreakoutViewModel: ScreenerViewModel = hiltViewModel(),
    williamsDmiViewModel: WilliamsDmiViewModel = hiltViewModel(),
    onNavigateToOrder: (symbol: String, accountHash: String) -> Unit,
    onNavigateToDetail: (symbol: String) -> Unit,
    onConnectSchwab: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("High Breakout", "Williams DMI")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Stock Screener", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = DarkBackground)
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkBackground,
                    contentColor = AccentBlue,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentBlue
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    color = if (selectedTab == index) AccentBlue else TextSecondary,
                                    fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HighBreakoutContent(
                viewModel = highBreakoutViewModel,
                innerPadding = innerPadding,
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToOrder = onNavigateToOrder,
                onConnectSchwab = onConnectSchwab
            )
            1 -> WilliamsDmiContent(
                viewModel = williamsDmiViewModel,
                innerPadding = innerPadding,
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToOrder = onNavigateToOrder,
                onConnectSchwab = onConnectSchwab
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighBreakoutContent(
    viewModel: ScreenerViewModel,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToOrder: (String, String) -> Unit,
    onConnectSchwab: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val criteria by viewModel.criteria.collectAsState()

    when (val state = uiState) {
        is ScreenerUiState.NotConnected -> NotConnectedState(onConnectSchwab = onConnectSchwab)
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

                    // Action button row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (state) {
                                is ScreenerUiState.Loading, is ScreenerUiState.Running -> {
                                    Button(
                                        onClick = { viewModel.stopScreener() },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Stop Search", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                is ScreenerUiState.Success -> {
                                    Button(
                                        onClick = { viewModel.runScreener() },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("New Search", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                else -> {
                                    Button(
                                        onClick = { viewModel.runScreener() },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Run Screener", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // State-specific content
                    when (state) {
                        is ScreenerUiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = AccentBlue)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Fetching index data…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                        Text("This may take a minute", color = TextSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        is ScreenerUiState.Running -> {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Screened ${state.processedCount} of ${state.totalCount} stocks",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "${state.stocks.size} match${if (state.stocks.size != 1) "es" else ""}",
                                            color = if (state.stocks.isNotEmpty()) GainGreen else TextSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = state.processedCount.toFloat() / state.totalCount.coerceAtLeast(1),
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
                                        onViewDetails = { onNavigateToDetail(stock.symbol) },
                                        onBuy = { onNavigateToOrder(stock.symbol, "") }
                                    )
                                }
                            }
                        }

                        is ScreenerUiState.Success -> {
                            if (state.stocks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
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
                                        "${state.stocks.size} stocks found — tap any to view details",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                items(state.stocks) { stock ->
                                    ScreenedStockCard(
                                        stock = stock,
                                        onViewDetails = { onNavigateToDetail(stock.symbol) },
                                        onBuy = { onNavigateToOrder(stock.symbol, "") }
                                    )
                                }
                            }
                        }

                        is ScreenerUiState.Error -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Error running screener", color = LossRed, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WilliamsDmiContent(
    viewModel: WilliamsDmiViewModel,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToOrder: (String, String) -> Unit,
    onConnectSchwab: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedIndex by viewModel.index.collectAsState()

    when (val state = uiState) {
        is WilliamsDmiUiState.NotConnected -> NotConnectedState(onConnectSchwab = onConnectSchwab)
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Index selector
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FilterList, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Williams %R + DMI Criteria", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Adaptive periods auto-tuned per stock", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Williams %R ≤ -80 within last 3 candles", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("• +DI > -DI (bullish DMI crossover confirmed)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Universe", style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IndexType.values().forEach { index ->
                                    FilterChip(
                                        selected = selectedIndex == index,
                                        onClick = { viewModel.setIndex(index) },
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
                        }
                    }
                }

                // Action button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (state) {
                            is WilliamsDmiUiState.Loading, is WilliamsDmiUiState.Running -> {
                                Button(
                                    onClick = { viewModel.stopScreener() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop Search", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            is WilliamsDmiUiState.Success -> {
                                Button(
                                    onClick = { viewModel.runScreener() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("New Search", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = { viewModel.runScreener() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Run Screener", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // State content
                when (state) {
                    is WilliamsDmiUiState.Loading -> {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = AccentBlue)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Analyzing price structure…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                    Text("Auto-tuning periods per stock", color = TextSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    is WilliamsDmiUiState.Running -> {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "Screened ${state.processedCount} of ${state.totalCount} stocks",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "${state.results.size} match${if (state.results.size != 1) "es" else ""}",
                                        color = if (state.results.isNotEmpty()) GainGreen else TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = state.processedCount.toFloat() / state.totalCount.coerceAtLeast(1),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = AccentBlue,
                                    trackColor = SurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                        if (state.results.isNotEmpty()) {
                            item {
                                Text(
                                    "Results so far (${state.results.size})",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(state.results) { result ->
                                WilliamsDmiResultCard(
                                    result = result,
                                    onViewDetails = { onNavigateToDetail(result.symbol) },
                                    onBuy = { onNavigateToOrder(result.symbol, "") }
                                )
                            }
                        }
                    }

                    is WilliamsDmiUiState.Success -> {
                        if (state.results.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "No stocks matched the Williams DMI criteria.\nTry a different universe.",
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    "${state.results.size} stocks found — tap any to view details",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(state.results) { result ->
                                WilliamsDmiResultCard(
                                    result = result,
                                    onViewDetails = { onNavigateToDetail(result.symbol) },
                                    onBuy = { onNavigateToOrder(result.symbol, "") }
                                )
                            }
                        }
                    }

                    is WilliamsDmiUiState.Error -> {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Error running screener", color = LossRed, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
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

@Composable
private fun WilliamsDmiResultCard(
    result: WilliamsDmiResult,
    onViewDetails: () -> Unit,
    onBuy: () -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onViewDetails() },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(result.symbol, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    if (result.companyName.isNotBlank() && result.companyName != result.symbol) {
                        Text(result.companyName, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(currencyFormatter.format(result.currentPrice), style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    val wrColor = if (result.williamsRCurrent <= -80.0) GainGreen else AccentBlue
                    Text(
                        "W%%R ${"%.0f".format(result.williamsRCurrent)} (${result.williamsRPeriod}p)",
                        style = MaterialTheme.typography.bodySmall,
                        color = wrColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // DMI indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+DI", style = MaterialTheme.typography.labelSmall, color = GainGreen)
                        Text("${"%.1f".format(result.plusDI)}", style = MaterialTheme.typography.bodyMedium, color = GainGreen, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("-DI", style = MaterialTheme.typography.labelSmall, color = LossRed)
                        Text("${"%.1f".format(result.minusDI)}", style = MaterialTheme.typography.bodyMedium, color = LossRed, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DMI period", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${result.dmiPeriod}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "+DI leads by ${"%.1f".format(result.plusDI - result.minusDI)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GainGreen,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = GainGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buy", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Screen Criteria", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Trading Above Historical Highs", style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighPeriod.values().forEach { period ->
                    val selected = criteria.highPeriods.contains(period)
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleHighPeriod(period) },
                        label = {
                            Text(when (period) {
                                HighPeriod.ONE_YEAR   -> "1Y High"
                                HighPeriod.THREE_YEAR -> "3Y High"
                                HighPeriod.FIVE_YEAR  -> "5Y High"
                            })
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
            Text("Compare vs Index", style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontWeight = FontWeight.Medium)
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Min Outperformance vs Index", style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontWeight = FontWeight.Medium)
                Text("+${"%.0f".format(criteria.minOutperformance)}%", style = MaterialTheme.typography.bodySmall, color = GainGreen, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = criteria.minOutperformance,
                onValueChange = onSetOutperformance,
                valueRange = 0f..50f,
                steps = 9,
                colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue, inactiveTrackColor = SurfaceVariant)
            )
        }
    }
}

@Composable
private fun ScreenedStockCard(
    stock: ScreenedStock,
    onViewDetails: () -> Unit,
    onBuy: () -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onViewDetails() },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stock.symbol, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = "View details", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    if (stock.companyName.isNotBlank() && stock.companyName != stock.symbol) {
                        Text(stock.companyName, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(currencyFormatter.format(stock.currentPrice), style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    val returnColor = if (stock.oneYearReturn >= 0) GainGreen else LossRed
                    Text(
                        "${if (stock.oneYearReturn >= 0) "+" else ""}${"%.1f".format(stock.oneYearReturn)}% (1Y)",
                        style = MaterialTheme.typography.bodySmall,
                        color = returnColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stock.meetsHighCriteria.forEach { period ->
                    val label = when (period) {
                        HighPeriod.ONE_YEAR   -> "1Y High"
                        HighPeriod.THREE_YEAR -> "3Y High"
                        HighPeriod.FIVE_YEAR  -> "5Y High"
                    }
                    val pctAbove = when (period) {
                        HighPeriod.ONE_YEAR   -> stock.percentFromOneYearHigh
                        HighPeriod.THREE_YEAR -> stock.percentFromThreeYearHigh
                        HighPeriod.FIVE_YEAR  -> stock.percentFromFiveYearHigh
                    }
                    Text(
                        text = "$label (+${"%.1f".format(pctAbove)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                    Text("vs Index:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    val outperfColor = if (stock.outperformance >= 0) GainGreen else LossRed
                    Text(
                        "${if (stock.outperformance >= 0) "+" else ""}${"%.1f".format(stock.outperformance)}% outperformance",
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
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buy", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun NotConnectedState(onConnectSchwab: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(72.dp), tint = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Connect Schwab to Run Screener", style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("The screener requires access to real-time market data via your Schwab API credentials", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onConnectSchwab, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Text("Connect Schwab Account")
            }
        }
    }
}
