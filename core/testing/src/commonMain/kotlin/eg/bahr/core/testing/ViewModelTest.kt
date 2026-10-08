package eg.bahr.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Runs a view-model test with `Dispatchers.Main` pointed at the test scheduler.
 *
 * `viewModelScope` runs on `Dispatchers.Main.immediate`, which does not exist in a JVM unit test
 * and is the real main queue on iOS; replacing it gives one virtual clock for the test body and
 * every coroutine the view model launches. Construct the view model *inside* [testBody], after
 * Main is replaced.
 *
 * The default [StandardTestDispatcher] queues work instead of running it eagerly, so what a view
 * model launches (including from `init`) runs only when the test lets it: call `runCurrent()` /
 * `advanceUntilIdle()` and assert on the states in between (loading, then loaded).
 *
 * A function rather than a base class with `@BeforeTest`/`@AfterTest`, so it behaves the same on
 * the JVM and Kotlin/Native and leaves the test class free of inheritance. The pattern:
 *
 * ```kotlin
 * @Test
 * fun `a failed first page shows the error`() = runViewModelTest {
 *     val repository = FakeTripRepository(listTrips = { AppResult.Failure(AppError.Network) })
 *     val vm = TripListViewModel(repository, AppErrorController())
 *     advanceUntilIdle()
 *     assertEquals(AppError.Network, vm.uiState.value.error)
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(
    dispatcher: TestDispatcher = StandardTestDispatcher(),
    testBody: suspend TestScope.() -> Unit,
): TestResult {
    Dispatchers.setMain(dispatcher)
    try {
        return runTest(dispatcher) { testBody() }
    } finally {
        Dispatchers.resetMain()
    }
}
