package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
data class AlasalExtendedColors(
    val gold: Color,
    val electricYellow: Color,
    val creditGreen: Color,
    val creditContainer: Color,
    val arrearsRed: Color,
    val arrearsContainer: Color,
    val navyBrand: Color,
    val navyBrandElevated: Color,
    val cardBorder: Color
)

private val LightExtendedColors = AlasalExtendedColors(
    gold = GoldPrimary,
    electricYellow = ElectricYellow,
    creditGreen = CreditGreenLight,
    creditContainer = CreditGreenContainerLight,
    arrearsRed = ArrearsRedLight,
    arrearsContainer = ArrearsRedContainerLight,
    navyBrand = NavyDeep,
    navyBrandElevated = NavyElevated,
    cardBorder = SlateBorder
)

private val DarkExtendedColors = AlasalExtendedColors(
    gold = GoldPrimary,
    electricYellow = ElectricYellow,
    creditGreen = CreditGreenDark,
    creditContainer = CreditGreenContainerDark,
    arrearsRed = ArrearsRedDark,
    arrearsContainer = ArrearsRedContainerDark,
    navyBrand = NavyNightSurface,
    navyBrandElevated = NavyElevated,
    cardBorder = SlateBorderDark
)

val LocalAlasalColors = staticCompositionLocalOf { LightExtendedColors }

private val AlasalLightColorScheme = lightColorScheme(
    primary = NavyDeep,
    onPrimary = PureWhite,
    primaryContainer = NavyElevated,
    onPrimaryContainer = PureWhite,
    secondary = GoldDark,
    onSecondary = PureWhite,
    secondaryContainer = GoldSoftContainer,
    onSecondaryContainer = GoldOnContainer,
    tertiary = ElectricYellow,
    onTertiary = NavyDeep,
    background = LightGrayCanvas,
    onBackground = TextDarkPrimary,
    surface = PureWhite,
    onSurface = TextDarkPrimary,
    surfaceVariant = LightGraySurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    error = ArrearsRedLight,
    onError = PureWhite,
    errorContainer = ArrearsRedContainerLight,
    onErrorContainer = ArrearsRedLight,
    outline = SlateBorder
)

private val AlasalDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = NavyDeep,
    primaryContainer = NavyElevated,
    onPrimaryContainer = TextLightPrimary,
    secondary = ElectricYellow,
    onSecondary = NavyDeep,
    secondaryContainer = NavyNightSurfaceVariant,
    onSecondaryContainer = GoldSoftContainer,
    tertiary = ElectricYellowSoft,
    onTertiary = NavyDeep,
    background = NavyNightBackground,
    onBackground = TextLightPrimary,
    surface = NavyNightSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = NavyNightSurfaceVariant,
    onSurfaceVariant = TextLightSecondary,
    error = ArrearsRedDark,
    onError = NavyNightBackground,
    errorContainer = ArrearsRedContainerDark,
    onErrorContainer = ArrearsRedDark,
    outline = SlateBorderDark
)

val AlasalShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun Alasal2027Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AlasalDarkColorScheme else AlasalLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalAlasalColors provides extendedColors,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlasalTypography,
            shapes = AlasalShapes,
            content = content
        )
    }
}
