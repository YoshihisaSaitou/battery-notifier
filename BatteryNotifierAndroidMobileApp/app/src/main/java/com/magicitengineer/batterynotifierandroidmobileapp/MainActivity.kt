package com.magicitengineer.batterynotifierandroidmobileapp

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSettingsState
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.ManualSyncUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.MonitoringCommandUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.NotificationPermissionUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.ThresholdSaveUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.notificationPermissionUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.batteryAlertNotificationsEnabled
import com.magicitengineer.batterynotifierandroidmobileapp.platform.notification.AndroidMobileAlertNotificationFactory
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.toManualSyncUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.toMonitoringCommandUiState
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.toUiResult
import com.magicitengineer.batterynotifierandroidmobileapp.ui.theme.BatteryNotifierAndroidMobileAppTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var notificationsEnabled by mutableStateOf(false)
    private var batteryAlertChannelDisabled = false
    private val thresholdSettingsController by lazy {
        MobileAppContainer.thresholdSettingsController(this)
    }
    private val monitoringController by lazy {
        MobileAppContainer.monitoringController(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshNotificationPermissionState()
        enableEdgeToEdge()
        setContent {
            BatteryNotifierAndroidMobileAppTheme {
                val scope = rememberCoroutineScope()
                val loadedSettings by thresholdSettingsController.state.collectAsState(
                    initial = null
                )
                if (loadedSettings == null) {
                    LoadingScreen()
                } else {
                    val settings = requireNotNull(loadedSettings)
                    var draftThreshold by rememberSaveable(settings.thresholdPercent) {
                        mutableIntStateOf(settings.thresholdPercent)
                    }
                    var saveState by rememberSaveable {
                        mutableStateOf(ThresholdSaveUiState.IDLE)
                    }
                    var currentAtOrBelowThreshold by rememberSaveable { mutableStateOf(false) }
                    var syncState by rememberSaveable {
                        mutableStateOf(ManualSyncUiState.IDLE)
                    }
                    var monitoringCommandState by rememberSaveable {
                        mutableStateOf(MonitoringCommandUiState.IDLE)
                    }
                    val notificationPermissionState = notificationPermissionUiState(
                        notificationsEnabled = notificationsEnabled,
                        requestPreviouslyCompleted = settings.notificationPermissionRequested,
                    )

                    SettingsAndSyncScreen(
                        settings = settings,
                        draftThreshold = draftThreshold,
                        saveState = saveState,
                        currentAtOrBelowThreshold = currentAtOrBelowThreshold,
                        syncState = syncState,
                        monitoringCommandState = monitoringCommandState,
                        notificationPermissionState = notificationPermissionState,
                        onThresholdChanged = { threshold ->
                            draftThreshold = threshold
                            saveState = ThresholdSaveUiState.IDLE
                            currentAtOrBelowThreshold = false
                        },
                        onSaveThreshold = {
                            saveState = ThresholdSaveUiState.SAVING
                            scope.launch {
                                val result = thresholdSettingsController
                                    .saveThreshold(draftThreshold)
                                    .toUiResult()
                                saveState = result.state
                                currentAtOrBelowThreshold = result.currentAtOrBelowThreshold
                            }
                        },
                        onSync = {
                            syncState = ManualSyncUiState.SYNCING
                            scope.launch {
                                syncState = MobileAppContainer
                                    .runtimeTriggerHandler(this@MainActivity)
                                    .onManualSync()
                                    .toManualSyncUiState()
                            }
                        },
                        onMonitoringToggle = {
                            scope.launch {
                                if (settings.monitoringEnabled) {
                                    monitoringCommandState = MonitoringCommandUiState.STOPPING
                                    monitoringCommandState = monitoringController
                                        .stopMonitoring()
                                        .toMonitoringCommandUiState()
                                } else {
                                    monitoringCommandState = MonitoringCommandUiState.STARTING
                                    monitoringCommandState = monitoringController
                                        .startMonitoring()
                                        .toMonitoringCommandUiState()
                                }
                            }
                        },
                        onNotificationPermissionAction = {
                            when (notificationPermissionState) {
                                NotificationPermissionUiState.REQUEST_AVAILABLE ->
                                    requestPermissions(
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                        REQUEST_NOTIFICATION_PERMISSION,
                                    )
                                NotificationPermissionUiState.ENABLED,
                                NotificationPermissionUiState.SETTINGS_REQUIRED ->
                                    openNotificationSettings()
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationPermissionState()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            lifecycleScope.launch {
                thresholdSettingsController.markNotificationPermissionRequested()
            }
            refreshNotificationPermissionState()
        }
    }

    private fun refreshNotificationPermissionState() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(
            AndroidMobileAlertNotificationFactory.CHANNEL_ID
        )
        batteryAlertChannelDisabled = channel?.importance == NotificationManager.IMPORTANCE_NONE
        notificationsEnabled = batteryAlertNotificationsEnabled(
            runtimePermissionGranted = checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED,
            appNotificationsEnabled = manager.areNotificationsEnabled(),
            batteryAlertChannelEnabled = !batteryAlertChannelDisabled,
        )
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(
                if (batteryAlertChannelDisabled) {
                    Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                } else {
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                }
            ).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                if (batteryAlertChannelDisabled) {
                    putExtra(
                        Settings.EXTRA_CHANNEL_ID,
                        AndroidMobileAlertNotificationFactory.CHANNEL_ID,
                    )
                }
            }
        )
    }

    private companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}

@Composable
private fun LoadingScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Text(
            text = stringResource(R.string.settings_loading),
            modifier = Modifier.padding(innerPadding).padding(24.dp),
        )
    }
}

@Composable
private fun SettingsAndSyncScreen(
    settings: ThresholdSettingsState,
    draftThreshold: Int,
    saveState: ThresholdSaveUiState,
    currentAtOrBelowThreshold: Boolean,
    syncState: ManualSyncUiState,
    monitoringCommandState: MonitoringCommandUiState,
    notificationPermissionState: NotificationPermissionUiState,
    onThresholdChanged: (Int) -> Unit,
    onSaveThreshold: () -> Unit,
    onSync: () -> Unit,
    onMonitoringToggle: () -> Unit,
    onNotificationPermissionAction: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.monitoring_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(text = stringResource(R.string.monitoring_description))
            Text(
                text = stringResource(
                    when {
                        settings.monitoringEnabled -> R.string.monitoring_status_active
                        settings.resumeRequired -> R.string.monitoring_status_resume_required
                        else -> R.string.monitoring_status_stopped
                    },
                ),
            )
            Button(
                enabled = monitoringCommandState != MonitoringCommandUiState.STARTING &&
                    monitoringCommandState != MonitoringCommandUiState.STOPPING,
                onClick = onMonitoringToggle,
            ) {
                Text(
                    text = stringResource(
                        when {
                            monitoringCommandState == MonitoringCommandUiState.STARTING ->
                                R.string.monitoring_starting
                            monitoringCommandState == MonitoringCommandUiState.STOPPING ->
                                R.string.monitoring_stopping
                            settings.monitoringEnabled -> R.string.monitoring_stop_action
                            settings.resumeRequired -> R.string.monitoring_resume_action
                            else -> R.string.monitoring_start_action
                        },
                    ),
                )
            }
            Text(text = stringResource(monitoringCommandState.messageResource()))

            HorizontalDivider()

            Text(
                text = stringResource(R.string.notification_permission_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(text = stringResource(R.string.notification_permission_description))
            Text(
                text = stringResource(
                    when (notificationPermissionState) {
                        NotificationPermissionUiState.ENABLED ->
                            R.string.notification_permission_enabled
                        NotificationPermissionUiState.REQUEST_AVAILABLE ->
                            R.string.notification_permission_not_requested
                        NotificationPermissionUiState.SETTINGS_REQUIRED ->
                            R.string.notification_permission_settings_required
                    }
                ),
            )
            Button(onClick = onNotificationPermissionAction) {
                Text(
                    text = stringResource(
                        when (notificationPermissionState) {
                            NotificationPermissionUiState.REQUEST_AVAILABLE ->
                                R.string.notification_permission_request_action
                            NotificationPermissionUiState.ENABLED,
                            NotificationPermissionUiState.SETTINGS_REQUIRED ->
                                R.string.notification_permission_settings_action
                        }
                    )
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.threshold_settings_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(text = stringResource(R.string.threshold_settings_description))
            Text(
                text = stringResource(R.string.threshold_value, draftThreshold),
                style = MaterialTheme.typography.headlineSmall,
            )
            Slider(
                value = draftThreshold.toFloat(),
                onValueChange = { onThresholdChanged(it.roundToInt()) },
                valueRange = AlertRule.MIN_THRESHOLD_PERCENT.toFloat()..
                    AlertRule.MAX_THRESHOLD_PERCENT.toFloat(),
                steps = THRESHOLD_SLIDER_STEPS,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val decreaseDescription = stringResource(R.string.threshold_decrease_description)
                val increaseDescription = stringResource(R.string.threshold_increase_description)
                Button(
                    modifier = Modifier.semantics {
                        contentDescription = decreaseDescription
                    },
                    enabled = draftThreshold > AlertRule.MIN_THRESHOLD_PERCENT,
                    onClick = { onThresholdChanged(draftThreshold - 1) },
                ) {
                    Text(stringResource(R.string.threshold_decrease_action))
                }
                Button(
                    modifier = Modifier.semantics {
                        contentDescription = increaseDescription
                    },
                    enabled = draftThreshold < AlertRule.MAX_THRESHOLD_PERCENT,
                    onClick = { onThresholdChanged(draftThreshold + 1) },
                ) {
                    Text(stringResource(R.string.threshold_increase_action))
                }
            }
            Button(
                enabled = saveState != ThresholdSaveUiState.SAVING &&
                    draftThreshold != settings.thresholdPercent,
                onClick = onSaveThreshold,
            ) {
                Text(
                    text = if (saveState == ThresholdSaveUiState.SAVING) {
                        stringResource(R.string.threshold_saving)
                    } else {
                        stringResource(R.string.threshold_save_action)
                    },
                )
            }
            Text(text = stringResource(saveState.messageResource()))
            when (settings.mobileNotificationDisposition) {
                MobileNotificationDisposition.PERMISSION_DENIED -> Text(
                    text = stringResource(R.string.mobile_notification_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                )
                MobileNotificationDisposition.FAILED -> Text(
                    text = stringResource(R.string.mobile_notification_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                MobileNotificationDisposition.NONE,
                MobileNotificationDisposition.POSTED -> Unit
            }
            if (currentAtOrBelowThreshold) {
                Text(
                    text = stringResource(R.string.threshold_currently_below),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.mobile_sync_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(text = stringResource(R.string.mobile_sync_description))
            Button(
                enabled = syncState != ManualSyncUiState.SYNCING,
                onClick = onSync,
            ) {
                Text(
                    text = if (syncState == ManualSyncUiState.SYNCING) {
                        stringResource(R.string.mobile_sync_in_progress)
                    } else {
                        stringResource(R.string.mobile_sync_action)
                    },
                )
            }
            Text(text = stringResource(syncState.messageResource()))
        }
    }
}

private fun ThresholdSaveUiState.messageResource(): Int = when (this) {
    ThresholdSaveUiState.IDLE -> R.string.threshold_save_idle
    ThresholdSaveUiState.SAVING -> R.string.threshold_saving
    ThresholdSaveUiState.SAVED -> R.string.threshold_saved
    ThresholdSaveUiState.SAVED_SYNC_PENDING -> R.string.threshold_saved_sync_pending
    ThresholdSaveUiState.SAVED_SYNC_FAILED -> R.string.threshold_saved_sync_failed
    ThresholdSaveUiState.INVALID_THRESHOLD -> R.string.threshold_invalid
}

private fun ManualSyncUiState.messageResource(): Int = when (this) {
    ManualSyncUiState.IDLE -> R.string.mobile_sync_idle
    ManualSyncUiState.SYNCING -> R.string.mobile_sync_in_progress
    ManualSyncUiState.SUCCESS -> R.string.mobile_sync_success
    ManualSyncUiState.FAILED -> R.string.mobile_sync_failed
    ManualSyncUiState.BATTERY_UNAVAILABLE -> R.string.mobile_sync_battery_unavailable
    ManualSyncUiState.INVALID_BATTERY_INPUT -> R.string.mobile_sync_invalid_battery
}

private fun MonitoringCommandUiState.messageResource(): Int = when (this) {
    MonitoringCommandUiState.IDLE -> R.string.monitoring_command_idle
    MonitoringCommandUiState.STARTING -> R.string.monitoring_starting
    MonitoringCommandUiState.ACTIVE -> R.string.monitoring_started
    MonitoringCommandUiState.STOPPING -> R.string.monitoring_stopping
    MonitoringCommandUiState.STOPPED -> R.string.monitoring_stopped
    MonitoringCommandUiState.START_FAILED -> R.string.monitoring_start_failed
}

private const val THRESHOLD_SLIDER_STEPS = 94
