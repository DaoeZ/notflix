package com.nuvio.tv.data.wikipedia

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WikipediaLabBudgetTest {
    private val root=Files.createTempDirectory("nf18-budget-").toFile()
    private val file=File(root,"budget.json")
    private var time=1_700_000_000_000L
    private fun budget()=WikipediaLabBudget(file){time}
    @After fun cleanup() {
        check(root.canonicalFile.parentFile==File(System.getProperty("java.io.tmpdir")).canonicalFile && root.name.startsWith("nf18-budget-"));root.deleteRecursively()
    }
    @Test fun thirtyReservationsAreDurableAndNeverResetOnReopening() {
        repeat(30){assertEquals(it+1,budget().reserve("tt0000001")!!.requests)}
        assertNull(budget().reserve("tt0000001"));assertEquals(30,budget().snapshot()!!.requests)
    }
    @Test fun sixUniqueIdsAllowedAndSameTitleIdDoesNotConsumeAnotherIdentity() {
        repeat(6){assertNotNull(budget().reserve("tt"+(it+1).toString().padStart(7,'0')))}
        assertNull(budget().reserve("tt0000007"));assertNotNull(budget().reserve("tt0000001"))
        assertEquals(7,budget().snapshot()!!.requests);assertEquals(6,budget().snapshot()!!.identities.size)
    }
    @Test fun corruptionOrMissingStateAfterGuardFailsClosed() {
        assertNotNull(budget().reserve("tt0000001"));file.writeText("broken");assertNull(budget().reserve("tt0000001"))
        file.delete();assertNull(budget().snapshot());assertNull(budget().reserve("tt0000001"))
    }
    @Test fun retryAfterSurvivesReopeningAndCannotBeShortened() {
        budget().reserve("tt0000001");assertTrue(budget().deferUntil(time+600_000));budget().deferUntil(time+1)
        assertNull(budget().reserve("tt0000001"));time+=599_999;assertNull(budget().reserve("tt0000001"))
        time++;assertEquals(2,budget().reserve("tt0000001")!!.requests)
    }
    @Test fun invalidIdOrMalformedIdentityListNeverIssuesAReservation() {
        assertNull(budget().reserve("a film title"));assertFalse(file.exists())
        file.writeText("{\"schema\":1,\"requests\":0,\"identities\":[\"tt0000001\"],\"notBefore\":0}")
        assertNull(budget().snapshot());assertNull(budget().reserve("tt0000001"))
    }
}
