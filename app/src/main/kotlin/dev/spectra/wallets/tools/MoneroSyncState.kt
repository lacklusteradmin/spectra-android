package dev.spectra.wallets.tools

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import uniffi.spectra_core.MoneroSyncStatus
import java.util.UUID

/** Native scan controls and progress; each run has its own lifetime. */
internal class MoneroSyncState {
    var status by mutableStateOf<MoneroSyncStatus?>(null)
    val password = TextFieldState()
    var error by mutableStateOf<String?>(null)
    var requestId by mutableStateOf<UUID?>(null)
        private set
    val isRunning: Boolean get() = requestId != null

    fun begin() {
        if (requestId != null) return
        requestId = UUID.randomUUID()
    }

    fun cancel() {
        requestId = null
        password.clearText()
    }

    private suspend fun isCurrent(request: UUID): Boolean = requestId == request && currentCoroutineContext().isActive

    suspend fun sync(request: UUID, operation: suspend (String?, (MoneroSyncStatus) -> Unit) -> String?) {
        if (!isCurrent(request)) return
        try {
            error = null
            val failure = operation(password.text.toString().ifEmpty { null }) { status ->
                if (requestId == request) this.status = status
            }
            if (!isCurrent(request)) return
            error = failure
        } finally {
            if (requestId == request) {
                password.clearText()
                requestId = null
            }
        }
    }
}
