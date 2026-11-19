package iecxiv.meshfabric.data.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.math.BigDecimal

class DeviceTimestampAdapter : TypeAdapter<Long?>() {
    override fun read(reader: JsonReader): Long? {
        if (reader.peek() != JsonToken.NUMBER) {
            reader.skipValue()
            return null
        }
        val raw = reader.nextString()
        return runCatching { BigDecimal(raw).longValueExact().takeIf { it > 0L } }.getOrNull()
    }

    override fun write(writer: JsonWriter, value: Long?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}
