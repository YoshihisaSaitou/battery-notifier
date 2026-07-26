package com.magicitengineer.batterynotifierandroidwearapp.presentation

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magicitengineer.batterynotifierandroidwearapp.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoDataStringInstrumentedTest {
    @Test
    fun englishNoDataStringsUseOneLiteralPercentSign() {
        val context = localizedContext(Locale.ENGLISH)

        assertEquals("Phone battery --%", context.getString(R.string.phone_battery_no_data))
        assertEquals("--%", context.getString(R.string.no_data_short))
    }

    @Test
    fun japaneseNoDataStringsUseOneLiteralPercentSign() {
        val context = localizedContext(Locale.JAPANESE)

        assertEquals(
            "スマートフォンの電池残量--%",
            context.getString(R.string.phone_battery_no_data),
        )
        assertEquals("--%", context.getString(R.string.no_data_short))
    }

    private fun localizedContext(locale: Locale): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
