package ma.elaroui.pos.desktop.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

class TouchInputFieldTest {

    @Test
    fun `single-line defaults remain single-line`() {
        assertEquals(
            TextLineLimits(singleLine = true, minLines = 1, maxLines = 1),
            resolveTextLineLimits(singleLine = true, minLines = 1, maxLines = 1)
        )
    }

    @Test
    fun `multi-line minimum expands an invalid maximum`() {
        assertEquals(
            TextLineLimits(singleLine = false, minLines = 3, maxLines = 3),
            resolveTextLineLimits(singleLine = false, minLines = 3, maxLines = 1)
        )
    }

    @Test
    fun `multi-line minimum disables contradictory single-line mode`() {
        assertEquals(
            TextLineLimits(singleLine = false, minLines = 2, maxLines = 2),
            resolveTextLineLimits(singleLine = true, minLines = 2, maxLines = 1)
        )
    }
}
