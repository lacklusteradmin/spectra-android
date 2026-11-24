package dev.spectra.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDisclosure
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLoadingRow
import dev.spectra.components.SpectraNoticeCard
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.artworkName
import dev.spectra.models.isNativeCoin
import uniffi.spectra_core.OwnedSendPreview

/** The pre-build review form. */
@Composable
internal fun SendConfirmationStep() {
    val app = LocalAppState.current
    val flow = app.sendFlow
    val selectedCoin = app.selectedSendCoin
    val confirmedQuote = if (flow.quoteIsCurrent) app.sendQuoteForEnteredAmount else null
    if (selectedCoin != null) {
        SendAmountSummaryView(
            artworkName = selectedCoin.artworkName,
            symbol = selectedCoin.symbol,
            chain = selectedCoin.chainId,
            amount = flow.amountInput,
            fiatText = app.amounts.formattedFiatIfAvailable(confirmedQuote?.amountValue)?.let { "≈ $it" },
        )
        SpectraCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpectraLayout.cardPadding),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                SendTransferPartiesView(
                    walletId = flow.walletId,
                    chain = selectedCoin.chainId,
                    sender = app.selectedWalletForSend()?.address,
                    recipient = flow.currentRecipient?.address ?: flow.address,
                )
                SpectraDivider()
                val fee = networkFeeText(app, confirmedQuote)
                SendCostRows(fee = fee, total = totalText(app, confirmedQuote, fee))
                val previewError = flow.previewError
                val refusal = app.sendAmountRefusal
                if (previewError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpectraLabel(
                            text = previewError,
                            icon = Icons.Outlined.WarningAmber,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = flow::schedulePreview) { Text(AppLocalization.string("Retry")) }
                    }
                } else if (refusal != null) {
                    SpectraLabel(text = refusal, icon = Icons.Outlined.WarningAmber, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    // Under the parties they are about, before anything else on the page, as
    // the built transaction's review puts its warnings.
    flow.destinationWarnings.forEach { warning ->
        SpectraLabel(
            text = warning,
            icon = Icons.Outlined.WarningAmber,
            color = SpectraColors.semantic.warning,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    SpectraDisclosure(title = AppLocalization.string("Fee and advanced settings")) {
        SendNetworkStep()
    }
    SpectraLabel(
        text = AppLocalization.string(
            "Check the full address and amount. You can review the built transaction again before signing.",
        ),
        icon = Icons.Outlined.VerifiedUser,
        color = secondaryText,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (flow.session.isBusy) {
        SpectraLoadingRow(
            title = AppLocalization.string("Preparing transaction..."),
            subtitle = AppLocalization.string("Keep this screen open while Spectra prepares the transfer."),
        )
    }
}

/**
 * The fee as far as it is known: being quoted, quoted, unavailable, or — on
 * a network whose fee only the built transaction fixes — set at build. A
 * quote that is not yet asked for (typing pauses first) is on its way, not
 * absent; an override still being typed is answered beside its field.
 */
private fun networkFeeText(app: AppState, confirmedQuote: OwnedSendPreview?): String {
    val flow = app.sendFlow
    if (flow.evmNonceValidationError != null || flow.customEvmFeeValidationError != null) {
        return AppLocalization.string("Unavailable")
    }
    if (flow.previewError != null) return AppLocalization.string("Unavailable")
    if (flow.isPreparingPreview || !flow.quoteIsCurrent) return AppLocalization.string("Estimating…")
    val quote = confirmedQuote ?: return AppLocalization.string("Set when the transaction is built")
    return "≈ " + app.amounts.compactNetworkFee(quote.preview.estimatedNetworkFee, quote.networkFeeValue, quote.chainId)
}

/**
 * The amount and the quoted fee together, when the coin pays its own fee, at
 * the coin's display precision as every amount is. The row stays while the
 * fee is being quoted, so the card does not jump.
 */
private fun totalText(app: AppState, confirmedQuote: OwnedSendPreview?, feeText: String): String? {
    val coin = app.selectedSendCoin ?: return null
    if (!coin.isNativeCoin) return null
    val flow = app.sendFlow
    val total = confirmedQuote?.total
    if (total == null || flow.isPreparingPreview || flow.previewError != null || !flow.quoteIsCurrent) return feeText
    return "≈ " + app.amounts.formattedAssetAmount(total, coin.symbol, coin.id)
}

/**
 * The build's, signature's and broadcast's errors and core's verification
 * notice, riding with the button that raised them, where they are seen
 * whatever the scroll.
 */
@Composable
internal fun SendStatusCards() {
    val flow = LocalAppState.current.sendFlow
    val error = flow.session.error
    val notice = flow.verificationNotice
    if (error == null && notice == null) return
    Column(
        modifier = Modifier
            .widthIn(max = SpectraLayout.pageMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = SpectraLayout.Space.l),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        if (error != null) {
            SpectraNoticeCard(text = error, color = SpectraColors.semantic.failure, icon = Icons.Outlined.WarningAmber)
        }
        if (notice != null) {
            SpectraNoticeCard(
                text = notice,
                color = if (flow.verificationNoticeIsWarning) SpectraColors.semantic.failure else SpectraColors.semantic.warning,
                icon = Icons.Outlined.ErrorOutline,
            )
        }
    }
}
