package ma.elaroui.pos.desktop.platform

import java.nio.file.Path

/**
 * Unified file dialog entrypoint for the POS application.
 * On Windows, this launches the genuine Windows File Explorer dialog via Windows COM IFileOpenDialog / IFileSaveDialog.
 */
object NativeFileDialogs {

    private fun choose(
        title: String,
        extensions: Set<String>,
        save: Boolean = false,
        defaultName: String? = null
    ): Path? = when (DesktopPlatform.detect()) {
        DesktopPlatform.WINDOWS -> when {
            extensions.contains("csv") -> WindowsNativeFileDialog.openCsvDialog(title, isSave = save, defaultName = defaultName ?: "modele_catalogue_pos.csv")
            extensions.any { it == "db" || it == "zip" } -> WindowsNativeFileDialog.openBackupDialog(title, save, defaultName ?: "PATISSERIE_POS_Backup.zip")
            extensions.any { it in setOf("licence", "license", "txt") } -> WindowsNativeFileDialog.openLicenseDialog(title)
            else -> WindowsNativeFileDialog.openImageDialog(title)
        }
        DesktopPlatform.LINUX -> LinuxNativeFileDialog.open(title, extensions, save, defaultName)
        DesktopPlatform.UNSUPPORTED -> LinuxNativeFileDialog.open(title, extensions, save, defaultName)
    }

    /**
     * Opens the native Windows File Explorer dialog to pick an image (.png, .jpg, .jpeg, .webp).
     */
    fun selectImage(title: String = "Choisir une image"): Path? =
        choose(title, setOf("png", "jpg", "jpeg", "webp"))

    /**
     * Opens the native Windows File Explorer dialog to pick a CSV file (.csv).
     */
    fun selectCsv(title: String = "Sélectionner un fichier CSV"): Path? =
        choose(title, setOf("csv"))

    /**
     * Opens the native Windows File Explorer dialog to save a CSV template (.csv).
     */
    fun saveCsv(title: String = "Enregistrer le modèle CSV", defaultName: String = "modele_catalogue_pos.csv"): Path? =
        choose(title, setOf("csv"), save = true, defaultName = defaultName)

    /**
     * Opens the native Windows File Explorer dialog to select or save a database file (.db).
     */
    fun selectDb(title: String = "Sélectionner un fichier de sauvegarde", save: Boolean = false, defaultName: String = "pos-backup.db"): Path? =
        choose(title, setOf("db"), save, defaultName)

    fun selectBackup(title: String, save: Boolean, defaultName: String): Path? =
        choose(title, setOf("zip", "db"), save, defaultName)

    fun selectLicense(title: String = "Sélectionner un fichier de licence"): Path? =
        choose(title, setOf("licence", "license", "txt"))
}
