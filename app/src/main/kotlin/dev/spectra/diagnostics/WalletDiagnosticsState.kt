package dev.spectra.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.WalletServiceBridge
import dev.spectra.models.AppLocalization
import dev.spectra.models.displayName
import dev.spectra.models.exportTag
import dev.spectra.models.formatUnixDate
import dev.spectra.models.id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainDegradation
import uniffi.spectra_core.DiagnosticCommand
import uniffi.spectra_core.DiagnosticLog
import uniffi.spectra_core.DiagnosticLogInput
import uniffi.spectra_core.DiagnosticLogLevel
import uniffi.spectra_core.DiagnosticState
import java.time.Instant
import kotlin.reflect.KMutableProperty1

/** Why a chain's data is stale, worded for the dashboard notice. */
data class ChainDegradedBanner(val chain: Chain, val message: String, val lastGoodSyncAtUnix: Double?) {
    val chainName: String get() = chain.displayName
}

/**
 * The operational log and the chains core marked degraded, as core recorded
 * them. Every write is a diagnostic command, queued in order.
 */
class WalletDiagnosticsState(private val bridge: WalletServiceBridge, private val scope: CoroutineScope) {
    private var snapshot by mutableStateOf(DiagnosticState(emptyMap(), emptyMap(), emptyList()))

    var operationalLogs by mutableStateOf<List<DiagnosticLog>>(emptyList())
        private set
    var operationalLogsRevision by mutableIntStateOf(0)
        private set

    private var pendingCommand: Job? = null
    private var revision = 0L

    private fun adopt(state: DiagnosticState) {
        snapshot = state
        operationalLogs = state.logs
        operationalLogsRevision += 1
    }

    private fun enqueue(command: DiagnosticCommand) {
        revision += 1
        val previous = pendingCommand
        // A queued event finishes even when its diagnostics view is closed.
        pendingCommand = scope.launch {
            previous?.join()
            // A diagnostics write that fails has nowhere to be logged; the
            // next read adopts whatever core kept.
            val result = runCatching { bridge.ready().applyDiagnosticCommand(command) }.getOrNull() ?: return@launch
            adopt(result)
        }
    }

    suspend fun loadFromSQLite() {
        pendingCommand?.join()
        val started = revision
        val state = runCatching { bridge.ready().diagnosticState() }.getOrNull() ?: return
        if (started == revision) adopt(state)
    }

    /** One banner per degraded chain, ordered by name. Core keys both maps by chain. */
    val chainDegradedBanners: List<ChainDegradedBanner>
        get() = snapshot.degraded.map { (chain, reason) ->
            ChainDegradedBanner(chain, localizedDegradedMessage(reason, chain), snapshot.lastGoodUnix[chain])
        }.sortedBy { it.chainName.lowercase(AppLocalization.locale) }

    fun clearOperationalLogs() = enqueue(DiagnosticCommand.ClearLogs(null))

    fun exportOperationalLogsText(networkSyncStatusText: String, events: List<DiagnosticLog>? = null): String {
        val entries = events ?: operationalLogs
        val header = listOf(
            AppLocalization.string("Spectra Operational Logs"),
            AppLocalization.format("Generated: %@", Instant.now().toString()),
            AppLocalization.format("Entries: %d", entries.size),
            networkSyncStatusText,
            "",
        )
        val lines = entries.map { log ->
            val event = log.input
            val parts = mutableListOf(
                Instant.ofEpochMilli((log.timestampUnix * 1000).toLong()).toString(),
                "[${event.level.exportTag}]",
                "[${event.category}]",
                event.message,
            )
            event.source?.takeIf { it.isNotEmpty() }?.let { parts += "source=$it" }
            event.chainId?.let { parts += "chain=${it.id}" }
            event.transactionHash?.takeIf { it.isNotEmpty() }?.let { parts += "tx=$it" }
            parts.joinToString(" | ")
        }
        return (header + lines).joinToString("\n")
    }

    /** Every line this side writes is a failure; core logs the rest itself. */
    fun appendOperationalLog(category: String, message: String, chain: Chain? = null, source: String? = null) {
        enqueue(
            DiagnosticCommand.Append(
                DiagnosticLogInput(DiagnosticLogLevel.ERROR, category, message, chain, null, source),
            ),
        )
    }

    /** Core stores why a chain is stale; the sentence is worded here. */
    private fun localizedDegradedMessage(reason: ChainDegradation, chain: Chain): String {
        val chainName = chain.displayName
        val detail = when (reason) {
            ChainDegradation.HistoryRefreshFailed ->
                AppLocalization.format("%@ history refresh failed. Using cached history.", chainName)
            ChainDegradation.HistoryPartiallyLoaded ->
                AppLocalization.format("%@ history loaded with partial provider failures.", chainName)
            is ChainDegradation.Failed -> reason.message
        }
        return listOf(detail, degradedSyncSuffix(chain)).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun degradedSyncSuffix(chain: Chain): String {
        val lastGood = snapshot.lastGoodUnix[chain]
            ?: return AppLocalization.string("diagnostics.degradedNoPriorSuccessfulSyncYet")
        return AppLocalization.format("diagnostics.degradedLastGoodSyncFormat", formatUnixDate(lastGood))
    }
}

/**
 * Which diagnostics runs are in flight, keyed by chain, and a revision that
 * tells screens to re-read what core recorded. Results live in core.
 *
 * A run outlives the page that started it, so whether one is in flight
 * lives here rather than in the page: a page left and opened again mid-run
 * still shows it running, and sees it end.
 */
class WalletChainDiagnosticsState {
    var diagnosticsRevision by mutableIntStateOf(0)
    var runningHistory by mutableStateOf(emptySet<Chain>())
    var checkingEndpoints by mutableStateOf(emptySet<Chain>())
    var runningSelfTests by mutableStateOf(emptySet<Chain>())
    var runningRescans by mutableStateOf(emptySet<Chain>())

    /** Chains a diagnostics page is refreshing balances and history for. */
    var refreshing by mutableStateOf(emptySet<Chain>())

    /** Whether the hub is checking every network's endpoints in turn. */
    var isCheckingAllEndpoints by mutableStateOf(false)

    /**
     * Hold [chain]'s slot in [runs] for [operation], then tell screens to
     * re-read. A run already in flight is not started twice.
     */
    suspend fun run(runs: KMutableProperty1<WalletChainDiagnosticsState, Set<Chain>>, chain: Chain, operation: suspend () -> Unit) {
        if (chain in runs.get(this)) return
        runs.set(this, runs.get(this) + chain)
        try {
            operation()
        } finally {
            runs.set(this, runs.get(this) - chain)
            diagnosticsRevision += 1
        }
    }
}
