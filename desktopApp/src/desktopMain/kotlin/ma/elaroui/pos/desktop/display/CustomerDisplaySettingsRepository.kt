package ma.elaroui.pos.desktop.display

import ma.elaroui.pos.shared.display.CustomerDisplayConfig
import ma.elaroui.pos.shared.display.VfdBaudRate
import ma.elaroui.pos.shared.display.VfdConnectionMode
import ma.elaroui.pos.shared.display.VfdDataBits
import ma.elaroui.pos.shared.display.VfdParity
import ma.elaroui.pos.shared.display.VfdProtocol
import ma.elaroui.pos.shared.display.VfdStopBits
import ma.elaroui.pos.shared.domain.AppSetting
import ma.elaroui.pos.shared.domain.SettingsRepository

object CustomerDisplaySettingsRepository {

    const val KEY_ENABLED = "customer_display_enabled"
    const val KEY_CONNECTION_MODE = "customer_display_connection_mode"
    const val KEY_PORT = "customer_display_port"
    const val KEY_PROTOCOL = "customer_display_protocol"
    const val KEY_BAUD_RATE = "customer_display_baud_rate"
    const val KEY_DATA_BITS = "customer_display_data_bits"
    const val KEY_STOP_BITS = "customer_display_stop_bits"
    const val KEY_PARITY = "customer_display_parity"
    const val KEY_COLUMNS = "customer_display_columns"
    const val KEY_WELCOME_LINE1 = "customer_display_welcome_line1"
    const val KEY_WELCOME_LINE2 = "customer_display_welcome_line2"
    const val KEY_THANKYOU_LINE1 = "customer_display_thankyou_line1"
    const val KEY_THANKYOU_LINE2 = "customer_display_thankyou_line2"
    const val KEY_THANKYOU_DURATION = "customer_display_thankyou_duration"

    suspend fun loadConfig(settingsRepo: SettingsRepository): CustomerDisplayConfig {
        val enabled = settingsRepo.get(KEY_ENABLED)?.toBooleanStrictOrNull() ?: false
        val mode = VfdConnectionMode.fromName(settingsRepo.get(KEY_CONNECTION_MODE))
        val port = settingsRepo.get(KEY_PORT).orEmpty()
        val protocol = VfdProtocol.fromName(settingsRepo.get(KEY_PROTOCOL))
        val baudRate = VfdBaudRate.fromInt(settingsRepo.get(KEY_BAUD_RATE)?.toIntOrNull())
        val dataBits = VfdDataBits.fromInt(settingsRepo.get(KEY_DATA_BITS)?.toIntOrNull())
        val stopBits = VfdStopBits.fromInt(settingsRepo.get(KEY_STOP_BITS)?.toIntOrNull())
        val parity = VfdParity.fromName(settingsRepo.get(KEY_PARITY))
        val columns = settingsRepo.get(KEY_COLUMNS)?.toIntOrNull() ?: 20
        val welcome1 = settingsRepo.get(KEY_WELCOME_LINE1)?.takeIf { it.isNotBlank() } ?: "BIENVENUE"
        val establishmentName = settingsRepo.get("establishment_name")?.trim().orEmpty()
        val storedWelcome2 = settingsRepo.get(KEY_WELCOME_LINE2)?.trim()
        val welcome2 = if (storedWelcome2.isNullOrBlank() || storedWelcome2 == "HYPER CAISSE") {
            establishmentName
        } else {
            storedWelcome2
        }
        val thankYou1 = settingsRepo.get(KEY_THANKYOU_LINE1) ?: "MERCI POUR VOTRE"
        val thankYou2 = settingsRepo.get(KEY_THANKYOU_LINE2) ?: "VISITE"
        val thankYouDuration = settingsRepo.get(KEY_THANKYOU_DURATION)?.toIntOrNull() ?: 5

        return CustomerDisplayConfig(
            enabled = enabled,
            connectionMode = mode,
            portName = port,
            protocol = protocol,
            baudRate = baudRate,
            dataBits = dataBits,
            stopBits = stopBits,
            parity = parity,
            columns = columns,
            rows = 2,
            welcomeLine1 = welcome1,
            welcomeLine2 = welcome2,
            thankYouLine1 = thankYou1,
            thankYouLine2 = thankYou2,
            thankYouDurationSeconds = thankYouDuration
        )
    }

    suspend fun saveConfig(settingsRepo: SettingsRepository, config: CustomerDisplayConfig) {
        settingsRepo.put(AppSetting(KEY_ENABLED, config.enabled.toString()))
        settingsRepo.put(AppSetting(KEY_CONNECTION_MODE, config.connectionMode.name))
        settingsRepo.put(AppSetting(KEY_PORT, config.portName))
        settingsRepo.put(AppSetting(KEY_PROTOCOL, config.protocol.name))
        settingsRepo.put(AppSetting(KEY_BAUD_RATE, config.baudRate.rate.toString()))
        settingsRepo.put(AppSetting(KEY_DATA_BITS, config.dataBits.bits.toString()))
        settingsRepo.put(AppSetting(KEY_STOP_BITS, config.stopBits.bits.toString()))
        settingsRepo.put(AppSetting(KEY_PARITY, config.parity.name))
        settingsRepo.put(AppSetting(KEY_COLUMNS, config.columns.toString()))
        settingsRepo.put(AppSetting(KEY_WELCOME_LINE1, config.welcomeLine1))
        settingsRepo.put(AppSetting(KEY_WELCOME_LINE2, config.welcomeLine2))
        settingsRepo.put(AppSetting(KEY_THANKYOU_LINE1, config.thankYouLine1))
        settingsRepo.put(AppSetting(KEY_THANKYOU_LINE2, config.thankYouLine2))
        settingsRepo.put(AppSetting(KEY_THANKYOU_DURATION, config.thankYouDurationSeconds.toString()))
    }
}
