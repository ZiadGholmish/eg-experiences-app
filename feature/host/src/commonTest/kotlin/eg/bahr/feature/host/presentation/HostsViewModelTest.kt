package eg.bahr.feature.host.presentation

import androidx.paging.testing.ErrorRecovery
import androidx.paging.testing.LoadErrorHandler
import androidx.paging.testing.asSnapshot
import eg.bahr.core.catalog.data.HOST_PAGE_SIZE
import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.FacetType
import eg.bahr.core.catalog.model.GovernorateChipDto
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.catalog.model.HostPageDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.host.data.FakeHostRepository
import eg.bahr.feature.host.data.HostListRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hosts directory (MV-M1) on a fake repository, paged through Paging's testing `asSnapshot`. Every change of
 * search, sort or filter is a new query, and a new query starts the list over at page 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HostsViewModelTest {
    /** Two pages of hosts named after the request, and two region chips (the second with no hosts). */
    private fun repository(categories: AppResult<List<FacetDto>> = AppResult.Success(listOf(category("birds"), category("on_the_boat")))) =
        FakeHostRepository(
            listHosts = { query, page -> hostPage(query, page) },
            tripCategories = { categories },
        )

    private fun TestScope.vm(
        repository: FakeHostRepository = repository(),
        q: String? = null,
        sort: String? = null,
    ) = HostsViewModel(q, sort, repository).also { runCurrent() }

    @Test
    fun `the first page is top rated with nothing else sent - its regions and count come with it`() =
        runViewModelTest {
            val repository = repository()
            val hosts = vm(repository)

            val shown = hosts.hosts.asSnapshot()

            // The snapshot's viewport also prefetches page 1; the first request is what the list opens with.
            assertEquals(HostListRequest(HostListQuery(sort = "top_rated"), 0, HOST_PAGE_SIZE), repository.hostRequests.first())
            assertEquals("null-top_rated-0-0", shown.first().slug)
            assertEquals(
                listOf("kafr-el-sheikh", "red-sea"),
                hosts.uiState.value.governorates
                    .map { it.key },
            )
            assertEquals(TOTAL, hosts.uiState.value.totalItems)
        }

    @Test
    fun `scrolling reads the next page - and the list ends at the last`() =
        runViewModelTest {
            val repository = repository()
            val hosts = vm(repository)

            val shown = hosts.hosts.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf(0, 1), repository.hostRequests.map { it.page })
            assertEquals(TOTAL.toInt(), shown.size)
        }

    @Test
    fun `the kinds of trip load once - and a failure leaves no chips`() =
        runViewModelTest {
            val loaded = vm(repository())
            advanceUntilIdle()
            assertEquals(
                listOf("birds", "on_the_boat"),
                loaded.uiState.value.categories
                    .map { it.key },
            )

            val failing = repository(categories = AppResult.Failure(AppError.Network))
            val without = vm(failing)
            advanceUntilIdle()
            assertTrue(
                without.uiState.value.categories
                    .isEmpty(),
            )
            assertEquals(1, failing.categoryReads)
        }

    @Test
    fun `typing is searched once it rests - and the list starts over at page 0`() =
        runViewModelTest {
            val repository = repository()
            val hosts = vm(repository)
            hosts.hosts.asSnapshot { appendScrollWhile { true } }
            repository.hostRequests.clear()

            hosts.onTextChange("هدى")
            advanceTimeBy(HOSTS_SEARCH_DEBOUNCE_MILLIS - 1)
            runCurrent()
            assertNull(hosts.uiState.value.q, "still inside the debounce")
            advanceTimeBy(1)
            runCurrent()

            hosts.hosts.asSnapshot()
            assertEquals(HostListRequest(HostListQuery(q = "هدى", sort = "top_rated"), 0, HOST_PAGE_SIZE), repository.hostRequests.first())
        }

    @Test
    fun `fewer than two letters search nothing - control characters are not sent`() =
        runViewModelTest {
            val hosts = vm()
            hosts.onTextChange(" ه ")
            advanceUntilIdle()
            assertNull(hosts.uiState.value.q)

            hosts.onTextChange("\u0007hoda ")
            advanceUntilIdle()
            assertEquals("hoda", hosts.uiState.value.q)
        }

    @Test
    fun `text longer than the contract allows is cut to it - never inside a character`() =
        runViewModelTest {
            val hosts = vm()
            hosts.onTextChange("x".repeat(HOSTS_MAX_QUERY_LENGTH + 5))
            assertEquals("x".repeat(HOSTS_MAX_QUERY_LENGTH), hosts.uiState.value.text)

            // An emoji (two chars) straddling the limit goes whole.
            hosts.onTextChange("x".repeat(HOSTS_MAX_QUERY_LENGTH - 1) + "\uD83D\uDE00")
            assertEquals("x".repeat(HOSTS_MAX_QUERY_LENGTH - 1), hosts.uiState.value.text)
        }

    @Test
    fun `a new sort starts the list over at page 0`() =
        runViewModelTest {
            val repository = repository()
            val hosts = vm(repository)
            hosts.hosts.asSnapshot { appendScrollWhile { true } }
            repository.hostRequests.clear()

            hosts.selectSort(HostSort.MostTrips)
            hosts.hosts.asSnapshot()

            assertEquals(HostListRequest(HostListQuery(sort = "most_trips"), 0, HOST_PAGE_SIZE), repository.hostRequests.first())
        }

    @Test
    fun `each filter narrows from page 0 - and tapping the chosen chip clears it`() =
        runViewModelTest {
            val repository = repository()
            val hosts = vm(repository)
            hosts.hosts.asSnapshot { appendScrollWhile { true } }

            hosts.selectCategory("birds")
            hosts.selectGovernorate("kafr-el-sheikh")
            hosts.selectKind(HostKindFilter.Company)
            repository.hostRequests.clear()
            hosts.hosts.asSnapshot()
            assertEquals(
                HostListRequest(
                    HostListQuery(sort = "top_rated", category = "birds", governorate = "kafr-el-sheikh", kind = "company"),
                    0,
                    HOST_PAGE_SIZE,
                ),
                repository.hostRequests.first(),
            )

            hosts.selectCategory("birds")
            hosts.selectGovernorate("kafr-el-sheikh")
            hosts.selectKind(HostKindFilter.Company)
            assertEquals(HostListQuery(sort = "top_rated"), hosts.uiState.value.query)
        }

    @Test
    fun `Clear all drops every filter - the search and the order stay`() =
        runViewModelTest {
            val hosts = vm(q = "hoda", sort = "newest")
            hosts.selectCategory("birds")
            hosts.selectGovernorate("red-sea")
            hosts.selectKind(HostKindFilter.Person)
            assertTrue(hosts.uiState.value.hasFilters)

            hosts.clearFilters()

            assertEquals(HostListQuery(q = "hoda", sort = "newest"), hosts.uiState.value.query)
            assertTrue(!hosts.uiState.value.hasFilters)
        }

    @Test
    fun `the opener's text and order are the first query - searched at once`() =
        runViewModelTest {
            val repository = repository()
            val hosts = vm(repository, q = "hoda", sort = "most_trips")

            hosts.hosts.asSnapshot()

            assertEquals("hoda", hosts.uiState.value.text)
            assertEquals(HostListQuery(q = "hoda", sort = "most_trips"), repository.hostRequests.first().query)
        }

    @Test
    fun `an unknown order from an opener is the default`() =
        runViewModelTest {
            assertEquals(HostSort.TopRated, vm(sort = "cheapest").uiState.value.sort)
        }

    @Test
    fun `a refused region is dropped - then the list loads without it`() =
        runViewModelTest {
            val repository =
                FakeHostRepository(
                    listHosts = { query, page -> if (query.governorate != null) VALIDATION_FAILED else hostPage(query, page) },
                )
            val hosts = vm(repository)
            hosts.selectGovernorate("gone")

            hosts.hosts.asSnapshot(onError = RETURN_CURRENT)
            val shown = hosts.hosts.asSnapshot()

            assertNull(hosts.uiState.value.governorate)
            assertEquals("null-top_rated-0-0", shown.first().slug)
        }

    @Test
    fun `a refused kind of trip is dropped once the region is not to blame`() =
        runViewModelTest {
            val repository =
                FakeHostRepository(
                    listHosts = { query, page -> if (query.category != null) VALIDATION_FAILED else hostPage(query, page) },
                )
            val hosts = vm(repository)
            hosts.selectCategory("gone")

            hosts.hosts.asSnapshot(onError = RETURN_CURRENT)

            assertNull(hosts.uiState.value.category)
        }

    @Test
    fun `Retry reads the failed first page again - and the kinds of trip if they failed`() =
        runViewModelTest {
            var fail = true
            val repository =
                FakeHostRepository(
                    listHosts = { query, page -> if (fail) AppResult.Failure(AppError.Network) else hostPage(query, page) },
                    tripCategories = { if (fail) AppResult.Failure(AppError.Network) else AppResult.Success(listOf(category("birds"))) },
                )
            val hosts = vm(repository)
            hosts.hosts.asSnapshot(onError = RETURN_CURRENT)

            fail = false
            hosts.retry()
            advanceUntilIdle()
            val shown = hosts.hosts.asSnapshot()

            assertEquals("null-top_rated-0-0", shown.first().slug)
            assertEquals(
                listOf("birds"),
                hosts.uiState.value.categories
                    .map { it.key },
            )
        }

    private fun hostPage(
        query: HostListQuery,
        page: Int,
    ): AppResult<HostPageDto> =
        AppResult.Success(
            HostPageDto(
                items =
                    (0 until HOST_PAGE_SIZE).map {
                        HostCardDto(
                            slug = "${query.q}-${query.sort}-$page-$it",
                            name = "Host $it",
                            publishedTrips = 1,
                        )
                    },
                page = page,
                size = HOST_PAGE_SIZE,
                totalItems = TOTAL,
                totalPages = PAGES,
                governorates =
                    listOf(
                        GovernorateChipDto("kafr-el-sheikh", "Kafr El Sheikh", count = 4, selected = query.governorate == "kafr-el-sheikh"),
                        GovernorateChipDto("red-sea", "Red Sea", count = 0),
                    ),
            ),
        )

    private fun category(key: String) = FacetDto(type = FacetType.CATEGORY, key = key, label = key, count = 3)

    private companion object {
        const val PAGES = 2
        const val TOTAL = (HOST_PAGE_SIZE * PAGES).toLong()
        val RETURN_CURRENT = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT }
        val VALIDATION_FAILED = AppResult.Failure(AppError.Api(ApiErrorCodes.VALIDATION_FAILED, "bad", 400))
    }
}
