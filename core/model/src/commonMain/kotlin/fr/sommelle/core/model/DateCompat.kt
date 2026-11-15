package fr.sommelle.core.model

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/*
 * Couche de compatibilité multiplateforme remplaçant `java.time` par `kotlinx.datetime` et `kotlin.time.Duration`.
 */

fun LocalDate.plusDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.plusDays(days: Int): LocalDate = plusDays(days.toLong())

fun LocalDate.minusDays(days: Long): LocalDate = minus(days, DateTimeUnit.DAY)

fun LocalDate.minusDays(days: Int): LocalDate = minusDays(days.toLong())

fun LocalDate.isBefore(other: LocalDate): Boolean = this < other

fun LocalDate.isAfter(other: LocalDate): Boolean = this > other

fun LocalDate.toEpochDay(): Long = toEpochDays().toLong()

fun LocalDate.daysTo(other: LocalDate): Int = daysUntil(other)

fun LocalDate.Companion.of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)

fun LocalDate.Companion.ofEpochDay(epochDay: Long): LocalDate = fromEpochDays(epochDay.toInt())

fun LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalTime.Companion.of(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)

val LocalTime.Companion.MIDNIGHT: LocalTime get() = LocalTime(0, 0)

val LocalTime.Companion.NOON: LocalTime get() = LocalTime(12, 0)

fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime(year, monthNumber, dayOfMonth, hour, minute)

fun LocalDate.atTime(time: LocalTime): LocalDateTime = LocalDateTime.of(this, time)

fun LocalTime.plusMinutes(minutes: Long): LocalTime {
    val total = (((toSecondOfDay() + minutes * 60) % 86_400) + 86_400) % 86_400
    return LocalTime((total / 3600).toInt(), ((total % 3600) / 60).toInt(), (total % 60).toInt(), nanosecond)
}

fun LocalTime.minusMinutes(minutes: Long): LocalTime = plusMinutes(-minutes)

fun LocalTime.plusHours(hours: Long): LocalTime = plusMinutes(hours * 60)

fun LocalTime.minusHours(hours: Long): LocalTime = plusMinutes(-hours * 60)

fun LocalTime.minus(duration: Duration): LocalTime = minusMinutes(duration.inWholeMinutes)

fun LocalTime.isBefore(other: LocalTime): Boolean = this < other

fun LocalTime.isAfter(other: LocalTime): Boolean = this > other

fun LocalDateTime.Companion.of(date: LocalDate, time: LocalTime): LocalDateTime =
    LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, time.hour, time.minute, time.second, time.nanosecond)

fun LocalDateTime.Companion.of(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime =
    LocalDateTime(year, month, day, hour, minute)

fun LocalDateTime.toLocalDate(): LocalDate = date

fun LocalDateTime.toLocalTime(): LocalTime = time

fun LocalDateTime.plusDays(days: Long): LocalDateTime = LocalDateTime.of(date.plusDays(days), time)

fun LocalDateTime.plusDays(days: Int): LocalDateTime = plusDays(days.toLong())

fun LocalDateTime.plusMinutes(minutes: Long): LocalDateTime {
    val tz = TimeZone.UTC
    return (toInstant(tz) + minutes.minutes).toLocalDateTime(tz)
}

fun LocalDateTime.minusMinutes(minutes: Long): LocalDateTime = plusMinutes(-minutes)

fun LocalDateTime.plusHours(hours: Long): LocalDateTime = plusMinutes(hours * 60)

fun LocalDateTime.minusHours(hours: Long): LocalDateTime = plusMinutes(-hours * 60)

fun LocalDateTime.isBefore(other: LocalDateTime): Boolean = this < other

fun LocalDateTime.isAfter(other: LocalDateTime): Boolean = this > other

fun LocalDateTime.Companion.now(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun durationBetween(start: LocalDateTime, end: LocalDateTime): Duration {
    val tz = TimeZone.UTC
    return end.toInstant(tz) - start.toInstant(tz)
}

fun durationBetween(start: LocalTime, end: LocalTime): Duration {
    val startMin = start.hour * 60 + start.minute
    val endMin = end.hour * 60 + end.minute
    return (endMin - startMin).minutes
}

fun Duration.Companion.ofMinutes(minutes: Long): Duration = minutes.minutes

fun Duration.Companion.ofHours(hours: Long): Duration = hours.hours

fun Duration.Companion.between(start: LocalDateTime, end: LocalDateTime): Duration = durationBetween(start, end)

fun Duration.Companion.between(start: LocalTime, end: LocalTime): Duration = durationBetween(start, end)

val Duration.isNegative: Boolean get() = this < Duration.ZERO

val Duration.isZero: Boolean get() = this == Duration.ZERO

fun Duration.toMinutes(): Long = inWholeMinutes

fun Duration.toHours(): Long = inWholeHours

fun Duration.minusMinutes(minutes: Long): Duration = this - minutes.minutes

fun Duration.plusMinutes(minutes: Long): Duration = this + minutes.minutes

fun Duration.abs(): Duration = absoluteValue

val Month.value: Int get() = number

val DayOfWeek.value: Int get() = isoDayNumber

val LocalDate.monthValue: Int get() = monthNumber
