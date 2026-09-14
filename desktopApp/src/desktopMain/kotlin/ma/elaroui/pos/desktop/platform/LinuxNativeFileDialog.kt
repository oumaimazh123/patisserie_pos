package ma.elaroui.pos.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path

object LinuxNativeFileDialog {
    fun open(
        title: String,
        allowedExtensions: Set<String>,
        save: Boolean = false,
        defaultName: String? = null
    ): Path? {
        val extensions = allowedExtensions.map { it.removePrefix(".").lowercase() }.toSet()
        val dialog = FileDialog(null as Frame?, title, if (save) FileDialog.SAVE else FileDialog.LOAD).apply {
            defaultName?.let { file = it }
            if (extensions.isNotEmpty()) {
                filenameFilter = java.io.FilenameFilter { _, name ->
                    name.substringAfterLast('.', "").lowercase() in extensions
                }
            }
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val fileName = dialog.file ?: return null
        return File(directory, fileName).toPath()
    }
}
