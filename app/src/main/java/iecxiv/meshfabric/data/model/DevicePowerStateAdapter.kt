package iecxiv.meshfabric.data.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.math.BigDecimal

class DevicePowerStateAdapter : TypeAdapter<Int?>() {
    override fun read(reader: JsonReader): Int? {
        if (reader.peek() != JsonToken.NUMBER) {
            reader.skipValue()
            return null
        }
        val raw = reader.nextString()
        return runCatching { BigDecimal(raw).intValueExact().takeIf { it in 0..8 } }.getOrNull()
    }

    override fun write(writer: JsonWriter, value: Int?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}
