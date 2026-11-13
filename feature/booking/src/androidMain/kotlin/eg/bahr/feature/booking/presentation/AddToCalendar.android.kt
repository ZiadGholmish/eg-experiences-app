package eg.bahr.feature.booking.presentation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger

/**
 * The calendar app's own "new event" screen, filled in (`Intent.ACTION_INSERT` on `CalendarContract.Events`): the
 * traveller saves it there, so the app needs no calendar permission and never writes to a calendar itself.
 */
@Composable
internal actual fun rememberCalendarInserter(): CalendarInserter {
    val context = LocalContext.current
    return remember(context) { IntentCalendarInserter(context) }
}

private class IntentCalendarInserter(
    private val context: Context,
) : CalendarInserter {
    override fun add(event: CalendarEvent) {
        val intent =
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilliseconds())
                // No end known: the event is the moment the bus leaves, as the web ticket's `.ics` has it.
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, (event.end ?: event.start).toEpochMilliseconds())
                putExtra(CalendarContract.Events.TITLE, event.title)
                event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                // Outside an Activity (never expected from a screen) a new task is the only way to start one.
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (noCalendar: ActivityNotFoundException) {
            // No calendar app at all (resolving first would need a <queries> entry since Android 11): nothing opens,
            // and the ticket, which is what the traveller needs at the bus, stays as it is.
            Logger.withTag(LOG_TAG).w(noCalendar) { "No calendar app to add the trip to" }
        }
    }

    private companion object {
        const val LOG_TAG = "AddToCalendar"
    }
}
