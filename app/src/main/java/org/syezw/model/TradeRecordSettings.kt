package org.syezw.model

import com.google.gson.GsonBuilder

enum class TradeOrderType {
    BUY,
    SELL,
    OPEN
}

data class TradeRecordItem(
    val type: TradeOrderType = TradeOrderType.BUY,
    val price: String = "",
    val quantity: String = "",
    val fee: String = "",
    val year: String = "",
    val month: String = "",
    val day: String = ""
)

data class TradeRecordState(
    val title: String = "交易明细",
    val accountMasked: String = "",
    val stockName: String = "",
    val stockCode: String = "",
    val stockPrice: String = "",
    val stockChangePercent: String = "",
    val referenceProfit: String = "",
    val referenceProfitPercent: String = "",
    val holdDays: String = "",
    val buyTimes: String = "",
    val sellTimes: String = "",
    val historyRecords: List<TradeRecordItem> = emptyList(),
    val dayRecords: List<TradeRecordItem> = emptyList()
)

object TradeJson {
    private val gson = GsonBuilder().create()

    fun tradeRecordStateToJson(state: TradeRecordState): String = gson.toJson(state)

    fun tradeRecordStateFromJson(json: String?): TradeRecordState {
        if (json.isNullOrBlank()) return TradeRecordState()
        return runCatching { gson.fromJson(json, TradeRecordState::class.java) }
            .getOrElse { TradeRecordState() }
    }
}
