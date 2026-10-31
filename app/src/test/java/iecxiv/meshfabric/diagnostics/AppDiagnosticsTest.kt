package iecxiv.meshfabric.diagnostics
import org.junit.Assert.*
import org.junit.Test
class AppDiagnosticsTest {
    @Test fun disabledDiagnosticsDoNotEvaluateMessagesOrEmitLogs() {
        AppDiagnostics.setEnabled(false)
        var evaluated=false
        assertEquals(0,AppDiagnostics.trace(3,"MeshDesktopDiag","test") { evaluated=true; "rx=1" })
        assertFalse(evaluated)
    }
    @Test fun enabledDiagnosticsEmitOnlyAllowedNumericMetadata() {
        val original=AppDiagnostics.sink
        var output=""
        try {
            AppDiagnostics.sink={ _,_,message -> output=message; 0 }
            AppDiagnostics.setEnabled(true)
            AppDiagnostics.trace(3,"MeshDesktopDiag","test") { "rx=12 decoded=3 token=SECRET clipboard=PRIVATE" }
            assertTrue(output.contains("rx=12"))
            assertFalse(output.contains("SECRET"))
            assertFalse(output.contains("PRIVATE"))
        } finally { AppDiagnostics.setEnabled(false); AppDiagnostics.sink=original }
    }
    @Test fun arbitraryPayloadLogsAreNeverEvaluatedEvenWhenEnabled() {
        val original=AppDiagnostics.sink
        try {
            AppDiagnostics.sink={ _,_,_ -> 0 }
            AppDiagnostics.setEnabled(true)
            var evaluated=false
            AppDiagnostics.trace(3,"MeshFilesRecvRawText","payload omitted") { evaluated=true; "PRIVATE" }
            assertFalse(evaluated)
        } finally { AppDiagnostics.setEnabled(false); AppDiagnostics.sink=original }
    }
}
