package com.magicitengineer.batterynotifierandroidwearapp.application.sync

enum class PhoneStateRequestResult {
    SENT,
    NO_REACHABLE_NODE,
    API_UNAVAILABLE,
    FAILED,
}

fun interface PhoneStateRequestGateway {
    suspend fun requestCurrentState(): PhoneStateRequestResult
}

class RequestPhoneState(
    private val gateway: PhoneStateRequestGateway,
) {
    suspend operator fun invoke(): PhoneStateRequestResult = gateway.requestCurrentState()
}
