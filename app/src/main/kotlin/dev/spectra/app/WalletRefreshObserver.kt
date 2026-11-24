package dev.spectra.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import uniffi.spectra_core.AppRefreshResult
import uniffi.spectra_core.Chain
import uniffi.spectra_core.RefreshObserver
import uniffi.spectra_core.TorStatus

/**
 * Rust→Kotlin observer for core's refresh engine.
 *
 * Core calls these on its own threads. Each call becomes one event on a
 * channel that [AppState] drains on the main thread in the order core sent
 * them, so a later Tor status or refresh result is never overtaken by an
 * earlier one.
 */
class WalletRefreshObserver : RefreshObserver {
    sealed interface Event {
        /** A wallet's balance landed; the app reads the portfolio. */
        data object BalanceUpdated : Event
        data class RefreshComplete(val result: AppRefreshResult) : Event
        data class TorStatusChanged(val status: TorStatus) : Event
    }

    private val channel = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = channel.receiveAsFlow()

    override fun onBalanceRefreshed(chainId: Chain, walletId: String, failure: String?) {
        if (failure == null) channel.trySend(Event.BalanceUpdated)
    }

    override fun onRefreshCycleComplete(refreshed: UInt, errors: UInt) {}

    override fun onRefreshComplete(result: AppRefreshResult) {
        channel.trySend(Event.RefreshComplete(result))
    }

    override fun onTorStatusChanged(status: TorStatus) {
        channel.trySend(Event.TorStatusChanged(status))
    }
}
