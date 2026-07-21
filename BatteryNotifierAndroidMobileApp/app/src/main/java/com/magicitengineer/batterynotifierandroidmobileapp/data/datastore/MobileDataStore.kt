package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto

private const val MOBILE_DATA_STORE_FILE_NAME = "battery_notifier_mobile.pb"

val Context.mobileStateDataStore: DataStore<MobileStateProto> by dataStore(
    fileName = MOBILE_DATA_STORE_FILE_NAME,
    serializer = MobileStateSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        MobileStateSanitizer.defaultValue()
    },
)
