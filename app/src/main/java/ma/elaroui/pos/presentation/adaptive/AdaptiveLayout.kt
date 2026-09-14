package ma.elaroui.pos.presentation.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWidthClass { Compact, Medium, Expanded }

@Immutable
data class AdaptiveDimensions(
    val widthClass: AppWidthClass,
    val screenPadding: Dp,
    val gridSpacing: Dp,
    val productCardMinWidth: Dp,
    val generalCardMinWidth: Dp,
    val dialogMaxWidth: Dp,
    val navigationWidth: Dp,
    val contentMaxWidth: Dp
) {
    val isCompact: Boolean get() = widthClass == AppWidthClass.Compact
    val isMedium: Boolean get() = widthClass == AppWidthClass.Medium
    val isExpanded: Boolean get() = widthClass == AppWidthClass.Expanded
    val screenContentPadding: PaddingValues
        get() = PaddingValues(horizontal = screenPadding, vertical = screenPadding)
}

val LocalAdaptiveDimensions = staticCompositionLocalOf {
    dimensionsForWidth(360.dp)
}

fun dimensionsForWidth(width: Dp): AdaptiveDimensions = when {
    width < 600.dp -> AdaptiveDimensions(
        widthClass = AppWidthClass.Compact,
        screenPadding = 12.dp,
        gridSpacing = 10.dp,
        productCardMinWidth = 140.dp,
        generalCardMinWidth = 140.dp,
        dialogMaxWidth = 560.dp,
        navigationWidth = 0.dp,
        contentMaxWidth = 720.dp
    )
    width < 840.dp -> AdaptiveDimensions(
        widthClass = AppWidthClass.Medium,
        screenPadding = 18.dp,
        gridSpacing = 12.dp,
        productCardMinWidth = 200.dp,
        generalCardMinWidth = 190.dp,
        dialogMaxWidth = 640.dp,
        navigationWidth = 80.dp,
        contentMaxWidth = 960.dp
    )
    else -> AdaptiveDimensions(
        widthClass = AppWidthClass.Expanded,
        screenPadding = 24.dp,
        gridSpacing = 16.dp,
        productCardMinWidth = 220.dp,
        generalCardMinWidth = 220.dp,
        dialogMaxWidth = 720.dp,
        navigationWidth = 88.dp,
        contentMaxWidth = 1280.dp
    )
}

@Composable
fun ProvideAdaptiveLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(
            LocalAdaptiveDimensions provides dimensionsForWidth(maxWidth),
            content = content
        )
    }
}
