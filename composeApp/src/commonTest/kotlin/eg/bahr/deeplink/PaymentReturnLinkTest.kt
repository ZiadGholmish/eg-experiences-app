package eg.bahr.deeplink

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentReturnLinkTest {
    private val returnUrl = "eg.bahr.app.dev://checkout"

    @Test
    fun `the return link and anything under it is the checkout coming back`() {
        assertTrue(isPaymentReturn("eg.bahr.app.dev://checkout", returnUrl))
        assertTrue(isPaymentReturn("eg.bahr.app.dev://checkout/BRL-7K4M2X9P", returnUrl))
        assertTrue(isPaymentReturn("eg.bahr.app.dev://checkout/BRL-7K4M2X9P?success=true", returnUrl))
        assertTrue(isPaymentReturn("eg.bahr.app.dev://checkout/", "$returnUrl/"))
    }

    @Test
    fun `other links and look-alikes are not`() {
        assertFalse(isPaymentReturn("https://bahr.eg/t/burullus-dawn", returnUrl))
        assertFalse(isPaymentReturn("eg.bahr.app.dev://checkoutx/BRL", returnUrl))
        assertFalse(isPaymentReturn("eg.bahr.app://checkout/BRL", returnUrl))
    }

    @Test
    fun `a build without a return link matches nothing`() {
        assertFalse(isPaymentReturn("eg.bahr.app.dev://checkout/BRL", null))
        assertFalse(isPaymentReturn("eg.bahr.app.dev://checkout/BRL", ""))
    }
}
