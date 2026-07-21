package com.magicitengineer.batterynotifierandroidwearapp.data.wearable

import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem

object DataMapValueReader {
    fun read(dataItem: DataItem): Map<String, Any?> {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
        return dataMap.keySet().associateWith { key -> dataMap[key] }
    }
}
