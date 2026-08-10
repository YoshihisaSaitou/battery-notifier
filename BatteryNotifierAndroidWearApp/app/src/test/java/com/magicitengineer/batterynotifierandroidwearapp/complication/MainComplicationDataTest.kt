package com.magicitengineer.batterynotifierandroidwearapp.complication

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.magicitengineer.batterynotifierandroidwearapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MainComplicationDataTest {
    @Test
    fun `short text visibly distinguishes charging and delayed states`() {
        val charging = buildBatteryComplicationData(
            ComplicationType.SHORT_TEXT,
            68,
            "68%",
            "Phone battery 68%, Charging",
            "Charging",
        ) as ShortTextComplicationData
        val delayed = buildBatteryComplicationData(
            ComplicationType.SHORT_TEXT,
            68,
            "68%",
            "Phone battery 68%, Updated 3 min ago",
            "Updated 3 min ago",
        ) as ShortTextComplicationData

        assertNotNull(charging.title)
        assertNotNull(delayed.title)
        assertNotEquals(charging.title, delayed.title)
    }

    @Test
    fun `ranged value includes visible status title`() {
        val data = buildBatteryComplicationData(
            ComplicationType.RANGED_VALUE,
            68,
            "68%",
            "Phone battery 68%, Charging",
            "Charging",
        )

        assertTrue(data is RangedValueComplicationData)
        assertNotNull((data as RangedValueComplicationData).title)
    }

    @Test
    fun `long text includes percentage and visible status fields`() {
        val data = buildBatteryComplicationData(
            ComplicationType.LONG_TEXT,
            68,
            "68%",
            "Phone battery 68%, Charging",
            "Charging",
        )

        assertTrue(data is LongTextComplicationData)
        assertNotNull((data as LongTextComplicationData).title)
    }

    @Test
    fun `icon selection uses fixed low boundary and charging precedence`() {
        val fixtures = listOf(
            Triple(21, false, R.drawable.ic_complication_battery_full_24),
            Triple(20, false, R.drawable.ic_complication_battery_alert_24),
            Triple(0, false, R.drawable.ic_complication_battery_alert_24),
            Triple(20, true, R.drawable.ic_complication_battery_charging_full_24),
            Triple(100, true, R.drawable.ic_complication_battery_charging_full_24),
        )

        fixtures.forEach { (level, isCharging, expectedDrawable) ->
            assertEquals(
                expectedDrawable,
                batteryComplicationIconRes(level, isCharging),
            )
        }
    }

    @Test
    fun `relative age text changes at six ten and sixty minutes`() {
        val receivedAt = 1_000_000L
        val text = relativeAgeComplicationText(receivedAt, "Updated ^1 ago")
        val atSixMinutes = Instant.ofEpochMilli(receivedAt + 6 * 60_000L)
        val atTenMinutes = Instant.ofEpochMilli(receivedAt + 10 * 60_000L)
        val atSixtyMinutes = Instant.ofEpochMilli(receivedAt + 60 * 60_000L)

        assertFalse(text.returnsSameText(atSixMinutes, atTenMinutes))
        assertFalse(text.returnsSameText(atTenMinutes, atSixtyMinutes))
    }

    @Test
    fun `stale short and ranged data descriptions include warning and dynamic age`() {
        val receivedAt = 1_000_000L
        val atSixMinutes = Instant.ofEpochMilli(receivedAt + 6 * 60_000L)
        val atTenMinutes = Instant.ofEpochMilli(receivedAt + 10 * 60_000L)
        val description = relativeAgeComplicationText(
            receivedAt,
            "Phone battery 68%, data may be outdated, updated ^1 ago",
        )

        val returnedDescriptions = listOf(
            buildBatteryComplicationData(
                ComplicationType.SHORT_TEXT,
                68,
                "68%!",
                description,
                visibleStatus = description,
            ) as ShortTextComplicationData,
            buildBatteryComplicationData(
                ComplicationType.RANGED_VALUE,
                68,
                "68%!",
                description,
                visibleStatus = description,
            ) as RangedValueComplicationData,
            buildBatteryComplicationData(
                ComplicationType.LONG_TEXT,
                68,
                "68%!",
                description,
                visibleStatus = description,
            ) as LongTextComplicationData,
        ).map { data ->
            when (data) {
                is ShortTextComplicationData -> data.contentDescription
                is RangedValueComplicationData -> data.contentDescription
                is LongTextComplicationData -> data.contentDescription
                else -> null
            }
        }

        returnedDescriptions.forEach { returnedDescription ->
            assertNotNull(returnedDescription)
            assertSame(description, returnedDescription)
            assertFalse(
                requireNotNull(returnedDescription)
                    .returnsSameText(atSixMinutes, atTenMinutes),
            )
        }
    }
}
