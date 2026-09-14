package ma.elaroui.pos.core.license

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensePublicKeyTest {

    @Test
    fun packagedPublicKey_isTheConfiguredEcKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val verifier = LicenseVerifier(context)
        val assetPem = context.assets.open("public_key.pem")
            .bufferedReader()
            .use { it.readText() }

        val assetKey = verifier.parsePublicKeyFromPem(assetPem)
        val fallbackKey = verifier.parsePublicKeyFromPem(
            LicenseVerifier.FALLBACK_PUBLIC_KEY_PEM
        )

        assertEquals("EC", assetKey.algorithm)
        assertArrayEquals(fallbackKey.encoded, assetKey.encoded)
    }
}
