package eg.bahr.core.common.error

import eg.bahr.core.common.result.AppError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AppErrorControllerTest {
    @Test
    fun `an error shown before the host collects is still delivered`() =
        runTest {
            val controller = AppErrorController()

            controller.show(AppError.Network)

            assertEquals(AppError.Network, controller.current.first()?.error)
        }

    @Test
    fun `the head stays current until the host dismisses it - a torn-down host shows it again`() =
        runTest {
            val controller = AppErrorController()
            controller.show(AppError.Network)
            controller.show(AppError.Timeout)

            val first = controller.current.first()!!
            // A second collector (the host after a language switch or Activity recreation) sees the same one.
            assertEquals(first, controller.current.first())
            assertEquals(AppError.Network, first.error)

            controller.dismiss(first.id)

            assertEquals(AppError.Timeout, controller.current.first()?.error)
            controller.dismiss(controller.current.first()!!.id)
            assertNull(controller.current.first())
        }

    @Test
    fun `dismissing a message that is already gone changes nothing`() =
        runTest {
            val controller = AppErrorController()
            controller.show(AppError.Network)
            val stale = controller.current.first()!!
            controller.dismiss(stale.id)
            controller.show(AppError.Timeout)

            controller.dismiss(stale.id)

            assertEquals(AppError.Timeout, controller.current.first()?.error)
        }

    @Test
    fun `the same error twice is two messages`() =
        runTest {
            val controller = AppErrorController()
            controller.show(AppError.Network)
            controller.show(AppError.Network)

            val first = controller.current.first()!!
            controller.dismiss(first.id)
            val second = controller.current.first()!!

            assertEquals(AppError.Network, second.error)
            assertNotEquals(first.id, second.id)
        }

    @Test
    fun `when the buffer overflows the head is kept and the oldest waiting errors are dropped`() =
        runTest {
            val controller = AppErrorController()
            val errors = (1..6).map { AppError.Unknown("e$it") }

            errors.forEach(controller::show)

            val shown = mutableListOf<AppError>()
            while (true) {
                val next = controller.current.first() ?: break
                shown += next.error
                controller.dismiss(next.id)
            }
            assertEquals<List<AppError>>(listOf(errors[0]) + errors.takeLast(3), shown)
        }
}
