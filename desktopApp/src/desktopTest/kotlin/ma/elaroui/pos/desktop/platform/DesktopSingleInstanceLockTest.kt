package ma.elaroui.pos.desktop.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopSingleInstanceLockTest {
    @Test
    fun `only one lock can own an application directory`() {
        val directory = Files.createTempDirectory("pos-single-instance-")
        val lockFile = directory.resolve("application.lock")
        val first = DesktopSingleInstanceLock.acquire(lockFile)
        assertNotNull(first)
        try {
            assertNull(DesktopSingleInstanceLock.acquire(lockFile))
        } finally {
            first.close()
        }
        DesktopSingleInstanceLock.acquire(lockFile)?.close()
            ?: error("Lock must be reusable after the first process exits")
    }
}
