package dev.spectra.wallets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.UUID

/**
 * One run of a private-funds scan at a time — a Zcash wallet's shielded
 * pools, a Litecoin wallet's MWEB funds — each run with its own lifetime.
 * Core keeps every batch, so a cancelled run resumes where it stopped.
 */
class ScanRun {
    /**
     * The password the first batch derives the scanning keys with, typed in a
     * [dev.spectra.components.WalletPasswordField]. Never saved with the
     * page, and cleared when the run ends.
     */
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

    suspend fun isCurrent(request: UUID): Boolean = requestId == request && currentCoroutineContext().isActive

    fun finish(request: UUID) {
        if (requestId == request) cancel()
    }
}

/**
 * What a private-funds page's operation does: move the wallet's transparent
 * funds in, or pay from the private ones.
 */
enum class PrivateFundsFlow { MOVE_IN, SEND }
