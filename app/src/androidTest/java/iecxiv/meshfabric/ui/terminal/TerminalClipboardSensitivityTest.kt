package iecxiv.meshfabric.ui.terminal

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalClipboardSensitivityTest {
    @Test fun copyAllStillCreatesSensitivePlainText() {
        val clip = terminalCopyClip("terminal output\nsecond line")
        assertEquals("Terminal", clip.description.label.toString())
        assertEquals(1, clip.itemCount)
        assertEquals("terminal output\nsecond line", clip.getItemAt(0).text.toString())
        assertTrue(requireNotNull(clip.description.extras).getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test fun selectionEntryIsMarkedSensitiveWithoutChangingContent() {
        val clip = ClipData.newPlainText("selection", "selected terminal output")
        val entry = ClipEntry(clip)
        assertSame(entry, terminalSensitiveClipEntry(entry))
        assertSame(clip, entry.clipData)
        assertEquals("selection", clip.description.label.toString())
        assertEquals("selected terminal output", clip.getItemAt(0).text.toString())
        assertTrue(requireNotNull(clip.description.extras).getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test fun sensitivityPreservesAllItemsAndExistingMetadata() {
        val clip = ClipData.newPlainText("selection", "first")
        clip.addItem(ClipData.Item("second"))
        val metadata = PersistableBundle().apply { putString("source", "terminal-test") }
        clip.description.extras = metadata
        terminalSensitiveClipEntry(ClipEntry(clip))
        assertEquals(2, clip.itemCount)
        assertEquals("first", clip.getItemAt(0).text.toString())
        assertEquals("second", clip.getItemAt(1).text.toString())
        val extras = requireNotNull(clip.description.extras)
        assertEquals("terminal-test", extras.getString("source"))
        assertTrue(extras.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test fun anExplicitNonSensitiveFlagIsOverriddenForTerminalSelection() {
        val clip = ClipData.newPlainText("selection", "terminal output")
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", false)
        }
        terminalSensitiveClipEntry(ClipEntry(clip))
        assertTrue(requireNotNull(clip.description.extras).getBoolean("android.content.extra.IS_SENSITIVE"))
    }
}
