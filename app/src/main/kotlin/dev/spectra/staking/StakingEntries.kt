package dev.spectra.staking

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes
import dev.spectra.models.AppLocalization
import dev.spectra.wallets.tools.WithWallet

/** A wallet's staking page. Its validator list and confirmations are drawn over it, not pushed. */
fun EntryProviderScope<Route>.stakingEntries() {
    entry<Routes.WalletStaking> { route ->
        WithWallet(route.walletId, AppLocalization.string("Staking")) { WalletStakingView(it) }
    }
}
