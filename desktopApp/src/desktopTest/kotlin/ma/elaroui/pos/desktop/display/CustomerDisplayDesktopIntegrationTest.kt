package ma.elaroui.pos.desktop.display

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.display.CustomerDisplayConfig
import ma.elaroui.pos.shared.display.VfdBaudRate
import ma.elaroui.pos.shared.display.VfdConnectionMode
import ma.elaroui.pos.shared.display.VfdDataBits
import ma.elaroui.pos.shared.display.VfdParity
import ma.elaroui.pos.shared.display.VfdProtocol
import ma.elaroui.pos.shared.display.VfdStopBits
import ma.elaroui.pos.shared.domain.AppSetting
import ma.elaroui.pos.shared.domain.SettingsRepository
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class CustomerDisplayDesktopIntegrationTest {

    private class InMemorySettingsRepository : SettingsRepository {
        private val map = mutableMapOf<String, String>()

        override suspend fun get(key: String): String? = map[key]

        override suspend fun put(setting: AppSetting) {
            map[setting.key] = setting.value
        }
    }

    @Test
    fun testSettingsRepositorySaveAndLoad() = runBlocking {
        val repo = InMemorySettingsRepository()

        val initialConfig = CustomerDisplayConfig(
            enabled = true,
            connectionMode = VfdConnectionMode.SERIAL,
            portName = "COM3",
            protocol = VfdProtocol.DSP800,
            baudRate = VfdBaudRate.B19200,
            dataBits = VfdDataBits.EIGHT,
            stopBits = VfdStopBits.ONE,
            parity = VfdParity.NONE,
            columns = 20,
            rows = 2,
            welcomeLine1 = "BIENVENUE SPECIAL",
            welcomeLine2 = "SUPER PATISSERIE",
            thankYouLine1 = "MERCI POUR VOTRE",
            thankYouLine2 = "FIDELITE",
            thankYouDurationSeconds = 7
        )

        CustomerDisplaySettingsRepository.saveConfig(repo, initialConfig)

        val loadedConfig = CustomerDisplaySettingsRepository.loadConfig(repo)

        assertEquals(initialConfig.enabled, loadedConfig.enabled)
        assertEquals(initialConfig.connectionMode, loadedConfig.connectionMode)
        assertEquals(initialConfig.portName, loadedConfig.portName)
        assertEquals(initialConfig.protocol, loadedConfig.protocol)
        assertEquals(initialConfig.baudRate, loadedConfig.baudRate)
        assertEquals(initialConfig.dataBits, loadedConfig.dataBits)
        assertEquals(initialConfig.stopBits, loadedConfig.stopBits)
        assertEquals(initialConfig.parity, loadedConfig.parity)
        assertEquals(initialConfig.columns, loadedConfig.columns)
        assertEquals(initialConfig.welcomeLine1, loadedConfig.welcomeLine1)
        assertEquals(initialConfig.welcomeLine2, loadedConfig.welcomeLine2)
        assertEquals(initialConfig.thankYouLine1, loadedConfig.thankYouLine1)
        assertEquals(initialConfig.thankYouLine2, loadedConfig.thankYouLine2)
        assertEquals(initialConfig.thankYouDurationSeconds, loadedConfig.thankYouDurationSeconds)
    }

    @Test
    fun testSettingsRepositoryDefaultsWhenEmpty() = runBlocking {
        val repo = InMemorySettingsRepository()
        val loadedConfig = CustomerDisplaySettingsRepository.loadConfig(repo)

        assertFalse(loadedConfig.enabled)
        assertEquals(VfdConnectionMode.SERIAL, loadedConfig.connectionMode)
        assertEquals(VfdProtocol.ESC_POS, loadedConfig.protocol)
        assertEquals(VfdBaudRate.B9600, loadedConfig.baudRate)
        assertEquals(20, loadedConfig.columns)
        assertEquals(2, loadedConfig.rows)
        assertEquals("BIENVENUE", loadedConfig.welcomeLine1)
        assertEquals("", loadedConfig.welcomeLine2)
        assertEquals("MERCI POUR VOTRE", loadedConfig.thankYouLine1)
        assertEquals("VISITE", loadedConfig.thankYouLine2)
        assertEquals(5, loadedConfig.thankYouDurationSeconds)
    }

    @Test
    fun testSettingsRepositoryLoadsDynamicEstablishmentName() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.put(AppSetting("establishment_name", "Pâtisserie Étoile"))

        val loadedConfig = CustomerDisplaySettingsRepository.loadConfig(repo)
        assertEquals("Pâtisserie Étoile", loadedConfig.welcomeLine2)

        // Legacy "HYPER CAISSE" setting is automatically replaced with establishment name
        repo.put(AppSetting("customer_display_welcome_line2", "HYPER CAISSE"))
        val legacyMigratedConfig = CustomerDisplaySettingsRepository.loadConfig(repo)
        assertEquals("Pâtisserie Étoile", legacyMigratedConfig.welcomeLine2)

        // Custom non-default welcome line 2 is preserved
        repo.put(AppSetting("customer_display_welcome_line2", "Mon Message Personnalisé"))
        val customConfig = CustomerDisplaySettingsRepository.loadConfig(repo)
        assertEquals("Mon Message Personnalisé", customConfig.welcomeLine2)
    }

    @Test
    fun testCustomerDisplayDeviceDetectorDoesNotCrash() {
        val devices = CustomerDisplayDeviceDetector.detectDevices()
        assertNotNull(devices)
        assertTrue(devices.isNotEmpty())
    }

    @Test
    fun testDesktopSerialTransportWithUnavailablePortFailsGracefully() {
        val config = CustomerDisplayConfig(
            enabled = true,
            portName = "COM99_NON_EXISTENT",
            baudRate = VfdBaudRate.B9600
        )
        val transport = DesktopSerialVfdTransport()

        val connectResult = transport.connect(config)
        assertTrue(connectResult.isFailure)
        assertFalse(transport.isConnected)

        // Sending bytes to closed port must return failure and not throw an uncaught exception
        val writeResult = transport.send(byteArrayOf(0x0C))
        assertTrue(writeResult.isFailure)

        transport.disconnect()
        assertFalse(transport.isConnected)
    }

    @Test
    fun testDigitalCustomerDisplayEndToEndWithVirtualDevice() {
        runBlocking {
        val tempDir = Files.createTempDirectory("vfd-digital-test")
        val vfdDeviceFile = tempDir.resolve("virtual_customer_display.bin").toFile()
        vfdDeviceFile.createNewFile()
        assertTrue(vfdDeviceFile.exists())

        val config = CustomerDisplayConfig(
            enabled = true,
            portName = vfdDeviceFile.absolutePath,
            protocol = VfdProtocol.ESC_POS,
            columns = 20,
            rows = 2,
            welcomeLine1 = "BIENVENUE",
            welcomeLine2 = "PATISSERIE POS",
            thankYouLine1 = "MERCI POUR VOTRE",
            thankYouLine2 = "VISITE"
        )

        val transport = DesktopSerialVfdTransport()
        val connectResult = transport.connect(config)
        assertTrue(connectResult.isSuccess, "Digital customer display connection must succeed")
        assertTrue(transport.isConnected)

        val controller = ma.elaroui.pos.shared.display.CustomerDisplayController(
            initialConfig = config,
            transport = transport,
            scope = this
        )

        // 1. Initial / Idle state
        kotlinx.coroutines.delay(100)
        controller.showIdle()
        kotlinx.coroutines.delay(100)
        assertTrue(vfdDeviceFile.length() > 0L, "Bytes must be written to digital customer display device")

        // 2. Cart updated during sale
        controller.updateCart(totalCentimes = 4500L)
        kotlinx.coroutines.delay(100)
        val sizeAfterCart = vfdDeviceFile.length()
        assertTrue(sizeAfterCart > 0L)

        // 3. Payment started
        controller.paymentStarted(totalCentimes = 4500L)
        kotlinx.coroutines.delay(100)
        val sizeAfterPayment = vfdDeviceFile.length()
        assertTrue(sizeAfterPayment >= sizeAfterCart)

        // 4. Cash received with change
        controller.cashReceived(receivedCentimes = 5000L, totalCentimes = 4500L)
        kotlinx.coroutines.delay(100)
        val sizeAfterCash = vfdDeviceFile.length()
        assertTrue(sizeAfterCash >= sizeAfterPayment)

        // 5. Payment completed
        controller.paymentCompleted()
        kotlinx.coroutines.delay(100)
        val sizeAfterThankYou = vfdDeviceFile.length()
        assertTrue(sizeAfterThankYou >= sizeAfterCash)

        controller.shutdown()
        kotlinx.coroutines.delay(50)
        assertFalse(transport.isConnected)

        // Cleanup
        vfdDeviceFile.delete()
        }
    }

    @Test
    fun testVirtualCustomerDisplayDeviceDetectionInCI() {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val vfdFile = File(tempDir, "virtual_customer_display.bin")
        val created = vfdFile.createNewFile()
        try {
            val detected = CustomerDisplayDeviceDetector.detectDevices()
            val virtualDevice = detected.find { it.portName.contains("virtual_customer_display.bin") }
            assertNotNull(virtualDevice, "Virtual digital customer display must be discovered when file exists")
            assertTrue(virtualDevice.isUsb)
            assertTrue(virtualDevice.description.contains("Digital") || virtualDevice.description.contains("Virtuel"))
        } finally {
            if (created) vfdFile.delete()
        }
    }

    @Test
    fun testAllProtocolsSupportedOnDigitalCustomerDisplay() {
        val tempFile = File.createTempFile("vfd_proto_", ".bin")
        try {
            val protocols = listOf(VfdProtocol.ESC_POS, VfdProtocol.DSP800, VfdProtocol.CD5220, VfdProtocol.UTC_STANDARD, VfdProtocol.PLAIN_TEXT)
            for (proto in protocols) {
                val config = CustomerDisplayConfig(
                    enabled = true,
                    portName = tempFile.absolutePath,
                    protocol = proto
                )
                val transport = DesktopSerialVfdTransport()
                assertTrue(transport.connect(config).isSuccess)
                assertTrue(transport.isConnected)

                // Send test clear and display bytes
                val sendResult = transport.send("TEST DIGITAL $proto\n".toByteArray(Charsets.US_ASCII))
                assertTrue(sendResult.isSuccess)
                transport.disconnect()
            }
            assertTrue(tempFile.length() > 0L)
        } finally {
            tempFile.delete()
        }
    }
}
