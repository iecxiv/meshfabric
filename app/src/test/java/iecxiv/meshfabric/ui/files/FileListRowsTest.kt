package iecxiv.meshfabric.ui.files

import iecxiv.meshfabric.data.model.FileItem
import org.junit.Assert.*
import org.junit.Test

class FileListRowsTest {
    private val first = FileItem(name="recording.mp4", path="/Videos/recording.mp4", size=124630927L, modifiedTime=1792525816L)
    private val second = first.copy(size=2589962L, modifiedTime=1792526121L)

    @Test fun homonymousVideosKeepBothRowsAndTheirOriginalObjects() {
        val rows = fileListRows(listOf(first,second))
        assertEquals(2,rows.size)
        assertEquals(2,rows.map { it.key }.toSet().size)
        assertSame(first,rows[0].item)
        assertSame(second,rows[1].item)
    }
    @Test fun identicalMetadataStillHasUniqueKeysWithoutDroppingEntries() {
        val input = List(36) { first.copy() }
        val rows = fileListRows(input)
        assertEquals(input,rows.map { it.item })
        assertEquals(36,rows.map { it.key }.toSet().size)
    }
    @Test fun keysAreDeterministicAndStableWhenDistinctRowsAreReordered() {
        val forward = fileListRows(listOf(first,second))
        val reverse = fileListRows(listOf(second,first))
        assertEquals(forward,fileListRows(listOf(first,second)))
        assertEquals(forward[0].key,reverse[1].key)
        assertEquals(forward[1].key,reverse[0].key)
    }
    @Test fun filteringAfterKeyAssignmentRetainsDuplicateIdentity() {
        val rows = fileListRows(listOf(first,first.copy()))
        assertEquals(rows.map { it.key },rows.filter { it.item.name.contains("record") }.map { it.key })
    }
    @Test fun windowsAndLinuxPathsAndEmptyListsAreUnchanged() {
        val input = listOf(FileItem(name="C:",isDir=true,path="C:/"),FileItem(name="log",path="/var/log"))
        assertEquals(input,fileListRows(input).map { it.item })
        assertTrue(fileListRows(emptyList()).isEmpty())
    }
    @Test fun delimiterCharactersCannotCreateIdentityCollisions() {
        val input = listOf(first.copy(path="a|b",name="c"),first.copy(path="a",name="b|c"))
        assertEquals(2,fileListRows(input).map { it.key }.toSet().size)
    }
}
