package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.BalanceText
import dev.spectra.components.CopyRow
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.wallets.title
import uniffi.spectra_core.AddressBranch
import uniffi.spectra_core.OwnedAddressCoins
import uniffi.spectra_core.UnspentOutput
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletCoins
import uniffi.spectra_core.WalletState

/**
 * The addresses an account-discovery UTXO wallet has handed out and the
 * coins they hold, as core reads them from the network. Read-only.
 */
@Composable
internal fun WalletCoinsView(wallet: WalletState) {
    val app = LocalAppState.current
    val coins = remember(wallet.id) { ToolRead<WalletCoins>() }
    suspend fun load() = coins.load { app.bridge.ready().walletCoins(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.COINS.title, onRefresh = { load() }) {
        ToolReadSections(coins) { CoinsContent(it) }
    }
}

@Composable
private fun CoinsContent(coins: WalletCoins) {
    val hidesBalances = LocalAppState.current.preferences.hideBalances
    coins.nextReceiveAddress?.let { next ->
        ToolSection(
            title = AppLocalization.string("Next Receive Address"),
            footer = AppLocalization.string("Unused: a payment to it does not link to your other addresses."),
        ) { AddressCopyRow(next) }
    }
    if (coins.maturing != "0") {
        ToolSection {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(20.dp))
                BalanceText(
                    text = coins.maturing,
                    isHidden = hidesBalances,
                    style = MaterialTheme.typography.bodyMedium,
                    sentence = {
                        AppLocalization.format(
                            "%@ %@ in minting rewards is still maturing: counted in the balance, not yet spendable.",
                            it,
                            coins.symbol,
                        )
                    },
                )
            }
        }
    }
    AddressSection(AppLocalization.string("Receive Addresses"), coins.addresses.filter { it.branch == AddressBranch.RECEIVE }, coins)
    AddressSection(AppLocalization.string("Change Addresses"), coins.addresses.filter { it.branch == AddressBranch.CHANGE }, coins)
    AddressSection(AppLocalization.string("Other Addresses"), coins.addresses.filter { it.branch == null }, coins)
    ToolListSection(
        items = coins.outputs,
        title = AppLocalization.format("Coins (%lld)", coins.outputs.size),
        emptyText = AppLocalization.string("No unspent coins."),
    ) { output ->
        Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BalanceText(
                    text = AmountPresentation.exactAmount(output.amount, coins.symbol),
                    isHidden = hidesBalances,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
                Text(output.confirmationsText, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
            IdentifierText(output.outpoint)
            if (!output.spendable) {
                SpectraLabel(
                    text = AppLocalization.string("Maturing"),
                    icon = Icons.Outlined.HourglassEmpty,
                    color = SpectraColors.semantic.warning,
                )
            }
        }
    }
}

@Composable
private fun AddressSection(title: String, rows: List<OwnedAddressCoins>, coins: WalletCoins) {
    if (rows.isEmpty()) return
    val hidesBalances = LocalAppState.current.preferences.hideBalances
    ToolListSection(items = rows, title = title) { row ->
        Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                row.index?.let {
                    Text(
                        "#$it",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = secondaryText,
                    )
                }
                Spacer(Modifier.weight(1f))
                BalanceText(
                    text = AmountPresentation.exactAmount(row.balance, coins.symbol),
                    isHidden = hidesBalances,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
            AddressCopyRow(row.address)
            if (!row.used) {
                Text(AppLocalization.string("Unused"), style = MaterialTheme.typography.labelSmall, color = secondaryText)
            }
        }
    }
}

/** An address on one line, copied whole wherever it is tapped. */
@Composable
private fun AddressCopyRow(address: String) {
    CopyRow(value = address, padded = false) {
        Text(
            address,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

internal val UnspentOutput.outpoint: String get() = "$txid:$vout"

internal val UnspentOutput.confirmationsText: String
    get() = if (confirmations == 0uL) {
        AppLocalization.string("Unconfirmed")
    } else {
        AppLocalization.formatCount("%lld confirmations", confirmations.toLong(), confirmations.toLong())
    }
