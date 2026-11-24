package dev.spectra.history

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes
import uniffi.spectra_core.Chain
import uniffi.spectra_core.MoneroPaymentProof

/** History's own pages, which no other feature opens. */
object HistoryRoutes {
    /** A Monero send's payment proof, from its transaction's page. */
    data class PaymentProof(val proof: MoneroPaymentProof, val chain: Chain) : Route
}

/** History's pages: the tab, a transaction, one wallet's history and a payment's proof. */
fun EntryProviderScope<Route>.historyEntries() {
    entry<Routes.History> { HistoryListScreen(walletId = null) }
    entry<Routes.TransactionDetail> { TransactionDetailScreen(it.transactionId) }
    entry<Routes.WalletHistory> { HistoryListScreen(walletId = it.walletId) }
    entry<HistoryRoutes.PaymentProof> { PaymentProofScreen(it.proof, it.chain) }
}
