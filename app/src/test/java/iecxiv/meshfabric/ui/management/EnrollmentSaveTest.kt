package iecxiv.meshfabric.ui.management
import java.io.*
import org.junit.Assert.*
import org.junit.Test
class EnrollmentSaveTest {
    private fun file(bytes:ByteArray)=File.createTempFile("enrollment-test-",".bin").apply {writeBytes(bytes);deleteOnExit()}
    @Test fun nonEmptySaveIsReadBackAndVerified() {val bytes=ByteArray(131073) {(it%251).toByte()};val f=file(bytes);try {val out=ByteArrayOutputStream();val receipt=copyEnrollmentSave(f,{out},{true});assertEquals(bytes.size.toLong(),receipt.size);assertArrayEquals(bytes,out.toByteArray());verifyEnrollmentSave(receipt,{ByteArrayInputStream(out.toByteArray())},{true})} finally {f.delete()}}
    @Test fun missingSourceNeverOpensDestination() {val f=file(byteArrayOf(1));f.delete();var opened=false;assertThrows(EnrollmentSaveFailure::class.java) {copyEnrollmentSave(f,{opened=true;ByteArrayOutputStream()},{true})};assertFalse(opened)}
    @Test fun emptySourceNeverOpensDestination() {val f=file(byteArrayOf());var opened=false;try {assertThrows(EnrollmentSaveFailure::class.java) {copyEnrollmentSave(f,{opened=true;ByteArrayOutputStream()},{true})};assertFalse(opened)} finally {f.delete()}}
    @Test fun identityMismatchNeverOpensDestination() {val f=file(byteArrayOf(1,2,3));var opened=false;try {assertThrows(EnrollmentSaveFailure::class.java) {copyEnrollmentSave(f,{opened=true;ByteArrayOutputStream()},{false})};assertFalse(opened)} finally {f.delete()}}
    @Test fun zeroByteReadbackIsRejected() {val f=file(byteArrayOf(1,2,3));try {val r=copyEnrollmentSave(f,{ByteArrayOutputStream()},{true});assertThrows(EnrollmentSaveFailure::class.java) {verifyEnrollmentSave(r,{ByteArrayInputStream(byteArrayOf())},{true})}} finally {f.delete()}}
    @Test fun outputFailureDoesNotProduceReceipt() {val f=file(byteArrayOf(1,2,3));try {assertThrows(IOException::class.java) {copyEnrollmentSave(f,{object:OutputStream() {override fun write(b:Int) {throw IOException("inert output failure")}}},{true})}} finally {f.delete()}}
}
