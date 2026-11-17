package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopControlsBarGeometryTest {
    @Test fun collapsedBarReachesExactTop() {val limit=desktopControlsBarLimit(800f,44,12f);assertEquals(0f,800f-44f-12f+limit,0.001f)}
    @Test fun expandedBarUsesItsOwnHeight() {assertEquals(-732f,desktopControlsBarLimit(800f,56,12f),0.001f)}
    @Test fun differentViewportSizesStillReachTop() {for(h in listOf(300f,600f,1000f)) for(bar in listOf(44,56,96)) {val limit=desktopControlsBarLimit(h,bar,12f);assertEquals(0f,h-bar-12f+limit,0.001f)}}
    @Test fun differentDensitiesIncludeScaledBottomMargin() {for(d in listOf(1f,2f,3f)) {val h=800f*d;val bar=(44*d).toInt();val margin=12*d;assertEquals(0f,h-bar-margin+desktopControlsBarLimit(h,bar,margin),0.001f)}}
    @Test fun unmeasuredOrOversizedBarHasNoUpwardTravel() {assertEquals(0f,desktopControlsBarLimit(600f,0,12f),0f);assertEquals(0f,desktopControlsBarLimit(50f,56,12f),0f)}
    @Test fun invalidGeometryHasNoUpwardTravel() {for(h in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY)) assertEquals(0f,desktopControlsBarLimit(h,44,12f),0f);assertEquals(0f,desktopControlsBarLimit(600f,44,-1f),0f)}
    @Test fun bottomAndMiddlePositionsStayWithinBounds() {val limit=desktopControlsBarLimit(800f,44,12f);assertEquals(744f,800f-44f-12f+0f*limit,0.001f);assertEquals(372f,800f-44f-12f+0.5f*limit,0.001f)}
}
