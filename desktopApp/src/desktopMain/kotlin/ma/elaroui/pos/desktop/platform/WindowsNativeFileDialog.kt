package ma.elaroui.pos.desktop.platform

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import java.io.File
import java.nio.file.Path

/**
 * Direct implementation of the modern Windows IFileOpenDialog / IFileSaveDialog (Common Item Dialog)
 * via Windows COM and JNA.
 *
 * This invokes the genuine, native Windows File Explorer dialog (the exact same dialog used by
 * native Windows desktop applications), completely bypassing Java/Swing file choosers.
 */
object WindowsNativeFileDialog {

    private val CLSID_FileOpenDialog = CLSID("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")
    private val IID_IFileOpenDialog = IID("D57C5288-8A66-4361-B347-EBCA6F0B7DE9")

    private val CLSID_FileSaveDialog = CLSID("C0B4E2F3-BA21-4773-8DBA-335EC946EB8B")
    private val IID_IFileSaveDialog = IID("84BCCD23-5FDE-4CDB-AEA4-AF64B83D78AB")

    private const val CLSCTX_INPROC_SERVER = 1
    private const val S_OK = 0
    private const val SIGDN_FILESYSPATH = -0x7ffa8000 // 0x80058000 as Int

    // File Dialog Options
    private const val FOS_FORCEFILESYSTEM = 0x00000040
    private const val FOS_FILEMUSTEXIST = 0x00001000
    private const val FOS_PATHMUSTEXIST = 0x00000800
    private const val FOS_NOCHANGEDIR = 0x00000008
    private const val FOS_STRICTFILETYPES = 0x00000004

    @Structure.FieldOrder("pszName", "pszSpec")
    class COMDLG_FILTERSPEC : Structure {
        @JvmField var pszName: WString? = null
        @JvmField var pszSpec: WString? = null

        constructor() : super()
        constructor(p: Pointer) : super(p)
        constructor(name: String, spec: String) : super() {
            this.pszName = WString(name)
            this.pszSpec = WString(spec)
        }
    }

    /**
     * Opens the native Windows File Explorer Open Dialog for image selection (.png, .jpg, .jpeg, .webp).
     * Returns the selected Path, or null if the user cancelled.
     */
    fun matchesExtension(fileName: String, filterExtensions: List<String>): Boolean {
        if (filterExtensions.isEmpty()) return true
        val exts = filterExtensions.map { it.lowercase().removePrefix("*.") }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in exts
    }

    fun formatDefaultFilterPattern(filterExtensions: List<String>): String {
        val exts = filterExtensions.map { it.lowercase().removePrefix("*.") }
        return exts.joinToString(";") { "*.$it" }
    }

    fun findActiveFrame(): java.awt.Frame? {
        val frames = java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
        return frames.firstOrNull { it.isFocused } ?: frames.firstOrNull { it.isVisible }
    }

    /**
     * Reliable native Windows File Explorer dialog using Java AWT (which wraps native Win32/COM
     * GetOpenFileName / GetSaveFileName / IFileOpenDialog in-process).
     *
     * 1. Finds the active Compose POS Window to act as the modal owner (hwndOwner).
     * 2. This guarantees the file picker appears in the FOREGROUND and never behind the POS window.
     * 3. If the POS window was in fullscreen, temporarily adjusts placement to Maximized so the dialog
     *    and taskbar render normally without exclusive fullscreen DWM clipping.
     * 4. Automatically restores the POS window to Fullscreen mode and regains window focus upon
     *    file selection or cancellation.
     * 5. The POS application remains running in the background without losing any state.
     */
    fun showNativeWindowsDialog(
        title: String,
        isSave: Boolean,
        filterExtensions: List<String>,
        defaultFileName: String? = null
    ): Path? {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return null
        }

        val activeFrame = findActiveFrame()
        val composeWindow = activeFrame as? ComposeWindow
        val wasFullscreen = composeWindow?.placement == WindowPlacement.Fullscreen

        val mode = if (isSave) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD
        val dialog = java.awt.FileDialog(activeFrame, title, mode).apply {
            if (filterExtensions.isNotEmpty()) {
                setFilenameFilter { _, name -> matchesExtension(name, filterExtensions) }
                file = if (isSave && !defaultFileName.isNullOrBlank()) {
                    defaultFileName
                } else {
                    formatDefaultFilterPattern(filterExtensions)
                }
            }
        }

        try {
            if (wasFullscreen) {
                composeWindow?.placement = WindowPlacement.Maximized
            }
            dialog.isVisible = true
            val fileName = dialog.file ?: return null
            val directory = dialog.directory ?: return null
            return File(directory, fileName).toPath()
        } finally {
            dialog.dispose()
            if (wasFullscreen) {
                composeWindow?.placement = WindowPlacement.Fullscreen
            }
            activeFrame?.toFront()
            activeFrame?.requestFocus()
        }
    }

    /**
     * Opens the native Windows File Explorer Open Dialog for image selection (.png, .jpg, .jpeg, .webp).
     * Returns the selected Path, or null if the user cancelled.
     */
    fun openImageDialog(title: String = "Choisir une image"): Path? {
        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            return showNativeWindowsDialog(
                title = title,
                isSave = false,
                filterExtensions = listOf("png", "jpg", "jpeg", "webp")
            )
        }
        val filters = arrayOf(
            COMDLG_FILTERSPEC("Tous les fichiers image (*.png;*.jpg;*.jpeg;*.webp)", "*.png;*.jpg;*.jpeg;*.webp"),
            COMDLG_FILTERSPEC("Images PNG (*.png)", "*.png"),
            COMDLG_FILTERSPEC("Images JPEG (*.jpg;*.jpeg)", "*.jpg;*.jpeg"),
            COMDLG_FILTERSPEC("Images WebP (*.webp)", "*.webp")
        )
        return showCommonItemDialog(
            isSave = false,
            title = title,
            filters = filters,
            defaultExtension = "png"
        )
    }

    /**
     * Opens the native Windows File Explorer Open/Save Dialog for CSV import/export (.csv).
     */
    fun openCsvDialog(
        title: String = "Importer un fichier CSV",
        isSave: Boolean = false,
        defaultName: String? = null
    ): Path? {
        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            return showNativeWindowsDialog(
                title = title,
                isSave = isSave,
                filterExtensions = listOf("csv"),
                defaultFileName = defaultName
            )
        }
        val filters = arrayOf(
            COMDLG_FILTERSPEC("Fichiers CSV (*.csv)", "*.csv"),
            COMDLG_FILTERSPEC("Tous les fichiers (*.*)", "*.*")
        )
        return showCommonItemDialog(
            isSave = isSave,
            title = title,
            filters = filters,
            defaultExtension = "csv",
            defaultFileName = defaultName
        )
    }

    /**
     * Opens the native Windows File Explorer Open/Save Dialog for database files (.db, .zip).
     */
    fun openBackupDialog(title: String, isSave: Boolean, defaultName: String): Path? {
        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            val ext = if (defaultName.endsWith(".db", true)) "db" else "zip"
            return showNativeWindowsDialog(
                title = title,
                isSave = isSave,
                filterExtensions = listOf(ext, "db", "zip"),
                defaultFileName = defaultName
            )
        }
        val filters = arrayOf(
            COMDLG_FILTERSPEC("Sauvegardes PATISSERIE_POS (*.zip)", "*.zip"),
            COMDLG_FILTERSPEC("Fichiers de base de données (*.db)", "*.db"),
            COMDLG_FILTERSPEC("Tous les fichiers (*.*)", "*.*")
        )
        return showCommonItemDialog(
            isSave = isSave,
            title = title,
            filters = filters,
            defaultExtension = if (defaultName.endsWith(".db", true)) "db" else "zip",
            defaultFileName = defaultName
        )
    }

    fun openDbDialog(title: String = "Sélectionner une base de données", isSave: Boolean = false, defaultName: String = "pos-backup.db"): Path? =
        openBackupDialog(title, isSave, defaultName)

    fun openLicenseDialog(title: String = "Sélectionner une licence"): Path? {
        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            return showNativeWindowsDialog(
                title = title,
                isSave = false,
                filterExtensions = listOf("licence", "license", "txt")
            )
        }
        val filters = arrayOf(
            COMDLG_FILTERSPEC("Fichiers de licence (*.licence;*.license;*.txt)", "*.licence;*.license;*.txt"),
            COMDLG_FILTERSPEC("Tous les fichiers (*.*)", "*.*")
        )
        return showCommonItemDialog(
            isSave = false,
            title = title,
            filters = filters,
            defaultExtension = "licence;license;txt"
        )
    }

    private fun showCommonItemDialog(
        isSave: Boolean,
        title: String,
        filters: Array<COMDLG_FILTERSPEC>,
        defaultExtension: String? = null,
        defaultFileName: String? = null
    ): Path? {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) {
            // Fallback for non-Windows dev environments
            return fallbackAwtDialog(title, isSave, defaultExtension, defaultFileName)
        }

        return try {
            Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)

            val clsid = if (isSave) CLSID_FileSaveDialog else CLSID_FileOpenDialog
            val iid = if (isSave) IID_IFileSaveDialog else IID_IFileOpenDialog
            val pDialogRef = PointerByReference()

            val hrCreate = Ole32.INSTANCE.CoCreateInstance(clsid, null, CLSCTX_INPROC_SERVER, iid, pDialogRef)
            if (hrCreate.toInt() != S_OK || pDialogRef.value == null) {
                return fallbackAwtDialog(title, isSave, defaultExtension, defaultFileName)
            }

            val pDialog = pDialogRef.value

            try {
                // Set Dialog Title (vtable index 17)
                callVtableMethod(pDialog, 17, WString(title))

                // Set Options (vtable index 9)
                val options = FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or FOS_NOCHANGEDIR or
                        (if (!isSave) FOS_FILEMUSTEXIST else 0)
                callVtableMethod(pDialog, 9, options)

                // Set Default Extension (vtable index 22)
                if (!defaultExtension.isNullOrBlank()) {
                    callVtableMethod(pDialog, 22, WString(defaultExtension))
                }

                // Set Default File Name (vtable index 15)
                if (!defaultFileName.isNullOrBlank()) {
                    callVtableMethod(pDialog, 15, WString(defaultFileName))
                }

                // Set File Types Filters (vtable index 4)
                if (filters.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    val array = COMDLG_FILTERSPEC().toArray(filters.size) as Array<COMDLG_FILTERSPEC>
                    for (i in filters.indices) {
                        array[i].pszName = filters[i].pszName
                        array[i].pszSpec = filters[i].pszSpec
                        array[i].write()
                    }
                    callVtableMethod(pDialog, 4, filters.size, array[0].pointer)
                }

                // Show Dialog (vtable index 3: Show(HWND hwndOwner))
                val hrShow = callVtableMethod(pDialog, 3, Pointer.NULL)
                if (hrShow != S_OK) {
                    // User cancelled or closed dialog
                    return null
                }

                // Get Result IShellItem (vtable index 20: GetResult(IShellItem **ppsi))
                val pShellItemRef = PointerByReference()
                val hrGetResult = callVtableMethod(pDialog, 20, pShellItemRef)
                if (hrGetResult != S_OK || pShellItemRef.value == null) {
                    return null
                }

                val pShellItem = pShellItemRef.value
                try {
                    // Get Display Name (IShellItem vtable index 5: GetDisplayName(SIGDN sigdnName, LPWSTR *ppszName))
                    val ppszNameRef = PointerByReference()
                    val hrGetName = callVtableMethod(pShellItem, 5, SIGDN_FILESYSPATH, ppszNameRef)
                    if (hrGetName == S_OK && ppszNameRef.value != null) {
                        val pathStr = ppszNameRef.value.getWideString(0)
                        Ole32.INSTANCE.CoTaskMemFree(ppszNameRef.value)
                        if (pathStr.isNotBlank()) {
                            return File(pathStr).toPath()
                        }
                    }
                } finally {
                    // Release IShellItem (vtable index 2: Release())
                    callVtableMethod(pShellItem, 2)
                }
            } finally {
                // Release IFileDialog (vtable index 2: Release())
                callVtableMethod(pDialog, 2)
            }
            null
        } catch (_: Throwable) {
            fallbackAwtDialog(title, isSave, defaultExtension, defaultFileName)
        }
    }

    private fun callVtableMethod(comObject: Pointer, methodIndex: Int, vararg args: Any?): Int {
        val vtable = comObject.getPointer(0)
        val methodAddress = vtable.getPointer((methodIndex * Native.POINTER_SIZE).toLong())
        val func = Function.getFunction(methodAddress)
        val fullArgs = arrayOf(comObject, *args)
        val result = func.invoke(Int::class.java, fullArgs)
        return (result as? Number)?.toInt() ?: S_OK
    }

    private fun fallbackAwtDialog(title: String, isSave: Boolean, defaultExt: String?, defaultName: String?): Path? {
        val mode = if (isSave) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, mode).apply {
            if (!defaultExt.isNullOrBlank()) {
                val exts = defaultExt.split(';').map { it.trim().removePrefix("*.") }
                setFilenameFilter { _, name ->
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in exts
                }
                file = if (isSave && !defaultName.isNullOrBlank()) defaultName else "*.$defaultExt"
            }
            isVisible = true
        }
        val fileName = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        return File(dir, fileName).toPath()
    }
}
