package dev.spectra.wallets.tools

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes
import dev.spectra.components.SpectraPage
import dev.spectra.models.AppLocalization
import dev.spectra.wallets.title
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletState

/**
 * A wallet's tool pages, one per tool action, and the pages they open: each
 * transaction a tool builds, and a multisig session.
 */
fun EntryProviderScope<Route>.walletToolsEntries() {
    entry<Routes.WalletTool> { route ->
        WithWallet(route.walletId, route.action.title) { wallet -> WalletToolPage(wallet, route.action) }
    }
    entry<DeleteAccessKeyRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Delete Key")) { DeleteAccessKeyView(it, route.key) }
    }
    entry<RevokeApprovalRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Revoke Approval")) { RevokeApprovalView(it, route.approval) }
    }
    entry<MergeCoinsRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Merge Coins")) { MergeCoinsView(it, route.type) }
    }
    entry<MultisigSessionRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Multisig Transaction")) {
            MultisigSessionView(route.account, route.session)
        }
    }
    entry<MwebTransactionRoute> { route ->
        WithWallet(route.walletId, WalletAction.MWEB_FUNDS.title) { MwebTransactionView(it, route.flow) }
    }
    entry<ShieldedTransactionRoute> { route ->
        WithWallet(route.walletId, WalletAction.SHIELDED_FUNDS.title) { ShieldedTransactionView(it, route.flow) }
    }
    entry<CloseAccountRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Close Account")) { CloseAccountView(it) }
    }
    entry<SendNftRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Send NFT")) { SendNftView(it, route.nft) }
    }
    entry<CloseTokenAccountsRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Close Token Accounts")) { CloseTokenAccountsView(it) }
    }
    entry<RefundTokenStorageRoute> { route ->
        WithWallet(route.walletId, AppLocalization.string("Get Deposit Back")) { RefundTokenStorageView(it, route.deposit) }
    }
    entry<TrustLineChangeRoute> { route ->
        val title = AppLocalization.string(if (route.remove) "Remove Trust Line" else "Trust Asset")
        WithWallet(route.walletId, title) { TrustLineChangeView(it, route.asset, route.remove) }
    }
}

/** The page a wallet's tool action opens. */
@Composable
private fun WalletToolPage(wallet: WalletState, action: WalletAction) {
    when (action) {
        WalletAction.MULTISIG -> WalletMultisigView(wallet)
        WalletAction.SCAN_BLOCKS -> WalletBlockScanView(wallet)
        WalletAction.COINS -> WalletCoinsView(wallet)
        WalletAction.TOKEN_APPROVALS -> WalletApprovalsView(wallet)
        WalletAction.NFTS -> WalletNftsView(wallet)
        WalletAction.SHIELDED_FUNDS -> WalletShieldedFundsView(wallet)
        WalletAction.MWEB_FUNDS -> WalletMwebFundsView(wallet)
        WalletAction.NETWORK_ACCOUNT -> WalletNetworkAccountView(wallet)
        WalletAction.ACCESS_KEYS -> WalletAccessKeysView(wallet)
        WalletAction.TOKEN_STORAGE -> WalletTokenStorageView(wallet)
        WalletAction.COIN_OBJECTS -> WalletCoinObjectsView(wallet)
        WalletAction.TOKEN_ACCOUNTS -> WalletTokenAccountsView(wallet)
        WalletAction.TRUST_LINES -> WalletTrustLinesView(wallet)
        WalletAction.SIGN_MESSAGE, WalletAction.VERIFY_MESSAGE ->
            WalletMessageView(wallet, canSign = action == WalletAction.SIGN_MESSAGE)
        // Not a tool: each of these has its own route or happens in place on
        // the wallet's page, which never opens one here. Drawn empty, as the
        // Apple app draws them.
        WalletAction.SEND, WalletAction.RECEIVE, WalletAction.HISTORY, WalletAction.OPEN_IN_EXPLORER,
        WalletAction.ADD_KEYS, WalletAction.STAKE, WalletAction.GET_TEST_COINS, WalletAction.ADD_TO_NETWORK,
        WalletAction.RENAME, WalletAction.REVEAL_PHRASE, WalletAction.EXPORT_KEYS, WalletAction.DELETE,
        -> SpectraPage(title = action.title) {}
    }
}
