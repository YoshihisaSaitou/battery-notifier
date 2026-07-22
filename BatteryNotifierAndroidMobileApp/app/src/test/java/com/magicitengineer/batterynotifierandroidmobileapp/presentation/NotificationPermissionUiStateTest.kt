package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionUiStateTest {
    @Test
    fun enabledTakesPrecedenceOverRequestHistory() {
        assertEquals(
            NotificationPermissionUiState.ENABLED,
            notificationPermissionUiState(
                notificationsEnabled = true,
                requestPreviouslyCompleted = true,
            ),
        )
    }

    @Test
    fun firstUserActionMayLaunchTheRuntimeRequest() {
        assertEquals(
            NotificationPermissionUiState.REQUEST_AVAILABLE,
            notificationPermissionUiState(
                notificationsEnabled = false,
                requestPreviouslyCompleted = false,
            ),
        )
    }

    @Test
    fun denialOrDismissalUsesSettingsInsteadOfRepeatingTheDialog() {
        assertEquals(
            NotificationPermissionUiState.SETTINGS_REQUIRED,
            notificationPermissionUiState(
                notificationsEnabled = false,
                requestPreviouslyCompleted = true,
            ),
        )
    }

    @Test
    fun disabledBatteryAlertChannelIsNotReportedAsEnabled() {
        assertEquals(
            false,
            batteryAlertNotificationsEnabled(
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                batteryAlertChannelEnabled = false,
            ),
        )
    }
}
