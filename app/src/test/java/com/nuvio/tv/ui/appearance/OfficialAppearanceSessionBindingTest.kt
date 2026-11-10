package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.PersistedAppearanceProfile
import org.junit.Assert.*
import org.junit.Test

class OfficialAppearanceSessionBindingTest {
    private val session = AppearanceSessionConfirmation()
    private var candidate: AppearanceSessionContext? = null
    private var begins = 0
    private val binding = OfficialAppearanceSessionBinding(
        { id -> begins++; session.begin(candidate?.takeIf { it.persisted.profileId == id }) },
        { session.confirm(it, candidate) }, session::revoke
    )
    private fun context(id: Int, revision: Long = 1, generation: Long = 1) =
        AppearanceSessionContext(PersistedAppearanceProfile(id, revision), AppearanceContext(id, generation))
    private fun decision(id: Int, serial: Long = 1, revision: Long = 0, account: String = "guest") =
        OfficialAppearanceDecision(serial, id, account, revision)
    private fun update(d: OfficialAppearanceDecision?, c: AppearanceSessionContext?, revision: Long = 0,
                       account: String? = "guest"): Boolean {
        candidate = c
        return binding.update(d, c, revision, account)
    }
    private fun authorized() = session.authorized(candidate)

    @Test fun persistedIdAloneNeverAuthorizesPinOrSession() {
        update(null, context(2))
        assertNull(authorized())
        assertEquals(0, begins)
    }

    @Test fun callbackBeforeIdWaitsForChosenProfile() {
        val d = decision(2)
        update(d, null)
        update(d, context(1))
        assertNull(authorized())
        assertEquals(0, begins)
        update(d, context(2, 2))
        assertEquals(2, authorized()?.context?.storage?.profileId)
        update(d, candidate)
        assertEquals(1, begins)
    }

    @Test fun idBeforeCallbackStillNeedsSuccessfulDecision() {
        val c = context(2)
        update(null, c)
        assertNull(authorized())
        update(decision(2), c)
        assertNotNull(authorized())
    }

    @Test fun failedOrCancelledSelectionCannotFinishLater() {
        update(decision(2), context(1))
        binding.close() // selection/PIN cancelled or host/session exited
        assertTrue(update(decision(2), context(2)))
        assertNull(authorized())
        update(null, context(2))
        update(decision(2, serial = 2), context(2))
        assertNotNull(authorized())
    }

    @Test fun autoAndSingleProfileDecisionsUseSameExplicitContract() {
        for (id in listOf(2, 1)) {
            update(decision(id, serial = id.toLong()), context(id))
            assertEquals(id, authorized()?.context?.storage?.profileId)
        }
    }

    @Test fun confirmedDecisionCannotSurviveAtoBtoAOrLateToken() {
        val a = decision(1)
        val first = context(1)
        update(a, first)
        val old = requireNotNull(authorized())
        assertTrue(update(a, context(2, 2)))
        assertTrue(update(a, context(1, 3)))
        assertFalse(session.confirm(old, candidate))
        assertNull(authorized())
        update(decision(1, 2), candidate)
        assertNotNull(authorized())
        assertNotSame(old, authorized())
    }

    @Test fun resetRevokesPendingAndConfirmedWithoutAutomaticRetry() {
        for (ready in listOf(false, true)) {
            val d = decision(1, if (ready) 2 else 1)
            update(d, if (ready) context(1) else null)
            assertTrue(update(d, context(1, generation = 2), revision = 1))
            assertNull(authorized())
            assertTrue(update(d, context(1, generation = 2), revision = 1))
        }
    }

    @Test fun accountChangeAndExitRevokeAndRequireNewDecision() {
        val d = decision(1, account = "account:A")
        update(d, context(1), account = "account:A")
        assertNotNull(authorized())
        assertTrue(update(d, candidate, account = "account:B"))
        assertNull(authorized())
        update(null, candidate, account = "account:B")
        update(decision(1, 2, account = "account:B"), candidate, account = "account:B")
        assertNotNull(authorized())
        binding.close()
        assertNull(authorized())
    }

    @Test fun recreatedControllerNeedsRestoredDecisionAndFreshCapability() {
        val d = decision(1)
        update(d, context(1))
        val old = requireNotNull(authorized())
        binding.close()
        val recreatedSession = AppearanceSessionConfirmation()
        val recreated = OfficialAppearanceSessionBinding(
            { recreatedSession.begin(candidate) },
            { recreatedSession.confirm(it, candidate) }, recreatedSession::revoke
        )
        recreated.update(null, candidate, 0, "guest")
        assertNull(recreatedSession.authorized(candidate))
        recreated.update(d, candidate, 0, "guest")
        assertNotNull(recreatedSession.authorized(candidate))
        assertFalse(recreatedSession.confirm(old, candidate))
    }
}
