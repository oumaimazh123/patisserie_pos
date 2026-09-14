package ma.elaroui.pos.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File

@Composable
fun ManagedLocalImage(
    imagePath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    placeholderText: String = "Aucune image"
) {
    val bitmap = remember(imagePath) {
        imagePath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) } }.getOrNull()
        }
    }
    Box(modifier.background(Color(0xFFE9EEF3)), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(placeholderText, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
        }
    }
}
