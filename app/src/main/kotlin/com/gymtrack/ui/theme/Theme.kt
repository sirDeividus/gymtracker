package com.gymtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.gymtrack.R

/** Colores de marca leídos de res/values/brand.xml (único sitio donde se personalizan). */
@Immutable
data class BrandColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val primary: Color,
    val primaryEnd: Color,
    val primaryGradient: Brush,
    val onPrimary: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val silhouette: Color,
    val warning: Color,
    val error: Color,
)

val LocalBrand = staticCompositionLocalOf<BrandColors> { error("BrandColors no proporcionados") }

object Brand {
    val colors: BrandColors
        @Composable get() = LocalBrand.current
}

private val GymTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp),
)

private val GymShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** Títulos de ejercicios y rutinas: pesados y en mayúsculas (aplicar .uppercase() al texto). */
val ExerciseTitleStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp)

/** Cifras que cambian cada segundo: ancho fijo (tabular) para que el texto no "salte". */
val TimerStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/** Números grandes para pesos y repeticiones. */
val NumberStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp)

@Composable
fun GymTrackTheme(content: @Composable () -> Unit) {
    // Tema oscuro por defecto y único (decisión de producto); isSystemInDarkTheme queda para un futuro tema claro.
    @Suppress("UNUSED_VARIABLE") val systemDark = isSystemInDarkTheme()
    val brand = BrandColors(
        background = colorResource(R.color.brand_background),
        surface = colorResource(R.color.brand_surface),
        surfaceHigh = colorResource(R.color.brand_surface_high),
        outline = colorResource(R.color.brand_outline),
        primary = colorResource(R.color.brand_primary),
        primaryEnd = colorResource(R.color.brand_primary_end),
        primaryGradient = Brush.horizontalGradient(listOf(colorResource(R.color.brand_primary), colorResource(R.color.brand_primary_end))),
        onPrimary = colorResource(R.color.brand_on_primary),
        text = colorResource(R.color.brand_text),
        textSecondary = colorResource(R.color.brand_text_secondary),
        textMuted = colorResource(R.color.brand_text_muted),
        silhouette = colorResource(R.color.brand_silhouette),
        warning = colorResource(R.color.brand_warning),
        error = colorResource(R.color.brand_error),
    )
    val scheme = darkColorScheme(
        primary = brand.primary,
        onPrimary = brand.onPrimary,
        primaryContainer = brand.surfaceHigh,
        onPrimaryContainer = brand.primary,
        secondary = brand.primary,
        onSecondary = brand.onPrimary,
        background = brand.background,
        onBackground = brand.text,
        surface = brand.surface,
        onSurface = brand.text,
        surfaceVariant = brand.surfaceHigh,
        onSurfaceVariant = brand.textSecondary,
        surfaceContainer = brand.surface,
        surfaceContainerHigh = brand.surfaceHigh,
        surfaceContainerHighest = brand.surfaceHigh,
        outline = brand.outline,
        outlineVariant = brand.outline,
        error = brand.error,
        onError = brand.text,
    )
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme, typography = GymTypography, shapes = GymShapes, content = content)
    }
}
