package ma.elaroui.pos.presentation.management.products

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ma.elaroui.pos.R
import ma.elaroui.pos.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cardsRemainEqualWithLongContentAndInvalidImage() {
        val longProduct = Product(
            id = 1,
            categoryId = 1,
            name = "Très long nom de produit marocain qui doit rester dans exactement deux lignes",
            priceCentimes = 999_999_999,
            imagePath = "C:/invalid/missing-product.jpg"
        )
        val shortProduct = Product(
            id = 2,
            categoryId = 1,
            name = "Café",
            priceCentimes = 2_500,
            imagePath = null
        )
        composeRule.setContent {
            MaterialTheme {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    Box(Modifier.width(240.dp)) {
                        ProductCard(longProduct, "Une catégorie au nom extrêmement long", {}, {}, {})
                    }
                    Box(Modifier.width(240.dp)) {
                        ProductCard(shortProduct, "Café", {}, {}, {})
                    }
                }
            }
        }

        val first = composeRule.onNodeWithTag("product_card_1").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("product_card_2").fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, second.width, 1f)
        assertEquals(first.height, second.height, 1f)
        val density = composeRule.activity.resources.displayMetrics.density
        assertEquals(370f, first.height / density, 1f)
    }

    @Test
    fun noImagePlaceholderRendersInRtlWithoutCollapsing() {
        val noImage = Product(
            id = 3,
            categoryId = 1,
            name = "شاي مغربي بالنعناع",
            priceCentimes = 1_500,
            imagePath = null
        )
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ProductCard(noImage, "المشروبات الساخنة", {}, {}, {})
                }
            }
        }

        val placeholderText = composeRule.activity.getString(R.string.no_image)
        composeRule.onNodeWithText(placeholderText).assertExists()
        val imageBounds = composeRule
            .onNodeWithTag("product_image", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        assertEquals(132f, imageBounds.height / density, 1f)
    }
}
