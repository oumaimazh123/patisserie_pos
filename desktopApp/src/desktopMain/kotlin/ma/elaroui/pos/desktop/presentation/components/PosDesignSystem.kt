package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Artisan Boulangerie POS Design System Tokens
 * Strictly adhering to design.md ("Artisanal Modern Touch")
 */
object PosColors {
    // Primary Tier — Warm Terracotta & Roasted Pastry Browns
    val Primary = Color(0xFFB5673B)          // Terracotta
    val PrimaryDark = Color(0xFFA65A2E)      // Roasted Brown
    val PrimaryLight = Color(0xFFFBECE5)     // Soft Terracotta Tint
    val BakeryBrown = Color(0xFF8B5A3C)      // Deep Bakery Brown
    val PrimaryContainer = Color(0xFFAD6135)
    val OnPrimary = Color(0xFFFFFFFF)

    // Secondary Tier — Honey & Miel Doré
    val Secondary = Color(0xFFD89B3C)        // Honey Amber
    val SecondaryDark = Color(0xFFC7852E)    // Deep Honey Amber
    val SecondaryLight = Color(0xFFFDF4E7)   // Soft Honey Tint
    val GoldenHoney = Color(0xFFE2A94F)      // Miel Doré
    val SecondaryContainer = Color(0xFFFFBD5B)
    val OnSecondary = Color(0xFFFFFFFF)

    // Tertiary Tier — Pastry Confectionery Pink
    val Tertiary = Color(0xFFC77D8A)         // Pastry Pink
    val TertiaryContainer = Color(0xFFA5616D)
    val OnTertiary = Color(0xFFFFFFFF)

    // Neutrals & Surfaces — Chalk White, Crème & Warm Canvas
    val Canvas = Color(0xFFF7F4F0)           // Base shell canvas
    val Workspace = Color(0xFFFAF8F5)        // Order panes & workspace
    val Surface = Color(0xFFFFFFFF)          // Crisp white tiles & cards
    val SurfaceContainer = Color(0xFFEFEEEB) // Container backgrounds
    val SurfaceHigh = Color(0xFFEAE8E5)      // Slightly elevated containers
    val SurfaceHighest = Color(0xFFE4E2DF)

    // Borders & Hairlines
    val Border = Color(0xFFE6E0DA)           // Clean 1px dividing lines
    val BorderVariant = Color(0xFFD9C2B7)    // Subtle outline variant
    val BorderFocus = Color(0xFFB5673B)      // Terracotta active focus

    // Text & Typography Contrast Tiers
    val TextHigh = Color(0xFF252525)         // High emphasis: product names, total price, headers
    val TextMedium = Color(0xFF4B5563)       // Medium emphasis: modifiers, quantities, tax
    val TextMuted = Color(0xFF8C827A)        // Subtle muted: placeholders, barcodes, inactive
    val TextOnPrimary = Color(0xFFFFFFFF)

    // Functional Semantics
    val Success = Color(0xFF2E7D32)          // Pistachio green for confirmations / open register
    val SuccessLight = Color(0xFFE8F5E9)     // Soft Green tint
    val SuccessContainer = Color(0xFFE8F5E9)
    val Danger = Color(0xFFC62828)           // Groseille red for voids / cancellations / alerts
    val DangerLight = Color(0xFFFFEBEE)      // Soft Red tint
    val DangerContainer = Color(0xFFFFEBEE)
    val Alert = Color(0xFFD89B3C)            // Honey amber for holds / warnings
    val AlertLight = Color(0xFFFFF8E1)       // Soft Amber tint
    val AlertContainer = Color(0xFFFFF8E1)

    // Aliases for compatibility
    val Honey = Secondary
    val TextDark = TextHigh
    val TextLow = TextMuted
}

object PosDimens {
    val TouchMin = 48.dp
    val TouchStandard = 56.dp
    val TouchLg = 64.dp

    val PadXs = 4.dp
    val PadSm = 8.dp
    val PadMd = 12.dp
    val PadLg = 16.dp
    val PadXl = 24.dp
    val Pad2Xl = 32.dp

    val GutterTerminal = 12.dp
    val PanelTicketWidthCompact = 340.dp
    val PanelTicketWidthRegular = 420.dp

    val CategoryBarHeight = 54.dp
    val SearchHeight = 50.dp
    val CartRowHeight = 52.dp
    val CashOutButtonHeight = 64.dp
    val KeypadKeyHeight = 56.dp

    val RadiusSm = 4.dp
    val RadiusMd = 8.dp
    val RadiusLg = 12.dp
    val RadiusCard = 14.dp
    val RadiusPanel = 16.dp
    val RadiusPill = 9999.dp
}

/**
 * Shared touch-first dimensions and backward-compatible color aliases.
 * Maps legacy calls to the new warm Artisan Boulangerie palette.
 */
object PosUi {
    val ScreenPadding = PosDimens.PadLg
    val CompactPadding = PosDimens.PadMd
    val SectionGap = PosDimens.PadMd
    val GridGap = PosDimens.PadMd
    val TouchTarget = PosDimens.TouchMin
    val PrimaryActionHeight = PosDimens.TouchStandard
    val HeaderHeight = PosDimens.TouchLg
    val CardRadius = PosDimens.RadiusCard

    // Backward-compatible color aliases routed to the new warm artisan palette
    val Navy = PosColors.PrimaryDark         // Replaces cold navy with roasted brown
    val NavySoft = PosColors.BakeryBrown     // Warm bakery brown
    val Red = PosColors.Danger               // Groseille red
    val Canvas = PosColors.Canvas            // Warm canvas #F7F4F0
    val Border = PosColors.Border            // Warm border #E6E0DA
    val Muted = PosColors.TextMedium         // Balanced medium text #4B5563
}

object PosTheme {
    fun colorScheme(): ColorScheme = lightColorScheme(
        primary = PosColors.Primary,
        onPrimary = PosColors.OnPrimary,
        primaryContainer = PosColors.PrimaryContainer,
        onPrimaryContainer = Color.White,
        secondary = PosColors.Secondary,
        onSecondary = PosColors.OnSecondary,
        secondaryContainer = PosColors.SecondaryContainer,
        onSecondaryContainer = PosColors.TextHigh,
        tertiary = PosColors.Tertiary,
        onTertiary = PosColors.OnTertiary,
        background = PosColors.Canvas,
        onBackground = PosColors.TextHigh,
        surface = PosColors.Surface,
        onSurface = PosColors.TextHigh,
        surfaceVariant = PosColors.SurfaceContainer,
        onSurfaceVariant = PosColors.TextMedium,
        outline = PosColors.Border,
        outlineVariant = PosColors.BorderVariant,
        error = PosColors.Danger,
        onError = Color.White
    )

    fun typography(): Typography = Typography(
        displayLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            color = PosColors.TextHigh
        ),
        headlineLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = PosColors.TextHigh
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            color = PosColors.TextHigh
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = PosColors.TextHigh
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            color = PosColors.TextHigh
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = PosColors.TextHigh
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = PosColors.TextHigh
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = PosColors.TextMedium
        ),
        bodySmall = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = PosColors.TextMuted
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = PosColors.TextHigh
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = PosColors.TextMedium
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = PosColors.TextMuted
        )
    )
}
