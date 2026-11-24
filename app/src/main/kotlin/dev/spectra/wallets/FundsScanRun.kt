package dev.spectra.wallets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.WalletServiceBridge
import dev.spectra.models.ChainCatalog
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.spectra_core.Chain
import uniffi.spectra_core.FundsFinderRequest
import uniffi.spectra_core.FundsScan
import uniffi.spectra_core.FundsScanRead
import uniffi.spectra_core.WalletService

/** Why one network's addresses could not be read. */
data class UnreadChain(val chain: Chain, val reason: String)

/**
 * One run of core's funds scan: Funds Finder's, across every network a phrase
 * restores on, or the used-account list of one network. What it read is this
 * run's: starting again or cancelling drops it, and the page that owns the
 * run cancels it when it closes.
 */
class FundsScanRun {
    /** Every address read so far, in the order core read them. */
    var reads by mutableStateOf<List<FundsScanRead>>(emptyList())
        private set
    var checked by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var isRunning by mutableStateOf(false)
        private set

    /** Why the scan itself stopped. */
    var error by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    /** The share of the candidates read so far. */
    val progress: Float get() = if (total == 0) 0f else checked.toFloat() / total.toFloat()

    /** The accounts read as used: funded, or with history. */
    val used: List<FundsScanRead> get() = reads.filter { it.used }

    /**
     * Why each network's addresses could not be read: the first failure on
     * it, worded like any other failed call, in registry order.
     */
    val unreadChains: List<UnreadChain>
        get() {
            val reasons = mutableMapOf<Chain, String>()
            for (read in reads) {
                val error = read.error ?: continue
                if (read.candidate.chainId !in reasons) reasons[read.candidate.chainId] = userErrorMessage(error)
            }
            return ChainCatalog.all.mapNotNull { chain -> reasons[chain]?.let { UnreadChain(chain, it) } }
        }

    /**
     * Scans [request] from the start, in place of what an earlier run read. A
     * scan session is consumed as it runs, so each run begins its own.
     */
    fun start(request: FundsFinderRequest, chain: Chain?, bridge: WalletServiceBridge, scope: CoroutineScope) {
        cancel()
        isRunning = true
        job = scope.launch { perform(request, chain, bridge) }
    }

    /** Stops the run and drops what it read. */
    fun cancel() {
        job?.cancel()
        job = null
        reads = emptyList()
        checked = 0
        total = 0
        isRunning = false
        error = null
    }

    /** A cancelled run writes nothing: [cancel] has already reset the state, and a new run may own it. */
    private suspend fun CoroutineScope.perform(request: FundsFinderRequest, chain: Chain?, bridge: WalletServiceBridge) {
        try {
            begin(request, chain, bridge.service()).use { scan ->
                while (isActive) {
                    val batch = scan.nextBatch()
                    if (!isActive) return
                    total = batch.total.toInt()
                    checked = batch.checked.toInt()
                    reads = reads + batch.reads
                    if (batch.complete) break
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!isActive) return
            error = userErrorMessage(failure)
        }
        if (isActive) isRunning = false
    }

    companion object {
        /**
         * Derives the candidate addresses of [request] — on [chain], or on
         * every network that restores the phrase — and contacts nothing.
         * Deriving is blocking work in core, so it runs off the main thread.
         */
        suspend fun begin(request: FundsFinderRequest, chain: Chain?, service: WalletService): FundsScan =
            withContext(Dispatchers.IO) { service.beginFundsScan(request, chain) }
    }
}
