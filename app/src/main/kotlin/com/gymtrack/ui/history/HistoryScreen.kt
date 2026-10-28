package com.gymtrack.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.domain.Formatting
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.theme.Brand
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HistoryScreen(vm: HistoryViewModel, contentPadding: PaddingValues, onOpenSession: (String) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHeader("Historial", subtitle = if (st.items.isEmpty()) null else "${st.items.size} sesiones")
        if (st.items.isEmpty() && !st.loading) {
            EmptyState(
                Icons.Default.History, "Todavía no hay entrenamientos",
                "Cuando finalices tu primera sesión aparecerá aquí con todas sus series, pesos y repeticiones.",
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                MonthCalendar(
                    month = st.month, trained = st.trainedDays, selected = st.selectedDay,
                    onPrev = vm::previousMonth, onNext = vm::nextMonth, onSelect = vm::selectDay,
                )
            }
            if (st.selectedDay != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.dayLong(st.selectedDay!!.atStartOfDay(Dates.zone).toInstant().toEpochMilli()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearDay) { Text("Ver todas", color = Brand.colors.primary) }
                    }
                }
            }
            if (st.visible.isEmpty()) {
                item { Text("No hay sesiones ese día.", style = MaterialTheme.typography.bodyMedium, color = Brand.colors.textSecondary) }
            }
            items(st.visible, key = { it.session.id }) { item ->
                HistoryCard(item, st.settings.unit, onClick = { onOpenSession(item.session.id) })
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, unit: com.gymtrack.domain.WeightUnit, onClick: () -> Unit) {
    val c = Brand.colors
    val s = item.session
    val dur = ((s.endedAt ?: s.startedAt) - s.startedAt) / 1000
    GymCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.routineName, style = MaterialTheme.typography.titleMedium)
                Text("${Dates.day(s.startedAt)} · ${Dates.time(s.startedAt)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Text(Dates.shortDuration(dur), style = MaterialTheme.typography.titleMedium, color = c.primary)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${item.exerciseCount} ejercicios", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text("${item.totals.completedSets} series", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            if (item.totals.volumeKg > 0) {
                Text(Formatting.weight(item.totals.volumeKg, unit), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    trained: Set<LocalDate>,
    selected: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val c = Brand.colors
    val today = LocalDate.now()
    GymCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.size(MinTouch)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior") }
            Text(Dates.month(month.atDay(1)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = onNext, modifier = Modifier.size(MinTouch)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            }
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val did = date in trained
                            val sel = date == selected
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(
                                        when {
                                            sel -> c.primary
                                            did -> c.primary.copy(alpha = 0.18f)
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        },
                                    )
                                    .let { if (date == today && !sel) it.border(1.5.dp, c.primary, CircleShape) else it }
                                    .let { if (did) it.clickable { onSelect(date) } else it }
                                    .semantics { contentDescription = "$dayNum" + if (did) ", con entrenamiento" else "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$dayNum", style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        sel -> c.onPrimary
                                        did -> c.primary
                                        date == today -> c.text
                                        else -> c.textSecondary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Text("Los días con entrenamiento aparecen resaltados; púlsalos para filtrar.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}
