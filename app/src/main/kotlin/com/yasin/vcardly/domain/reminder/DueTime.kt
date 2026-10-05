package com.yasin.vcardly.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Date/time conversions for the follow-up form. */
object DueTime {
    /** Material's DatePicker reports midnight UTC of the chosen day; read it in UTC or the day shifts in negative-offset zones. */
    fun pickerMillisToDate(pickerMillis: Long): LocalDate =
        Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun dateToPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Local wall-clock to epoch millis. A time that does not exist (DST gap) moves forward. */
    fun toEpochMillis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()

    /** Tomorrow at 09:00 local: a sensible default for a new follow-up. */
    fun defaultDue(now: Instant, zone: ZoneId): Long =
        toEpochMillis(now.atZone(zone).toLocalDate().plusDays(1), LocalTime.of(9, 0), zone)
}
