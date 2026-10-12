package eg.bahr.feature.map.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.map.data.FakeTripMapRepository
import eg.bahr.feature.map.data.MapFixtures
import eg.bahr.feature.map.model.TripMapDto
import eg.bahr.feature.map.presentation.components.GeoBounds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The map screen's state (M4-M2): loading and errors, the pin selection, and the camera's framings
 * (every pin, once per read; the drive and "All trips" toggles were removed, user decision 2026-10-12).
 */
class TripMapViewModelTest {
    private fun loaded(map: TripMapDto = MapFixtures.map()) = FakeTripMapRepository(tripMap = { AppResult.Success(map) })

    @Test
    fun `loading then every pin framed`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            assertTrue(vm.uiState.value.loading)

            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.loading)
            assertEquals(5, state.map?.pins?.size)
            assertEquals(CameraRequest(id = 1), state.camera)
            assertEquals(state.map!!.pins.map { GeoPoint(it.lat, it.lng) }, state.cameraPoints())
        }

    @Test
    fun `a failed read shows the error and Retry reads again`() =
        runViewModelTest {
            var answer: AppResult<TripMapDto> = AppResult.Failure(AppError.Network)
            val repository = FakeTripMapRepository(tripMap = { answer })
            val vm = TripMapViewModel(repository)
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.error)
            assertNull(vm.uiState.value.map)

            answer = AppResult.Success(MapFixtures.map())
            vm.retry()
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertEquals(
                5,
                vm.uiState.value.map
                    ?.pins
                    ?.size,
            )
            assertEquals(2, repository.reads)
        }

    @Test
    fun `a burst of Retry taps is one read`() =
        runViewModelTest {
            val gate = CompletableDeferred<AppResult<TripMapDto>>()
            val repository = FakeTripMapRepository(tripMap = { AppResult.Failure(AppError.Network) })
            val vm = TripMapViewModel(repository)
            advanceUntilIdle()
            repository.tripMap = { gate.await() }

            repeat(3) { vm.retry() }
            runCurrent()
            gate.complete(AppResult.Success(MapFixtures.map()))
            advanceUntilIdle()

            assertEquals(2, repository.reads)
        }

    @Test
    fun `no pins is the empty state`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded(TripMapDto()))
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isEmpty)
        }

    @Test
    fun `tapping a pin opens its card and the map clears it`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()

            vm.selectPin("burullus-dawn")
            assertEquals(
                "burullus-dawn",
                vm.uiState.value.selectedPin
                    ?.slug,
            )

            vm.clearSelection()
            assertNull(vm.uiState.value.selectedPin)
        }

    @Test
    fun `an unknown slug selects nothing`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()

            vm.selectPin("not-a-trip")

            assertNull(vm.uiState.value.selectedSlug)
        }

    @Test
    fun `picking a pin keeps the camera - the map is not re-framed`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()

            vm.selectPin("burullus-dawn")
            vm.selectPin("ain-sokhna-beach-day")

            assertEquals(1, vm.uiState.value.camera.id)
            assertEquals(
                5,
                vm.uiState.value
                    .cameraPoints()
                    .size,
            )
        }

    // ---------- Camera bounds ----------

    @Test
    fun `bounds around one pin keep a minimum span`() {
        val bounds = GeoBounds.around(listOf(GeoPoint(31.5, 30.8)))!!

        assertEquals(GeoBounds.MIN_SPAN_DEGREES, bounds.north - bounds.south, TOLERANCE)
        assertEquals(GeoBounds.MIN_SPAN_DEGREES, bounds.east - bounds.west, TOLERANCE)
        assertEquals(31.5, bounds.center.lat, TOLERANCE)
        assertEquals(30.8, bounds.center.lng, TOLERANCE)
    }

    @Test
    fun `bounds around several pins are their box`() {
        val bounds = GeoBounds.around(listOf(GeoPoint(27.36, 28.17), GeoPoint(31.59, 32.34)))!!

        assertEquals(27.36, bounds.south, TOLERANCE)
        assertEquals(28.17, bounds.west, TOLERANCE)
        assertEquals(31.59, bounds.north, TOLERANCE)
        assertEquals(32.34, bounds.east, TOLERANCE)
        assertNull(GeoBounds.around(emptyList()))
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
