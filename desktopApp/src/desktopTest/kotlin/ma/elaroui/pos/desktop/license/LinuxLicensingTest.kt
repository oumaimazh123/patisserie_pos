package ma.elaroui.pos.desktop.license

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxLicensingTest {
    @Test
    fun normalizationAndHashingAreDeterministicAndDoNotExposeRawIdentifiers() {
        assertEquals("abcdef-123456", DeviceFingerprintDerivation.normalize("  ABCDEF-123456 \n"))
        assertEquals(null, DeviceFingerprintDerivation.normalize("bad"))

        val first = DeviceFingerprintDerivation.linux(" ABCDEF123456 ", "550E8400-E29B-41D4-A716-446655440000", "unused")
        val second = DeviceFingerprintDerivation.linux("abcdef123456", "550e8400-e29b-41d4-a716-446655440000", "unused")

        assertEquals(first, second)
        assertTrue(first.matches(Regex("LINUX-[0-9A-F]{64}")))
        assertFalse(first.contains("abcdef", ignoreCase = true))
    }

    @Test
    fun missingMachineIdUsesProductUuidAndIgnoresFallbackSeed() {
        val first = DeviceFingerprintDerivation.linux(null, "550e8400-e29b-41d4-a716-446655440000", "fallback-one")
        val second = DeviceFingerprintDerivation.linux("bad", "550E8400-E29B-41D4-A716-446655440000", "fallback-two")
        assertEquals(first, second)
    }

    @Test
    fun missingSystemIdentifiersUsePersistentFallbackAcrossRestart() {
        val root = Files.createTempDirectory("linux-fingerprint-fallback-")
        try {
            val identifiers = FakeIdentifiers(null, null)
            val seedFile = root.resolve("secure/device-id.seed")
            val first = LinuxDeviceFingerprintProvider(identifiers, seedFile) { "11111111-2222-3333-4444-555555555555" }
            val second = LinuxDeviceFingerprintProvider(identifiers, seedFile) { "different-fallback-value" }

            assertEquals(first.getFingerprint(), second.getFingerprint())
            assertTrue(first.getFingerprint().startsWith("LINUX-"))
            assertTrue(Files.isRegularFile(seedFile))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun linuxLicenceSupportsValidWrongDeviceExpiredCorruptedAndRestartPersistence() {
        val root = Files.createTempDirectory("linux-license-flow-")
        try {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val publicPem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(pair.public.encoded)}\n-----END PUBLIC KEY-----"
            val fingerprint = DeviceFingerprintProvider { DeviceFingerprintDerivation.linux("abcdef1234567890", null, "unused") }
            val file = root.resolve("config/secure/license.dat")
            val store = LinuxEncryptedLicenseStore(file, fingerprint)
            var now = 1_000_000L
            val manager = WindowsLicenseManager(store, { now }, publicPem, fingerprint)

            assertEquals(WindowsLicenseStatus.TRIAL_ACTIVE, manager.state().status)
            val validToken = signedLicence(pair.private, fingerprint.getFingerprint(), now + 20_000L)
            assertEquals(WindowsLicenseStatus.VALID, manager.activate(validToken).status)

            val restarted = WindowsLicenseManager(LinuxEncryptedLicenseStore(file, fingerprint), { now }, publicPem, fingerprint)
            assertEquals(WindowsLicenseStatus.VALID, restarted.state().status)
            assertEquals(fingerprint.getFingerprint(), restarted.installationId)

            val wrongDevice = signedLicence(pair.private, "LINUX-${"0".repeat(64)}", now + 20_000L)
            assertEquals(WindowsLicenseStatus.WRONG_DEVICE, restarted.verify(wrongDevice).status)
            assertEquals(WindowsLicenseStatus.INVALID, restarted.verify("corrupted.licence").status)

            now += 30_000L
            assertEquals(WindowsLicenseStatus.EXPIRED, restarted.state().status)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun trialStartAndExpiryPersistAcrossRestartAndSettingsChanges() {
        val root = Files.createTempDirectory("linux-trial-")
        try {
            val fingerprint = DeviceFingerprintProvider { "LINUX-${"A".repeat(64)}" }
            val file = root.resolve("config/secure/license.dat")
            var now = 10_000_000L
            val first = WindowsLicenseManager(LinuxEncryptedLicenseStore(file, fingerprint), { now }, deviceFingerprintProvider = fingerprint)
            assertEquals(WindowsLicenseStatus.TRIAL_ACTIVE, first.state().status)

            // Unrelated application data does not participate in licence storage.
            Files.writeString(root.resolve("settings.db"), "changed-setting")
            now += WindowsLicenseManager.TRIAL_MS + 1
            val restarted = WindowsLicenseManager(LinuxEncryptedLicenseStore(file, fingerprint), { now }, deviceFingerprintProvider = fingerprint)
            assertEquals(WindowsLicenseStatus.TRIAL_EXPIRED, restarted.state().status)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun corruptedEncryptedStateFailsClosedWithoutThrowingFromManager() {
        val root = Files.createTempDirectory("linux-license-corrupt-")
        try {
            val fingerprint = DeviceFingerprintProvider { "LINUX-${"B".repeat(64)}" }
            val file = root.resolve("secure/license.dat")
            val manager = WindowsLicenseManager(LinuxEncryptedLicenseStore(file, fingerprint), deviceFingerprintProvider = fingerprint)
            assertEquals(WindowsLicenseStatus.TRIAL_ACTIVE, manager.state().status)
            Files.write(file, byteArrayOf(1, 2, 3, 4, 5))

            assertEquals(WindowsLicenseStatus.INVALID, manager.state().status)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun unavailableDeviceIdentifierFailsClosedWithoutCrashing() {
        val unavailable = DeviceFingerprintProvider { error("permission denied") }
        val store = object : DesktopLicenseStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = Unit
        }
        val manager = WindowsLicenseManager(store, deviceFingerprintProvider = unavailable)

        assertEquals(WindowsLicenseManager.UNAVAILABLE_DEVICE_ID, manager.installationId)
        assertEquals(WindowsLicenseStatus.INVALID, manager.verify("corrupted.licence").status)
    }

    private fun signedLicence(privateKey: java.security.PrivateKey, deviceId: String, expiry: Long): String {
        val json = """{"licenseId":"LINUX-TEST","productId":"GENERAL_POS_V1","appId":"ma.elaroui.generalpos","installationId":"$deviceId","customerName":"Atlas","issueDateMs":1,"expirationDateMs":$expiry,"licenseType":"SUBSCRIPTION","schemaVersion":"1.0"}"""
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(json.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8)) + "." +
            Base64.getEncoder().encodeToString(signer.sign())
    }

    private data class FakeIdentifiers(val machine: String?, val product: String?) : LinuxMachineIdentifierReader {
        override fun machineId() = machine
        override fun productUuid() = product
    }
}
