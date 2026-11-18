package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopKeyDisplayLabelsTest {
    @Test fun localizedKeysHaveDistinctDisplayResources() {
        val keys=listOf(16,8,13,19,20,32,33,34,35,36,37,38,39,40,44,45,46,144,145)
        val resources=keys.map { key->requireNotNull(desktopKeyDisplayResource(key)) }
        assertEquals(19,resources.toSet().size)
    }
    @Test fun technicalAndUnknownKeysKeepTheExistingFallback() {
        for(key in listOf(17,18,91,9,27,48,65,112,135,255)) {
            assertNull(desktopKeyDisplayResource(key))
        }
        assertEquals("Ctrl",desktopKeyLabel(17))
        assertEquals("F1",desktopKeyLabel(112))
        assertEquals("VK 255",desktopKeyLabel(255))
    }
    @Test fun localizationDoesNotChangeTheStoredShortcutFormat() {
        val shortcut=DesktopShortcut(listOf(17,46))
        assertEquals("Ctrl + Delete",shortcut.label)
        val encoded=DesktopShortcuts.encode(listOf(shortcut))
        assertEquals("17,46",encoded)
        assertEquals(listOf(shortcut),DesktopShortcuts.decode(encoded))
    }
}
