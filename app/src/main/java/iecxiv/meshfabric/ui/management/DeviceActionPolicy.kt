package iecxiv.meshfabric.ui.management

import java.util.Locale

internal enum class DevicePlatform { WINDOWS, UNIX, UNKNOWN }
internal data class CommandInterpreter(val key:String,val label:String,val type:Int)
internal fun devicePlatform(description:String?):DevicePlatform {
    val os=description.orEmpty().lowercase(Locale.ROOT)
    return when {
        listOf("windows","win32","win64").any { os.contains(it) } -> DevicePlatform.WINDOWS
        listOf("linux","debian","ubuntu","fedora","centos","alpine","proxmox","rocky","red hat","raspbian","openwrt","suse","arch linux","gentoo","macos","mac os","darwin","bsd","solaris","illumos").any { os.contains(it) } -> DevicePlatform.UNIX
        else -> DevicePlatform.UNKNOWN
    }
}
internal fun commandInterpreters(description:String?):List<CommandInterpreter> {
    val shell=CommandInterpreter("shell","Shell",3)
    val cmd=CommandInterpreter("cmd","CMD",0)
    val powershell=CommandInterpreter("powershell","PowerShell",2)
    return when(devicePlatform(description)) {
        DevicePlatform.UNIX -> listOf(shell)
        DevicePlatform.WINDOWS -> listOf(cmd,powershell)
        DevicePlatform.UNKNOWN -> listOf(shell,cmd,powershell)
    }
}
internal fun defaultInterpreterKey(description:String?):String = when(devicePlatform(description)) {
    DevicePlatform.UNIX -> "shell"
    DevicePlatform.WINDOWS -> "cmd"
    DevicePlatform.UNKNOWN -> ""
}
internal fun actionVisible(requiresAgent:Boolean,agentOnline:Boolean):Boolean = !requiresAgent || agentOnline
internal fun needsReviewStep(fieldCount:Int):Boolean = fieldCount>0
