package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import java.io.InputStream
import java.io.OutputStream

object MobileStateSerializer : Serializer<MobileStateProto> {
    override val defaultValue: MobileStateProto = MobileStateSanitizer.defaultValue()

    override suspend fun readFrom(input: InputStream): MobileStateProto {
        try {
            return MobileStateSanitizer.sanitize(MobileStateProto.parseFrom(input))
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Unable to read MobileStateProto", exception)
        }
    }

    override suspend fun writeTo(t: MobileStateProto, output: OutputStream) {
        MobileStateSanitizer.sanitize(t).writeTo(output)
    }
}
