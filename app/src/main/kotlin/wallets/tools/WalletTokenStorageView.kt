package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.isWatchOnly
import dev.spectra.wallets.title
import uniffi.spectra_core.NearStorageDeposit
import uniffi.spectra_core.NearStorageDeposits
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState

/** Getting one token contract's storage deposit back. */
internal data class RefundTokenStorageRoute(val walletId: String, val deposit: NearStorageDeposit) : Route

/**
 * The token contracts holding a storage deposit this NEAR account can have
 * back, as core reads them from a node; and getting one back: core builds the
 * unregistration as a send, and its page signs and broadcasts it through the
 * same stages.
 */
@Composable
internal fun WalletTokenStorageView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val storage = remember(wallet.id) { ToolRead<NearStorageDeposits>() }
    suspend fun load() = storage.load { app.bridge.ready().walletTokenStorage(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.TOKEN_STORAGE.title, onRefresh = { load() }) {
        ToolReadSections(storage) { read ->
            ToolListSection(
                items = read.deposits,
                footer = AppLocalization.string(
                    "A token contract keeps a deposit, usually 0.00125 NEAR, for each account it holds a balance for, even after the tokens are gone. Once the account holds none of a token, unregistering returns the deposit.",
                ),
                emptyText = AppLocalization.string("No storage deposits to get back."),
            ) { deposit ->
                Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                    ) {
                        Text(
                            deposit.symbol,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                        Text(
                            app.amounts.formattedNativeAmount(deposit.refund, wallet.chainId),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                    IdentifierText(deposit.contract, style = MaterialTheme.typography.labelSmall)
                    if (!wallet.signing.isWatchOnly) {
                        ToolRowButton(
                            text = AppLocalization.string("Get Deposit Back"),
                            onClick = { navigator.push(RefundTokenStorageRoute(wallet.id, deposit)) },
                        )
                    }
                }
            }
            if (read.deposits.isNotEmpty()) {
                ToolSection {
                    SpectraValueRow(
                        label = AppLocalization.string("Refundable"),
                        value = app.amounts.formattedNativeAmount(read.refundable, wallet.chainId),
                    )
                }
            }
        }
    }
}

/** Building, signing and broadcasting one unregistration. */
@Composable
internal fun RefundTokenStorageView(wallet: WalletState, deposit: NearStorageDeposit) {
    val app = LocalAppState.current
    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string("Get Deposit Back"),
        authenticationReason = AppLocalization.format("Authenticate to get a storage deposit back for %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The deposit is back once it confirms."),
        build = { app.bridge.ready().buildTokenStorageRefund(wallet.id, deposit.contract) },
    ) { artifact ->
        ToolSection(
            footer = AppLocalization.string(
                "The contract unregisters this account and returns its deposit. Receiving the token again registers the account anew, for a new deposit.",
            ),
        ) {
            IdentifierValueRow(AppLocalization.string("Contract"), deposit.contract)
            val refund = artifact?.operation as? WalletOperation.RefundTokenStorage
            if (refund != null) {
                SpectraValueRow(
                    label = AppLocalization.string("Deposit Returned"),
                    value = app.amounts.formattedNativeAmount(refund.refund, wallet.chainId),
                )
            }
        }
    }
}
