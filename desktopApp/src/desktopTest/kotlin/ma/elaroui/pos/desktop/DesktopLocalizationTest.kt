package ma.elaroui.pos.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLocalizationTest {
    @Test fun supportedLanguagesHaveExpectedDirectionAndCoreTranslations(){
        assertEquals(DesktopLanguage.FR,DesktopLanguage.from("unknown"))
        assertFalse(DesktopLanguage.FR.rtl);assertFalse(DesktopLanguage.EN.rtl);assertTrue(DesktopLanguage.AR.rtl)
        assertEquals("Réglages",DesktopStrings(DesktopLanguage.FR).settings)
        assertEquals("Settings",DesktopStrings(DesktopLanguage.EN).settings)
        assertEquals("الإعدادات",DesktopStrings(DesktopLanguage.AR).settings)
    }
}
