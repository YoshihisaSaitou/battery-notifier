package com.magicitengineer.batterynotifierandroidwearapp.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionUiStateTest {
    @Test
    fun runtimePermissionStartsAtApi33() {
        assertEquals(false, notificationRuntimePermissionRequired(32))
        assertEquals(true, notificationRuntimePermissionRequired(33))
    }

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
}
