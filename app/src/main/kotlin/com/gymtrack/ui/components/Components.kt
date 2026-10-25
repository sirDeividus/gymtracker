package com.gymtrack.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymtrack.domain.Formatting
import com.gymtrack.ui.theme.Brand
import com.gymtrack.ui.theme.NumberStyle

val ScreenPadding = 16.dp

private val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Respuesta táctil inmediata: escala sutil mientras se pulsa (~130 ms, ease-out). Usa graphicsLayer,
 * así que no mueve ni redimensiona nada a su alrededor.
 */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 130, easing = EaseOutQuint),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
val MinTouch = 48.dp

@Composable
fun GymCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    radius: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Brand.colors
    val shape = RoundedCornerShape(radius)
    val source = remember { MutableInteractionSource() }
    val base = (if (onClick != null) modifier.pressScale(source) else modifier)
        .clip(shape)
        .background(c.surface)
        .border(BorderStroke(1.dp, if (highlighted) c.primary.copy(alpha = 0.6f) else c.outline), shape)
        .let { if (onClick != null) it.clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick) else it }
    Column(base.padding(contentPadding), content = content)
}

/** Botón principal grande, cómodo con una sola mano. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Brand.colors
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.pressScale(source).heightIn(min = 60.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.primary, contentColor = c.onPrimary,
            disabledContainerColor = c.surfaceHigh, disabledContentColor = c.textSecondary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Botón cápsula con PremiumRedGradient para la acción principal (ENTRENAR / INICIAR ENTRENAMIENTO).
 * El degradado se pinta con background + clip(CircleShape) sobre un Button transparente y sin elevación,
 * de modo que el área táctil, el ripple y la forma coinciden exactamente.
 */
@Composable
fun GradientCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = Icons.Default.PlayArrow,
) {
    val c = Brand.colors
    val source = remember { MutableInteractionSource() }
    val fill: Brush = if (enabled) c.primaryGradient else SolidColor(c.surfaceHigh)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = CircleShape,
        elevation = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = c.onGradient,
            disabledContainerColor = Color.Transparent, disabledContentColor = c.textMuted,
        ),
        modifier = modifier.pressScale(source).heightIn(min = MinTouch).clip(CircleShape).background(fill, CircleShape),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val c = Brand.colors
    val color = if (destructive) c.error else c.text
    val source = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.pressScale(source).heightIn(min = MinTouch),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (destructive) c.error.copy(alpha = 0.6f) else c.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Brand.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = if (onBack != null) 4.dp else ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(MinTouch)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Brand.colors.textSecondary)
            }
        }
        actions()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Brand.colors.textSecondary, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Brand.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    GymCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.colors.textSecondary)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Brand.colors.textSecondary)
    }
}

@Composable
fun <T> ChipSelector(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Brand.colors
    androidx.compose.foundation.lazy.LazyRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = ScreenPadding),
    ) {
        items(options.size) { i ->
            val o = options[i]
            val isSel = o in selected
            FilterChip(
                selected = isSel,
                onClick = { onToggle(o) },
                label = { Text(label(o)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = c.surface,
                    labelColor = c.text,
                    selectedContainerColor = c.primary,
                    selectedLabelColor = c.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = isSel,
                    borderColor = c.outline, selectedBorderColor = c.primary,
                ),
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = "Cancelar",
) {
    val c = Brand.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(title) },
        text = { Text(message, color = c.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(confirmText, color = if (destructive) c.error else c.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(dismissText, color = c.text) }
        },
    )
}

/**
 * Campo numérico de edición rápida: selecciona todo al enfocar y no pelea con el estado externo
 * (mientras escribes "42," no se reescribe el texto).
 */
@Composable
fun NumberInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    enabled: Boolean = true,
    highlight: Boolean = false,
    textStyle: TextStyle = NumberStyle,
    description: String? = null,
) {
    val c = Brand.colors
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (parseNumber(tfv.text, decimal) != parseNumber(value, decimal)) tfv = TextFieldValue(value)
    }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = tfv,
        onValueChange = { new ->
            val filtered = new.text.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }
            tfv = new.copy(text = filtered)
            onValueChange(filtered)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = if (enabled) c.text else c.textSecondary, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
            .heightIn(min = MinTouch)
            .onFocusChanged { if (it.isFocused) tfv = tfv.copy(selection = TextRange(0, tfv.text.length)) }
            .semantics { if (description != null) contentDescription = description },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = MinTouch)
                    .clip(shape)
                    .background(c.surfaceHigh)
                    .border(1.dp, if (highlight) c.warning else c.outline, shape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (tfv.text.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = c.textSecondary.copy(alpha = 0.6f)), textAlign = TextAlign.Center)
                }
                inner()
            }
        },
    )
}

private fun parseNumber(text: String, decimal: Boolean): Double? =
    if (decimal) Formatting.parseDecimal(text) else text.trim().toIntOrNull()?.toDouble()

/** Gráfico de línea sencillo sin dependencias. Muestra valores reales o nada. */
@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    description: String,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    formatValue: (Double) -> String = { Formatting.number(it) },
    startLabel: String? = null,
    endLabel: String? = null,
) {
    val c = Brand.colors
    if (values.isEmpty()) return
    val min = values.min()
    val max = values.max()
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth()) {
            Text("Mín ${formatValue(min)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text("Máx ${formatValue(max)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val pad = 8.dp.toPx()
            val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
            fun x(i: Int) = if (values.size == 1) w / 2 else pad + (w - 2 * pad) * i / (values.size - 1)
            fun y(v: Double) = (h - pad - ((v - min) / range * (h - 2 * pad))).toFloat()
            for (g in 0..2) {
                val gy = pad + (h - 2 * pad) * g / 2f
                drawLine(c.outline, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            }
            if (values.size > 1) {
                val path = Path()
                values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                drawPath(path, c.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            values.forEachIndexed { i, v ->
                drawCircle(c.background, radius = 6.dp.toPx(), center = Offset(x(i), y(v)))
                drawCircle(c.primary, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(startLabel.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text(endLabel.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

/** Barras verticales con etiqueta y valor encima. */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    description: String,
    height: androidx.compose.ui.unit.Dp = 120.dp,
    formatValue: (Double) -> String = { Formatting.number(it, 0) },
) {
    val c = Brand.colors
    if (values.isEmpty()) return
    val max = values.max().takeIf { it > 0 } ?: 1.0
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatValue(v), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height((height * (v / max).toFloat()).coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (v > 0) c.primary else c.outline),
                )
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

