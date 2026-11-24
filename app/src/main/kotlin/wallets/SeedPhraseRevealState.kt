package dev.spectra.wallets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.UUID

/**
 * Transient secret presentation. A page that left the screen, or one the app
 * left for the background, cannot adopt a late reveal.
 *
 * A paused activity is not one the app left: the biometric prompt a reveal
 * asks for can pause it, and its answer can arrive before it resumes. That
 * answer waits and is shown on return.
 */
class SeedPhraseRevealState {
    var isShowingPasswordPrompt by mutableStateOf(false)
    var isShowingPhraseSheet by mutableStateOf(false)
    /** Typed in the prompt's [dev.spectra.components.WalletPasswordField]; never saved with the page. */
    val passwordInput = TextFieldState()
    var phrase by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var isRevealing by mutableStateOf(false)
        private set

    /** Binds results and cleanup to one reveal. */
    private var requestId = UUID.randomUUID()

    /** Rejects work queued before the page left the screen. */
    private var isVisible = false

    /** Completion reads the live lifecycle state. */
    private var isSceneActive = true

    /** An answer that arrived while the activity was paused, shown once it resumes. */
    private var pending: PendingOutcome? = null

    private sealed interface PendingOutcome {
        data object Phrase : PendingOutcome
        data class Error(val message: String) : PendingOutcome
    }

    fun activate(sceneIsActive: Boolean = true) {
        isVisible = true
        isSceneActive = sceneIsActive
    }

    fun deactivate() {
        isVisible = false
        invalidate()
    }

    /**
     * Pausing hides what is shown; resuming shows an answer that arrived
     * meanwhile. Going to the background invalidates the request besides
     * ([invalidate]), so nothing from it is waiting on return.
     */
    fun setSceneIsActive(active: Boolean) {
        isSceneActive = active
        if (!active) {
            // An answer already waiting outlives this; one on screen is hidden.
            if (pending == null) clearPresentation()
            return
        }
        val outcome = pending
        if (!isVisible || outcome == null) return
        pending = null
        when (outcome) {
            PendingOutcome.Phrase -> isShowingPhraseSheet = true
            is PendingOutcome.Error -> errorMessage = outcome.message
        }
    }

    fun clearPresentation() {
        pending = null
        isShowingPasswordPrompt = false
        isShowingPhraseSheet = false
        passwordInput.clearText()
        phrase = ""
        errorMessage = null
    }

    fun invalidate() {
        requestId = UUID.randomUUID()
        isRevealing = false
        clearPresentation()
    }

    fun clearPhrase() {
        phrase = ""
    }

    /** `null` means the request no longer belongs to a visible page. */
    suspend fun reveal(canPresent: () -> Boolean, operation: suspend () -> String): Boolean? {
        if (!isVisible || !isSceneActive || isRevealing || !canPresent() || !currentCoroutineContext().isActive) return null
        val request = requestId
        isRevealing = true
        try {
            val revealed = operation()
            if (requestId != request || !isVisible || !currentCoroutineContext().isActive || !canPresent()) return null
            phrase = revealed
            passwordInput.clearText()
            errorMessage = null
            if (isSceneActive) isShowingPhraseSheet = true else pending = PendingOutcome.Phrase
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (requestId != request || !isVisible || !currentCoroutineContext().isActive || !canPresent()) return null
            val message = userErrorMessage(error)
            if (isSceneActive) errorMessage = message else pending = PendingOutcome.Error(message)
            return false
        } finally {
            if (requestId == request) isRevealing = false
        }
    }
}
