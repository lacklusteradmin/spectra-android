package dev.spectra.history

import dev.spectra.app.AppState
import dev.spectra.app.performCoreRefresh
import dev.spectra.app.refreshTransactionProjection
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.AppRefreshIntent
import uniffi.spectra_core.Chain
import uniffi.spectra_core.HistoryRefreshScope

/** Fetch the next page of on-chain history for [walletIds], one fetch at a time. */
suspend fun AppState.loadMoreOnChainHistory(walletIds: Set<String>) {
    historyPaging.loadMore(walletIds) { ids ->
        adoptHistoryRefresh(HistoryRefreshScope.Wallets(ids.toList()), loadMore = true)
    }
}

suspend fun AppState.refreshHistory(chain: Chain) {
    adoptHistoryRefresh(HistoryRefreshScope.Chains(listOf(chain)))
}

/**
 * Run a history refresh and adopt what it changed.
 *
 * Core records the run's diagnostics rows and the chain's health itself;
 * this re-reads them.
 */
private suspend fun AppState.adoptHistoryRefresh(refreshScope: HistoryRefreshScope, loadMore: Boolean = false) {
    try {
        val results = bridge.ready().refreshHistory(refreshScope, loadMore, null, 0.0)
        chainDiagnosticsState.diagnosticsRevision += 1
        diagnostics.loadFromSQLite()
        // Loading more always moves a cursor, even when every page was already stored.
        val changed = results.any { (it.outcome?.added ?: 0u) > 0u || (it.outcome?.updated ?: 0u) > 0u }
        if (loadMore || changed) refreshTransactionProjection()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        appendOperationalLog("History", error.toString())
    }
}

/** A refresh of one chain the user asked for. */
suspend fun AppState.performUserInitiatedRefresh(chain: Chain): Boolean = performCoreRefresh(AppRefreshIntent.Chain(chain))
