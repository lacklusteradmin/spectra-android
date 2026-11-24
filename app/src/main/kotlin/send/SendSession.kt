package dev.spectra.send

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.WalletServiceBridge
import dev.spectra.app.rethrowIfCancellation
import dev.spectra.app.runToCompletion
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import uniffi.spectra_core.Chain
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendNextStep
import uniffi.spectra_core.SendProgress
import java.util.UUID

/**
 * Transient native workflow identity. Core owns every transaction and
 * signature, and says what may be done with a staged one next: every artifact
 * this session adopts comes with core's [SendProgress] for it, read after the
 * step that produced it, so the two never disagree.
 *
 * Every step checks, after each suspension, that the session it started in
 * is still the current one: a reset in between — the composer closed, the
 * form changed, Back from the built transaction — means its answer is not
 * adopted, and a signature asked for before the reset is never made. A
 * build, signature or broadcast core has started runs to its end even so.
 *
 * The send composer, the staking page and every wallet tool that builds a
 * transaction stage it through one of these.
 */
class SendSession(private val readProgress: suspend (SendArtifact) -> SendProgress) {
    constructor(bridge: WalletServiceBridge) : this({ bridge.ready().sendProgress(it.id) })

    enum class Operation { BUILD, RESUME, SIGN, BROADCAST }

    var id: UUID by mutableStateOf(UUID.randomUUID())
        private set
    var operation by mutableStateOf<Operation?>(null)
        private set

    /** The staged transaction, its history record and what core allows next. */
    var progress by mutableStateOf<SendProgress?>(null)
        private set
    var endpoints by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedEndpoints by mutableStateOf<Set<String>>(emptySet())
        private set
    var error by mutableStateOf<String?>(null)

    val isBusy: Boolean get() = operation != null
    val artifact: SendArtifact? get() = progress?.artifact

    fun reset() {
        id = UUID.randomUUID()
        operation = null
        progress = null
        endpoints = emptyList()
        selectedEndpoints = emptySet()
        error = null
    }

    /** Choose whether [endpoint] receives the next submission. */
    fun select(endpoint: String, selected: Boolean) {
        selectedEndpoints = if (selected) selectedEndpoints + endpoint else selectedEndpoints - endpoint
    }

    /** Whether [request] is still this session's, in a coroutine that was not cancelled. */
    suspend fun isCurrent(request: UUID): Boolean = id == request && currentCoroutineContext().isActive

    private fun finish(request: UUID) {
        if (id == request) operation = null
    }

    /**
     * Adopt a complete artifact, progress and endpoint set together, never
     * part of one.
     */
    suspend fun load(
        operation: Operation,
        prepare: suspend () -> SendArtifact,
        loadEndpoints: suspend (Chain) -> List<String>,
    ): Boolean {
        if (this.operation != null) return false
        val request = id
        this.operation = operation
        try {
            val prepared = runToCompletion { prepare() }
            if (!isCurrent(request)) return false
            val progress = readProgress(prepared)
            if (!isCurrent(request)) return false
            val choices = loadEndpoints(prepared.chainId)
            if (!isCurrent(request)) return false
            this.progress = progress
            endpoints = choices
            selectedEndpoints = progress.artifact.selectedEndpoints.toSet().intersect(choices.toSet())
            error = null
            return true
        } catch (failure: Exception) {
            failure.rethrowIfCancellation()
            if (isCurrent(request)) error = userErrorMessage(failure)
            return false
        } finally {
            finish(request)
        }
    }

    /**
     * Ask the device owner, then sign — only if this session and its artifact
     * are still the ones the request started with once the answer is in.
     */
    suspend fun sign(
        password: String?,
        authenticate: suspend () -> String?,
        sign: suspend (id: String, reviewDigest: String, password: String?) -> SendArtifact,
    ) {
        val progress = progress
        if (operation != null || progress == null || progress.next != SendNextStep.SIGN) return
        val artifact = progress.artifact
        val request = id
        operation = Operation.SIGN
        try {
            val failure = authenticate()
            if (!isCurrent(request) || this.artifact?.id != artifact.id) return
            if (failure != null) {
                error = failure
                return
            }
            try {
                val signed = runToCompletion { sign(artifact.id, artifact.reviewDigest, password) }
                if (!isCurrent(request) || this.artifact?.id != artifact.id) return
                val next = readProgress(signed)
                if (!isCurrent(request) || this.artifact?.id != artifact.id) return
                this.progress = next
                error = null
            } catch (failure: Exception) {
                failure.rethrowIfCancellation()
                if (isCurrent(request)) error = userErrorMessage(failure)
            }
        } finally {
            finish(request)
        }
    }

    /**
     * Submit the signed transaction to the selected nodes, the first time or
     * again. Returns the submitted artifact, which belongs to the application
     * even after this session's form closes; `null` if nothing was submitted.
     */
    suspend fun broadcast(submit: suspend (id: String, endpoints: List<String>) -> SendArtifact): SendArtifact? {
        val progress = progress ?: return null
        if (operation != null) return null
        if (progress.next != SendNextStep.BROADCAST && !progress.canRebroadcast) return null
        val artifact = progress.artifact
        val request = id
        operation = Operation.BROADCAST
        try {
            val result = try {
                runToCompletion { submit(artifact.id, endpoints.filter { it in selectedEndpoints }) }
            } catch (failure: Exception) {
                failure.rethrowIfCancellation()
                if (isCurrent(request)) error = userErrorMessage(failure)
                return null
            }
            // A committed broadcast belongs to the application even after its
            // composer closes. Only the form's adoption is session-scoped.
            try {
                val next = readProgress(result)
                if (isCurrent(request) && this.artifact?.id == artifact.id) {
                    this.progress = next
                    error = null
                }
            } catch (failure: Exception) {
                failure.rethrowIfCancellation()
                if (isCurrent(request)) error = userErrorMessage(failure)
            }
            return result
        } finally {
            finish(request)
        }
    }

    /**
     * Read core's answer for the staged transaction again: its history record
     * moves without the artifact changing.
     */
    suspend fun refresh() {
        val artifact = artifact
        if (operation != null || artifact == null) return
        val request = id
        try {
            val next = readProgress(artifact)
            if (!isCurrent(request) || operation != null || this.artifact?.id != artifact.id) return
            progress = next
        } catch (failure: Exception) {
            failure.rethrowIfCancellation()
            if (isCurrent(request) && this.artifact?.id == artifact.id) error = userErrorMessage(failure)
        }
    }
}
