package eg.bahr.feature.booking.data

import eg.bahr.core.datastore.ActiveHoldStore
import eg.bahr.core.datastore.StoredHold
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-written fake: the stored hold in memory, with every clear recorded. */
internal class FakeActiveHoldStore(
    initial: StoredHold? = null,
) : ActiveHoldStore {
    override val hold = MutableStateFlow(initial)
    val clears = mutableListOf<String>()

    override suspend fun save(hold: StoredHold) {
        this.hold.value = hold
    }

    override suspend fun clear(ref: String) {
        clears += ref
        if (hold.value?.ref == ref) hold.value = null
    }
}
