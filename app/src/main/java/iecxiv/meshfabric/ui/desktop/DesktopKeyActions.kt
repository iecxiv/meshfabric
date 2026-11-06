package iecxiv.meshfabric.ui.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A transport binding is an identity token: delayed releases never migrate to a new socket. */
internal class DesktopKeyActions(
    private val scope: CoroutineScope,
    private val binding: () -> Any?,
    private val emit: (Any, Int, Boolean) -> Unit
) {
    private val _held = MutableStateFlow<Set<Int>>(emptySet())
    val held: StateFlow<Set<Int>> = _held.asStateFlow()
    private var token: Any? = null
    private var keys = DesktopHeldKeys { _, _ -> }
    private val jobs = mutableSetOf<Job>()

    private fun bind(): Boolean {
        val next = binding()
        if (next !== token) {
            jobs.toList().forEach { it.cancel() }
            jobs.clear()
            keys.releaseAll()
            token = next
            keys = DesktopHeldKeys { key, down -> if (next != null) emit(next, key, down) }
            _held.value = emptySet()
        }
        return next != null
    }
    fun physical(key: Int, down: Boolean) {
        if (key in 1..255 && bind()) keys.physical(key, down)
    }
    fun toggle(key: Int) {
        if (key !in desktopHoldKeys || !bind()) return
        keys.toggle(key)
        _held.value = keys.held
    }
    fun releaseHolds() {
        bind()
        keys.releaseHolds()
        _held.value = emptySet()
    }
    fun combo(vararg requested: Int) {
        val codes = requested.distinct()
        if (codes.isEmpty() || codes.size > 8 || codes.any { it !in 1..255 } || !bind()) return
        val engine = keys
        val origin = token
        val owner = Any()
        val acquired = mutableListOf<Int>()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (key in codes) {
                    if (binding() !== origin) return@launch
                    engine.press(key, owner)
                    acquired.add(key)
                    delay(20)
                }
                delay(50)
            } finally {
                acquired.asReversed().forEach { engine.release(it, owner) }
                jobs.remove(coroutineContext[Job])
            }
        }
        jobs.add(job)
        job.start()
    }
    fun clear() {
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
        keys.releaseAll()
        _held.value = emptySet()
        token = null
        keys = DesktopHeldKeys { _, _ -> }
    }
}
