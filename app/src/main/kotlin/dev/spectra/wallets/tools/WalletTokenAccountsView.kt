package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.isWatchOnly
import dev.spectra.wallets.title
import uniffi.spectra_core.EmptyTokenAccount
import uniffi.spectra_core.EmptyTokenAccounts
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState

/** Closing every closable empty token account of the wallet. */
internal data class CloseTokenAccountsRoute(val walletId: String) : Route

/**
 * A Solana wallet's empty token accounts and the rent each holds, as core
 * reads them from a node; and closing the closable ones for their rent: core
 * builds the closure as a send, and its page signs and broadcasts it through
 * the same stages.
 */
@Composable
internal fun WalletTokenAccountsView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val empty = remember(wallet.id) { ToolRead<EmptyTokenAccounts>() }
    suspend fun load() = empty.load { app.bridge.ready().walletEmptyTokenAccounts(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.TOKEN_ACCOUNTS.title, onRefresh = { load() }) {
        ToolReadSections(empty) { read ->
            if (read.accounts.isEmpty()) {
                WalletToolEmptySection(AppLocalization.string("No empty token accounts."))
            } else {
                ToolListSection(
                    items = read.accounts,
                    footer = AppLocalization.string(
                        "Each token this address has held keeps an account with rent in it, even after the tokens are gone. Closing an empty one returns its rent.",
                    ),
                ) { TokenAccountRow(wallet, it) }
                if (read.accounts.any { it.blocked == null } && !wallet.signing.isWatchOnly) {
                    ToolSection(footer = AppLocalization.string("One transaction closes up to 20 accounts; close again for the rest.")) {
                        FilledTonalButton(
                            onClick = { navigator.push(CloseTokenAccountsRoute(wallet.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(AppLocalization.format("Close and Reclaim %@ SOL", read.reclaimable)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenAccountRow(wallet: WalletState, account: EmptyTokenAccount) {
    val app = LocalAppState.current
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            Text(
                account.symbol,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (account.token2022) {
                Text("Token-2022", style = MaterialTheme.typography.labelSmall, color = secondaryText)
            }
            Text(
                app.amounts.formattedNativeAmount(account.rent, wallet.chainId),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.End,
            )
        }
        IdentifierText(account.address, style = MaterialTheme.typography.labelSmall)
        account.blocked?.let { blocked ->
            SpectraLabel(text = AppLocalization.string(blocked), icon = Icons.Outlined.Lock, color = SpectraColors.semantic.warning)
        }
    }
}

/** Building, signing and broadcasting one closure of every closable account. */
@Composable
internal fun CloseTokenAccountsView(wallet: WalletState) {
    val app = LocalAppState.current
    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string("Close Token Accounts"),
        authenticationReason = AppLocalization.format("Authenticate to close token accounts of %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The rent is back once it confirms."),
        build = { app.bridge.ready().buildTokenAccountClosure(wallet.id, emptyList()) },
    ) { artifact ->
        val closure = artifact?.operation as? WalletOperation.CloseTokenAccounts
        if (closure != null) {
            ToolSection(
                footer = AppLocalization.string(
                    "The accounts are deleted and their rent comes back to this wallet. A token sent here later opens a new account.",
                ),
            ) {
                SpectraValueRow(
                    label = AppLocalization.string("Accounts"),
                    value = AppLocalization.formatCount("%lld accounts", closure.accounts.size.toLong(), closure.accounts.size),
                )
                SpectraValueRow(
                    label = AppLocalization.string("Rent Returned"),
                    value = app.amounts.formattedNativeAmount(closure.rent, wallet.chainId),
                )
            }
        }
    }
}
