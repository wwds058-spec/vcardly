package com.yasin.vcardly.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DueTimeTest {
    @Test fun pickerRoundTrip_keepsTheCalendarDay_inAnyZone() {
        val day = LocalDate.of(2026, 3, 10)
        assertEquals(day, DueTime.pickerMillisToDate(DueTime.dateToPickerMillis(day)))
    }

    @Test fun localConversion_respectsZone() {
        val tokyo = DueTime.toEpochMillis(LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), ZoneId.of("Asia/Tokyo"))
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), tokyo)
    }

    @Test fun dstGap_movesForward() {
        val ny = ZoneId.of("America/New_York")
        // 02:30 on 2026-03-08 does not exist in New York
        val ms = DueTime.toEpochMillis(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), ny)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(), ms) // 03:30 EDT
    }

    @Test fun defaultDue_isTomorrowNineLocal() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = Instant.parse("2026-05-01T20:00:00Z") // 01:30 on May 2 in Kolkata
        val expected = DueTime.toEpochMillis(LocalDate.of(2026, 5, 3), LocalTime.of(9, 0), zone)
        assertEquals(expected, DueTime.defaultDue(now, zone))
    }
}
