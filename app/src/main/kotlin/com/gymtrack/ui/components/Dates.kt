package com.gymtrack.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val es = Locale("es", "ES")
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", es)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)
    private val dayYear = DateTimeFormatter.ofPattern("d MMM yyyy", es)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", es)
    private val time = DateTimeFormatter.ofPattern("HH:mm", es)
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun day(ms: Long): String = day.format(Instant.ofEpochMilli(ms).atZone(zone)).replaceFirstChar { it.uppercase() }
    fun dayLong(ms: Long): String = dayLong.format(Instant.ofEpochMilli(ms).atZone(zone)).replaceFirstChar { it.uppercase() }
    fun dayYear(ms: Long): String = dayYear.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun month(date: LocalDate): String = month.format(date).replaceFirstChar { it.uppercase() }
    fun time(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "Hoy", "Ayer", "Hace 3 días" o la fecha. */
    fun relative(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(localDate(ms), localDate(nowMs))
        return when {
            days <= 0 -> "Hoy"
            days == 1L -> "Ayer"
            days < 7 -> "Hace $days días"
            else -> dayYear(ms)
        }
    }

    fun shortDuration(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return when {
            h > 0 -> "${h} h ${m} min"
            m > 0 -> "$m min"
            else -> "${totalSec} s"
        }
    }
}
