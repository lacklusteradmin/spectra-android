package dev.spectra.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.WalletServiceBridge
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.spectra_core.TorStatus

/**
 * Live Tor bootstrap and connection state, as core's refresh engine reports
 * it. Whether Tor runs follows core's committed settings; this only asks for
 * a reconnect.
 */
class TorState(private val bridge: WalletServiceBridge, private val scope: CoroutineScope) {
    var status by mutableStateOf<TorStatus>(TorStatus.Stopped)
        private set

    /** The refresh observer's writer. */
    fun adopt(status: TorStatus) {
        this.status = status
    }

    fun reconnect() {
        scope.launch {
            status = try {
                bridge.ready().reconnectTor()
            } catch (error: Exception) {
                TorStatus.Error(userErrorMessage(error))
            }
        }
    }
}
