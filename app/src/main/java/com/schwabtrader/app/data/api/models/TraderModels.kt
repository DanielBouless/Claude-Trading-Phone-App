package com.schwabtrader.app.data.api.models

import com.google.gson.annotations.SerializedName

data class AccountResponse(
    @SerializedName("securitiesAccount")
    val securitiesAccount: SecuritiesAccount,
    @SerializedName("aggregatedBalance")
    val aggregatedBalance: AggregatedBalance? = null
)

data class AggregatedBalance(
    @SerializedName("currentLiquidationValue")
    val currentLiquidationValue: Double = 0.0,
    @SerializedName("liquidationValue")
    val liquidationValue: Double = 0.0
)

data class SecuritiesAccount(
    @SerializedName("type")
    val type: String = "",
    @SerializedName("accountNumber")
    val accountNumber: String = "",
    @SerializedName("roundTrips")
    val roundTrips: Int = 0,
    @SerializedName("isDayTrader")
    val isDayTrader: Boolean = false,
    @SerializedName("isClosingOnlyRestricted")
    val isClosingOnlyRestricted: Boolean = false,
    @SerializedName("positions")
    val positions: List<Position> = emptyList(),
    @SerializedName("initialBalances")
    val initialBalances: AccountBalance? = null,
    @SerializedName("currentBalances")
    val currentBalances: AccountBalance? = null
)

data class AccountBalance(
    @SerializedName("accruedInterest")
    val accruedInterest: Double = 0.0,
    @SerializedName("availableFundsNonMarginableTrade")
    val availableFundsNonMarginableTrade: Double = 0.0,
    @SerializedName("bondValue")
    val bondValue: Double = 0.0,
    @SerializedName("buyingPower")
    val buyingPower: Double = 0.0,
    @SerializedName("cashBalance")
    val cashBalance: Double = 0.0,
    @SerializedName("cashAvailableForTrading")
    val cashAvailableForTrading: Double = 0.0,
    @SerializedName("cashReceipts")
    val cashReceipts: Double = 0.0,
    @SerializedName("dayTradingBuyingPower")
    val dayTradingBuyingPower: Double = 0.0,
    @SerializedName("dayTradingBuyingPowerCall")
    val dayTradingBuyingPowerCall: Double = 0.0,
    @SerializedName("dayTradingEquityCall")
    val dayTradingEquityCall: Double = 0.0,
    @SerializedName("equity")
    val equity: Double = 0.0,
    @SerializedName("equityPercentage")
    val equityPercentage: Double = 0.0,
    @SerializedName("liquidationValue")
    val liquidationValue: Double = 0.0,
    @SerializedName("longMarginValue")
    val longMarginValue: Double = 0.0,
    @SerializedName("longOptionMarketValue")
    val longOptionMarketValue: Double = 0.0,
    @SerializedName("longStockValue")
    val longStockValue: Double = 0.0,
    @SerializedName("maintenanceCall")
    val maintenanceCall: Double = 0.0,
    @SerializedName("maintenanceRequirement")
    val maintenanceRequirement: Double = 0.0,
    @SerializedName("margin")
    val margin: Double = 0.0,
    @SerializedName("marginEquity")
    val marginEquity: Double = 0.0,
    @SerializedName("moneyMarketFund")
    val moneyMarketFund: Double = 0.0,
    @SerializedName("mutualFundValue")
    val mutualFundValue: Double = 0.0,
    @SerializedName("regTCall")
    val regTCall: Double = 0.0,
    @SerializedName("shortMarginValue")
    val shortMarginValue: Double = 0.0,
    @SerializedName("shortOptionMarketValue")
    val shortOptionMarketValue: Double = 0.0,
    @SerializedName("shortStockValue")
    val shortStockValue: Double = 0.0,
    @SerializedName("totalCash")
    val totalCash: Double = 0.0,
    @SerializedName("isInCall")
    val isInCall: Boolean = false,
    @SerializedName("pendingDeposits")
    val pendingDeposits: Double = 0.0,
    @SerializedName("marginBalance")
    val marginBalance: Double = 0.0,
    @SerializedName("shortBalance")
    val shortBalance: Double = 0.0,
    @SerializedName("accountValue")
    val accountValue: Double = 0.0
)

data class Position(
    @SerializedName("shortQuantity")
    val shortQuantity: Double = 0.0,
    @SerializedName("averagePrice")
    val averagePrice: Double = 0.0,
    @SerializedName("currentDayProfitLoss")
    val currentDayProfitLoss: Double = 0.0,
    @SerializedName("currentDayProfitLossPercentage")
    val currentDayProfitLossPercentage: Double = 0.0,
    @SerializedName("longQuantity")
    val longQuantity: Double = 0.0,
    @SerializedName("settledLongQuantity")
    val settledLongQuantity: Double = 0.0,
    @SerializedName("settledShortQuantity")
    val settledShortQuantity: Double = 0.0,
    @SerializedName("instrument")
    val instrument: Instrument,
    @SerializedName("marketValue")
    val marketValue: Double = 0.0,
    @SerializedName("maintenanceRequirement")
    val maintenanceRequirement: Double = 0.0,
    @SerializedName("averageLongPrice")
    val averageLongPrice: Double = 0.0,
    @SerializedName("taxLotAverageLongPrice")
    val taxLotAverageLongPrice: Double = 0.0,
    @SerializedName("longOpenProfitLoss")
    val longOpenProfitLoss: Double = 0.0,
    @SerializedName("previousSessionLongQuantity")
    val previousSessionLongQuantity: Double = 0.0,
    @SerializedName("currentDayCost")
    val currentDayCost: Double = 0.0
)

data class Instrument(
    @SerializedName("assetType")
    val assetType: String = "",
    @SerializedName("cusip")
    val cusip: String = "",
    @SerializedName("symbol")
    val symbol: String = "",
    @SerializedName("description")
    val description: String = "",
    @SerializedName("instrumentId")
    val instrumentId: Long = 0L,
    @SerializedName("netChange")
    val netChange: Double = 0.0
)

data class OrderRequest(
    @SerializedName("orderType")
    val orderType: String,
    @SerializedName("session")
    val session: String = "NORMAL",
    @SerializedName("duration")
    val duration: String = "DAY",
    @SerializedName("orderStrategyType")
    val orderStrategyType: String = "SINGLE",
    @SerializedName("orderLegCollection")
    val orderLegCollection: List<OrderLegCollection>,
    @SerializedName("price")
    val price: Double? = null,
    @SerializedName("stopPrice")
    val stopPrice: Double? = null,
    @SerializedName("stopPriceLinkBasis")
    val stopPriceLinkBasis: String? = null,
    @SerializedName("stopPriceLinkType")
    val stopPriceLinkType: String? = null,
    @SerializedName("stopPriceOffset")
    val stopPriceOffset: Double? = null
)

data class OrderLegCollection(
    @SerializedName("instruction")
    val instruction: String,
    @SerializedName("quantity")
    val quantity: Double,
    @SerializedName("instrument")
    val instrument: OrderInstrument
)

data class OrderInstrument(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("assetType")
    val assetType: String = "EQUITY"
)

data class OrderLeg(
    val symbol: String,
    val quantity: Double,
    val instruction: String = "BUY"
)
