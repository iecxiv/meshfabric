package iecxiv.meshfabric.ui.management
import com.google.gson.JsonElement
internal enum class ServerEventsIssueKind { FRAME_SIZE, JSON, ENVELOPE, IDENTITY, HISTORY_SHAPE, RECORD_SHAPE, FIELD_TYPE, FIELD_LENGTH, INTERNAL }
internal enum class ServerEventsField(val wire:String) { ACTION("action"), TAG("tag"), MSG("msg"), USERNAME("username"), NODEID("nodeid"), NODENAME("nodename"), RESULT("result"), ERROR("error"), ERR("err"), EVENTS("events"), USERINFO("userinfo"), SITEADMIN("siteadmin"), UNKNOWN("") }
internal enum class ServerEventsValueType { MISSING, NULL, OBJECT, ARRAY, STRING, NUMBER, BOOLEAN }
internal data class ServerEventsIssue(val kind:ServerEventsIssueKind,val field:ServerEventsField=ServerEventsField.UNKNOWN,val type:ServerEventsValueType=ServerEventsValueType.MISSING,val length:Int?=null) {
    fun safeCode()=listOfNotNull(kind.name,field.takeUnless {it==ServerEventsField.UNKNOWN}?.name,type.name,length?.let {"length=$it"}).joinToString(";")
}
internal class ServerEventsDecodeException(val issue:ServerEventsIssue):IllegalArgumentException(issue.safeCode())
internal fun eventValueType(value:JsonElement?):ServerEventsValueType=when {
    value==null->ServerEventsValueType.MISSING
    value.isJsonNull->ServerEventsValueType.NULL
    value.isJsonObject->ServerEventsValueType.OBJECT
    value.isJsonArray->ServerEventsValueType.ARRAY
    value.asJsonPrimitive.isString->ServerEventsValueType.STRING
    value.asJsonPrimitive.isNumber->ServerEventsValueType.NUMBER
    else->ServerEventsValueType.BOOLEAN
}
internal fun eventDecodeFailure(kind:ServerEventsIssueKind,key:String="",value:JsonElement?=null,length:Int?=null):Nothing {
    throw ServerEventsDecodeException(ServerEventsIssue(kind,ServerEventsField.entries.firstOrNull {it.wire==key} ?: ServerEventsField.UNKNOWN,eventValueType(value),length))
}
