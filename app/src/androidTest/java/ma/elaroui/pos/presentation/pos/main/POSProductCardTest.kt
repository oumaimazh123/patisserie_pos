package ma.elaroui.pos.presentation.pos.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ma.elaroui.pos.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class POSProductCardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun imageAndPlaceholderCardsKeepSameBoundsAndRemainClickable() {
        var clicks = 0
        val noImage = Product(101, 1, "Nom de produit très long sur deux lignes maximum", 2_500)
        val invalidImage = Product(102, 1, "Produit", 999_999_999, imagePath = "/missing/image.jpg")
        rule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Row {
                    Box(Modifier.width(200.dp)) { POSProductCard(noImage) { clicks++ } }
                    Box(Modifier.width(200.dp)) { POSProductCard(invalidImage) { clicks++ } }
                }
            }
        }
        val first = rule.onNodeWithTag("pos_product_101").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("pos_product_102").fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, second.width, 1f)
        assertEquals(first.height, second.height, 1f)
        rule.onNodeWithTag("pos_product_101").performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }
}
