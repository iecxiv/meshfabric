package iecxiv.meshfabric.ui.theme

internal fun statusBarUsesDarkIcons(backgroundLuminance:Float):Boolean {
    if(!backgroundLuminance.isFinite() || backgroundLuminance !in 0f..1f) return false
    val blackContrast=(backgroundLuminance+0.05f)/0.05f
    val whiteContrast=1.05f/(backgroundLuminance+0.05f)
    return blackContrast>=whiteContrast
}
