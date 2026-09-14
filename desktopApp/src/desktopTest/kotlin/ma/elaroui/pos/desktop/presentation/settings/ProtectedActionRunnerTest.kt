package ma.elaroui.pos.desktop.presentation.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProtectedActionRunnerTest {
    @Test
    fun `wrong password immediately resets loading and permits retry`() = runBlocking {
        var loading = false
        var actionCalls = 0

        val first = runProtectedAction(
            onProcessingChanged = { loading = it },
            verifyCredential = { false },
            action = { actionCalls++ }
        )

        assertIs<ProtectedActionResult.InvalidCredential>(first)
        assertFalse(loading)
        assertEquals(0, actionCalls)

        val retry = runProtectedAction(
            onProcessingChanged = { loading = it },
            verifyCredential = { true },
            action = { actionCalls++; "done" }
        )

        assertIs<ProtectedActionResult.Success<String>>(retry)
        assertFalse(loading)
        assertEquals(1, actionCalls)
    }

    @Test
    fun `exception always resets loading and returns a controlled failure`() = runBlocking {
        var loading = false
        val transitions = mutableListOf<Boolean>()

        val result = runProtectedAction(
            onProcessingChanged = { loading = it; transitions += it },
            verifyCredential = { throw IllegalStateException("database unavailable") },
            action = { error("must not run") }
        )

        assertIs<ProtectedActionResult.Failure>(result)
        assertFalse(loading)
        assertEquals(listOf(true, false), transitions)
        assertTrue(result.error.message.orEmpty().contains("database unavailable"))
    }
}
