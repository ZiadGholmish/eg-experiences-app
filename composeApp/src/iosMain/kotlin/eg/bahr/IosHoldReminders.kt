package eg.bahr

import co.touchlab.kermit.Logger
import eg.bahr.feature.booking.di.HoldReminder
import eg.bahr.feature.booking.di.HoldReminders
import eg.bahr.push.PUSH_TYPE
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The hold reminder on iOS (M3-M7): a local notification with a time-interval trigger, which counts from now, not from
 * a wall-clock date, so a wrong device clock cannot move it. Its identifier is [HoldReminder.tag]: adding it again
 * replaces the pending one (one reminder per hold), and a server push sent with that `apns-collapse-id` takes its
 * place in Notification Center. A tap reaches Swift's notification delegate with `type` and `ref`, as a push's data.
 *
 * iOS decides when it fires whether it shows (notifications allowed or not); nothing here asks for permission.
 */
internal object IosHoldReminders : HoldReminders {
    private val log = Logger.withTag("HoldReminder")
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun schedule(reminder: HoldReminder) {
        val content =
            UNMutableNotificationContent().apply {
                setTitle(reminder.title)
                setBody(reminder.body)
                setSound(UNNotificationSound.defaultSound)
                setUserInfo(mapOf<Any?, Any?>(PUSH_TYPE to HoldReminder.TYPE, HoldReminder.REF_KEY to reminder.ref))
            }
        // iOS refuses an interval that is not positive; the planner only schedules a positive one, this is a floor.
        val seconds = reminder.fireIn.inWholeMilliseconds / MILLIS_PER_SECOND.toDouble()
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds.coerceAtLeast(MIN_INTERVAL), repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(HoldReminder.tag(reminder.ref), content, trigger)
        center.addNotificationRequest(request) { error ->
            if (error !=
                null
            ) {
                log.w { "Hold reminder not scheduled: ${error.localizedDescription}" }
            }
        }
    }

    override fun cancel(ref: String) {
        val ids = listOf(HoldReminder.tag(ref))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /** A server `hold_expiring` push for [ref] is being shown: the local reminder still to come is not needed. */
    fun cancelPending(ref: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(HoldReminder.tag(ref)))
    }

    private const val MILLIS_PER_SECOND = 1_000
    private const val MIN_INTERVAL = 1.0
}
