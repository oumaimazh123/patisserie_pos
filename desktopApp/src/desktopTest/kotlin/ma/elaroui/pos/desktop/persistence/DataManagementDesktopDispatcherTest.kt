package ma.elaroui.pos.desktop.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataManagementDesktopDispatcherTest {

    @Test
    fun testMainDispatcherIsAvailableOnDesktopClasspath() {
        // Must not throw IllegalStateException: Module with the Main dispatcher is missing
        val mainDispatcher = Dispatchers.Main
        assertNotNull(mainDispatcher, "Dispatchers.Main must be resolved via kotlinx-coroutines-swing")

        val immediateDispatcher = Dispatchers.Main.immediate
        assertNotNull(immediateDispatcher, "Dispatchers.Main.immediate must be resolved")
    }

    @Test
    fun testDataManagementOperationsOnDispatchersIOAndMain() {
        runBlocking {
            WindowsPosDatabase.openInMemory().use { db ->
                // Simulates opening DataManagementScreen and executing initial state verification
                val isValid = withContext(Dispatchers.IO) {
                    db.setAdminDeletionPassword("DispatcherTestPassword2026!")
                    db.verifyAdminDeletionPassword("DispatcherTestPassword2026!")
                }
                assertTrue(isValid)

                // Switch to Main context simulation (ensures no dispatcher resolution runtime exception)
                withContext(Dispatchers.Main) {
                    val ownerUser = withContext(Dispatchers.IO) {
                        db.users.observeActive().first().firstOrNull()
                    }
                    assertNotNull(ownerUser)
                }
            }
        }
    }
}
