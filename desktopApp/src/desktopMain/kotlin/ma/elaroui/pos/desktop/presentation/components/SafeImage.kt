package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.skia.Image as SkiaImage

object DesktopImageCache {
    private const val MAX_ENTRIES = 100
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(path: String): ImageBitmap? = synchronized(lock) {
        cache[path]
    }

    fun put(path: String, bitmap: ImageBitmap) = synchronized(lock) {
        cache[path] = bitmap
    }

    fun remove(path: String) = synchronized(lock) {
        cache.remove(path)
    }

    fun clear() = synchronized(lock) {
        cache.clear()
    }
}

private suspend fun loadBytesFromPathOrUrl(pathOrUrl: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val trimmed = pathOrUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val url = java.net.URI.create(trimmed).toURL()
            val conn = url.openConnection()
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            conn.getInputStream().use { it.readBytes() }
        } else {
            val file = File(trimmed)
            if (file.exists() && file.isFile) {
                file.readBytes()
            } else null
        }
    }.getOrNull()
}

@Composable
fun SafeProductImage(
    imagePath: String?,
    contentDescription: String = "",
    placeholderText: String = "",
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = imagePath?.let { DesktopImageCache.get(it) },
        key1 = imagePath
    ) {
        if (imagePath.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val cached = DesktopImageCache.get(imagePath)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            val bytes = loadBytesFromPathOrUrl(imagePath)
            if (bytes != null) {
                runCatching {
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            } else null
        }
        if (loaded != null) {
            DesktopImageCache.put(imagePath, loaded)
        }
        value = loaded
    }

    Box(
        modifier = modifier.background(PosColors.Workspace),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                if (maxHeight < 50.dp || maxWidth < 50.dp) {
                    if (placeholderText.isNotBlank()) {
                        Text(
                            text = placeholderText,
                            color = PosColors.TextMuted,
                            fontSize = if (maxHeight < 36.dp) 12.sp else 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        ImagePlaceholderIcon(modifier = Modifier.size((maxHeight * 0.6f).coerceAtMost(24.dp)))
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ImagePlaceholderIcon()
                        if (placeholderText.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(placeholderText, color = PosColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafeAreaImage(
    imagePath: String?,
    contentDescription: String,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = imagePath?.let { DesktopImageCache.get(it) },
        key1 = imagePath
    ) {
        if (imagePath.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val cached = DesktopImageCache.get(imagePath)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            val bytes = loadBytesFromPathOrUrl(imagePath)
            if (bytes != null) {
                runCatching {
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            } else null
        }
        if (loaded != null) {
            DesktopImageCache.put(imagePath, loaded)
        }
        value = loaded
    }

    Box(
        modifier = modifier.background(PosColors.Workspace),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ImagePlaceholderIcon()
                if (placeholderText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(placeholderText, color = PosColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ImagePlaceholderIcon(modifier: Modifier = Modifier.size(32.dp)) {
    Canvas(modifier) {
        val stroke = (size.width * 0.065f).coerceIn(1.dp.toPx(), 2.dp.toPx())
        val radius = (size.width * 0.09f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
        val corner = (size.width * 0.12f).coerceIn(2.dp.toPx(), 4.dp.toPx())
        drawRoundRect(
            color = PosColors.TextMuted.copy(alpha = 0.5f),
            style = Stroke(width = stroke),
            cornerRadius = CornerRadius(corner)
        )
        drawCircle(
            color = PosColors.TextMuted.copy(alpha = 0.5f),
            radius = radius,
            center = Offset(x = size.width * 0.72f, y = size.height * 0.28f)
        )
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.78f)
            lineTo(size.width * 0.42f, size.height * 0.46f)
            lineTo(size.width * 0.58f, size.height * 0.62f)
            lineTo(size.width * 0.72f, size.height * 0.50f)
            lineTo(size.width * 0.88f, size.height * 0.78f)
        }
        drawPath(path, color = PosColors.TextMuted.copy(alpha = 0.5f), style = Stroke(width = stroke))
    }
}
