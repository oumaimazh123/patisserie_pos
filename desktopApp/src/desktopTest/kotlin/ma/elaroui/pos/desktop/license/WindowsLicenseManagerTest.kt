package ma.elaroui.pos.desktop.license

import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*

class WindowsLicenseManagerTest {
    @Test fun trialDeviceBindingActivationExpirationAndPersistence() {
        val dir=Files.createTempDirectory("win-license");var now=1_000_000L;val store=WindowsSecureStore(dir.resolve("license.dat"));val pair=KeyPairGenerator.getInstance("EC").apply{initialize(256)}.generateKeyPair();val pem="-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(pair.public.encoded)}\n-----END PUBLIC KEY-----";val manager=WindowsLicenseManager(store,{now},pem)
        assertEquals(WindowsLicenseStatus.TRIAL_ACTIVE,manager.state().status);val id=manager.installationId
        fun license(device:String,expiry:Long):String { val json="{\"licenseId\":\"L1\",\"productId\":\"GENERAL_POS_V1\",\"appId\":\"ma.elaroui.generalpos\",\"installationId\":\"$device\",\"customerName\":\"Atlas\",\"issueDateMs\":1,\"expirationDateMs\":$expiry,\"licenseType\":\"SUBSCRIPTION\",\"schemaVersion\":\"1.0\"}";val signer=Signature.getInstance("SHA256withECDSA");signer.initSign(pair.private);signer.update(json.toByteArray());return Base64.getEncoder().encodeToString(json.toByteArray())+"."+Base64.getEncoder().encodeToString(signer.sign()) }
        assertEquals(WindowsLicenseStatus.WRONG_DEVICE,manager.activate(license("OTHER",now+1000)).status)
        assertEquals(WindowsLicenseStatus.VALID,manager.activate(license(id,now+1000)).status)
        assertEquals(WindowsLicenseStatus.VALID,WindowsLicenseManager(store,{now},pem).state().status)
        now+=2000;assertEquals(WindowsLicenseStatus.EXPIRED,manager.state().status)
    }

    @Test fun trialRestartCorruptionAndClockRollbackAreHandled() {
        val dir = Files.createTempDirectory("win-license-edges")
        var now = 10_000_000L
        val store = WindowsSecureStore(dir.resolve("license.dat"))
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val pem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(pair.public.encoded)}\n-----END PUBLIC KEY-----"

        val first = WindowsLicenseManager(store, { now }, pem)
        assertEquals(WindowsLicenseStatus.TRIAL_ACTIVE, first.state().status)
        assertEquals(7, first.state().daysRemaining)
        assertEquals(first.installationId, WindowsLicenseManager(store, { now }, pem).installationId)

        now += WindowsLicenseManager.TRIAL_MS + 1
        assertEquals(WindowsLicenseStatus.TRIAL_EXPIRED, first.state().status)

        store.put("license", "corrupted-license")
        assertEquals(WindowsLicenseStatus.INVALID, first.state().status)

        store.put("license", "")
        store.put("last_trusted", (now + 600_000).toString())
        assertEquals(WindowsLicenseStatus.CLOCK_ROLLBACK, first.state().status)
    }

    @Test
    fun generateRealLicenseForCurrentInstallation() {
        val privPem = """-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgyFiEDq2z5INCmgmJ
8UGgx01M/kP/aVgc+bE5ekUN6wahRANCAAQJWalPPYUCLTJ3QXw7gDsKQ7/XSoPk
fJnH/FPndlogioEOca/gUmVZlwOkEY9VUgk8Pyr+d5Uc/lahMUHWkICb
-----END PRIVATE KEY-----"""
        val privBytes = Base64.getDecoder().decode(
            privPem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
        )
        val kf = java.security.KeyFactory.getInstance("EC")
        val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privBytes))

        val installationId = "WIN-3AC2B194-6FF1-44B8-8F36-EC1AF18A0B98"
        val customerName = "Café Restaurant Elite (Version Complète)"
        val issueDateMs = System.currentTimeMillis()
        val licenseId = "LIC-LIFETIME-${System.currentTimeMillis()}"

        val json = """{"licenseId":"$licenseId","productId":"GENERAL_POS_V1","appId":"ma.elaroui.generalpos","installationId":"$installationId","customerName":"$customerName","issueDateMs":$issueDateMs,"licenseType":"FULL","schemaVersion":"1.0"}"""

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privKey)
        signer.update(json.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        val signatureBytes = signer.sign()

        val token = Base64.getEncoder().encodeToString(json.toByteArray(java.nio.charset.StandardCharsets.UTF_8)) + "." +
                Base64.getEncoder().encodeToString(signatureBytes)

        val tempDir = Files.createTempDirectory("test-lic-gen")
        val store = WindowsSecureStore(tempDir.resolve("license.dat"))
        store.put("installation_id", installationId)
        val mgr = WindowsLicenseManager(store)
        val state = mgr.activate(token)

        println("GENERATED_LIFETIME_TOKEN: $token")
        println("VERIFY_STATUS: ${state.status}")
        println("CUSTOMER: ${state.customer}")

        assertEquals(WindowsLicenseStatus.VALID, state.status)

        val outDir = java.nio.file.Path.of("D:\\POS-Licensing\\licences")
        if (Files.exists(outDir)) {
            Files.writeString(outDir.resolve("licence-$installationId.txt"), token)
            Files.writeString(outDir.resolve("licence-$installationId-lifetime.txt"), token)
        }
    }
}
