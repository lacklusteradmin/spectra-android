package dev.spectra.diagnostics

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes
import uniffi.spectra_core.Chain

/** One network's diagnostics, under the hub. */
internal data class ChainDiagnosticsRoute(val chain: Chain) : Route

/** Diagnostics and the operational log. */
fun EntryProviderScope<Route>.diagnosticsEntries() {
    entry<Routes.Diagnostics> { DiagnosticsHubPage() }
    entry<ChainDiagnosticsRoute> { ChainDiagnosticsPage(it.chain) }
    entry<Routes.OperationalLogs> { LogsPage() }
}
