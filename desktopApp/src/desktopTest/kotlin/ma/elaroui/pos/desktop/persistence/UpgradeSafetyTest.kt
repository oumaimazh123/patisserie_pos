package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.DesktopBuildInfo
import ma.elaroui.pos.desktop.license.DesktopLicenseStoreFactory
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.shared.domain.AppSetting
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.domain.UserRole

/**
 * Validation suite ensuring database data, printer configurations,
 * license credentials, and settings survive application version upgrades intact.
 */
class UpgradeSafetyTest {

    @Test
    fun `windows upgrade uuid remains stable across versions for in-place installer upgrade`() {
        assertEquals("46788696-3912-4d20-9f7d-220b62935d31", DesktopBuildInfo.WINDOWS_UPGRADE_UUID)
    }

    @Test
    fun `existing database, settings, and users persist seamlessly across version restarts`() = runBlocking {
        val tempDir = Files.createTempDirectory("upgrade-safety-test")
        val dbPath = tempDir.resolve("pos.db")

        // 1. Simulate v1.0.1 installation and configuration
        WindowsPosDatabase.open(dbPath).use { db ->
            db.configureInitialSetup("Café Belle Vue", "Owner Brahim", "1234")
            db.createCashier("Caissière Fatima", "5678")

            // Configure printers and store settings
            db.settings.put(AppSetting("customer_printer", "EPSON TM-T20III"))
            db.settings.put(AppSetting("kitchen_printer", "EPSON TM-T88VI"))
            db.settings.put(AppSetting("printer_width", "80"))
            db.settings.put(AppSetting("cash_drawer_enabled", "true"))
            db.settings.put(AppSetting("selected_language", "fr"))
            db.settings.put(AppSetting("seller_ice", "001234567890001"))

            // Create catalog
            val catId = db.categories.save(Category(id = 0, name = "Boissons Chaudes", active = true, displayOrder = 1))
            db.products.save(
                Product(
                    id = 0,
                    categoryId = catId,
                    name = "Café Expresso",
                    priceCentimes = 1400L,
                    taxRateBasisPoints = 2000,
                    active = true,
                    available = true
                )
            )
        }

        // 2. Simulate v1.0.2 upgrade opening the same data directory
        WindowsPosDatabase.open(dbPath).use { upgradedDb ->
            // Verify setup status
            assertEquals("true", upgradedDb.settings.get("setup_complete"))
            assertEquals("Café Belle Vue", upgradedDb.settings.get("establishment_name"))
            assertEquals("001234567890001", upgradedDb.settings.get("seller_ice"))

            // Verify printer configurations remain intact
            assertEquals("EPSON TM-T20III", upgradedDb.settings.get("customer_printer"))
            assertEquals("EPSON TM-T88VI", upgradedDb.settings.get("kitchen_printer"))
            assertEquals("80", upgradedDb.settings.get("printer_width"))
            assertEquals("true", upgradedDb.settings.get("cash_drawer_enabled"))
            assertEquals("fr", upgradedDb.settings.get("selected_language"))

            // Verify authentication & roles
            val owner = upgradedDb.authentication.authenticate("1234")
            assertNotNull(owner)
            assertEquals("Owner Brahim", owner.name)
            assertEquals(UserRole.OWNER, owner.role)

            val cashier = upgradedDb.authentication.authenticate("5678")
            assertNotNull(cashier)
            assertEquals("Caissière Fatima", cashier.name)
            assertEquals(UserRole.CASHIER, cashier.role)

            // Verify catalogue data
            val categories = upgradedDb.categories.observeAll().first()
            assertEquals(1, categories.size)
            assertEquals("Boissons Chaudes", categories.first().name)

            val products = upgradedDb.products.observeAll().first()
            assertEquals(1, products.size)
            assertEquals("Café Expresso", products.first().name)
            assertEquals(1400L, products.first().priceCentimes)
        }
    }

    @Test
    fun `encrypted license store survives upgrade intact`() {
        val root = Files.createTempDirectory("upgrade-license-test")
        val licenseFile = root.resolve("secure/license.dat")

        // 1. Save license in previous version
        val previousStore = DesktopLicenseStoreFactory.create(DesktopPlatform.LINUX, licenseFile)
        previousStore.put("license_token", "SIGNED-JWT-PAYLOAD-V1")
        previousStore.put("installation_id", "HW-NODE-XYZ-99")

        // 2. Re-open in updated version
        val updatedStore = DesktopLicenseStoreFactory.create(DesktopPlatform.LINUX, licenseFile)
        assertEquals("SIGNED-JWT-PAYLOAD-V1", updatedStore.get("license_token"))
        assertEquals("HW-NODE-XYZ-99", updatedStore.get("installation_id"))
    }
}
