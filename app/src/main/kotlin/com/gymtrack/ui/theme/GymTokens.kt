package com.gymtrack.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Alias con los nombres del sistema de diseño. Los valores reales viven en res/values/brand.xml
// (así la marca sigue siendo configurable en un solo sitio).

/** #070708 — fondo de pantalla. */
val DeepBlack: Color @Composable get() = Brand.colors.background

/** #141416 — tarjetas Bento. */
val CardGrey: Color @Composable get() = Brand.colors.surface

/** #232326 — bordes finos. */
val StrokeGrey: Color @Composable get() = Brand.colors.outline

/** #71717A — etiquetas secundarias (11 sp, mayúsculas, letterSpacing 1,5 sp). */
val TextMuted: Color @Composable get() = Brand.colors.textMuted

/** #FF1E46 — acento de alta intensidad. */
val ElectricRed: Color @Composable get() = Brand.colors.primary

/** #FF1E46 → #FF4D6D — solo para cápsulas de acción principal. */
val PremiumRedGradient: Brush @Composable get() = Brand.colors.primaryGradient
