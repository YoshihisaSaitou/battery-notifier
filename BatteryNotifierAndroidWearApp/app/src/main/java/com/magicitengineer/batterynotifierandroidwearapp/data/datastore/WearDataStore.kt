package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto

private const val WEAR_DATA_STORE_FILE_NAME = "battery_notifier_wear.pb"

val Context.wearStateDataStore: DataStore<WearStateProto> by dataStore(
    fileName = WEAR_DATA_STORE_FILE_NAME,
    serializer = WearStateSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        WearStateSanitizer.defaultValue()
    },
)
