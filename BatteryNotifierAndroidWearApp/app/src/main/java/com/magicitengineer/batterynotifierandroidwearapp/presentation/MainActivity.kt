package com.magicitengineer.batterynotifierandroidwearapp.presentation

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.magicitengineer.batterynotifierandroidwearapp.R
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.PhoneStateRequestResult
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.RequestPhoneState
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeStatus
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.Freshness
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayState
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayStateMapper
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearThresholdDisplayPolicy
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.presentation.theme.BatteryNotifierAndroidWearAppTheme
import com.magicitengineer.batterynotifierandroidwearapp.platform.wearable.GooglePlayServicesPhoneStateRequestGateway
import com.magicitengineer.batterynotifierandroidwearapp.platform.notification.BATTERY_ALERT_CHANNEL_ID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RetryUiState {
    IDLE,
    SENDING,
    SENT,
    NO_REACHABLE_NODE,
    FAILED,
}

class MainActivity : ComponentActivity() {
    private val repository by lazy { WearAppContainer.repository(this) }
    private val notificationDelivery by lazy {
        WearAppContainer.notificationDelivery(this)
    }
    private val thresholdSettingsController by lazy {
        WearAppContainer.thresholdSettingsController(this)
    }
    private var notificationsEnabled by mutableStateOf(false)
    private var batteryAlertChannelDisabled = false
    private var thresholdWriterAvailable by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        refreshNotificationPermissionState()
        lifecycleScope.launch {
            repository.recoverInterruptedThresholdChange()
        }
        val requestPhoneState = RequestPhoneState(
            GooglePlayServicesPhoneStateRequestGateway(this)
        )

        setContent {
            val persistentState by repository.state.collectAsStateWithLifecycle(
                initialValue = WearPersistentState()
            )
            val lifecycleOwner = LocalLifecycleOwner.current
            val coroutineScope = rememberCoroutineScope()
            var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var retryUiState by remember { mutableStateOf(RetryUiState.IDLE) }
            var notificationRetryInProgress by remember { mutableStateOf(false) }
            var thresholdEditing by remember { mutableStateOf(false) }
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        nowEpochMillis = System.currentTimeMillis()
                        delay(60_000L)
                    }
                }
            }
            LaunchedEffect(persistentState.phoneStateReceivedAtEpochMillis) {
                nowEpochMillis = System.currentTimeMillis()
            }
            val mappedDisplayState = WearDisplayStateMapper.map(
                    state = persistentState,
                    nowEpochMillis = nowEpochMillis.coerceAtLeast(1L),
                )
            val displayState = mappedDisplayState.copy(
                thresholdPercent =
                    WearThresholdDisplayPolicy.effectiveThresholdPercent(persistentState)
            )
            val notificationPermissionState = notificationPermissionUiState(
                notificationsEnabled = notificationsEnabled,
                requestPreviouslyCompleted = persistentState.notificationPermissionRequested,
            )
            WearApp(
                displayState = displayState,
                retryUiState = retryUiState,
                notificationPermissionState = notificationPermissionState,
                onRetry = {
                    if (retryUiState != RetryUiState.SENDING) {
                        retryUiState = RetryUiState.SENDING
                        coroutineScope.launch {
                            retryUiState = when (requestPhoneState()) {
                                PhoneStateRequestResult.SENT -> RetryUiState.SENT
                                PhoneStateRequestResult.NO_REACHABLE_NODE ->
                                    RetryUiState.NO_REACHABLE_NODE

                                PhoneStateRequestResult.API_UNAVAILABLE,
                                PhoneStateRequestResult.FAILED -> RetryUiState.FAILED
                            }
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
                        NotificationPermissionUiState.ENABLED -> Unit
                        NotificationPermissionUiState.SETTINGS_REQUIRED ->
                            openNotificationSettings()
                    }
                },
                notificationRetryInProgress = notificationRetryInProgress,
                onNotificationRetry = {
                    if (!notificationRetryInProgress) {
                        notificationRetryInProgress = true
                        coroutineScope.launch {
                            try {
                                notificationDelivery.retry(
                                    state = persistentState,
                                    nowEpochMillis = System.currentTimeMillis().coerceAtLeast(1L),
                                )
                            } finally {
                                notificationRetryInProgress = false
                            }
                        }
                    }
                },
                thresholdDraftPercent = persistentState.thresholdDraftPercent
                    ?: displayState.thresholdPercent,
                thresholdChangeStatus = persistentState.thresholdChangeStatus,
                thresholdWriterAvailable = thresholdWriterAvailable,
                thresholdEditing = thresholdEditing,
                onThresholdEdit = {
                    thresholdEditing = true
                    coroutineScope.launch {
                        thresholdWriterAvailable =
                            thresholdSettingsController.isAvailable()
                    }
                },
                onThresholdDraftChange = { value ->
                    coroutineScope.launch {
                        thresholdSettingsController.updateDraft(value)
                    }
                },
                onThresholdSave = {
                    thresholdEditing = false
                    coroutineScope.launch {
                        thresholdSettingsController.save()
                    }
                },
                onThresholdRetry = {
                    coroutineScope.launch {
                        thresholdSettingsController.retry()
                    }
                },
                onThresholdCancel = {
                    thresholdEditing = false
                    coroutineScope.launch {
                        thresholdSettingsController.cancel()
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationPermissionState()
        lifecycleScope.launch {
            thresholdWriterAvailable = thresholdSettingsController.isAvailable()
        }
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
                repository.markNotificationPermissionRequested()
            }
            refreshNotificationPermissionState()
        }
    }

    private fun refreshNotificationPermissionState() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(BATTERY_ALERT_CHANNEL_ID)
        batteryAlertChannelDisabled = channel?.importance == NotificationManager.IMPORTANCE_NONE
        notificationsEnabled = batteryAlertNotificationsEnabled(
            runtimePermissionGranted =
                !notificationRuntimePermissionRequired(Build.VERSION.SDK_INT) ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
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
                    putExtra(Settings.EXTRA_CHANNEL_ID, BATTERY_ALERT_CHANNEL_ID)
                }
            }
        )
    }

    private companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}

@Composable
private fun WearApp(
    displayState: WearDisplayState,
    retryUiState: RetryUiState = RetryUiState.IDLE,
    notificationPermissionState: NotificationPermissionUiState =
        NotificationPermissionUiState.ENABLED,
    onRetry: () -> Unit = {},
    onNotificationPermissionAction: () -> Unit = {},
    notificationRetryInProgress: Boolean = false,
    onNotificationRetry: () -> Unit = {},
    thresholdDraftPercent: Int? = displayState.thresholdPercent,
    thresholdChangeStatus: ThresholdChangeStatus = ThresholdChangeStatus.IDLE,
    thresholdWriterAvailable: Boolean = false,
    thresholdEditing: Boolean = false,
    onThresholdEdit: () -> Unit = {},
    onThresholdDraftChange: (Int) -> Unit = {},
    onThresholdSave: () -> Unit = {},
    onThresholdRetry: () -> Unit = {},
    onThresholdCancel: () -> Unit = {},
) {
    BatteryNotifierAndroidWearAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
        ) {
            TimeText()
            BatteryStateList(
                displayState = displayState,
                retryUiState = retryUiState,
                notificationPermissionState = notificationPermissionState,
                onRetry = onRetry,
                onNotificationPermissionAction = onNotificationPermissionAction,
                notificationRetryInProgress = notificationRetryInProgress,
                onNotificationRetry = onNotificationRetry,
                thresholdDraftPercent = thresholdDraftPercent,
                thresholdChangeStatus = thresholdChangeStatus,
                thresholdWriterAvailable = thresholdWriterAvailable,
                thresholdEditing = thresholdEditing,
                onThresholdEdit = onThresholdEdit,
                onThresholdDraftChange = onThresholdDraftChange,
                onThresholdSave = onThresholdSave,
                onThresholdRetry = onThresholdRetry,
                onThresholdCancel = onThresholdCancel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BatteryStateList(
    displayState: WearDisplayState,
    retryUiState: RetryUiState,
    notificationPermissionState: NotificationPermissionUiState,
    onRetry: () -> Unit,
    onNotificationPermissionAction: () -> Unit,
    notificationRetryInProgress: Boolean,
    onNotificationRetry: () -> Unit,
    thresholdDraftPercent: Int?,
    thresholdChangeStatus: ThresholdChangeStatus,
    thresholdWriterAvailable: Boolean,
    thresholdEditing: Boolean,
    onThresholdEdit: () -> Unit,
    onThresholdDraftChange: (Int) -> Unit,
    onThresholdSave: () -> Unit,
    onThresholdRetry: () -> Unit,
    onThresholdCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val batteryDescription = displayState.levelPercent?.let {
        stringResource(R.string.phone_battery_percent, it)
    } ?: stringResource(R.string.phone_battery_no_data)
    val retryStatusText = when (retryUiState) {
        RetryUiState.IDLE,
        RetryUiState.SENDING -> null

        RetryUiState.SENT -> stringResource(R.string.retry_sent)
        RetryUiState.NO_REACHABLE_NODE -> stringResource(R.string.retry_no_connection)
        RetryUiState.FAILED -> stringResource(R.string.retry_failed)
    }
    ScalingLazyColumn(
        modifier = modifier.semantics { contentDescription = batteryDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = displayState.levelPercent?.let {
                    stringResource(R.string.battery_short, it)
                } ?: stringResource(R.string.no_data_short),
                style = MaterialTheme.typography.display1,
                textAlign = TextAlign.Center,
            )
        }
        if (displayState.levelPercent == null) {
            item { StateText(stringResource(R.string.no_phone_data)) }
            item { StateText(stringResource(R.string.open_phone_app_hint)) }
        } else {
            item {
                StateText(
                    stringResource(
                        if (displayState.isCharging) R.string.charging else R.string.discharging
                    )
                )
            }
            item { FreshnessText(displayState) }
            if (
                displayState.freshness == Freshness.STALE &&
                displayState.ageMinutes != null
            ) {
                item {
                    StateText(
                        stringResource(
                            R.string.delayed_updated,
                            displayState.ageMinutes,
                        )
                    )
                }
            }
            if (!displayState.monitoringEnabled) {
                item { StateText(stringResource(R.string.monitoring_off)) }
            }
            displayState.thresholdPercent?.let { threshold ->
                item { StateText(stringResource(R.string.threshold_value, threshold)) }
            }
            if (thresholdEditing && thresholdDraftPercent != null) {
                item {
                    StateText(
                        stringResource(
                            R.string.threshold_draft_value,
                            thresholdDraftPercent,
                        )
                    )
                }
                item {
                    Button(
                        onClick = {
                            onThresholdDraftChange(
                                (thresholdDraftPercent - 1).coerceAtLeast(5)
                            )
                        },
                        enabled = thresholdDraftPercent > 5,
                    ) {
                        Text(stringResource(R.string.decrease_threshold))
                    }
                }
                item {
                    Button(
                        onClick = {
                            onThresholdDraftChange(
                                (thresholdDraftPercent + 1).coerceAtMost(100)
                            )
                        },
                        enabled = thresholdDraftPercent < 100,
                    ) {
                        Text(stringResource(R.string.increase_threshold))
                    }
                }
                item {
                    Button(
                        onClick = onThresholdSave,
                        enabled = thresholdWriterAvailable,
                    ) {
                        Text(stringResource(R.string.save_threshold))
                    }
                }
                if (!thresholdWriterAvailable) {
                    item {
                        StateText(stringResource(R.string.threshold_writer_unavailable))
                    }
                }
                item {
                    Button(onClick = onThresholdCancel) {
                        Text(stringResource(R.string.cancel_threshold_change))
                    }
                }
            } else {
                ThresholdChangeStatusText(thresholdChangeStatus)
                when (thresholdChangeStatus) {
                    ThresholdChangeStatus.SENDING -> Unit
                    ThresholdChangeStatus.WAITING_RESULT,
                    ThresholdChangeStatus.SEND_FAILED,
                    ThresholdChangeStatus.APPLIED_WAITING_STATE -> {
                        item {
                            Button(onClick = onThresholdRetry) {
                                Text(stringResource(R.string.retry_threshold_change))
                            }
                        }
                        item {
                            Button(onClick = onThresholdCancel) {
                                Text(stringResource(R.string.cancel_threshold_change))
                            }
                        }
                    }

                    ThresholdChangeStatus.IDLE,
                    ThresholdChangeStatus.APPLIED,
                    ThresholdChangeStatus.CONFLICT,
                    ThresholdChangeStatus.REJECTED -> {
                        item {
                            Button(onClick = onThresholdEdit) {
                                Text(stringResource(R.string.change_threshold))
                            }
                        }
                    }
                }
            }
        }
        if (displayState.incompatibleSchema) {
            item { StateText(stringResource(R.string.incompatible_schema)) }
        }
        if (displayState.clockWarning) {
            item { StateText(stringResource(R.string.clock_warning)) }
        }
        if (displayState.notificationPermissionMissing) {
            item { StateText(stringResource(R.string.notification_permission_missing)) }
        }
        if (displayState.notificationDeliveryFailed) {
            item { StateText(stringResource(R.string.notification_delivery_failed)) }
        }
        if (displayState.notificationRetryExhausted) {
            item { StateText(stringResource(R.string.notification_retry_exhausted)) }
        }
        if (displayState.notificationRetryAvailable) {
            item {
                Button(
                    onClick = onNotificationRetry,
                    enabled = !notificationRetryInProgress,
                ) {
                    Text(
                        text = stringResource(
                            if (notificationRetryInProgress) {
                                R.string.retrying_notification
                            } else {
                                R.string.retry_notification
                            }
                        )
                    )
                }
            }
        }
        item {
            StateText(
                stringResource(
                    when (notificationPermissionState) {
                        NotificationPermissionUiState.ENABLED ->
                            R.string.notification_permission_enabled
                        NotificationPermissionUiState.REQUEST_AVAILABLE ->
                            R.string.notification_permission_not_requested
                        NotificationPermissionUiState.SETTINGS_REQUIRED ->
                            R.string.notification_permission_settings_required
                    }
                )
            )
        }
        if (notificationPermissionState != NotificationPermissionUiState.ENABLED) {
            item { StateText(stringResource(R.string.notification_permission_description)) }
            item {
                Button(onClick = onNotificationPermissionAction) {
                    Text(
                        text = stringResource(
                            if (
                                notificationPermissionState ==
                                NotificationPermissionUiState.REQUEST_AVAILABLE
                            ) {
                                R.string.notification_permission_request_action
                            } else {
                                R.string.notification_permission_settings_action
                            }
                        )
                    )
                }
            }
        }
        if (
            displayState.levelPercent == null ||
            displayState.freshness == Freshness.STALE
        ) {
            item {
                Button(
                    onClick = onRetry,
                    enabled = retryUiState != RetryUiState.SENDING,
                ) {
                    Text(
                        text = stringResource(
                            if (retryUiState == RetryUiState.SENDING) {
                                R.string.retrying_sync
                            } else {
                                R.string.retry_sync
                            }
                        )
                    )
                }
            }
            retryStatusText?.let { text ->
                item { StateText(text) }
            }
        }
    }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.ThresholdChangeStatusText(
    status: ThresholdChangeStatus,
) {
    when (status) {
        ThresholdChangeStatus.IDLE -> Unit
        ThresholdChangeStatus.SENDING -> item {
            StateText(stringResource(R.string.threshold_checking_phone))
        }
        ThresholdChangeStatus.WAITING_RESULT -> item {
            StateText(stringResource(R.string.threshold_waiting_result))
        }
        ThresholdChangeStatus.SEND_FAILED -> item {
            StateText(stringResource(R.string.threshold_not_saved))
        }
        ThresholdChangeStatus.APPLIED_WAITING_STATE -> item {
            StateText(stringResource(R.string.threshold_saved_confirming_sync))
        }
        ThresholdChangeStatus.APPLIED -> item {
            StateText(stringResource(R.string.threshold_saved))
        }
        ThresholdChangeStatus.CONFLICT -> item {
            StateText(stringResource(R.string.threshold_conflict))
        }
        ThresholdChangeStatus.REJECTED -> item {
            StateText(stringResource(R.string.threshold_rejected))
        }
    }
}

@Composable
private fun FreshnessText(displayState: WearDisplayState) {
    val text = when (displayState.freshness) {
        Freshness.NO_DATA -> stringResource(R.string.no_phone_data)
        Freshness.FRESH -> stringResource(R.string.fresh_updated)
        Freshness.DELAYED -> stringResource(
            R.string.delayed_updated,
            displayState.ageMinutes ?: 0,
        )

        Freshness.STALE -> stringResource(R.string.stale_data)
    }
    StateText(text)
}

@Composable
private fun StateText(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption1,
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun FreshPreview() {
    WearApp(
        WearDisplayState(
            freshness = Freshness.FRESH,
            levelPercent = 68,
            isCharging = true,
            thresholdPercent = 20,
            monitoringEnabled = true,
        )
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun StalePreview() {
    WearApp(
        WearDisplayState(
            freshness = Freshness.STALE,
            levelPercent = 68,
            thresholdPercent = 20,
            monitoringEnabled = false,
            ageMinutes = 6,
        )
    )
}
