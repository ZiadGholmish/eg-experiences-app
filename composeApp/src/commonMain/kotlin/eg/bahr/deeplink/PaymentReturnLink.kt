package eg.bahr.deeplink

/**
 * The hosted checkout coming back to the app (M3-M1): [url] is this build's payment return link ([returnUrl],
 * `<application id>://checkout`) or lies under it (`…/checkout/{ref}`). It only has to bring the app to the front
 * (Android: `MainActivity` is `singleTask`, which also clears the Custom Tab), where the held seats read the booking
 * again as they start. It must never reach the deep-link inbox, which would open the trip list in its place and
 * close the checkout. Null or blank [returnUrl]: this build sends none, so nothing matches.
 */
internal fun isPaymentReturn(
    url: String,
    returnUrl: String?,
): Boolean {
    val prefix = returnUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
    val link = url.trim()
    return link.equals(prefix, ignoreCase = true) || link.startsWith("$prefix/", ignoreCase = true)
}
