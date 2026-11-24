package dev.spectra.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.StateTransition

/**
 * The one queue every [StateCommand] goes through.
 *
 * Core serialises its own writes and stamps each state with a revision, so
 * adoption order is already safe; the queue keeps the order the user acted
 * in, so a later edit is never overtaken by an earlier one. [AppState] owns
 * it, adopts what each command committed, and hands it to the domain objects
 * that send commands of their own. Everything runs on the main thread.
 */
class StateCommandQueue(private val bridge: WalletServiceBridge, private val scope: CoroutineScope) {
    /** The tail of the queue: it completes once every command issued so far has. */
    private var tail: Job? = null

    /** Adopts what a command committed, before the sender's handler runs. */
    var adopt: suspend (StateTransition) -> Unit = {}

    /**
     * Send a command after every command issued before it, then adopt what
     * core committed. [then] runs after adoption, inside the queue.
     */
    fun enqueue(
        command: StateCommand,
        then: suspend (Result<StateTransition>) -> Unit = {},
    ): Deferred<StateTransition> {
        val previous = tail
        val task = scope.async {
            previous?.join()
            try {
                val transition = bridge.ready().applyStateCommand(command)
                adopt(transition)
                then(Result.success(transition))
                transition
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                then(Result.failure(error))
                throw error
            }
        }
        tail = scope.launch {
            try {
                task.await()
            } catch (_: Exception) {
                // The sender's handler saw it; the queue only orders.
            }
        }
        return task
    }

    /** Send a command, wait for it to be committed and adopted, and return it. */
    suspend fun apply(command: StateCommand): StateTransition = enqueue(command).await()

    /** Wait until every command issued so far has been committed and adopted. */
    suspend fun awaitPending() {
        tail?.join()
    }
}
