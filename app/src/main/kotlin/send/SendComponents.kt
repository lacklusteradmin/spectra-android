package dev.spectra.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.artworkName
import dev.spectra.models.chainName
import dev.spectra.models.color
import dev.spectra.models.displayName
import uniffi.spectra_core.AddressBookEntry
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.Chain
import uniffi.spectra_core.SendStatus
import uniffi.spectra_core.WalletState

/** What every composer page reads of the wallet projection, read once per page. */
internal class SendComposerPresentation(app: AppState) {
    val sendWallets: List<WalletState> = app.sendEnabledWallets
    val selectedWallet: WalletState? = sendWallets.firstOrNull { it.id == app.sendFlow.walletId }
    val availableSendCoins: List<AssetHolding> = app.availableSendCoins(app.sendFlow.walletId)
    val selectedCoin: AssetHolding? = availableSendCoins.firstOrNull { it.id == app.sendFlow.holdingKey }
    val selectedCoinAmountText: String? = selectedCoin?.let { app.amounts.formattedAssetAmount(it.amount, it.symbol, it.id) }
    val selectedCoinApproximateFiatText: String? =
        app.amounts.formattedFiatIfAvailable(app.sendQuoteForEnteredAmount?.amountValue)
    val addressBookEntries: List<AddressBookEntry> = app.sendAddressBookEntries
}

/** The small label over a group inside a send card. */
@Composable
internal fun SendComposerSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = secondaryText,
    )
}

/**
 * The fee and what leaves the wallet, under a transfer's parties: one style on
 * the review and on the built transaction, so the same fee does not read as
 * two different things a page apart. [total] is the amount and the fee
 * together, when the fee is paid in the sent asset.
 */
@Composable
internal fun SendCostRows(fee: String, total: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        SpectraValueRow(label = AppLocalization.string("Network Fee"), value = fee)
        if (total != null) {
            SpectraValueRow(label = AppLocalization.string("Total"), value = total)
        }
    }
}

/**
 * Amounts supplied by the reviewed preview or immutable artifact, without a
 * new quote. [status] is where a staged transaction stands; `null` before one
 * is built.
 */
@Composable
internal fun SendAmountSummaryView(
    artworkName: String?,
    symbol: String,
    chain: Chain,
    amount: String,
    fiatText: String? = null,
    status: SendStatus? = null,
    isReceipt: Boolean = false,
) {
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            if (isReceipt && status != null) {
                Box(
                    modifier = Modifier.size(64.dp).spectraInsetFill(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(32.dp))
                }
                Text(
                    status.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            } else {
                SendNetworkLabel(artworkName, symbol, chain)
            }

            // Every digit remains available during signing review, including
            // amounts too long for one line at the reader's chosen text size.
            val secondary = secondaryText
            val headline = MaterialTheme.typography.headlineSmall
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.displaySmall.fontSize, fontWeight = FontWeight.SemiBold)) {
                        append(AmountPresentation.localizedDecimal(amount))
                    }
                    append(" ")
                    withStyle(SpanStyle(fontSize = headline.fontSize, fontWeight = FontWeight.Medium, color = secondary)) {
                        append(symbol)
                    }
                },
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
            )

            if (fiatText != null) {
                Text(
                    fiatText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = secondaryText,
                )
            }

            if (isReceipt) {
                SendNetworkLabel(artworkName, symbol, chain)
            } else if (status != null) {
                Row(
                    modifier = Modifier
                        .spectraInsetFill()
                        .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
                ) {
                    Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(16.dp))
                    Text(
                        status.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = status.color,
                    )
                }
            }
        }
    }
}

@Composable
private fun SendNetworkLabel(artworkName: String?, symbol: String, chain: Chain) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        CoinBadge(
            artworkName = artworkName,
            fallbackText = symbol,
            color = chain.color,
            size = 24.dp,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(chain.displayName, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
    }
}

/** The current asset and exact wallet network remain visible while composing. */
@Composable
internal fun SendAssetContextView(presentation: SendComposerPresentation) {
    val coin = presentation.selectedCoin ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .spectraInsetFill()
            .padding(SpectraLayout.Space.m)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        CoinBadge(coin.artworkName, coin.symbol, coin.color, size = 24.dp)
        Text(coin.symbol, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(
            presentation.selectedWallet?.chainName ?: coin.chainName,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText,
            modifier = Modifier.weight(1f),
        )
    }
}
