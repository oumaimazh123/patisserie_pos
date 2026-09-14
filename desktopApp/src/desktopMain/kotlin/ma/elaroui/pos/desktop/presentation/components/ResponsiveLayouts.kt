package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

enum class DesktopDisplayClass {
    COMPACT, // < 600dp (Mobile / compact window)
    MEDIUM,  // 600dp - 840dp (Tablet / medium window)
    EXPANDED // >= 840dp (Laptop / Desktop widescreen)
}

fun getDisplayClass(width: Dp): DesktopDisplayClass = when {
    width < 600.dp -> DesktopDisplayClass.COMPACT
    width < 840.dp -> DesktopDisplayClass.MEDIUM
    else -> DesktopDisplayClass.EXPANDED
}

fun responsivePadding(width: Dp): Dp = when {
    width < 600.dp -> 12.dp
    width < 840.dp -> 18.dp
    else -> 24.dp
}

/** Equal-width flow grid for summary cards and form sections. */
@Composable
fun ResponsiveFlowGrid(
    modifier: Modifier = Modifier,
    minItemWidth: Dp,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}

        val horizontalGap = horizontalSpacing.roundToPx()
        val verticalGap = verticalSpacing.roundToPx()
        val minimumWidth = minItemWidth.roundToPx().coerceAtLeast(1)
        val columns = max(1, (constraints.maxWidth + horizontalGap) / (minimumWidth + horizontalGap))
            .coerceAtMost(measurables.size)
        val itemWidth = ((constraints.maxWidth - horizontalGap * (columns - 1)) / columns).coerceAtLeast(1)
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = itemWidth, maxWidth = itemWidth, minHeight = 0))
        }
        val rows = (placeables.size + columns - 1) / columns
        val rowHeights = IntArray(rows)
        placeables.forEachIndexed { index, placeable ->
            val row = index / columns
            rowHeights[row] = max(rowHeights[row], placeable.height)
        }
        val contentHeight = rowHeights.sum() + verticalGap * (rows - 1).coerceAtLeast(0)
        val layoutHeight = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(constraints.maxWidth, layoutHeight) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                val row = index / columns
                val column = index % columns
                placeable.placeRelative(column * (itemWidth + horizontalGap), y)
                if (column == columns - 1 || index == placeables.lastIndex) {
                    y += rowHeights[row] + verticalGap
                }
            }
        }
    }
}

/**
 * A two-pane container that keeps the desktop split layout when space permits and
 * stacks both panes when the window is narrow. Children remain shared between
 * both arrangements; screens do not need duplicated compact implementations.
 */
@Composable
fun ResponsiveSplitPane(
    modifier: Modifier = Modifier,
    horizontalBreakpoint: Dp = 900.dp,
    primaryFraction: Float = 0.65f,
    compactPrimaryFraction: Float = 0.54f,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(measurables.size == 2) { "ResponsiveSplitPane requires exactly two children" }
        val horizontal = constraints.maxWidth >= horizontalBreakpoint.roundToPx()

        if (horizontal) {
            val primaryWidth = (constraints.maxWidth * primaryFraction)
                .toInt()
                .coerceIn(0, constraints.maxWidth)
            val secondaryWidth = constraints.maxWidth - primaryWidth
            val primary = measurables[0].measure(
                constraints.copy(minWidth = primaryWidth, maxWidth = primaryWidth)
            )
            val secondary = measurables[1].measure(
                constraints.copy(minWidth = secondaryWidth, maxWidth = secondaryWidth)
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                primary.placeRelative(0, 0)
                secondary.placeRelative(primaryWidth, 0)
            }
        } else {
            val primaryHeight = (constraints.maxHeight * compactPrimaryFraction)
                .toInt()
                .coerceIn(0, constraints.maxHeight)
            val secondaryHeight = constraints.maxHeight - primaryHeight
            val primary = measurables[0].measure(
                constraints.copy(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = primaryHeight,
                    maxHeight = primaryHeight
                )
            )
            val secondary = measurables[1].measure(
                constraints.copy(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = secondaryHeight,
                    maxHeight = secondaryHeight
                )
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                primary.placeRelative(0, 0)
                secondary.placeRelative(0, primaryHeight)
            }
        }
    }
}

