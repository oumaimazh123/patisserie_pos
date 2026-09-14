package ma.elaroui.pos.presentation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ma.elaroui.pos.data.local.preferences.SetupPreferences
import ma.elaroui.pos.domain.license.LicenseManager
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.EnsureStarterDataUseCase
import ma.elaroui.pos.presentation.navigation.POSNavGraph
import ma.elaroui.pos.presentation.adaptive.ProvideAdaptiveLayout
import kotlinx.coroutines.launch
import javax.inject.Inject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainActivityEntryPoint {
    fun setupPreferences(): SetupPreferences
    fun sessionManager(): SessionManager
    fun licenseManager(): LicenseManager
    fun ensureStarterDataUseCase(): EnsureStarterDataUseCase
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var setupPreferences: SetupPreferences

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var licenseManager: LicenseManager

    @Inject
    lateinit var ensureStarterDataUseCase: EnsureStarterDataUseCase

    private val entryPoint: MainActivityEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, MainActivityEntryPoint::class.java)
    }

    private val safeSetupPreferences: SetupPreferences
        get() = if (::setupPreferences.isInitialized) setupPreferences else entryPoint.setupPreferences()

    private val safeSessionManager: SessionManager
        get() = if (::sessionManager.isInitialized) sessionManager else entryPoint.sessionManager()

    private val safeLicenseManager: LicenseManager
        get() = if (::licenseManager.isInitialized) licenseManager else entryPoint.licenseManager()

    private val safeEnsureStarterDataUseCase: EnsureStarterDataUseCase
        get() = if (::ensureStarterDataUseCase.isInitialized) {
            ensureStarterDataUseCase
        } else {
            entryPoint.ensureStarterDataUseCase()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveFullscreen()
        if (safeSetupPreferences.isSetupComplete) {
            lifecycleScope.launch {
                runCatching { safeEnsureStarterDataUseCase() }
            }
        }
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProvideAdaptiveLayout(Modifier.fillMaxSize()) {
                        POSNavGraph(
                            setupPreferences = safeSetupPreferences,
                            sessionManager = safeSessionManager,
                            licenseManager = safeLicenseManager
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveFullscreen()
        safeLicenseManager.refreshLicenseState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullscreen()
    }

    /**
     * Keeps the POS in immersive fullscreen while allowing the system bars to
     * be revealed temporarily with an edge swipe.
     */
    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
