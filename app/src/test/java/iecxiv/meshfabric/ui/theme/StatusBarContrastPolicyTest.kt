package iecxiv.meshfabric.ui.theme

import org.junit.Assert.*
import org.junit.Test

class StatusBarContrastPolicyTest {
    @Test fun whiteBackgroundUsesDarkIcons() {assertTrue(statusBarUsesDarkIcons(1f))}
    @Test fun blackBackgroundUsesLightIcons() {assertFalse(statusBarUsesDarkIcons(0f))}
    @Test fun lightBackgroundsUseDarkIcons() {for(l in listOf(0.4f,0.7f,0.95f)) assertTrue(statusBarUsesDarkIcons(l))}
    @Test fun darkBackgroundsUseLightIcons() {for(l in listOf(0.01f,0.03f,0.1f)) assertFalse(statusBarUsesDarkIcons(l))}
    @Test fun crossoverSelectsHigherContrast() {assertFalse(statusBarUsesDarkIcons(0.17f));assertTrue(statusBarUsesDarkIcons(0.18f))}
    @Test fun switchingBackgroundRecalculatesAppearance() {assertEquals(listOf(true,false,true,false),listOf(1f,0f,0.9f,0.02f).map(::statusBarUsesDarkIcons))}
    @Test fun invalidLuminanceHasDeterministicFallback() {for(l in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1f,2f)) assertFalse(statusBarUsesDarkIcons(l))}
}
