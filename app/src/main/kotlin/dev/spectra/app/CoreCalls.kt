package dev.spectra.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

// How a screen's coroutine calls core: what a cancelled coroutine means for
// a call in flight, and a read whose failure is shown as nothing.

/**
 * A cancelled coroutine is superseded, not failed: its exception goes on up
 * rather than being worded as an error.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/**
 * A core call that writes — a build, a signature, a broadcast, a scan
 * batch — runs to its end once started, as it does in the Apple app. The
 * bindings drop a cancelled call's Rust future, which would abandon the
 * write midway; leaving the page only stops its answer being adopted.
 */
suspend fun <T> runToCompletion(block: suspend () -> T): T = withContext(NonCancellable) { block() }

/** [block], or `null` when it throws: a read whose failure the page shows as nothing. Cancellation still cancels. */
suspend fun <T> readOrNull(block: suspend () -> T?): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
