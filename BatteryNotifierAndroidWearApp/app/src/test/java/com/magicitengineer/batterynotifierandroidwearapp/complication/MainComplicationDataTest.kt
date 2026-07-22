package com.magicitengineer.batterynotifierandroidwearapp.complication

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
