package ma.elaroui.pos.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LicensePreferences @Inject constructor(
    @ApplicationContext context: Context?
) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    open var firstLaunchTimestamp: Long
        get() = prefs?.getLong(KEY_FIRST_LAUNCH_TS, 0L) ?: 0L
        set(value) { prefs?.edit()?.putLong(KEY_FIRST_LAUNCH_TS, value)?.apply() }

    open var lastTrustedTimestamp: Long
        get() = prefs?.getLong(KEY_LAST_TRUSTED_TS, 0L) ?: 0L
        set(value) { prefs?.edit()?.putLong(KEY_LAST_TRUSTED_TS, value)?.apply() }

    open var activatedLicenseString: String?
        get() = prefs?.getString(KEY_ACTIVATED_LICENSE, null)
        set(value) { prefs?.edit()?.putString(KEY_ACTIVATED_LICENSE, value)?.apply() }

    open fun clearLicense() {
        prefs?.edit()?.remove(KEY_ACTIVATED_LICENSE)?.apply()
    }

    companion object {
        private const val PREF_NAME = "pos_license_prefs"
        private const val KEY_FIRST_LAUNCH_TS = "first_launch_ts"
        private const val KEY_LAST_TRUSTED_TS = "last_trusted_ts"
        private const val KEY_ACTIVATED_LICENSE = "activated_license"
    }
}
