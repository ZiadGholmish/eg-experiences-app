package eg.bahr

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import eg.bahr.feature.booking.di.HoldReminder
import eg.bahr.push.PUSH_REF
import eg.bahr.push.PUSH_TYPE
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The hold reminder on Android (M3-M7): one inexact alarm per hold, counted from boot; when it goes off, a notification
 * under the hold's tag that opens the hold, unless notifications are off (the permission-denied path) or the seats
 * are already released.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidHoldRemindersTest {
    class Receiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) = Unit
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val reminders = AndroidHoldReminders(context, Receiver::class.java)

    /** A launcher activity, as the real app has (MainActivity): the tap opens it. */
    @Before
    fun launcher() {
        val main = ComponentName(context.packageName, "eg.bahr.MainActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(main)
            addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
    }

    private fun reminder(ref: String = REF) =
        HoldReminder(ref = ref, fireIn = 12.minutes, releasedIn = 15.minutes, title = "Your seat hold ends in 3 min", body = "Pay")

    /** The alarm's intent, as the receiver would get it. */
    private fun firedIntent(): Intent = shadowOf(assertNotNull(alarms.nextScheduledAlarm).operation).savedIntent

    @Test
    fun `one alarm per hold - counted from boot - replaced on a re-schedule`() {
        reminders.schedule(reminder())
        reminders.schedule(reminder().copy(fireIn = 10.minutes))
        reminders.schedule(reminder(ref = "BRL-OTHER001"))

        assertEquals(2, alarms.scheduledAlarms.size)
        val alarm = alarms.scheduledAlarms.first { shadowOf(it.operation).savedIntent.getStringExtra(PUSH_REF) == REF }
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertEquals(SystemClock.elapsedRealtime() + 10.minutes.inWholeMilliseconds, alarm.triggerAtTime)
    }

    @Test
    fun `cancel removes that hold's alarm only`() {
        reminders.schedule(reminder())
        reminders.schedule(reminder(ref = "BRL-OTHER001"))

        reminders.cancel(REF)

        assertEquals(listOf("BRL-OTHER001"), alarms.scheduledAlarms.map { shadowOf(it.operation).savedIntent.getStringExtra(PUSH_REF) })
    }

    @Test
    fun `fired - it shows under the hold's tag and a tap opens the hold`() {
        reminders.schedule(reminder())

        AndroidHoldReminders.post(context, firedIntent(), CHANNEL, 0)

        val shown = shadowOf(notifications).allNotifications.single()
        assertEquals("Your seat hold ends in 3 min", shadowOf(shown).contentTitle)
        val tap = shadowOf(shown.contentIntent).savedIntent
        assertEquals(HoldReminder.TYPE, tap.getStringExtra(PUSH_TYPE))
        assertEquals(REF, tap.getStringExtra(PUSH_REF))
        assertTrue(AndroidHoldReminders.isShowing(context, HoldReminder.tag(REF)))

        // Cancelled with the hold: gone from the tray too.
        reminders.cancel(REF)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun `notifications off - the alarm goes off and nothing shows`() {
        shadowOf(notifications).setNotificationsEnabled(false)
        reminders.schedule(reminder())

        AndroidHoldReminders.post(context, firedIntent(), CHANNEL, 0)

        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun `an alarm that came after the seats were released shows nothing`() {
        reminders.schedule(reminder().copy(releasedIn = 1.minutes))
        val intent = firedIntent()
        ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(2))

        AndroidHoldReminders.post(context, intent, CHANNEL, 0)

        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun `a pending-only cancel keeps one already shown`() {
        reminders.schedule(reminder())
        AndroidHoldReminders.post(context, firedIntent(), CHANNEL, 0)

        reminders.cancelPending(REF)

        assertNull(alarms.nextScheduledAlarm)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
    }

    private companion object {
        const val REF = "BRL-7K4M2X9P"
        const val CHANNEL = "bahr_default"
    }
}
