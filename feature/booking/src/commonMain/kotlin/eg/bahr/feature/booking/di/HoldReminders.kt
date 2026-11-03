package eg.bahr.feature.booking.di

import kotlin.time.Duration

/**
 * The phone's own "your hold is about to expire" notification (M3-M7), as feature:booking asks for it: scheduled on the
 * device, so it works signed out, offline and in a build without Firebase. `:composeApp` implements it per platform
 * (Android `AlarmManager`, iOS `UNUserNotificationCenter`) and binds it in each entry point, as it binds `PushChannel`;
 * a graph without one schedules nothing ([NoHoldReminders]).
 *
 * Public, in `di/`, like feature:auth's `NativeSignIn`: the platform code that implements it lives outside the
 * feature. Everything it needs is in [HoldReminder]: the platform holds no strings and no rules of its own.
 */
interface HoldReminders {
    /**
     * Shows [reminder] [HoldReminder.fireIn] from now, replacing any reminder of the same [HoldReminder.ref] not yet
     * shown. Relative, never a wall-clock time: the delay comes from the server's `holdExpiresAt − serverNow`.
     */
    fun schedule(reminder: HoldReminder)

    /** Forgets [ref]'s reminder: the pending one, and one already shown (it would be wrong now). */
    fun cancel(ref: String)
}

/**
 * One hold's reminder. [fireIn] is how long from now to show it; [releasedIn] how long from now the seats are released
 * (the server's deadline), after which a late alarm shows nothing and a shown one goes away. [title] and [body] are
 * already in the app's language.
 */
data class HoldReminder(
    val ref: String,
    val fireIn: Duration,
    val releasedIn: Duration,
    val title: String,
    val body: String,
) {
    companion object {
        /**
         * The push `type` a tap carries (the inbox's `hold_expiring`, openapi `NotificationType`), with the hold's
         * [ref] under [REF_KEY]: the same keys a server `hold_expiring` push is asked to carry (M3-M7 report), so one
         * tap path opens both.
         */
        const val TYPE = "hold_expiring"
        const val REF_KEY = "ref"

        /**
         * The notification's identity: Android's notification tag, iOS's request identifier. A server push with this
         * collapse id replaces the local one in the tray rather than showing beside it (M3-M7 dedupe).
         */
        fun tag(ref: String): String = "$TYPE:$ref"
    }
}

/** Schedules nothing: a graph without a platform scheduler (tests). */
object NoHoldReminders : HoldReminders {
    override fun schedule(reminder: HoldReminder) = Unit

    override fun cancel(ref: String) = Unit
}
