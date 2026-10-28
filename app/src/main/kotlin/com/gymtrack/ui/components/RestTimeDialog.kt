package com.gymtrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymtrack.ui.theme.Brand

/** Opciones típicas de descanso con el valor actual incluido (p. ej. 1:15), ordenadas. */
fun restOptions(base: List<Int>, current: Int): List<Int> = (base + current).distinct().sorted()

/** Descanso a medida: minutos y segundos escritos directamente. */
@Composable
fun RestTimeDialog(
    title: String,
    initialSec: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    minSec: Int = 0,
    maxSec: Int = 1800,
) {
    val c = Brand.colors
    var minutes by remember { mutableStateOf((initialSec / 60).toString()) }
    var seconds by remember { mutableStateOf((initialSec % 60).toString()) }
    val total = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
    val valid = total in minSec..maxSec
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Minutos", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        NumberInput(minutes, "0", { minutes = it.take(2) }, Modifier.fillMaxWidth(), decimal = false, description = "Minutos")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Segundos", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        NumberInput(seconds, "0", { seconds = it.take(2) }, Modifier.fillMaxWidth(), decimal = false, description = "Segundos")
                    }
                }
                Text(
                    if (valid) "Total: ${total / 60}:${"%02d".format(total % 60)}" else "Debe estar entre ${minSec} s y ${maxSec / 60} min",
                    style = MaterialTheme.typography.bodySmall, color = if (valid) c.textSecondary else c.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(total) }, enabled = valid, modifier = Modifier.height(MinTouch)) { Text("Guardar", color = c.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.height(MinTouch)) { Text("Cancelar", color = c.text) } },
    )
}
