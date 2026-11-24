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
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.isWatchOnly
import dev.spectra.wallets.title
import uniffi.spectra_core.SuiCoinType
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState

/** Merging one Sui coin type's objects into one. */
internal data class MergeCoinsRoute(val walletId: String, val type: SuiCoinType) : Route

/** "N objects", in the reader's language and number. */
private fun objectCount(objects: ULong): String = AppLocalization.formatCount("%lld objects", objects.toLong(), objects.toLong())

/**
 * A Sui wallet's coin types and how many objects hold each, as core reads
 * them from a node; and merging a type's objects into one: core builds and
 * prices the merge as a send, and its page signs and broadcasts it through
 * the same stages.
 */
@Composable
internal fun WalletCoinObjectsView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val types = remember(wallet.id) { ToolRead<List<SuiCoinType>>() }
    suspend fun load() = types.load { app.bridge.ready().walletCoinObjects(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.COIN_OBJECTS.title, onRefresh = { load() }) {
        ToolReadSections(types) { coinTypes ->
            ToolListSection(
                items = coinTypes,
                footer = AppLocalization.string(
                    "Every coin received on Sui is a separate object, and a send names each one it spends. Merging a coin's objects into one makes later sends cheaper.",
                ),
            ) { type ->
                Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            type.symbol,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(objectCount(type.objects), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                    }
                    Text(
                        type.balance,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = secondaryText,
                    )
                    IdentifierText(type.coinType)
                    if (type.mergeable && !wallet.signing.isWatchOnly) {
                        ToolRowButton(
                            text = AppLocalization.string("Merge"),
                            onClick = { navigator.push(MergeCoinsRoute(wallet.id, type)) },
                        )
                    }
                }
            }
        }
    }
}

/** Building, signing and broadcasting one merge. */
@Composable
internal fun MergeCoinsView(wallet: WalletState, type: SuiCoinType) {
    val app = LocalAppState.current
    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string("Merge Coins"),
        authenticationReason = AppLocalization.format("Authenticate to merge coins in %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The objects are one once it confirms."),
        build = { app.bridge.ready().buildCoinMerge(wallet.id, type.coinType) },
    ) { artifact ->
        ToolSection(
            footer = AppLocalization.string(
                "The objects become one, and nothing leaves the wallet. Storage freed by the merged objects is refunded, which often covers the fee.",
            ),
        ) {
            SpectraValueRow(label = AppLocalization.string("Coin"), value = type.symbol)
            val merge = artifact?.operation as? WalletOperation.MergeCoins
            if (merge != null) {
                SpectraValueRow(label = AppLocalization.string("Objects Merged"), value = objectCount(merge.objects))
            }
        }
    }
}
