package dev.spectra.diagnostics

import dev.spectra.app.AppState
import dev.spectra.app.performCoreRefresh
import dev.spectra.app.refreshTransactionProjection
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.AppRefreshIntent
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainDiagnostics
import uniffi.spectra_core.DiagnosticLog
import uniffi.spectra_core.HistoryRefreshScope
import uniffi.spectra_core.KeypoolDiagnostic

// The diagnostics screen's actions. This side holds which runs are in
// flight; core runs them, records what they found and logs how they went.
// Each run goes on the app's scope, so leaving the page does not stop it.

suspend fun AppState.runHistoryDiagnostics(chain: Chain) {
    chainDiagnosticsState.run(WalletChainDiagnosticsState::runningHistory, chain) {
        // No deadline of its own: core's transport timeouts bound the run.
        refreshChainHistory(chain)
    }
}

/** Core probes the network the family is on and keeps the result. */
suspend fun AppState.runEndpointDiagnostics(chain: Chain) {
    chainDiagnosticsState.run(WalletChainDiagnosticsState::checkingEndpoints, chain) {
        try {
            bridge.ready().probeChainEndpoints(chain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            appendOperationalLog("Endpoints", error.toString(), chain)
        }
    }
}

/** Every mainnet's endpoints, one network after another, as the hub's one action. */
suspend fun AppState.runAllEndpointDiagnostics() {
    val runs = chainDiagnosticsState
    if (runs.isCheckingAllEndpoints) return
    runs.isCheckingAllEndpoints = true
    try {
        for (chain in ChainCatalog.mainnets) runEndpointDiagnostics(chain)
    } finally {
        runs.isCheckingAllEndpoints = false
    }
}

/**
 * Core resolves the selected network and effective RPC, runs the tests and
 * logs their outcome, which the chain's operational events show.
 */
suspend fun AppState.runSelfTests(chain: Chain) {
    chainDiagnosticsState.run(WalletChainDiagnosticsState::runningSelfTests, chain) {
        try {
            bridge.ready().runConfiguredSelfTests(chain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            appendOperationalLog("Self-Tests", error.toString(), chain)
        }
        diagnostics.loadFromSQLite()
    }
}

/** Core runs the rescan and logs how it went. */
suspend fun AppState.runUTXORescan(chain: Chain) {
    chainDiagnosticsState.run(WalletChainDiagnosticsState::runningRescans, chain) {
        performCoreRefresh(AppRefreshIntent.DeepRescan(chain))
    }
}

/**
 * Refresh [chain]'s balances and history as the user asked. `true` when
 * every part of it went through.
 */
suspend fun AppState.refreshChainForDiagnostics(chain: Chain): Boolean {
    var succeeded = false
    chainDiagnosticsState.run(WalletChainDiagnosticsState::refreshing, chain) {
        succeeded = performCoreRefresh(AppRefreshIntent.Chain(chain))
    }
    return succeeded
}

/** A family's diagnostics as core recorded them, on the network it is on. */
suspend fun AppState.chainDiagnostics(chain: Chain): ChainDiagnostics = bridge.ready().chainDiagnostics(chain)

/**
 * Read-only keypool diagnostics. Reading does not reserve an address; the
 * reserved address and path are those recorded when the index was handed out.
 */
suspend fun AppState.chainKeypoolDiagnostics(chain: Chain): List<KeypoolDiagnostic> = bridge.ready().keypoolDiagnostics(chain)

suspend fun AppState.operationalEvents(chain: Chain): List<DiagnosticLog> = try {
    bridge.ready().operationalEvents(chain)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    emptyList()
}

/**
 * Refresh [chain]'s history and adopt what it changed. Core records the
 * run's diagnostics rows and the chain's health itself; this re-reads them.
 */
private suspend fun AppState.refreshChainHistory(chain: Chain) {
    try {
        val results = bridge.ready().refreshHistory(HistoryRefreshScope.Chains(listOf(chain)), false, null, 0.0)
        chainDiagnosticsState.diagnosticsRevision += 1
        diagnostics.loadFromSQLite()
        if (results.any { (it.outcome?.added ?: 0u) > 0u || (it.outcome?.updated ?: 0u) > 0u }) {
            refreshTransactionProjection()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        appendOperationalLog("History", error.toString())
    }
}

/** What a manual refresh's outcome says. */
fun refreshOutcomeMessage(succeeded: Boolean): String = AppLocalization.string(
    if (succeeded) "Manual refresh completed." else "Refresh failed or completed partially. See refresh errors.",
)
