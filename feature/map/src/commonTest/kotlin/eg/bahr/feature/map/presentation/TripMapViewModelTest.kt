package eg.bahr.feature.map.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.map.data.FakeTripMapRepository
import eg.bahr.feature.map.data.MapFixtures
import eg.bahr.feature.map.data.MapFixtures.CAIRO_ID
import eg.bahr.feature.map.data.MapFixtures.pin
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
 * The map screen's state (M4-M2): loading and errors, the pin selection, the drive toggle and what
 * the map draws from them (departure marker, its label rule, the line), and the camera's framings.
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
            assertEquals(CameraRequest(id = 1, frame = CameraFrame.AllPins), state.camera)
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
    fun `with the drive off a selection draws no departure marker or line and keeps the camera`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()

            vm.selectPin("burullus-dawn")

            val state = vm.uiState.value
            assertTrue(state.departureMarkers().isEmpty())
            assertNull(state.driveLine())
            assertEquals(1, state.camera.id)
        }

    @Test
    fun `the drive with a pin selected draws the line to it and frames the two ends`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()
            vm.selectPin("burullus-dawn")

            vm.toggleDrive()

            val state = vm.uiState.value
            assertTrue(state.showsDrive)
            val line = state.driveLine()!!
            assertEquals(CAIRO_ID, line.from.id)
            assertEquals("burullus-dawn", line.to.slug)
            assertEquals(150, line.distanceKm)
            assertEquals(listOf(CAIRO_ID), state.departureMarkers().map { it.point.id })
            assertEquals(CameraRequest(2, CameraFrame.Drive), state.camera)
            assertEquals(listOf(GeoPoint(30.0566, 31.2288), GeoPoint(31.503, 30.804)), state.cameraPoints())
        }

    @Test
    fun `the drive with nothing selected shows where the buses leave and frames it with every pin`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()

            vm.toggleDrive()

            val state = vm.uiState.value
            assertNull(state.driveLine())
            assertEquals(listOf(CAIRO_ID), state.departureMarkers().map { it.point.id })
            assertEquals(6, state.cameraPoints().size)
        }

    @Test
    fun `picking another pin with the drive on moves the line and the camera`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()
            vm.toggleDrive()
            vm.selectPin("burullus-dawn")
            val before = vm.uiState.value.camera.id

            vm.selectPin("ain-sokhna-beach-day")

            val state = vm.uiState.value
            assertEquals("ain-sokhna-beach-day", state.driveLine()?.to?.slug)
            assertEquals(130, state.driveLine()?.distanceKm)
            assertEquals(before + 1, state.camera.id)
            // The same pin again is not a new framing.
            vm.selectPin("ain-sokhna-beach-day")
            assertEquals(before + 1, vm.uiState.value.camera.id)
        }

    @Test
    fun `the toggle off takes the line away without moving the camera`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()
            vm.selectPin("burullus-dawn")
            vm.toggleDrive()
            val camera = vm.uiState.value.camera

            vm.toggleDrive()

            assertFalse(vm.uiState.value.showsDrive)
            assertNull(vm.uiState.value.driveLine())
            assertEquals(camera, vm.uiState.value.camera)
        }

    @Test
    fun `All trips turns the drive off and frames every pin again on each tap`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()
            vm.toggleDrive()

            vm.showAllTrips()
            val first = vm.uiState.value.camera
            vm.showAllTrips()

            assertFalse(vm.uiState.value.showsDrive)
            assertEquals(CameraFrame.AllPins, first.frame)
            assertEquals(first.id + 1, vm.uiState.value.camera.id)
        }

    @Test
    fun `a pin with no departure point or one not listed has no line`() =
        runViewModelTest {
            val map =
                MapFixtures.map().copy(
                    pins =
                        listOf(
                            pin("no-point", 31.0, 30.0, departurePointId = null),
                            pin("unknown-point", 31.1, 30.1, departurePointId = "elsewhere"),
                        ),
                )
            val vm = TripMapViewModel(loaded(map))
            advanceUntilIdle()
            vm.toggleDrive()

            vm.selectPin("no-point")
            assertNull(vm.uiState.value.driveLine())
            assertTrue(
                vm.uiState.value
                    .departureMarkers()
                    .isEmpty(),
            )

            vm.selectPin("unknown-point")
            assertNull(vm.uiState.value.driveLine())
        }

    @Test
    fun `a pin with no recorded distance has a line but no distance`() =
        runViewModelTest {
            val map = MapFixtures.map().copy(pins = listOf(pin("far", 31.0, 30.0, distanceKm = null)))
            val vm = TripMapViewModel(loaded(map))
            advanceUntilIdle()
            vm.selectPin("far")
            vm.toggleDrive()

            assertNull(
                vm.uiState.value
                    .driveLine()
                    ?.distanceKm,
            )
            assertEquals(
                "far",
                vm.uiState.value
                    .driveLine()
                    ?.to
                    ?.slug,
            )
        }

    // ---------- The departure marker's label (MapPin.departTime) ----------

    @Test
    fun `the marker shows the time when every pin leaving from it agrees`() {
        val pins = listOf(pin("a", 31.0, 30.0, departTime = "05:00"), pin("b", 31.1, 30.1, departTime = "05:00"))

        assertEquals("05:00", departureTime(CAIRO_ID, pins))
    }

    @Test
    fun `pins without a time do not break the agreement`() {
        val pins = listOf(pin("a", 31.0, 30.0, departTime = "05:00"), pin("b", 31.1, 30.1, departTime = null))

        assertEquals("05:00", departureTime(CAIRO_ID, pins))
    }

    @Test
    fun `pins leaving at different times leave the marker without a time`() {
        val pins = listOf(pin("a", 31.0, 30.0, departTime = "05:00"), pin("b", 31.1, 30.1, departTime = "06:00"))

        assertNull(departureTime(CAIRO_ID, pins))
    }

    @Test
    fun `no pin with a time leaves the marker without one`() {
        val pins = listOf(pin("a", 31.0, 30.0, departTime = null))

        assertNull(departureTime(CAIRO_ID, pins))
    }

    @Test
    fun `only the pins naming that point count`() {
        val pins =
            listOf(
                pin("a", 31.0, 30.0, departTime = "05:00"),
                pin("b", 31.1, 30.1, departTime = "07:00", departurePointId = MapFixtures.ALEX_ID),
            )

        assertEquals("05:00", departureTime(CAIRO_ID, pins))
        assertEquals("07:00", departureTime(MapFixtures.ALEX_ID, pins))
    }

    @Test
    fun `the fixture's Cairo pins leave at 05 and 06 so its marker is the place alone`() =
        runViewModelTest {
            val vm = TripMapViewModel(loaded())
            advanceUntilIdle()
            vm.toggleDrive()

            assertNull(
                vm.uiState.value
                    .departureMarkers()
                    .single()
                    .time,
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
