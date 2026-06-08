package com.schwabtrader.app.ui.stockdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size  // used in WilliamsRChart drawRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwabtrader.app.data.api.models.Candle
import com.schwabtrader.app.data.repository.PeerStock
import com.schwabtrader.app.data.repository.StockDetail
import kotlin.math.abs
import com.schwabtrader.app.ui.theme.AccentBlue
import com.schwabtrader.app.ui.theme.AccentTeal
import com.schwabtrader.app.ui.theme.CardBackground
import com.schwabtrader.app.ui.theme.DarkBackground
import com.schwabtrader.app.ui.theme.GainGreen
import com.schwabtrader.app.ui.theme.LossRed
import com.schwabtrader.app.ui.theme.SurfaceVariant
import com.schwabtrader.app.ui.theme.TextPrimary
import com.schwabtrader.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    symbol: String,
    viewModel: StockDetailViewModel = hiltViewModel(),
    onNavigateToOrder: (symbol: String, accountHash: String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    LaunchedEffect(symbol) { viewModel.load(symbol) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(symbol, color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(symbol) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "Remove from watchlist" else "Add to watchlist",
                            tint = if (isFavorite) Color(0xFFFFD700) else TextSecondary
                        )
                    }
                    IconButton(onClick = { onNavigateToOrder(symbol, "") }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Buy", tint = GainGreen)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        when (val state = uiState) {
            is StockDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading $symbol…", color = TextSecondary)
                    }
                }
            }

            is StockDetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("Failed to load $symbol", color = LossRed, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry(symbol) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                            Text("Retry")
                        }
                    }
                }
            }

            is StockDetailUiState.Success -> {
                StockDetailContent(
                    detail = state.detail,
                    modifier = Modifier.padding(innerPadding),
                    onBuy = { onNavigateToOrder(symbol, "") }
                )
            }
        }
    }
}

@Composable
private fun StockDetailContent(
    detail: StockDetail,
    modifier: Modifier,
    onBuy: () -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val changeColor = if (detail.priceChange >= 0) GainGreen else LossRed
    val changeIcon = if (detail.priceChange >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Chart", "Performance", "Technicals", "Financials")

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = detail.companyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = currency.format(detail.currentPrice),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(changeIcon, contentDescription = null, tint = changeColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${if (detail.priceChange >= 0) "+" else ""}${"%.2f".format(detail.priceChange)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = changeColor,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "(${if (detail.priceChangePercent >= 0) "+" else ""}${"%.2f".format(detail.priceChangePercent)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = changeColor
                            )
                        }
                    }
                }
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = AccentBlue,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == i) AccentBlue else TextSecondary,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Tab content
        when (selectedTab) {
            0 -> ChartTab(detail = detail)
            1 -> PerformanceTab(detail = detail)
            2 -> TechnicalsTab(detail = detail)
            3 -> FinancialsTab(detail = detail, currency = currency, onBuy = onBuy)
        }
    }
}

// ─── Chart Tab ────────────────────────────────────────────────────────────────

// Pixels (dp) reserved on the left of every chart canvas for Y-axis labels
private const val Y_AXIS_DP = 52f

@Composable
private fun ChartTab(detail: StockDetail) {
    var selectedTimeframe by remember { mutableStateOf("1Y") }
    val timeframes = listOf("1M", "3M", "6M", "1Y", "5Y")

    val displayCandles = remember(selectedTimeframe) {
        val now = System.currentTimeMillis()
        when (selectedTimeframe) {
            "1M" -> detail.dailyCandles.filter { it.datetime >= now - 30L * 86_400_000L }
            "3M" -> detail.dailyCandles.filter { it.datetime >= now - 90L * 86_400_000L }
            "6M" -> detail.dailyCandles.filter { it.datetime >= now - 180L * 86_400_000L }
            "5Y" -> detail.weeklyCandles
            else -> detail.dailyCandles
        }
    }
    val wrPeriod  = detail.optimalWilliamsRPeriod
    val dmiPeriod = detail.optimalDmiPeriod
    val wr  = remember(selectedTimeframe) { computeWilliamsR(displayCandles, wrPeriod) }
    val dmi = remember(selectedTimeframe) { computeDMI(displayCandles, dmiPeriod) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Timeframe selector
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            timeframes.forEach { tf ->
                val selected = selectedTimeframe == tf
                if (selected) {
                    Button(
                        onClick = { selectedTimeframe = tf },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(tf, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedTimeframe = tf },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(tf, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (displayCandles.size < 2) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Not enough data for this timeframe", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val chartColor = if (displayCandles.last().close >= displayCandles.first().close) GainGreen else LossRed

            // ── Price ──────────────────────────────────────────────────────────
            Text("Price", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            PriceChart(candles = displayCandles, lineColor = chartColor,
                modifier = Modifier.fillMaxWidth().height(200.dp))

            Spacer(Modifier.height(10.dp))

            // ── Williams %R ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Williams %R ($wrPeriod)", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(LossRed.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
                    Text("OB −20", style = MaterialTheme.typography.labelSmall, color = LossRed.copy(alpha = 0.8f))
                    Box(Modifier.size(8.dp).background(GainGreen.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
                    Text("OS −80", style = MaterialTheme.typography.labelSmall, color = GainGreen.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(2.dp))
            WilliamsRChart(values = wr, modifier = Modifier.fillMaxWidth().height(110.dp))

            Spacer(Modifier.height(10.dp))

            // ── DMI ────────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DMI ($dmiPeriod)", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(GainGreen, RoundedCornerShape(2.dp)))
                    Text("+DI", style = MaterialTheme.typography.labelSmall, color = GainGreen)
                    Box(Modifier.size(8.dp).background(LossRed, RoundedCornerShape(2.dp)))
                    Text("−DI", style = MaterialTheme.typography.labelSmall, color = LossRed)
                }
            }
            Spacer(Modifier.height(2.dp))
            DmiChart(plusDI = dmi.first, minusDI = dmi.second,
                modifier = Modifier.fillMaxWidth().height(110.dp))

            // ── Shared X-axis dates ────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            XAxisLabels(candles = displayCandles)
        }
    }
}

@Composable
private fun PriceChart(
    candles: List<Candle>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val gradientColors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)
    val minP = candles.minOf { it.low }
    val maxP = candles.maxOf { it.high }
    val mid1 = maxP * 0.67 + minP * 0.33
    val mid2 = maxP * 0.33 + minP * 0.67

    Row(modifier = modifier) {
        // Y-axis labels at 0%, 33%, 67%, 100% of chart height
        Column(
            modifier = Modifier.width(Y_AXIS_DP.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(maxP, mid1, mid2, minP).forEach { price ->
                Text(
                    text = if (price >= 100) "$%.0f".format(price) else "$%.2f".format(price),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // Chart canvas
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val w = size.width; val h = size.height; val n = candles.size
            if (n < 2) return@Canvas
            val range = (maxP - minP).coerceAtLeast(0.01)
            fun xOf(i: Int)    = (i.toFloat() / (n - 1)) * w
            fun yOf(p: Double) = h * (1f - ((p - minP) / range).toFloat())
            // Horizontal grid lines
            listOf(maxP, mid1, mid2, minP).forEach { price ->
                val y = yOf(price).coerceIn(0f, h)
                drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            // Line + fill
            val linePath = Path(); val fillPath = Path()
            candles.forEachIndexed { i, c ->
                val x = xOf(i); val y = yOf(c.close)
                if (i == 0) { linePath.moveTo(x, y); fillPath.moveTo(0f, h); fillPath.lineTo(x, y) }
                else        { linePath.lineTo(x, y); fillPath.lineTo(x, y) }
            }
            fillPath.lineTo(w, h); fillPath.close()
            drawPath(fillPath, brush = Brush.verticalGradient(gradientColors, startY = 0f, endY = h))
            drawPath(linePath, color = lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun WilliamsRChart(
    values: List<Double>,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        // Y-axis labels: 0 at top, −20 at 20%, −80 at 80%, −100 at bottom
        // Weighted spacers: gap above −20 = 18%, gap between −20 and −80 = 56%, gap below −80 = 18%
        Column(modifier = Modifier.width(Y_AXIS_DP.dp).fillMaxHeight()) {
            Text("0",    style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(18f))
            Text("−20",  style = MaterialTheme.typography.labelSmall, color = LossRed.copy(alpha = 0.8f), fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(56f))
            Text("−80",  style = MaterialTheme.typography.labelSmall, color = GainGreen.copy(alpha = 0.8f), fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(18f))
            Text("−100", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
        // Chart canvas
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val w = size.width; val h = size.height; val n = values.size
            if (n < 2) return@Canvas
            val ob20y = h * 0.20f; val os80y = h * 0.80f
            fun xOf(i: Int)    = (i.toFloat() / (n - 1)) * w
            fun yOf(v: Double) = h * (v / -100.0).toFloat().coerceIn(0f, 1f)
            // Zone tints
            drawRect(LossRed.copy(alpha = 0.10f),   topLeft = Offset(0f, 0f),    size = Size(w, ob20y))
            drawRect(GainGreen.copy(alpha = 0.10f),  topLeft = Offset(0f, os80y), size = Size(w, h - os80y))
            // Reference lines
            val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()), 0f)
            drawLine(LossRed.copy(alpha = 0.55f),   Offset(0f, ob20y), Offset(w, ob20y), 1f, pathEffect = dash)
            drawLine(GainGreen.copy(alpha = 0.55f),  Offset(0f, os80y), Offset(w, os80y), 1f, pathEffect = dash)
            // %R line
            val path = Path(); var moved = false
            values.forEachIndexed { i, v ->
                if (v.isNaN()) { moved = false; return@forEachIndexed }
                val x = xOf(i); val y = yOf(v)
                if (!moved) { path.moveTo(x, y); moved = true } else path.lineTo(x, y)
            }
            drawPath(path, AccentTeal, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun DmiChart(
    plusDI: List<Double>,
    minusDI: List<Double>,
    modifier: Modifier = Modifier
) {
    val allValid = (plusDI + minusDI).filter { !it.isNaN() }
    val maxVal   = ceil((allValid.maxOrNull() ?: 40.0).coerceAtLeast(40.0) / 10.0) * 10.0
    // Fraction of chart height where the "20" label sits (from top)
    val frac20   = ((1.0 - 20.0 / maxVal) * 100f).toFloat().coerceIn(1f, 98f)

    Row(modifier = modifier) {
        // Y-axis: maxVal at top, "20" at the correct fractional height, "0" at bottom
        Column(modifier = Modifier.width(Y_AXIS_DP.dp).fillMaxHeight()) {
            Text("%.0f".format(maxVal), style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(frac20))
            Text("20", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(100f - frac20))
            Text("0",  style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
        // Chart canvas
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val w = size.width; val h = size.height; val n = plusDI.size
            if (n < 2) return@Canvas
            fun xOf(i: Int)    = (i.toFloat() / (n - 1)) * w
            fun yOf(v: Double) = h * (1f - (v / maxVal).toFloat()).coerceIn(0f, 1f)
            // Dashed line at 20
            val y20  = yOf(20.0)
            val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()), 0f)
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y20), Offset(w, y20), 1f, pathEffect = dash)
            // +DI
            val plusPath = Path(); var plusMoved = false
            plusDI.forEachIndexed { i, v ->
                if (v.isNaN()) { plusMoved = false; return@forEachIndexed }
                val x = xOf(i); val y = yOf(v)
                if (!plusMoved) { plusPath.moveTo(x, y); plusMoved = true } else plusPath.lineTo(x, y)
            }
            drawPath(plusPath, GainGreen, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // −DI
            val minusPath = Path(); var minusMoved = false
            minusDI.forEachIndexed { i, v ->
                if (v.isNaN()) { minusMoved = false; return@forEachIndexed }
                val x = xOf(i); val y = yOf(v)
                if (!minusMoved) { minusPath.moveTo(x, y); minusMoved = true } else minusPath.lineTo(x, y)
            }
            drawPath(minusPath, LossRed, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun XAxisLabels(candles: List<Candle>) {
    if (candles.size < 2) return
    val sdf = remember { SimpleDateFormat("MMM d", Locale.US) }
    val n   = candles.size
    val indices = remember(n) {
        when {
            n >= 5 -> listOf(0, n / 4, n / 2, 3 * n / 4, n - 1)
            n >= 3 -> listOf(0, n / 2, n - 1)
            else   -> listOf(0, n - 1)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Y_AXIS_DP.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        indices.forEach { idx ->
            Text(
                text = sdf.format(Date(candles[idx.coerceIn(0, n - 1)].datetime)),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 9.sp
            )
        }
    }
}

// Compute Williams %R — returns NaN for warmup bars (first period-1 indices)
private fun computeWilliamsR(candles: List<Candle>, period: Int = 14): List<Double> =
    candles.mapIndexed { i, c ->
        if (i < period - 1) Double.NaN
        else {
            val window  = candles.subList(i - period + 1, i + 1)
            val highest = window.maxOf { it.high }
            val lowest  = window.minOf { it.low }
            val range   = highest - lowest
            if (range == 0.0) -50.0 else ((highest - c.close) / range) * -100.0
        }
    }

// Compute DMI (+DI / −DI) using Wilder's smoothing — NaN for warmup bars
private fun computeDMI(candles: List<Candle>, period: Int = 14): Pair<List<Double>, List<Double>> {
    val n       = candles.size
    val plusDI  = MutableList(n) { Double.NaN }
    val minusDI = MutableList(n) { Double.NaN }
    if (n < period + 1) return Pair(plusDI, minusDI)

    val tr      = DoubleArray(n - 1)
    val plusDM  = DoubleArray(n - 1)
    val minusDM = DoubleArray(n - 1)
    for (j in 0 until n - 1) {
        val cur  = candles[j + 1]; val prev = candles[j]
        tr[j]      = maxOf(cur.high - cur.low, abs(cur.high - prev.close), abs(cur.low - prev.close))
        val up     = cur.high - prev.high
        val down   = prev.low - cur.low
        plusDM[j]  = if (up > down && up > 0) up else 0.0
        minusDM[j] = if (down > up && down > 0) down else 0.0
    }

    var smoothTR    = (0 until period).sumOf { tr[it] }
    var smoothPlus  = (0 until period).sumOf { plusDM[it] }
    var smoothMinus = (0 until period).sumOf { minusDM[it] }

    if (smoothTR > 0) {
        plusDI[period]  = 100.0 * smoothPlus  / smoothTR
        minusDI[period] = 100.0 * smoothMinus / smoothTR
    }
    for (i in period + 1 until n) {
        val j        = i - 1
        smoothTR     = smoothTR    - smoothTR    / period + tr[j]
        smoothPlus   = smoothPlus  - smoothPlus  / period + plusDM[j]
        smoothMinus  = smoothMinus - smoothMinus / period + minusDM[j]
        if (smoothTR > 0) {
            plusDI[i]  = 100.0 * smoothPlus  / smoothTR
            minusDI[i] = 100.0 * smoothMinus / smoothTR
        }
    }
    return Pair(plusDI, minusDI)
}

// ─── Performance Tab ──────────────────────────────────────────────────────────

@Composable
private fun PerformanceTab(detail: StockDetail) {
    val periods = listOf(
        "1 Month"  to detail.oneMonthReturn,
        "3 Months" to detail.threeMonthReturn,
        "6 Months" to detail.sixMonthReturn,
        "1 Year"   to detail.oneYearReturn,
        "3 Years"  to detail.threeYearReturn,
        "5 Years"  to detail.fiveYearReturn,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Price Returns", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                periods.forEachIndexed { i, (label, value) ->
                    if (i > 0) Divider(color = SurfaceVariant, thickness = 0.5.dp)
                    ReturnRow(label = label, value = value)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Day range card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Today", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                MetricRow("Open",     "${"%.2f".format(detail.openPrice)}")
                Divider(color = SurfaceVariant, thickness = 0.5.dp)
                MetricRow("Day High", "${"%.2f".format(detail.dayHigh)}")
                Divider(color = SurfaceVariant, thickness = 0.5.dp)
                MetricRow("Day Low",  "${"%.2f".format(detail.dayLow)}")
                if (detail.volume > 0) {
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                    MetricRow("Volume", "%,d".format(detail.volume))
                }
            }
        }

        // Sector peer comparison
        if (detail.peerComparison.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (detail.sector.isNotBlank()) "${detail.sector} — 1Y Return vs Peers" else "Sector Peers — 1Y Return",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap a stock to view its detail",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val maxAbs = detail.peerComparison.maxOfOrNull { abs(it.oneYearReturn) } ?: 1.0
                    detail.peerComparison.forEach { peer ->
                        PeerReturnBar(peer = peer, maxAbsReturn = maxAbs)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReturnRow(label: String, value: Double) {
    val color = if (value >= 0) GainGreen else LossRed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(
            text = "${if (value >= 0) "+" else ""}${"%.2f".format(value)}%",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Technicals Tab ───────────────────────────────────────────────────────────

@Composable
private fun TechnicalsTab(detail: StockDetail) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // RSI
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("RSI (14)", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                val rsiColor = when {
                    detail.rsi14 < 30 -> GainGreen
                    detail.rsi14 > 70 -> LossRed
                    else -> TextPrimary
                }
                val rsiLabel = when {
                    detail.rsi14 < 30 -> "Oversold"
                    detail.rsi14 > 70 -> "Overbought"
                    else -> "Neutral"
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(rsiLabel, style = MaterialTheme.typography.bodyMedium, color = rsiColor, fontWeight = FontWeight.Medium)
                    Text("${"%.1f".format(detail.rsi14)}", style = MaterialTheme.typography.bodyMedium, color = rsiColor, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                // RSI bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(SurfaceVariant, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((detail.rsi14 / 100.0).toFloat().coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(rsiColor, RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("30 Oversold", style = MaterialTheme.typography.labelSmall, color = GainGreen)
                    Text("70 Overbought", style = MaterialTheme.typography.labelSmall, color = LossRed)
                    Text("100", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Moving averages
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Moving Averages", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (detail.sma50 > 0) {
                    val sma50Color = if (detail.currentPrice >= detail.sma50) GainGreen else LossRed
                    val sma50Signal = if (detail.currentPrice >= detail.sma50) "Above" else "Below"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SMA 50", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(sma50Signal, style = MaterialTheme.typography.labelSmall, color = sma50Color)
                        }
                        Text("${"%.2f".format(detail.sma50)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                }

                if (detail.sma200 > 0) {
                    val sma200Color = if (detail.currentPrice >= detail.sma200) GainGreen else LossRed
                    val sma200Signal = if (detail.currentPrice >= detail.sma200) "Above" else "Below"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SMA 200", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(sma200Signal, style = MaterialTheme.typography.labelSmall, color = sma200Color)
                        }
                        Text("${"%.2f".format(detail.sma200)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 52-week range
        if (detail.high52Week > 0 && detail.low52Week > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("52-Week Range", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    val range52 = detail.high52Week - detail.low52Week
                    val position = if (range52 > 0) ((detail.currentPrice - detail.low52Week) / range52).toFloat().coerceIn(0f, 1f) else 0.5f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(SurfaceVariant, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(position)
                                .height(8.dp)
                                .background(AccentBlue, RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${"%.2f".format(detail.low52Week)}", style = MaterialTheme.typography.labelSmall, color = LossRed)
                        Text("Current: ${"%.2f".format(detail.currentPrice)}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        Text("${"%.2f".format(detail.high52Week)}", style = MaterialTheme.typography.labelSmall, color = GainGreen)
                    }
                }
            }
        }
    }
}

// ─── Financials Tab ───────────────────────────────────────────────────────────

@Composable
private fun FinancialsTab(detail: StockDetail, currency: NumberFormat, onBuy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Valuation", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (detail.peRatio > 0) {
                    MetricRow("P/E Ratio", "${"%.2f".format(detail.peRatio)}")
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                }
                if (detail.pbRatio > 0) {
                    MetricRow("P/B Ratio", "${"%.2f".format(detail.pbRatio)}")
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                }
                if (detail.eps != 0.0) {
                    MetricRow("EPS (TTM)", "${"%.2f".format(detail.eps)}")
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                }
                if (detail.marketCap > 0) {
                    MetricRow("Market Cap", formatMarketCap(detail.marketCap))
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                }
                if (detail.beta > 0) {
                    MetricRow("Beta", "${"%.2f".format(detail.beta)}")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Trading", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (detail.bid > 0) {
                    MetricRow("Bid", "${"%.2f".format(detail.bid)}")
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                    MetricRow("Ask", "${"%.2f".format(detail.ask)}")
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                }
                if (detail.volume > 0) {
                    MetricRow("Volume", "%,d".format(detail.volume))
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                }
                if (detail.high52Week > 0) {
                    MetricRow("52W High", "${"%.2f".format(detail.high52Week)}")
                    Divider(color = SurfaceVariant, thickness = 0.5.dp)
                    MetricRow("52W Low", "${"%.2f".format(detail.low52Week)}")
                }
            }
        }

        if (detail.dividendYield > 0 || detail.roe > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dividends & Returns", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (detail.dividendYield > 0) {
                        MetricRow("Dividend Yield", "${"%.2f".format(detail.dividendYield)}%")
                        Divider(color = SurfaceVariant, thickness = 0.5.dp)
                    }
                    if (detail.roe > 0) {
                        MetricRow("Return on Equity", "${"%.2f".format(detail.roe)}%")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBuy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GainGreen)
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Buy", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PeerReturnBar(peer: PeerStock, maxAbsReturn: Double) {
    val barColor = when {
        peer.isCurrentStock -> AccentBlue
        peer.oneYearReturn >= 0 -> GainGreen
        else -> LossRed
    }
    val fraction = (abs(peer.oneYearReturn) / maxAbsReturn.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val returnText = "${if (peer.oneYearReturn >= 0) "+" else ""}${"%.1f".format(peer.oneYearReturn)}%"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = peer.symbol,
            style = MaterialTheme.typography.labelMedium,
            color = if (peer.isCurrentStock) AccentBlue else TextPrimary,
            fontWeight = if (peer.isCurrentStock) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(56.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .background(SurfaceVariant, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        barColor.copy(alpha = if (peer.isCurrentStock) 1f else 0.75f),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
        Text(
            text = returnText,
            style = MaterialTheme.typography.labelSmall,
            color = barColor,
            fontWeight = if (peer.isCurrentStock) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End
        )
    }
}

private fun formatMarketCap(cap: Double): String = when {
    cap >= 1_000_000_000_000.0 -> "${"%.2f".format(cap / 1_000_000_000_000.0)}T"
    cap >= 1_000_000_000.0     -> "${"%.2f".format(cap / 1_000_000_000.0)}B"
    cap >= 1_000_000.0         -> "${"%.2f".format(cap / 1_000_000.0)}M"
    else                       -> "%,.0f".format(cap)
}
