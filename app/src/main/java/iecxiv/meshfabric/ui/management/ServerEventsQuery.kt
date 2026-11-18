package iecxiv.meshfabric.ui.management
internal val serverEventLimitChoices=listOf(60,120,250,500,1000,0)
internal enum class ServerEventType(val wire:String) {
    ALL(""), AGENT("agentlog"), RELAY("relaylog"), MANUAL("manual"), COMMAND("runcommands"), UPLOAD("batchupload"), CHANGE("changenode"), REMOVE("removenode");
    companion object { fun fromAction(action:String)=entries.firstOrNull {it.wire==action} ?: ALL }
}
internal data class ServerEventsQuery(val type:ServerEventType=ServerEventType.ALL,val limit:Int?=60,val nodeId:String?=null) {
    init {
        require(limit==null || limit in listOf(60,120,200,250,500,1000))
        require(nodeId==null || (nodeId.startsWith("node/") && nodeId.length<=4096 && nodeId.split('/').size==3 && nodeId.substringAfterLast('/').isNotBlank() && !nodeId.contains('\u0000')))
    }
}
internal val legacyServerEventsQuery=ServerEventsQuery(limit=SERVER_EVENTS_LIMIT)
