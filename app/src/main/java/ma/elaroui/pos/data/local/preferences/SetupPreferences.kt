package ma.elaroui.pos.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SetupPreferences @Inject constructor(
    @ApplicationContext context: Context?
) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("pos_setup_prefs", Context.MODE_PRIVATE)

    open var isSetupComplete: Boolean
        get() = prefs?.getBoolean(KEY_IS_SETUP_COMPLETE, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_IS_SETUP_COMPLETE, value)?.apply() }

    open var selectedLanguage: String
        get() = prefs?.getString(KEY_LANGUAGE, "fr") ?: "fr"
        set(value) { prefs?.edit()?.putString(KEY_LANGUAGE, value)?.apply() }

    open var restaurantName: String
        get() = prefs?.getString(KEY_RESTAURANT_NAME, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_RESTAURANT_NAME, value)?.apply() }

    open var restaurantPhone: String
        get() = prefs?.getString(KEY_RESTAURANT_PHONE, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_RESTAURANT_PHONE, value)?.apply() }

    open var restaurantAddress: String
        get() = prefs?.getString(KEY_RESTAURANT_ADDRESS, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_RESTAURANT_ADDRESS, value)?.apply() }

    open var restaurantLogoUri: String
        get() = prefs?.getString(KEY_RESTAURANT_LOGO_URI, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_RESTAURANT_LOGO_URI, value)?.apply() }

    open var sellerIce: String
        get() = prefs?.getString(KEY_SELLER_ICE, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_SELLER_ICE, value)?.apply() }

    open var sellerTaxId: String
        get() = prefs?.getString(KEY_SELLER_TAX_ID, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_SELLER_TAX_ID, value)?.apply() }

    open var sellerCommercialRegister: String
        get() = prefs?.getString(KEY_SELLER_COMMERCIAL_REGISTER, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_SELLER_COMMERCIAL_REGISTER, value)?.apply() }

    open var sellerPatente: String
        get() = prefs?.getString(KEY_SELLER_PATENTE, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_SELLER_PATENTE, value)?.apply() }

    open var wifiName: String
        get() = prefs?.getString(KEY_WIFI_NAME, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_WIFI_NAME, value)?.apply() }

    open var wifiCode: String
        get() = prefs?.getString(KEY_WIFI_CODE, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_WIFI_CODE, value)?.apply() }

    open var currency: String
        get() = prefs?.getString(KEY_CURRENCY, "MAD") ?: "MAD"
        set(value) { prefs?.edit()?.putString(KEY_CURRENCY, value)?.apply() }

    open var defaultRegisterId: Long
        get() = prefs?.getLong(KEY_DEFAULT_REGISTER_ID, 1L) ?: 1L
        set(value) { prefs?.edit()?.putLong(KEY_DEFAULT_REGISTER_ID, value)?.apply() }

    open var cashOutApprovalPinHash: String
        get() = prefs?.getString(KEY_CASH_OUT_APPROVAL_PIN_HASH, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_CASH_OUT_APPROVAL_PIN_HASH, value)?.apply() }

    open fun resetPreferences() {
        prefs?.edit()?.clear()?.apply()
    }

    companion object {
        private const val KEY_IS_SETUP_COMPLETE = "is_setup_complete"
        private const val KEY_LANGUAGE = "selected_language"
        private const val KEY_RESTAURANT_NAME = "restaurant_name"
        private const val KEY_RESTAURANT_PHONE = "restaurant_phone"
        private const val KEY_RESTAURANT_ADDRESS = "restaurant_address"
        private const val KEY_RESTAURANT_LOGO_URI = "restaurant_logo_uri"
        private const val KEY_SELLER_ICE = "seller_ice"
        private const val KEY_SELLER_TAX_ID = "seller_tax_id"
        private const val KEY_SELLER_COMMERCIAL_REGISTER = "seller_commercial_register"
        private const val KEY_SELLER_PATENTE = "seller_patente"
        private const val KEY_WIFI_NAME = "wifi_name"
        private const val KEY_WIFI_CODE = "wifi_code"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_DEFAULT_REGISTER_ID = "default_register_id"
        private const val KEY_CASH_OUT_APPROVAL_PIN_HASH = "cash_out_approval_pin_hash"
    }
}
