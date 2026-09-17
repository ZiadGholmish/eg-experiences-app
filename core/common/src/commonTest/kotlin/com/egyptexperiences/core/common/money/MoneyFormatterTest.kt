package com.egyptexperiences.core.common.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatterTest {
    @Test
    fun `whole pounds carry no decimal part`() {
        // The canvas shows "450 EGP", not "450.00 EGP".
        assertEquals("450 EGP", MoneyFormatter.format(Money(450.0, Money.EGP)))
    }

    @Test
    fun `thousands are grouped`() {
        assertEquals("6,300 EGP", MoneyFormatter.format(Money(6_300.0, Money.EGP)))
        assertEquals("1,234,567 EGP", MoneyFormatter.format(Money(1_234_567.0, Money.EGP)))
    }

    @Test
    fun `a fractional amount keeps two places`() {
        assertEquals("450.50 EGP", MoneyFormatter.format(Money(450.5, Money.EGP)))
        assertEquals("450.05 EGP", MoneyFormatter.format(Money(450.05, Money.EGP)))
    }

    @Test
    fun `a refund reads as negative`() {
        assertEquals("-450 EGP", MoneyFormatter.format(Money(-450.0, Money.EGP)))
    }

    @Test
    fun `the currency label can be overridden for a localized symbol`() {
        assertEquals("450 ج.م", MoneyFormatter.format(Money(450.0, Money.EGP), currencyLabel = "ج.م"))
    }
}
