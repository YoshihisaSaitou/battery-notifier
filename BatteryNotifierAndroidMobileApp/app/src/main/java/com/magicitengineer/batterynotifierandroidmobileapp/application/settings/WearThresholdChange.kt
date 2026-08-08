package com.magicitengineer.batterynotifierandroidmobileapp.application.settings

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeRequest

fun interface WearThresholdChangeProcessor {
    suspend fun process(request: ThresholdChangeRequest): ThresholdChangeProcessingResult
}

class RepositoryWearThresholdChangeProcessor(
    private val repository: MobileStateRepository,
) : WearThresholdChangeProcessor {
    override suspend fun process(
        request: ThresholdChangeRequest,
    ): ThresholdChangeProcessingResult =
        repository.applyThresholdChangeRequest(request)
}
