package ma.elaroui.pos.core.backup

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import ma.elaroui.pos.data.local.database.POSDatabase
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupManifest(
    val formatVersion: Int = 1,
    val dbSchemaVersion: Int = 8,
    val appVersion: String = "1.0.0",
    val createdAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val checksumSha256: String = ""
)

object BackupManager {

    private const val BACKUP_FORMAT_VERSION = 1
    private const val DB_SCHEMA_VERSION = 8
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_SIZE_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val MAX_BACKUP_ENTRY_BYTES = 256 * 1024 * 1024

    fun createBackup(
        context: Context,
        database: POSDatabase,
        outputStream: OutputStream,
        password: String? = null
    ): Result<Unit> {
        return runCatching {
            // Force WAL checkpoint to ensure db file is completely up-to-date
            database.openHelper.writableDatabase
                .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
                .use { it.moveToFirst() }

            val dbFile = File(
                requireNotNull(database.openHelper.writableDatabase.path) {
                    "Chemin de base de données indisponible."
                }
            )
            if (!dbFile.exists()) {
                throw FileNotFoundException("Fichier de base de données introuvable.")
            }

            val dbBytes = dbFile.readBytes()
            val checksum = computeSha256(dbBytes)
            val isEncrypted = !password.isNull_or_blank()

            val finalDbBytes = if (isEncrypted) {
                encryptAesGcm(dbBytes, password!!)
            } else {
                dbBytes
            }

            val setupPreferencesFile = File(
                context.applicationInfo.dataDir,
                "shared_prefs/pos_setup_prefs.xml"
            )
            ZipOutputStream(outputStream).use { zipOut ->
                // Add manifest.json entry
                val manifestContent = """
                    {
                        "formatVersion": $BACKUP_FORMAT_VERSION,
                        "dbSchemaVersion": $DB_SCHEMA_VERSION,
                        "appVersion": "1.0.0",
                        "createdAt": ${System.currentTimeMillis()},
                        "isEncrypted": $isEncrypted,
                        "checksumSha256": "$checksum"
                    }
                """.trimIndent()

                zipOut.putNextEntry(ZipEntry("manifest.json"))
                zipOut.write(manifestContent.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // Add database.db entry
                val dbEntryName = if (isEncrypted) "database.db.enc" else "database.db"
                zipOut.putNextEntry(ZipEntry(dbEntryName))
                zipOut.write(finalDbBytes)
                zipOut.closeEntry()
                if (setupPreferencesFile.exists()) {
                    zipOut.putNextEntry(ZipEntry("setup_preferences.xml"))
                    zipOut.write(setupPreferencesFile.readBytes())
                    zipOut.closeEntry()
                }
                val managedImagesRoot = File(context.filesDir, "app_images")
                if (managedImagesRoot.isDirectory) {
                    managedImagesRoot.walkTopDown().filter { it.isFile }.forEach { imageFile ->
                        val relative = imageFile.relativeTo(managedImagesRoot).invariantSeparatorsPath
                        zipOut.putNextEntry(ZipEntry("app_images/$relative"))
                        imageFile.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }
        }
    }

    fun restoreBackup(
        context: Context,
        database: POSDatabase,
        inputStream: InputStream,
        password: String? = null
    ): Result<Unit> {
        return runCatching {
            var manifest: BackupManifest? = null
            var dbBytes: ByteArray? = null
            var isDbEncrypted = false
            var setupPreferencesBytes: ByteArray? = null
            val restoredImages = linkedMapOf<String, ByteArray>()
            val seenEntries = mutableSetOf<String>()

            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (!seenEntries.add(entry.name)) {
                        throw IllegalArgumentException("Entrée dupliquée dans la sauvegarde.")
                    }
                    when (entry.name) {
                        "manifest.json" -> {
                            val content = zipIn.readLimitedBytes().toString(Charsets.UTF_8)
                            manifest = parseManifest(content)
                        }
                        "database.db" -> {
                            dbBytes = zipIn.readLimitedBytes()
                            isDbEncrypted = false
                        }
                        "database.db.enc" -> {
                            dbBytes = zipIn.readLimitedBytes()
                            isDbEncrypted = true
                        }
                        "setup_preferences.xml" -> {
                            setupPreferencesBytes = zipIn.readLimitedBytes()
                        }
                        else -> if (entry.name.startsWith("app_images/") && !entry.isDirectory) {
                            val relative = entry.name.removePrefix("app_images/")
                            if (relative.isBlank() || relative.contains("..") || relative.startsWith('/')) {
                                throw IllegalArgumentException("Chemin d'image invalide dans la sauvegarde.")
                            }
                            restoredImages[relative] = zipIn.readLimitedBytes()
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (manifest == null || dbBytes == null) {
                throw IllegalArgumentException("Fichier de sauvegarde invalide ou corrompu.")
            }
            if (manifest!!.formatVersion != BACKUP_FORMAT_VERSION) {
                throw IllegalArgumentException("Version de format de sauvegarde non prise en charge.")
            }

            if (manifest!!.dbSchemaVersion > DB_SCHEMA_VERSION) {
                throw IllegalArgumentException("La version de cette sauvegarde est plus récente que votre application.")
            }

            val decryptedDbBytes = if (isDbEncrypted) {
                if (password.isNull_or_blank()) {
                    throw IllegalArgumentException("Cette sauvegarde est chiffrée. Veuillez fournir le mot de passe.")
                }
                decryptAesGcm(dbBytes!!, password!!)
            } else {
                dbBytes!!
            }
            if (manifest!!.isEncrypted != isDbEncrypted) {
                throw IllegalArgumentException("Le manifeste de sauvegarde est incohérent.")
            }
            val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            if (
                decryptedDbBytes.size < sqliteHeader.size ||
                !decryptedDbBytes.copyOfRange(0, sqliteHeader.size).contentEquals(sqliteHeader)
            ) {
                throw IllegalArgumentException("La base de données de sauvegarde est invalide.")
            }

            // Verify checksum
            val actualChecksum = computeSha256(decryptedDbBytes)
            if (manifest!!.checksumSha256.isNotBlank() && actualChecksum != manifest!!.checksumSha256) {
                throw IllegalArgumentException("Échec de vérification de l'intégrité (Checksum ne correspond pas).")
            }

            val dbFile = File(
                requireNotNull(database.openHelper.writableDatabase.path) {
                    "Chemin de base de données indisponible."
                }
            )

            // Close current database connection safely
            database.close()

            // Overwrite existing database file atomically
            val walFile = File("${dbFile.path}-wal")
            val shmFile = File("${dbFile.path}-shm")

            val tempDb = File(dbFile.parentFile, "${dbFile.name}.restore.tmp")
            val originalDb = File(dbFile.parentFile, "${dbFile.name}.restore.previous")
            tempDb.writeBytes(decryptedDbBytes)
            if (originalDb.exists()) originalDb.delete()
            if (dbFile.exists() && !dbFile.renameTo(originalDb)) {
                tempDb.delete()
                throw IOException("Impossible de sécuriser la base actuelle avant restauration.")
            }
            try {
                if (!tempDb.renameTo(dbFile)) {
                    throw IOException("Impossible d'installer la base restaurée.")
                }
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()
                setupPreferencesBytes?.let { bytes ->
                    val preferencesFile = File(
                        context.applicationInfo.dataDir,
                        "shared_prefs/pos_setup_prefs.xml"
                    )
                    preferencesFile.parentFile?.mkdirs()
                    val temporaryPreferences = File(
                        preferencesFile.parentFile,
                        "${preferencesFile.name}.restore.tmp"
                    )
                    temporaryPreferences.writeBytes(bytes)
                    if (preferencesFile.exists()) preferencesFile.delete()
                    if (!temporaryPreferences.renameTo(preferencesFile)) {
                        throw IOException("Impossible de restaurer les paramètres de l'établissement.")
                    }
                }
                if (restoredImages.isNotEmpty()) {
                    val imagesRoot = File(context.filesDir, "app_images")
                    val stagedImages = File(context.cacheDir, "app_images_restore_${System.nanoTime()}")
                    try {
                        restoredImages.forEach { (relative, bytes) ->
                            val target = File(stagedImages, relative).canonicalFile
                            require(target.path.startsWith(stagedImages.canonicalPath + File.separator)) {
                                "Chemin d'image invalide dans la sauvegarde."
                            }
                            target.parentFile?.mkdirs()
                            target.writeBytes(bytes)
                        }
                        if (imagesRoot.exists()) imagesRoot.deleteRecursively()
                        imagesRoot.parentFile?.mkdirs()
                        if (!stagedImages.renameTo(imagesRoot)) {
                            stagedImages.copyRecursively(imagesRoot, overwrite = true)
                        }
                    } finally {
                        stagedImages.deleteRecursively()
                    }
                }
                originalDb.delete()
            } catch (error: Throwable) {
                dbFile.delete()
                if (originalDb.exists()) originalDb.renameTo(dbFile)
                throw error
            } finally {
                tempDb.delete()
            }
        }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun encryptAesGcm(plainText: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plainText)

        // Concatenate salt (16 bytes) + iv (12 bytes) + cipherText
        val output = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, output, 0, salt.size)
        System.arraycopy(iv, 0, output, salt.size, iv.size)
        System.arraycopy(cipherText, 0, output, salt.size + iv.size, cipherText.size)
        return output
    }

    private fun decryptAesGcm(encryptedPackage: ByteArray, password: String): ByteArray {
        if (encryptedPackage.size < 28) {
            throw IllegalArgumentException("Fichier chiffré invalide.")
        }
        val salt = ByteArray(16)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val cipherTextSize = encryptedPackage.size - 28
        val cipherText = ByteArray(cipherTextSize)

        System.arraycopy(encryptedPackage, 0, salt, 0, 16)
        System.arraycopy(encryptedPackage, 16, iv, 0, GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedPackage, 28, cipherText, 0, cipherTextSize)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun parseManifest(json: String): BackupManifest {
        fun extractString(key: String): String =
            Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""

        fun extractInt(key: String, default: Int): Int =
            Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: default

        fun extractBool(key: String, default: Boolean): Boolean =
            Regex("\"$key\"\\s*:\\s*(true|false)").find(json)?.groupValues?.get(1)?.toBoolean() ?: default

        fun extractLong(key: String, default: Long): Long =
            Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: default

        return BackupManifest(
            formatVersion = extractInt("formatVersion", 1),
            dbSchemaVersion = extractInt("dbSchemaVersion", 6),
            appVersion = extractString("appVersion"),
            createdAt = extractLong("createdAt", System.currentTimeMillis()),
            isEncrypted = extractBool("isEncrypted", false),
            checksumSha256 = extractString("checksumSha256")
        )
    }

    private fun InputStream.readLimitedBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BACKUP_ENTRY_BYTES) {
                throw IllegalArgumentException("Entrée de sauvegarde trop volumineuse.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
