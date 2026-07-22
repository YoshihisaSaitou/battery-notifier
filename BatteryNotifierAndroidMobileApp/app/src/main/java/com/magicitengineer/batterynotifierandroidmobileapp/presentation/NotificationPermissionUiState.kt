package com.magicitengineer.batterynotifierandroidmobileapp.presentation

enum class NotificationPermissionUiState {
    ENABLED,
    REQUEST_AVAILABLE,
    SETTINGS_REQUIRED,
}

fun notificationPermissionUiState(
    notificationsEnabled: Boolean,
    requestPreviouslyCompleted: Boolean,
): NotificationPermissionUiState = when {
    notificationsEnabled -> NotificationPermissionUiState.ENABLED
    requestPreviouslyCompleted -> NotificationPermissionUiState.SETTINGS_REQUIRED
    else -> NotificationPermissionUiState.REQUEST_AVAILABLE
}
