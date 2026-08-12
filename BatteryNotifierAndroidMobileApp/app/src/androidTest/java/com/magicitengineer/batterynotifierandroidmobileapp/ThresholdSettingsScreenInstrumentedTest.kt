package com.magicitengineer.batterynotifierandroidmobileapp

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSettingsState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.ManualSyncUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.MonitoringCommandUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.NotificationPermissionUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.ThresholdSaveUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.thresholdDraftSaveUiState
import com.magicitengineer.batterynotifierandroidmobileapp.ui.theme.BatteryNotifierAndroidMobileAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThresholdSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editingTwentyToTwentyOneIsUnsavedUntilExplicitSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val increaseDescription = context.getString(R.string.threshold_increase_description)
        val unsavedMessage = context.getString(R.string.threshold_unsaved)
        var saveCalls = 0
        var savedValue = 20

        composeRule.setContent {
            BatteryNotifierAndroidMobileAppTheme {
                val draft = remember { mutableIntStateOf(20) }
                val saveState = remember { mutableStateOf(ThresholdSaveUiState.IDLE) }
                SettingsAndSyncScreen(
                    settings = ThresholdSettingsState(thresholdPercent = 20),
                    draftThreshold = draft.intValue,
                    saveState = saveState.value,
                    currentAtOrBelowThreshold = false,
                    syncState = ManualSyncUiState.IDLE,
                    monitoringCommandState = MonitoringCommandUiState.IDLE,
                    notificationPermissionState = NotificationPermissionUiState.ENABLED,
                    onThresholdChanged = { threshold ->
                        draft.intValue = threshold
                        saveState.value = thresholdDraftSaveUiState(
                            savedThreshold = 20,
                            draftThreshold = threshold,
                        )
                    },
                    onSaveThreshold = {
                        saveCalls += 1
                        savedValue = draft.intValue
                        saveState.value = ThresholdSaveUiState.SAVED
                    },
                    onSync = {},
                    onMonitoringToggle = {},
                    onFullChargeNotificationToggle = {},
                    onNotificationPermissionAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(increaseDescription)
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithText(unsavedMessage).assertCountEquals(1)
        composeRule.runOnIdle {
            assertEquals(0, saveCalls)
            assertEquals(20, savedValue)
        }

        composeRule.onNodeWithTag(THRESHOLD_SAVE_BUTTON_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, saveCalls)
            assertEquals(21, savedValue)
        }
    }

    @Test
    fun privacyOptionsActionIsReachableWhenRequired() {
        var privacyOptionsCalls = 0

        composeRule.setContent {
            BatteryNotifierAndroidMobileAppTheme {
                SettingsAndSyncScreen(
                    settings = ThresholdSettingsState(thresholdPercent = 20),
                    draftThreshold = 20,
                    saveState = ThresholdSaveUiState.IDLE,
                    currentAtOrBelowThreshold = false,
                    syncState = ManualSyncUiState.IDLE,
                    monitoringCommandState = MonitoringCommandUiState.IDLE,
                    notificationPermissionState = NotificationPermissionUiState.ENABLED,
                    onThresholdChanged = {},
                    onSaveThreshold = {},
                    onSync = {},
                    onMonitoringToggle = {},
                    onFullChargeNotificationToggle = {},
                    onNotificationPermissionAction = {},
                    showPrivacyOptions = true,
                    onPrivacyOptions = { privacyOptionsCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PRIVACY_OPTIONS_BUTTON_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, privacyOptionsCalls)
        }
    }
}
