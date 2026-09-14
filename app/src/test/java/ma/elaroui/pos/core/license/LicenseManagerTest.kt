package ma.elaroui.pos.core.license

import ma.elaroui.pos.data.local.preferences.LicensePreferences
import ma.elaroui.pos.domain.license.LicenseManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseManagerTest {

    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey
    private lateinit var verifier: LicenseVerifier

    private val testInstallationId = "INST-A1B2-C3D4-E5F6"

    @Before
    fun setUp() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()
        privateKey = keyPair.private
        publicKey = keyPair.public

        verifier = LicenseVerifier(null)
        verifier.customPublicKey = publicKey
    }

    private fun createSignedLicenseString(
        licenseId: String = "LIC-1001",
        productId: String = "GENERAL_POS_V1",
        appId: String = "ma.elaroui.generalpos",
        installationId: String = testInstallationId,
        customerName: String = "Café Test",
        issueDateMs: Long = System.currentTimeMillis(),
        expirationDateMs: Long? = null,
        licenseType: LicenseType = LicenseType.FULL_LIFETIME,
        schemaVersion: String = "1.0",
        customSignature: ByteArray? = null
    ): String {
        val expJson = if (expirationDateMs != null) ",\"expirationDateMs\":$expirationDateMs" else ""
        val payloadJson = "{\"licenseId\":\"$licenseId\",\"productId\":\"$productId\",\"appId\":\"$appId\",\"installationId\":\"$installationId\",\"customerName\":\"$customerName\",\"issueDateMs\":$issueDateMs$expJson,\"licenseType\":\"${licenseType.name}\",\"schemaVersion\":\"$schemaVersion\"}"

        val sigBytes = customSignature ?: run {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initSign(privateKey)
            sig.update(payloadJson.toByteArray(Charsets.UTF_8))
            sig.sign()
        }

        val payloadB64 = Base64.getEncoder().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        val sigB64 = Base64.getEncoder().encodeToString(sigBytes)

        return "$payloadB64.$sigB64"
    }

    @Test
    fun verifyLicense_validSignature_returnsValidStatus() {
        val raw = createSignedLicenseString()
        val (status, payload) = verifier.verifyLicenseString(raw, testInstallationId)

        assertEquals(LicenseStatus.LICENCE_VALID, status)
        assertNotNull(payload)
        assertEquals("Café Test", payload?.customerName)
        assertEquals(testInstallationId, payload?.installationId)
    }

    @Test
    fun verifyLicense_tamperedPayload_returnsInvalidStatus() {
        val validRaw = createSignedLicenseString(customerName = "Café Original")
        val parts = validRaw.split(".")
        
        // Tamper payload JSON to change customerName
        val tamperedJson = "{\"licenseId\":\"LIC-1001\",\"productId\":\"GENERAL_POS_V1\",\"appId\":\"ma.elaroui.generalpos\",\"installationId\":\"$testInstallationId\",\"customerName\":\"Hacker Café\",\"issueDateMs\":${System.currentTimeMillis()},\"licenseType\":\"FULL_LIFETIME\",\"schemaVersion\":\"1.0\"}"

        val tamperedB64 = Base64.getEncoder().encodeToString(tamperedJson.toByteArray(Charsets.UTF_8))
        val tamperedLicense = "$tamperedB64.${parts[1]}"

        val (status, payload) = verifier.verifyLicenseString(tamperedLicense, testInstallationId)
        assertEquals(LicenseStatus.LICENCE_INVALID, status)
        assertNull(payload)
    }

    @Test
    fun verifyLicense_wrongInstallationId_returnsWrongDeviceStatus() {
        val raw = createSignedLicenseString(installationId = "INST-OTHER-DEVICE-9999")
        val (status, payload) = verifier.verifyLicenseString(raw, testInstallationId)

        assertEquals(LicenseStatus.LICENCE_WRONG_DEVICE, status)
        assertNotNull(payload)
    }

    @Test
    fun verifyLicense_expiredSubscription_returnsExpiredStatus() {
        val pastDate = System.currentTimeMillis() - (24 * 60 * 60 * 1000L) // Yesterday
        val raw = createSignedLicenseString(expirationDateMs = pastDate, licenseType = LicenseType.SUBSCRIPTION)
        val (status, payload) = verifier.verifyLicenseString(raw, testInstallationId)

        assertEquals(LicenseStatus.LICENCE_EXPIRED, status)
        assertNotNull(payload)
    }

    @Test
    fun trialPeriod_newInstallation_startsSevenDayTrial() {
        val fakePrefs = FakeLicensePreferences()
        val fakeInstManager = FakeInstallationIdManager(testInstallationId)
        val manager = LicenseManager(fakeInstManager, fakePrefs, verifier)

        val state = manager.refreshLicenseState()
        assertEquals(LicenseStatus.TRIAL_ACTIVE, state.status)
        assertTrue(state.remainingTrialTimeMs > 0)
        assertTrue(state.remainingTrialTimeMs <= LicenseVerifier.TRIAL_DURATION_MS)
        assertEquals(7 * 24 * 60 * 60 * 1000L, LicenseVerifier.TRIAL_DURATION_MS)
    }

    @Test
    fun trialPeriod_onDaySix_remainsUsableAndWarnsThatExpiryIsSoon() {
        val fakePrefs = FakeLicensePreferences()
        val now = System.currentTimeMillis()
        fakePrefs.firstLaunchTimestamp = now - (6 * 24 * 60 * 60 * 1000L)
        fakePrefs.lastTrustedTimestamp = now

        val manager = LicenseManager(
            FakeInstallationIdManager(testInstallationId),
            fakePrefs,
            verifier
        )

        val state = manager.refreshLicenseState()

        assertEquals(LicenseStatus.TRIAL_EXPIRING_SOON, state.status)
        assertTrue(state.remainingTrialTimeMs > 0)
    }

    @Test
    fun trialPeriod_pastSevenDays_expiresWithoutResettingTrialData() {
        val fakePrefs = FakeLicensePreferences()
        val now = System.currentTimeMillis()
        val originalFirstLaunch = now - (8 * 24 * 60 * 60 * 1000L)
        fakePrefs.firstLaunchTimestamp = originalFirstLaunch
        fakePrefs.lastTrustedTimestamp = now

        val fakeInstManager = FakeInstallationIdManager(testInstallationId)
        val manager = LicenseManager(fakeInstManager, fakePrefs, verifier)

        val state = manager.refreshLicenseState()
        assertEquals(LicenseStatus.TRIAL_EXPIRED, state.status)
        assertEquals(0L, state.remainingTrialTimeMs)
        assertEquals(originalFirstLaunch, fakePrefs.firstLaunchTimestamp)
        assertTrue(state.errorMessage?.contains("conservées", ignoreCase = true) == true)
    }

    @Test
    fun clockRollback_detectedWhenTimeMovedBackwards() {
        val fakePrefs = FakeLicensePreferences()
        val now = System.currentTimeMillis()
        fakePrefs.lastTrustedTimestamp = now + (10 * 60 * 60 * 1000L) // 10 hours in future

        val fakeInstManager = FakeInstallationIdManager(testInstallationId)
        val manager = LicenseManager(fakeInstManager, fakePrefs, verifier)

        val state = manager.refreshLicenseState()
        assertEquals(LicenseStatus.CLOCK_ROLLBACK_DETECTED, state.status)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun embeddedPublicKey_matchesConfiguredEcAsset() {
        val assetFile = java.io.File("app/src/main/assets/public_key.pem").takeIf { it.isFile }
            ?: java.io.File("src/main/assets/public_key.pem")
        assertTrue("The packaged public-key asset must exist.", assetFile.isFile)

        val assetPublicKey = verifier.parsePublicKeyFromPem(assetFile.readText())
        val fallbackPublicKey = verifier.parsePublicKeyFromPem(
            LicenseVerifier.FALLBACK_PUBLIC_KEY_PEM
        )

        assertEquals("EC", assetPublicKey.algorithm)
        assertArrayEquals(assetPublicKey.encoded, fallbackPublicKey.encoded)
    }

    @Test
    fun activateLicense_validEcSignature_persistsLicense() {
        val raw = createSignedLicenseString(customerName = "Restaurant Activé")
        val fakePrefs = FakeLicensePreferences()
        val manager = LicenseManager(
            FakeInstallationIdManager(testInstallationId),
            fakePrefs,
            verifier
        )

        val state = manager.activateLicense(raw)

        assertEquals(LicenseStatus.LICENCE_VALID, state.status)
        assertEquals("Restaurant Activé", state.customerName)
        assertEquals(raw, fakePrefs.activatedLicenseString)
    }

    @Test
    fun activateLicense_invalidSignature_returnsClearFailureWithoutPersisting() {
        val fakePrefs = FakeLicensePreferences()
        val manager = LicenseManager(
            FakeInstallationIdManager(testInstallationId),
            fakePrefs,
            verifier
        )

        val state = manager.activateLicense("not-a-valid-license")

        assertEquals(LicenseStatus.LICENCE_INVALID, state.status)
        assertTrue(state.errorMessage?.contains("signature", ignoreCase = true) == true)
        assertNull(fakePrefs.activatedLicenseString)
    }

    @Test
    fun storedCorruptedLicense_isInvalidAndDoesNotFallBackToTrial() {
        val fakePrefs = FakeLicensePreferences().apply {
            activatedLicenseString = "corrupted-stored-license"
            firstLaunchTimestamp = System.currentTimeMillis()
        }
        val manager = LicenseManager(
            FakeInstallationIdManager(testInstallationId),
            fakePrefs,
            verifier
        )

        val state = manager.refreshLicenseState()

        assertEquals(LicenseStatus.LICENCE_INVALID, state.status)
        assertEquals(0L, state.remainingTrialTimeMs)
        assertNotNull(state.errorMessage)
    }
}

private class FakeInstallationIdManager(private val id: String) : InstallationIdManager(null) {
    override fun getInstallationId(): String = id
}

private class FakeLicensePreferences : LicensePreferences(null) {
    override var firstLaunchTimestamp: Long = 0L
    override var lastTrustedTimestamp: Long = 0L
    override var activatedLicenseString: String? = null
}
