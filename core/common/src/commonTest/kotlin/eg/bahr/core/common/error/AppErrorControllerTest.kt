package eg.bahr.core.common.error

import eg.bahr.core.common.result.AppError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppErrorControllerTest {
    @Test
    fun `an error shown before the host collects is still delivered`() =
        runTest {
            val controller = AppErrorController()

            controller.show(AppError.Network)

            assertEquals(AppError.Network, controller.errors.first())
        }

    @Test
    fun `when the buffer overflows the oldest errors are dropped`() =
        runTest {
            val controller = AppErrorController()
            val errors = (1..6).map { AppError.Unknown("e$it") }

            errors.forEach(controller::show)

            assertEquals(errors.takeLast(4), controller.errors.take(4).toList())
        }
}
