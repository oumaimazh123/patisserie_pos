package ma.elaroui.pos.presentation.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun canonicalViewportWidthsMapToExpectedClasses() {
        assertEquals(AppWidthClass.Compact, dimensionsForWidth(360.dp).widthClass)
        assertEquals(AppWidthClass.Compact, dimensionsForWidth(412.dp).widthClass)
        assertEquals(AppWidthClass.Medium, dimensionsForWidth(600.dp).widthClass)
        assertEquals(AppWidthClass.Medium, dimensionsForWidth(800.dp).widthClass)
        assertEquals(AppWidthClass.Expanded, dimensionsForWidth(840.dp).widthClass)
        assertEquals(AppWidthClass.Expanded, dimensionsForWidth(960.dp).widthClass)
        assertEquals(AppWidthClass.Expanded, dimensionsForWidth(1280.dp).widthClass)
    }

    @Test
    fun dimensionsGrowWithoutShrinkingTouchOrCardTargets() {
        val compact = dimensionsForWidth(360.dp)
        val medium = dimensionsForWidth(600.dp)
        val expanded = dimensionsForWidth(1280.dp)

        assertTrue(compact.screenPadding <= medium.screenPadding)
        assertTrue(medium.screenPadding <= expanded.screenPadding)
        assertTrue(compact.productCardMinWidth <= medium.productCardMinWidth)
        assertTrue(medium.productCardMinWidth <= expanded.productCardMinWidth)
        assertTrue(medium.navigationWidth >= 48.dp)
        assertTrue(expanded.navigationWidth >= 48.dp)
    }
}
