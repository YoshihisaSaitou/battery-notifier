package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import java.io.InputStream
import java.io.OutputStream

object WearStateSerializer : Serializer<WearStateProto> {
    override val defaultValue: WearStateProto = WearStateSanitizer.defaultValue()

    override suspend fun readFrom(input: InputStream): WearStateProto = try {
        WearStateSanitizer.sanitize(WearStateProto.parseFrom(input))
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Unable to read WearStateProto", exception)
    }

    override suspend fun writeTo(t: WearStateProto, output: OutputStream) {
        WearStateSanitizer.sanitize(t).writeTo(output)
    }
}
