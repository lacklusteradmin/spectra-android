package dev.spectra.wallets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import uniffi.spectra_core.WalletState
import java.util.UUID

/**
 * Owns only the native form and its lifetime. Closing does not undo a core commit.
 *
 * Contract with `AppState`: [draft], [WalletImportDraft.canImportWallet] and
 * [close] keep these signatures.
 */
class WalletImportSession {
    /**
     * The open form's draft. Each form gets a new one: the pages of a closed
     * form are still drawing theirs while they leave the screen, and drop it,
     * with its secrets, once they are gone.
     */
    var draft by mutableStateOf(WalletImportDraft())
        private set

    /** This form's identity: a page or a result of another form is not this one's. */
    var id by mutableStateOf(UUID.randomUUID())
        private set

    var isBusy by mutableStateOf(false)
        private set

    var editingWalletId by mutableStateOf<String?>(null)
        private set

    /**
     * Why the last submit failed, or, once it succeeded and the form closed,
     * what it has to say — read by Home as an import notice.
     */
    var error by mutableStateOf<String?>(null)

    /**
     * Whether a form is open. Its pages are on a back stack while it is; the
     * stack leaving them closes it (`AppState.presentWalletImport`).
     */
    var isPresented by mutableStateOf(false)
        private set

    private fun invalidate() {
        id = UUID.randomUUID()
        isBusy = false
        error = null
        editingWalletId = null
        draft = WalletImportDraft()
    }

    fun begin(editing: WalletState? = null, configure: (WalletImportDraft) -> Unit) {
        invalidate()
        editingWalletId = editing?.id
        configure(draft)
        isPresented = true
    }

    fun close() {
        isPresented = false
        invalidate()
    }

    /**
     * Success, failure and cleanup may touch only the form that submitted
     * them. The operation still refreshes committed domain projections after
     * the form is gone.
     */
    suspend fun submit(operation: suspend () -> String?): Boolean {
        if (isBusy) return false
        val request = id
        isBusy = true
        error = null
        try {
            val notice = operation()
            if (id != request || !currentCoroutineContext().isActive) return false
            close()
            error = notice
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (id == request && currentCoroutineContext().isActive) error = userErrorMessage(failure)
            return false
        } finally {
            if (id == request) isBusy = false
        }
    }
}
