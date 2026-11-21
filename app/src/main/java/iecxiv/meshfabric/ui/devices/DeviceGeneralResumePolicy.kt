package iecxiv.meshfabric.ui.devices
internal fun generalResumeRead(stopped:Boolean,connected:Boolean,resumed:Boolean)=stopped && connected && resumed
internal fun generalInterruptedWrite(phase:String,code:String)=phase=="saving" || code=="unconfirmed"
