package com.magicitengineer.batterynotifierandroidmobileapp.platform.identity

import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.EventIdFactory
import java.util.UUID

object RandomUuidEventIdFactory : EventIdFactory {
    override fun create(): String = UUID.randomUUID().toString()
}
