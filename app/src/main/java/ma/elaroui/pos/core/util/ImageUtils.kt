package ma.elaroui.pos.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {

    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024 // 2MB
    private val ALLOWED_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")

    fun saveProductImage(context: Context, sourceUri: Uri): String {
        return saveManagedImage(context, sourceUri, "products", "prod_")
    }

    fun saveAreaImage(context: Context, sourceUri: Uri): String {
        return saveManagedImage(context, sourceUri, "areas", "area_")
    }

    private fun saveManagedImage(
        context: Context,
        sourceUri: Uri,
        directoryName: String,
        filePrefix: String
    ): String {
        val contentResolver = context.contentResolver

        // Check file type
        val mimeType = contentResolver.getType(sourceUri) ?: ""
        val allowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")
        val extension = sourceUri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (mimeType.isNotBlank()) {
            require(mimeType.lowercase() in allowedMimeTypes) { "Format d'image non pris en charge." }
        } else {
            require(extension in ALLOWED_EXTENSIONS) { "Format d'image non pris en charge." }
        }

        val imagesDir = File(context.filesDir, "app_images/$directoryName")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val fileName = "${filePrefix}${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
        val destFile = File(imagesDir, fileName)

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = contentResolver.openInputStream(sourceUri)
                ?: throw IllegalArgumentException("Impossible d'ouvrir l'image sélectionnée.")

            // Decode and compress thumbnail
            val bitmap = BitmapFactory.decodeStream(inputStream)
                ?: throw IllegalArgumentException("Format d'image non valide.")

            outputStream = FileOutputStream(destFile)
            // Compress to JPEG 85% quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()

            return destFile.absolutePath
        } catch (e: Exception) {
            if (destFile.exists()) {
                destFile.delete()
            }
            throw e
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    fun deleteProductImage(filePath: String?) {
        deleteManagedImage(filePath, "prod_")
    }

    fun deleteAreaImage(filePath: String?) {
        deleteManagedImage(filePath, "area_")
    }

    private fun deleteManagedImage(filePath: String?, expectedPrefix: String) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile && file.name.startsWith(expectedPrefix)) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
