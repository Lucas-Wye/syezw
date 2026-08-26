package org.syezw.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductOfferTest {
    @Test fun unitPriceIsPriceDividedByQuantity() {
        val offer = ProductOffer(name = "牛奶", merchant = "商家", price = 12.0, quantity = 3.0, quantityUnit = "盒")
        assertEquals(4.0, offer.unitPrice, 0.00001)
    }

    @Test fun zeroQuantityDoesNotProduceInfinity() {
        val offer = ProductOffer(name = "商品", merchant = "商家", price = 1.0, quantity = 0.0, quantityUnit = "个")
        assertEquals(0.0, offer.unitPrice, 0.00001)
    }
}
