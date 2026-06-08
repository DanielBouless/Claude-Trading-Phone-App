package com.schwabtrader.app.ui.order

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
fun OrderScreen(
    symbol: String,
    accountHash: String,
    viewModel: OrderViewModel = hiltViewModel(),
    onOrderPlaced: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccountIndex by viewModel.selectedAccountIndex.collectAsState()
    val currentPrice by viewModel.currentPrice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    var accountDropdownExpanded by remember { mutableStateOf(false) }

    val isSell = formState.direction == OrderDirection.SELL
    val actionColor = if (isSell) LossRed else GainGreen

    LaunchedEffect(symbol) { viewModel.loadPrice(symbol) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is OrderUiState.Success -> {
                snackbarHostState.showSnackbar("Order placed successfully!")
                onOrderPlaced()
            }
            is OrderUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearError()
            }
            else -> {}
        }
    }

    // ── Confirmation dialog ────────────────────────────────────────────────
    if (formState.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDialog() },
            containerColor = CardBackground,
            title = {
                Text(
                    "Confirm Order",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Symbol: $symbol", color = TextPrimary)
                    Text(
                        "Direction: ${formState.direction.name}",
                        color = if (isSell) LossRed else GainGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Quantity: ${formState.quantity} shares", color = TextSecondary)
                    if (isSell) {
                        when (formState.sellStrategy) {
                            SellStrategy.LIMIT ->
                                Text("Limit Price: $${formState.limitPrice}", color = TextSecondary)
                            SellStrategy.STOP_LOSS ->
                                Text("Stop Price: $${formState.stopPrice}", color = TextSecondary)
                            SellStrategy.TRAILING_STOP -> {
                                val unit = if (formState.trailingUnit == TrailingStopUnit.PERCENT) "%" else "$"
                                Text(
                                    "Trailing Stop: ${formState.trailingAmount}$unit",
                                    color = TextSecondary
                                )
                            }
                            else -> {}
                        }
                    } else {
                        if (formState.orderType == OrderType.LIMIT) {
                            Text("Limit Price: $${formState.limitPrice}", color = TextSecondary)
                        }
                        if (formState.addSellOnFill) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Divider(color = SurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Exit Strategy (on fill):", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            when (formState.sellOnFillStrategy) {
                                SellStrategy.STOP_LOSS ->
                                    Text("Stop Loss at $${formState.sellOnFillStopPrice}", color = LossRed)
                                SellStrategy.TRAILING_STOP -> {
                                    val u = if (formState.sellOnFillTrailingUnit == TrailingStopUnit.PERCENT) "%" else "$"
                                    Text("Trailing Stop: ${formState.sellOnFillTrailingAmount}$u", color = LossRed)
                                }
                                SellStrategy.LIMIT ->
                                    Text("Limit Sell at $${formState.sellOnFillLimitPrice}", color = GainGreen)
                                else -> {}
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Are you sure you want to place this order?",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.placeOrder(symbol, accountHash) },
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Text(
                        "Confirm ${formState.direction.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmDialog() }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${if (isSell) "Sell" else "Buy"} $symbol",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = DarkBackground)
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        if (uiState is OrderUiState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Placing order...", color = TextSecondary)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Stock info header ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            symbol,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentPrice > 0) {
                            Text(
                                currencyFormatter.format(currentPrice),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isSell) Icons.Default.TrendingDown else Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = actionColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Buy / Sell direction ───────────────────────────────────────────
            SectionLabel("Direction")
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderDirection.values().forEach { dir ->
                    val selected = formState.direction == dir
                    val color = if (dir == OrderDirection.SELL) LossRed else GainGreen
                    Button(
                        onClick = { viewModel.setDirection(dir) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) color else SurfaceVariant,
                            contentColor = if (selected) Color.Black else TextSecondary
                        )
                    ) {
                        Text(dir.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Order type / Sell strategy ─────────────────────────────────────
            if (isSell) {
                SectionLabel("Sell Strategy")
                Spacer(modifier = Modifier.height(8.dp))
                // Row 1: Market + Limit
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SellStrategy.MARKET, SellStrategy.LIMIT).forEach { strategy ->
                        val selected = formState.sellStrategy == strategy
                        Button(
                            onClick = { viewModel.setSellStrategy(strategy) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) LossRed else SurfaceVariant,
                                contentColor = if (selected) Color.Black else TextSecondary
                            )
                        ) {
                            Text(
                                strategy.name.replace('_', ' '),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Row 2: Stop Loss + Trailing Stop
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SellStrategy.STOP_LOSS, SellStrategy.TRAILING_STOP).forEach { strategy ->
                        val selected = formState.sellStrategy == strategy
                        Button(
                            onClick = { viewModel.setSellStrategy(strategy) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) LossRed else SurfaceVariant,
                                contentColor = if (selected) Color.Black else TextSecondary
                            )
                        ) {
                            Text(
                                strategy.name.replace('_', ' '),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                SectionLabel("Order Type")
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrderType.values().forEach { type ->
                        val selected = formState.orderType == type
                        Button(
                            onClick = { viewModel.setOrderType(type) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) AccentBlue else SurfaceVariant,
                                contentColor = if (selected) Color.White else TextSecondary
                            )
                        ) {
                            Text(type.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Quantity field ─────────────────────────────────────────────────
            SectionLabel("Quantity (Shares)")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = formState.quantity,
                onValueChange = { viewModel.setQuantity(it) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 10", color = TextSecondary) },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = SurfaceVariant,
                    cursorColor = AccentBlue,
                    containerColor = SurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // ── Dollar-amount calculator ───────────────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Divider(modifier = Modifier.weight(1f), color = SurfaceVariant)
                Text("or enter dollar amount", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Divider(modifier = Modifier.weight(1f), color = SurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            SectionLabel("Dollar Amount ($)")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = formState.dollarAmount,
                onValueChange = { viewModel.setDollarAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 1000.00", color = TextSecondary) },
                singleLine = true,
                leadingIcon = { Text("$", color = TextSecondary, style = MaterialTheme.typography.bodyLarge) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = SurfaceVariant,
                    cursorColor = AccentBlue,
                    containerColor = SurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            )
            if (currentPrice > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val sharesLabel = formState.quantity.toDoubleOrNull()?.takeIf { it > 0 }
                    ?.let { "≈ ${formState.quantity} shares" } ?: "Enter an amount to calculate shares"
                Text(
                    "$sharesLabel  ·  ${currencyFormatter.format(currentPrice)}/share",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue
                )
            }

            // ── Limit price (buy limit or sell limit) ──────────────────────────
            val showLimitPrice = (!isSell && formState.orderType == OrderType.LIMIT) ||
                (isSell && formState.sellStrategy == SellStrategy.LIMIT)
            if (showLimitPrice) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("Limit Price ($)")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = formState.limitPrice,
                    onValueChange = { viewModel.setLimitPrice(it) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("e.g. 150.00", color = TextSecondary) },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = TextPrimary,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = SurfaceVariant,
                        cursorColor = AccentBlue,
                        containerColor = SurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                if (isSell && currentPrice > 0) {
                    formState.limitPrice.toDoubleOrNull()?.let { lp ->
                        val pct = ((lp - currentPrice) / currentPrice) * 100
                        val sign = if (pct >= 0) "+" else ""
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Sell at $sign${"%.1f".format(pct)}% vs current ${currencyFormatter.format(currentPrice)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pct >= 0) GainGreen else LossRed
                        )
                    }
                }
            }

            // ── Stop loss ──────────────────────────────────────────────────────
            if (isSell && formState.sellStrategy == SellStrategy.STOP_LOSS) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("Stop Price ($)")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Order sells automatically when price reaches this level. Good Till Cancelled.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = formState.stopPrice,
                    onValueChange = { viewModel.setStopPrice(it) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("e.g. 145.00", color = TextSecondary) },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = TextPrimary,
                        focusedBorderColor = LossRed,
                        unfocusedBorderColor = SurfaceVariant,
                        cursorColor = LossRed,
                        containerColor = SurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                if (currentPrice > 0) {
                    formState.stopPrice.toDoubleOrNull()?.let { sp ->
                        val pct = abs((sp - currentPrice) / currentPrice * 100)
                        val dir = if (sp < currentPrice) "below" else "above"
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Triggers at ${currencyFormatter.format(sp)} (${"%.1f".format(pct)}% $dir current price)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sp < currentPrice) LossRed else GainGreen
                        )
                    }
                }
            }

            // ── Trailing stop ──────────────────────────────────────────────────
            if (isSell && formState.sellStrategy == SellStrategy.TRAILING_STOP) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("Trailing Stop")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Sells when the price drops by this amount from its highest point. Good Till Cancelled.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Unit toggle
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrailingStopUnit.values().forEach { unit ->
                        val selected = formState.trailingUnit == unit
                        Button(
                            onClick = { viewModel.setTrailingUnit(unit) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) LossRed else SurfaceVariant,
                                contentColor = if (selected) Color.Black else TextSecondary
                            )
                        ) {
                            Text(
                                if (unit == TrailingStopUnit.PERCENT) "%" else "$",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = formState.trailingAmount,
                    onValueChange = { viewModel.setTrailingAmount(it) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = {
                        Text(
                            if (formState.trailingUnit == TrailingStopUnit.PERCENT) "e.g. 5" else "e.g. 10.00",
                            color = TextSecondary
                        )
                    },
                    singleLine = true,
                    trailingIcon = {
                        Text(
                            if (formState.trailingUnit == TrailingStopUnit.PERCENT) "%" else "$",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = TextPrimary,
                        focusedBorderColor = LossRed,
                        unfocusedBorderColor = SurfaceVariant,
                        cursorColor = LossRed,
                        containerColor = SurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                if (currentPrice > 0) {
                    formState.trailingAmount.toDoubleOrNull()?.let { amt ->
                        val initialTrigger = if (formState.trailingUnit == TrailingStopUnit.PERCENT) {
                            currentPrice * (1 - amt / 100)
                        } else {
                            currentPrice - amt
                        }
                        if (initialTrigger > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Based on current price, initial trigger ≈ ${currencyFormatter.format(initialTrigger)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LossRed
                            )
                        }
                    }
                }
            }

            // ── Sell on fill (bracket order — only when BUY) ──────────────────
            if (!isSell) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Toggle header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Exit Strategy",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Automatically place a sell order when this buy fills",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = formState.addSellOnFill,
                                onCheckedChange = { viewModel.setAddSellOnFill(it) },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = LossRed,
                                    checkedTrackColor = LossRed.copy(alpha = 0.4f)
                                )
                            )
                        }

                        if (formState.addSellOnFill) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = SurfaceVariant)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Strategy selector (3 options — no Market)
                            SectionLabel("Sell Strategy")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    SellStrategy.STOP_LOSS to "Stop Loss",
                                    SellStrategy.TRAILING_STOP to "Trailing",
                                    SellStrategy.LIMIT to "Limit"
                                ).forEach { (strategy, label) ->
                                    val selected = formState.sellOnFillStrategy == strategy
                                    Button(
                                        onClick = { viewModel.setSellOnFillStrategy(strategy) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selected) LossRed else SurfaceVariant,
                                            contentColor = if (selected) Color.Black else TextSecondary
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            when (formState.sellOnFillStrategy) {
                                SellStrategy.STOP_LOSS -> {
                                    SectionLabel("Stop Price ($)")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = formState.sellOnFillStopPrice,
                                        onValueChange = { viewModel.setSellOnFillStopPrice(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        placeholder = { Text("e.g. 145.00", color = TextSecondary) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            textColor = TextPrimary,
                                            focusedBorderColor = LossRed,
                                            unfocusedBorderColor = SurfaceVariant,
                                            cursorColor = LossRed,
                                            containerColor = SurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    if (currentPrice > 0) {
                                        formState.sellOnFillStopPrice.toDoubleOrNull()?.let { sp ->
                                            val pct = abs((sp - currentPrice) / currentPrice * 100)
                                            val dir = if (sp < currentPrice) "below" else "above"
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Triggers at ${currencyFormatter.format(sp)} (${"%.1f".format(pct)}% $dir ${currencyFormatter.format(currentPrice)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (sp < currentPrice) LossRed else GainGreen
                                            )
                                        }
                                    }
                                }

                                SellStrategy.TRAILING_STOP -> {
                                    SectionLabel("Trailing Amount")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TrailingStopUnit.values().forEach { unit ->
                                            val selected = formState.sellOnFillTrailingUnit == unit
                                            Button(
                                                onClick = { viewModel.setSellOnFillTrailingUnit(unit) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selected) LossRed else SurfaceVariant,
                                                    contentColor = if (selected) Color.Black else TextSecondary
                                                )
                                            ) {
                                                Text(
                                                    if (unit == TrailingStopUnit.PERCENT) "%" else "$",
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = formState.sellOnFillTrailingAmount,
                                        onValueChange = { viewModel.setSellOnFillTrailingAmount(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        placeholder = {
                                            Text(
                                                if (formState.sellOnFillTrailingUnit == TrailingStopUnit.PERCENT) "e.g. 5" else "e.g. 10.00",
                                                color = TextSecondary
                                            )
                                        },
                                        singleLine = true,
                                        trailingIcon = {
                                            Text(
                                                if (formState.sellOnFillTrailingUnit == TrailingStopUnit.PERCENT) "%" else "$",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.padding(end = 12.dp)
                                            )
                                        },
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            textColor = TextPrimary,
                                            focusedBorderColor = LossRed,
                                            unfocusedBorderColor = SurfaceVariant,
                                            cursorColor = LossRed,
                                            containerColor = SurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    if (currentPrice > 0) {
                                        formState.sellOnFillTrailingAmount.toDoubleOrNull()?.let { amt ->
                                            val trigger = if (formState.sellOnFillTrailingUnit == TrailingStopUnit.PERCENT)
                                                currentPrice * (1 - amt / 100)
                                            else currentPrice - amt
                                            if (trigger > 0) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Initial trigger ≈ ${currencyFormatter.format(trigger)} from ${currencyFormatter.format(currentPrice)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = LossRed
                                                )
                                            }
                                        }
                                    }
                                }

                                SellStrategy.LIMIT -> {
                                    SectionLabel("Limit Sell Price ($)")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = formState.sellOnFillLimitPrice,
                                        onValueChange = { viewModel.setSellOnFillLimitPrice(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        placeholder = { Text("e.g. 175.00", color = TextSecondary) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            textColor = TextPrimary,
                                            focusedBorderColor = GainGreen,
                                            unfocusedBorderColor = SurfaceVariant,
                                            cursorColor = GainGreen,
                                            containerColor = SurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    if (currentPrice > 0) {
                                        formState.sellOnFillLimitPrice.toDoubleOrNull()?.let { lp ->
                                            val pct = ((lp - currentPrice) / currentPrice) * 100
                                            val sign = if (pct >= 0) "+" else ""
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Sell target: $sign${"%.1f".format(pct)}% vs ${currencyFormatter.format(currentPrice)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (pct >= 0) GainGreen else LossRed
                                            )
                                        }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }

            // ── Account selector ───────────────────────────────────────────────
            if (accountHash.isBlank() && accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("Account")
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { accountDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary,
                            containerColor = SurfaceVariant
                        )
                    ) {
                        Text(
                            accounts.getOrNull(selectedAccountIndex)?.second ?: "Select Account",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        accounts.forEachIndexed { index, (_, displayName) ->
                            DropdownMenuItem(
                                text = { Text(displayName, color = TextPrimary) },
                                onClick = {
                                    viewModel.setSelectedAccountIndex(index)
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Estimated value ────────────────────────────────────────────────
            val estimatedCost = viewModel.getEstimatedCost()
            if (estimatedCost > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isSell) "Estimated Proceeds" else "Estimated Cost",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            currencyFormatter.format(estimatedCost),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!isSell) {
                Text(
                    "Market orders execute at the best available price. Limit orders execute only at your specified price or better.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                when (formState.sellStrategy) {
                    SellStrategy.STOP_LOSS ->
                        Text(
                            "Stop orders trigger a market sell when the stop price is reached. Actual fill price may differ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    SellStrategy.TRAILING_STOP ->
                        Text(
                            "Trailing stop tracks the price peak and triggers a sell when it falls by the specified amount.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    else -> {}
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Action button ──────────────────────────────────────────────────
            val buttonLabel = when {
                !isSell -> "Place Buy Order"
                formState.sellStrategy == SellStrategy.STOP_LOSS -> "Set Stop Loss"
                formState.sellStrategy == SellStrategy.TRAILING_STOP -> "Set Trailing Stop"
                else -> "Place Sell Order"
            }
            Button(
                onClick = { viewModel.showConfirmDialog() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                enabled = uiState !is OrderUiState.Loading
            ) {
                Icon(
                    imageVector = if (isSell) Icons.Default.TrendingDown else Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonLabel, color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "All trades are final. Please review your order before confirming.",
                style = MaterialTheme.typography.bodySmall,
                color = LossRed.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        fontWeight = FontWeight.Medium
    )
}
