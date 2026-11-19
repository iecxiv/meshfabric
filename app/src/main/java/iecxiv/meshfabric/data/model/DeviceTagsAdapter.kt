package iecxiv.meshfabric.data.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class DeviceTagsAdapter : TypeAdapter<List<String>?>() {
    override fun read(reader: JsonReader): List<String>? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return null
        }
        val tags = linkedSetOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.STRING) {
                reader.nextString().trim().takeIf { it.isNotEmpty() }?.let { tags.add(it) }
            } else reader.skipValue()
        }
        reader.endArray()
        return tags.toList()
    }

    override fun write(writer: JsonWriter, value: List<String>?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginArray()
        value.forEach { writer.value(it) }
        writer.endArray()
    }
}
