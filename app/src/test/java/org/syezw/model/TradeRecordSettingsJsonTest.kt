package org.syezw.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TradeRecordSettingsJsonTest {
    @Test
    fun tradeRecordStateJsonRoundTrip() {
        val state = TradeRecordState(
            title = "交易明细",
            accountMasked = "528****40",
            stockName = "金安国纪",
            stockCode = "002636",
            stockPrice = "27.35",
            stockChangePercent = "+0.85%",
            referenceProfit = "+688.560",
            referenceProfitPercent = "0.00%",
            holdDays = "15",
            buyTimes = "2",
            sellTimes = "0",
            historyRecords = listOf(
                TradeRecordItem(
                    type = TradeOrderType.BUY,
                    price = "26.190",
                    quantity = "200",
                    fee = "0.52",
                    year = "2026",
                    month = "02",
                    day = "12"
                )
            ),
            dayRecords = listOf(
                TradeRecordItem(
                    type = TradeOrderType.SELL,
                    price = "27.4300",
                    quantity = "200",
                    year = "2026",
                    month = "02",
                    day = "12"
                )
            )
        )

        val json = TradeJson.tradeRecordStateToJson(state)
        val decoded = TradeJson.tradeRecordStateFromJson(json)

        assertEquals(state, decoded)
    }
}
