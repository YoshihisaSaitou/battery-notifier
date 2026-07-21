package com.magicitengineer.batterynotifierandroidwearapp.presentation

import android.os.Bundle
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.magicitengineer.batterynotifierandroidwearapp.R
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.PhoneStateRequestResult
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.RequestPhoneState
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.Freshness
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayState
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayStateMapper
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.presentation.theme.BatteryNotifierAndroidWearAppTheme
import com.magicitengineer.batterynotifierandroidwearapp.platform.wearable.GooglePlayServicesPhoneStateRequestGateway
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
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        val repository = WearAppContainer.repository(this)
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
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        nowEpochMillis = System.currentTimeMillis()
                        delay(60_000L)
                    }
                }
            }
            val displayState = WearDisplayStateMapper.map(
                    state = persistentState,
                    nowEpochMillis = nowEpochMillis.coerceAtLeast(1L),
                )
            WearApp(
                displayState = displayState,
                retryUiState = retryUiState,
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
            )
        }
    }
}

@Composable
private fun WearApp(
    displayState: WearDisplayState,
    retryUiState: RetryUiState = RetryUiState.IDLE,
    onRetry: () -> Unit = {},
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
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BatteryStateList(
    displayState: WearDisplayState,
    retryUiState: RetryUiState,
    onRetry: () -> Unit,
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
            if (displayState.freshness == Freshness.STALE) {
                item {
                    StateText(
                        stringResource(
                            R.string.delayed_updated,
                            displayState.ageMinutes ?: 0,
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
        }
        if (displayState.incompatibleSchema) {
            item { StateText(stringResource(R.string.incompatible_schema)) }
        }
        if (displayState.clockWarning) {
            item { StateText(stringResource(R.string.clock_warning)) }
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
