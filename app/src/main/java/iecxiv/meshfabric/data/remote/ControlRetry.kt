package iecxiv.meshfabric.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun controlRetryDelay(attempt:Int):Long = minOf(15_000L,500L shl attempt.coerceIn(0,5))

internal class ControlRetry(
    private val scope:CoroutineScope,
    private val ownership:SocketGeneration,
    private val shouldConnect:()->Boolean,
    private val connect:()->Unit,
    private val onError:(Exception)->Unit={},
    private val retryDelay:(Int)->Long=::controlRetryDelay
) {
    private var pending:Job?=null
    private var starting=false
    private var attempts=0

    fun hasPending():Boolean=ownership.locked { pending?.isActive==true }

    fun request(immediate:Boolean=false) {
        ownership.locked {
            if(!scope.isActive || !shouldConnect()) return
            if(pending?.isActive==true) {
                if(!immediate || starting) return
                cancel()
            }
            val wait=if(immediate) 0L else retryDelay(attempts)
            attempts=(attempts+1).coerceAtMost(5)
            lateinit var job:Job
            job=scope.launch(start=CoroutineStart.LAZY) {
                delay(wait)
                ownership.locked {
                    if(pending!==job || !shouldConnect()) return@locked
                    starting=true
                    try { connect() }
                    catch(e:CancellationException) { throw e }
                    catch(e:Exception) { onError(e) }
                }
            }
            pending=job
            job.invokeOnCompletion {
                ownership.locked {
                    if(pending===job) {
                        pending=null
                        starting=false
                        if(scope.isActive && shouldConnect()) request()
                    }
                }
            }
            job.start()
        }
    }

    fun cancel() {
        ownership.locked {
            val job=pending
            pending=null
            starting=false
            job?.cancel()
        }
    }

    fun reset() { ownership.locked { attempts=0; cancel() } }
}
