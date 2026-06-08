package com.schwabtrader.app.ui.watchlist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwabtrader.app.data.api.models.InstrumentDetail
import com.schwabtrader.app.data.api.models.QuoteDetail
import com.schwabtrader.app.data.repository.WatchlistItem
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
fun WatchlistScreen(
    viewModel: WatchlistViewModel = hiltViewModel(),
    onNavigateToDetail: (symbol: String) -> Unit
) {
    val watchlistItems by viewModel.watchlistItems.collectAsState()
    val watchlistQuotes by viewModel.watchlistQuotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuotes by viewModel.searchQuotes.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val currencyFmt = NumberFormat.getCurrencyInstance(Locale.US)

    LaunchedEffect(Unit) { viewModel.loadWatchlist() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search stocks, ETFs, funds...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = SurfaceVariant,
                    cursorColor = AccentBlue,
                    containerColor = SurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (searchQuery.isNotBlank()) {
                if (isSearching) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No results for \"$searchQuery\"", color = TextSecondary)
                    }
                } else {
                    Text(
                        "Search Results",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchResults, key = { it.symbol }) { instrument ->
                            val quote = searchQuotes[instrument.symbol]
                            val isWatchlisted = viewModel.isInWatchlist(instrument.symbol)
                            SearchResultRow(
                                instrument = instrument,
                                quote = quote,
                                isWatchlisted = isWatchlisted,
                                currencyFmt = currencyFmt,
                                onTap = { onNavigateToDetail(instrument.symbol) },
                                onToggleWatchlist = {
                                    viewModel.toggleWatchlist(
                                        WatchlistItem(
                                            symbol = instrument.symbol,
                                            companyName = instrument.description,
                                            assetType = instrument.assetType.ifBlank { "EQUITY" }
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                if (watchlistItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Your watchlist is empty", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Search for stocks or ETFs and tap ☆ to add them",
                                color = TextSecondary.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        "My Watchlist",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(watchlistItems, key = { it.symbol }) { item ->
                            val quote = watchlistQuotes[item.symbol]
                            WatchlistItemRow(
                                item = item,
                                quote = quote,
                                currencyFmt = currencyFmt,
                                onTap = { onNavigateToDetail(item.symbol) },
                                onRemove = { viewModel.toggleWatchlist(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    instrument: InstrumentDetail,
    quote: QuoteDetail?,
    isWatchlisted: Boolean,
    currencyFmt: NumberFormat,
    onTap: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onTap() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(instrument.symbol, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    if (instrument.assetType.isNotBlank() && instrument.assetType.uppercase() != "EQUITY") {
                        AssetTypeBadge(instrument.assetType)
                    }
                }
                Text(
                    instrument.description.ifBlank { instrument.symbol },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (quote != null && quote.lastPrice > 0) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                    Text(currencyFmt.format(quote.lastPrice), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    val sign = if (quote.netChange >= 0) "+" else ""
                    val changeColor = if (quote.netChange >= 0) GainGreen else LossRed
                    Text(
                        "$sign${"%.2f".format(quote.netChange)} ($sign${"%.2f".format(quote.netPercentChange)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = changeColor
                    )
                }
            }
            IconButton(onClick = onToggleWatchlist) {
                Icon(
                    imageVector = if (isWatchlisted) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isWatchlisted) "Remove from watchlist" else "Add to watchlist",
                    tint = if (isWatchlisted) Color(0xFFFFD700) else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun WatchlistItemRow(
    item: WatchlistItem,
    quote: QuoteDetail?,
    currencyFmt: NumberFormat,
    onTap: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onTap() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.symbol, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    if (item.assetType.isNotBlank() && item.assetType.uppercase() != "EQUITY") {
                        AssetTypeBadge(item.assetType)
                    }
                }
                Text(
                    item.companyName.ifBlank { item.symbol },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (quote != null && quote.lastPrice > 0) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                    Text(currencyFmt.format(quote.lastPrice), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    val sign = if (quote.netChange >= 0) "+" else ""
                    val changeColor = if (quote.netChange >= 0) GainGreen else LossRed
                    Text(
                        "$sign${"%.2f".format(quote.netChange)} ($sign${"%.2f".format(quote.netPercentChange)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = changeColor
                    )
                }
            } else {
                Text("--", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(end = 4.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Star, contentDescription = "Remove from watchlist", tint = Color(0xFFFFD700))
            }
        }
    }
}

@Composable
private fun AssetTypeBadge(assetType: String) {
    val label = when (assetType.uppercase()) {
        "ETF" -> "ETF"
        "MUTUAL_FUND", "MUTUAL FUND" -> "FUND"
        "INDEX" -> "IDX"
        "OPTION" -> "OPT"
        "FUTURE" -> "FUT"
        "BOND" -> "BOND"
        "FOREX" -> "FX"
        else -> assetType.take(4)
    }
    Box(
        modifier = Modifier
            .background(AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AccentBlue, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
    }
}
