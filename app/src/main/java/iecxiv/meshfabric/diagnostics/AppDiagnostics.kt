package iecxiv.meshfabric.diagnostics
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
internal object AppDiagnostics {
    private val state = MutableStateFlow(false)
    val active = state.asStateFlow()
    val enabled: Boolean get() = state.value
    fun setEnabled(value: Boolean) { state.value = value }
    internal var sink: (Int, String, String) -> Int = { p, t, m -> Log.println(p, t, m) }
    private val numeric = Regex("(epoch|rx|bytes|lastCmd|images|jumbo|decoded|decodeFailed|published|version|draws)=(-?[0-9]+|true|false)\\b")
    internal fun metadata(tag: String, value: Any?): String {
        if (tag != "MeshDesktopDiag" && tag != "MeshDesktopDraw") return ""
        return numeric.findAll(value.toString()).joinToString(" ") { it.value }
    }
    fun trace(priority: Int, tag: String, event: String, message: () -> Any?): Int {
        if (!enabled) return 0
        val details = if (tag == "MeshDesktopDiag" || tag == "MeshDesktopDraw") metadata(tag, message()) else ""
        return sink(priority, tag, (event + " " + details).trim())
    }
    fun fault(tag: String, event: String, error: Throwable? = null): Int =
        sink(Log.ERROR, tag, event + (error?.let { " type=" + it.javaClass.simpleName } ?: ""))
}
