package com.magicitengineer.batterynotifierandroidwearapp.application.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestPhoneStateTest {
    @Test
    fun `returns the gateway result without changing its meaning`() {
        PhoneStateRequestResult.entries.forEach { expected ->
            val request = RequestPhoneState { expected }

            val actual = kotlinx.coroutines.runBlocking { request() }

            assertEquals(expected, actual)
        }
    }
}
