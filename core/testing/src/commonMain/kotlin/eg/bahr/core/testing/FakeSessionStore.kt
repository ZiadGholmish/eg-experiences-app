package eg.bahr.core.testing

import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession

/**
 * A secure store holding [session] in memory (null: signed out), for the tests that need core:network's `AuthSession`
 * over it (MP-M1: every Koin graph with core:catalog's heart store in it). core:common's interface, so no new edge.
 */
class FakeSessionStore(
    var session: StoredSession? = null,
) : SessionStore {
    override suspend fun read(): StoredSession? = session

    override suspend fun write(session: StoredSession?) {
        this.session = session
    }
}
