package ma.elaroui.pos.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformServicesTest {
    @Test
    fun clockIsDeterministicAndDiagnosticConsumesUuidPlatformAndLocale() {
        val logger = FakeLogger()
        val diagnostic = CollectPlatformDiagnostic(
            clock = FakeClock(1_725_000_000_000),
            directories = FakeDirectories(),
            platformInformation = FakePlatformInformation(),
            localeInformation = FakeLocale("ar-MA"),
            uuidGenerator = SequenceUuid("diagnostic-1"),
            logger = logger
        ).execute()

        assertEquals(1_725_000_000_000, diagnostic.capturedAt.value)
        assertEquals("diagnostic-1", diagnostic.correlationId)
        assertEquals(PosPlatform.WINDOWS, diagnostic.platform.platform)
        assertEquals("ar-MA", diagnostic.locale.languageTag)
        assertTrue(diagnostic.locale.isRightToLeft)
        assertEquals(LogLevel.INFO, logger.entries.single().level)
    }

    @Test
    fun preferencesSupportDefaultsWritesAndDeletion() {
        val preferences = FakePreferences()
        assertEquals("fr", preferences.getString("language", "fr"))
        assertEquals(7, preferences.getLong("days", 7))
        assertFalse(preferences.getBoolean("setup"))

        preferences.putString("language", "ar")
        preferences.putLong("days", 10)
        preferences.putBoolean("setup", true)
        assertEquals("ar", preferences.getString("language"))
        assertEquals(10, preferences.getLong("days"))
        assertTrue(preferences.getBoolean("setup"))
        assertTrue(preferences.contains("setup"))

        preferences.remove("setup")
        assertFalse(preferences.contains("setup"))
        assertFalse(preferences.getBoolean("setup"))
    }

    @Test
    fun secureStorageSupportsPutReadAndRemovalWithoutEnumeration() {
        val storage = FakeSecureStorage()
        assertNull(storage.get("token"))
        storage.put("token", "secret")
        assertTrue(storage.contains("token"))
        assertEquals("secret", storage.get("token"))
        storage.remove("token")
        assertFalse(storage.contains("token"))
    }

    @Test
    fun pathsAndMetadataRemainPlatformNeutralAndValidated() {
        assertFailsWith<IllegalArgumentException> { PlatformPath.parse(" ") }
        assertFailsWith<IllegalArgumentException> { PlatformPath.parse("bad\u0000path") }
        val path = PlatformPath.parse("C:/temporary/report.backup")
        val metadata = FileMetadata(path, "report.backup", false, 42, EpochMilliseconds(100))
        assertEquals("report.backup", metadata.name)
        assertEquals(42, metadata.sizeBytes)
    }

    @Test
    fun loggerRetainsLevelCategoryAndFailureMessage() {
        val logger = FakeLogger()
        logger.debug("start")
        logger.error("failed", IllegalStateException("disk full"), "Backup")
        assertEquals(LogLevel.DEBUG, logger.entries[0].level)
        assertEquals("Backup", logger.entries[1].category)
        assertEquals("disk full", logger.entries[1].throwableMessage)
    }

    private class FakeClock(private val value: Long) : Clock {
        override fun now() = EpochMilliseconds(value)
    }

    private class FakePreferences : ApplicationPreferences {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, default: String) = values[key] as? String ?: default
        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun putString(key: String, value: String) { values[key] = value }
        override fun putLong(key: String, value: Long) { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun contains(key: String) = key in values
    }

    private class FakeSecureStorage : SecureKeyValueStorage {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun contains(key: String) = key in values
    }

    private class FakeLogger : PlatformLogger {
        val entries = mutableListOf<LogEntry>()
        override fun log(entry: LogEntry) { entries += entry }
    }

    private class FakeDirectories : ApplicationDirectoriesProvider {
        override fun directories() = ApplicationDirectories(
            PlatformPath.parse("test/data"),
            PlatformPath.parse("test/cache"),
            PlatformPath.parse("test/temp")
        )
    }

    private class FakePlatformInformation : PlatformInformationProvider {
        override fun current() = PlatformInformation(
            PosPlatform.WINDOWS, "Windows", "11", "amd64"
        )
    }

    private class FakeLocale(private val tag: String) : LocaleInformationProvider {
        override fun current() = LocaleInformation(tag, "ar", "MA", isRightToLeft = true)
    }

    private class SequenceUuid(private vararg val values: String) : RandomUuidGenerator {
        private var index = 0
        override fun randomUuid() = values[index++]
    }
}
