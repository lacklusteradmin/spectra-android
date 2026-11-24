package dev.spectra.wallets

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes
import uniffi.spectra_core.Chain
import java.util.UUID

/** The wallets' pages: adding a wallet, a wallet's detail, and the pages inside them. */
fun EntryProviderScope<Route>.walletsEntries() {
    entry<Routes.AddWallet> { AddWalletEntryPage() }
    entry<Routes.WalletDetail> { route -> WalletDetailPage(route.walletId) }
    entry<WalletSetupMethodsRoute> { route -> WalletSetupMethodsPage(route) }
    entry<FundsFinderRoute> { route -> FundsFinderPage(route) }
    entry<FundsFinderImportAllRoute> { route -> ImportAllFoundPage(route) }
    entry<WalletSetupRoute> { route -> WalletSetupPageScreen(route) }
    entry<WalletDerivationOptionsRoute> { route -> WalletDerivationOptionsPage(route) }
    entry<UsedAccountsRoute> { route -> UsedAccountsPage(route) }
    entry<WalletManageRoute> { route -> WalletAdvancedDetailsPage(route.walletId) }
    entry<WalletKeyExportRoute> { route -> WalletKeyExportPage(route.walletId) }
    entry<WalletCopyRoute> { route -> WalletCopyPage(route.walletId) }
}

// ── The feature's own routes ─────────────────────────────────────────────

/**
 * A network's ways of adding a wallet. With [upgradingWalletId], the ways of
 * giving that watched wallet its keys instead, and [upgradeNote], core's word
 * on what doing so means.
 */
internal data class WalletSetupMethodsRoute(
    val chain: Chain,
    val upgradingWalletId: String? = null,
    val upgradeNote: String = "",
) : Route

/**
 * Funds Finder. The scan's state lives with the page on the stack: the import
 * of a found account opens over it, and the results are still here on the
 * way back, for the next account to import.
 */
internal class FundsFinderRoute : Route {
    private val key = UUID.randomUUID()
    val state = FundsFinderState()

    override fun toString(): String = "FundsFinderRoute($key)"
}

/**
 * Importing every account a scan found, with the phrase and passphrase it
 * was scanned with. The run belongs to the page, not to its composition, so
 * imports already under way finish when the screen is covered.
 */
internal class FundsFinderImportAllRoute(
    val hits: List<FundsFinderHit>,
    val words: List<String>,
    val passphrase: String,
) : Route {
    private val key = UUID.randomUUID()
    val state = ImportAllFoundState()

    override fun toString(): String = "FundsFinderImportAllRoute($key)"
}

/** One page of the open import form [session]. Each page is its own route, so Back steps back one page. */
internal data class WalletSetupRoute(val session: UUID, val page: WalletSetupPage) : Route

/** The Advanced page over the secret step of the form [session]. */
internal data class WalletDerivationOptionsRoute(val session: UUID) : Route

/** Finding the accounts the phrase of the form [session] has used. */
internal data class UsedAccountsRoute(val session: UUID) : Route

/** Adding an endpoint from the setup summary, before the new wallet's first refresh reads from it. */

/** Managing a wallet: its name, phrase, keys, other networks and deletion. */
internal data class WalletManageRoute(val walletId: String) : Route

internal data class WalletKeyExportRoute(val walletId: String) : Route

internal data class WalletCopyRoute(val walletId: String) : Route

/** A page of the open import form, which closes with it. */
internal val Route.isImportFormPage: Boolean
    get() = this is WalletSetupRoute || this is WalletDerivationOptionsRoute ||
        this is UsedAccountsRoute

/** A page about [walletId], which cannot outlive it. */
internal fun Route.belongsToWallet(walletId: String): Boolean = when (this) {
    is Routes.WalletDetail -> this.walletId == walletId
    is Routes.WalletHistory -> this.walletId == walletId
    is Routes.WalletStaking -> this.walletId == walletId
    is Routes.WalletTool -> this.walletId == walletId
    is WalletManageRoute -> this.walletId == walletId
    is WalletKeyExportRoute -> this.walletId == walletId
    is WalletCopyRoute -> this.walletId == walletId
    is WalletSetupMethodsRoute -> upgradingWalletId == walletId
    else -> false
}
