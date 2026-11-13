package eg.bahr.feature.booking.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKitUI.EKEventEditViewAction
import platform.EventKitUI.EKEventEditViewController
import platform.EventKitUI.EKEventEditViewDelegateProtocol
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.time.Instant

@Composable
internal actual fun rememberCalendarInserter(): CalendarInserter {
    val host = LocalUIViewController.current
    return remember(host) { EventEditCalendarInserter(host) }
}

/**
 * The system's event editor (`EKEventEditViewController`), filled in and presented over the Compose root. Since iOS 17
 * it runs outside the app's process with its own access to the calendars, so the app requests no calendar access and
 * needs no usage description (Apple, "Accessing the event store"; the app targets iOS 18.2). The traveller saves or
 * cancels there; the app is not told which, and does not need to be.
 *
 * The editor holds its delegate weakly and the event needs its store: both are kept here while the editor is open.
 */
private class EventEditCalendarInserter(
    private val host: UIViewController,
) : CalendarInserter {
    private var store: EKEventStore? = null
    private var delegate: EditorDelegate? = null

    override fun add(event: CalendarEvent) {
        val eventStore = EKEventStore()
        val draft =
            EKEvent.eventWithEventStore(eventStore).apply {
                title = event.title
                startDate = event.start.toNSDate()
                // No end known: the event is the moment the bus leaves, as the web ticket's `.ics` has it.
                endDate = (event.end ?: event.start).toNSDate()
                location = event.location
            }
        val editor = EKEventEditViewController()
        val editorDelegate =
            EditorDelegate {
                store = null
                delegate = null
            }
        editor.eventStore = eventStore
        editor.event = draft
        editor.editViewDelegate = editorDelegate
        store = eventStore
        delegate = editorDelegate
        topmost(host).presentViewController(editor, animated = true, completion = null)
    }
}

private class EditorDelegate(
    private val onClosed: () -> Unit,
) : NSObject(),
    EKEventEditViewDelegateProtocol {
    override fun eventEditViewController(
        controller: EKEventEditViewController,
        didCompleteWithAction: EKEventEditViewAction,
    ) {
        controller.dismissViewControllerAnimated(true, completion = null)
        onClosed()
    }
}

/** What is on screen: a sheet already shown over the root (none is expected on the ticket) is presented from. */
private fun topmost(root: UIViewController): UIViewController {
    var top = root
    while (true) top = top.presentedViewController ?: return top
}

private fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1_000.0
