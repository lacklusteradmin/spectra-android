package dev.spectra.receive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/** Transient flow state; persisted domain data remains in core. */
class ReceiveFlowState {
    var walletId by mutableStateOf("")
    var holdingKey by mutableStateOf("")
    var resolvedAddress by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    /** Rejects stale asynchronous results. */
    var requestId: UUID = UUID.randomUUID()
    var isResolving by mutableStateOf(false)

    private var isPresentedState by mutableStateOf(false)

    /** Whether the flow is on screen. Leaving it drops the address it read. */
    var isPresented: Boolean
        get() = isPresentedState
        set(value) {
            val wasPresented = isPresentedState
            isPresentedState = value
            if (wasPresented && !value) clearAddress()
        }

    fun clearAddress() {
        requestId = UUID.randomUUID()
        resolvedAddress = ""
        error = null
        isResolving = false
    }

    fun reset() {
        clearAddress()
        walletId = ""
        holdingKey = ""
        isPresented = false
    }
}
