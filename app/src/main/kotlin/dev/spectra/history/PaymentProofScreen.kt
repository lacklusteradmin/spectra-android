package dev.spectra.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import dev.spectra.components.CopyRow
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.breakableAnywhere
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.gasTokenSymbol
import uniffi.spectra_core.Chain
import uniffi.spectra_core.MoneroPaymentProof

/**
 * The proof of a Monero payment, as core derives and checks it: the
 * transaction, its key and the address it paid. The recipient, or anyone
 * they show it to, checks it with monero-wallet-cli's `check_tx_key`.
 */
@Composable
internal fun PaymentProofScreen(proof: MoneroPaymentProof, chain: Chain) {
    val command = "check_tx_key ${proof.txid} ${proof.txKey} ${proof.address}"
    SpectraPage(title = AppLocalization.string("Payment Proof")) {
        SpectraRowSection(
            footer = AppLocalization.string(
                "Anyone with these three can see that this address received the amount, and nothing else about the wallet. Share them only with whoever needs to check the payment.",
            ),
            dividerInset = SpectraLayout.rowHorizontal,
        ) {
            row { ProofCopyRow(AppLocalization.string("Transaction ID"), proof.txid) }
            row { ProofCopyRow(AppLocalization.string("Transaction Key"), proof.txKey) }
            row { ProofCopyRow(AppLocalization.string("Address"), proof.address) }
            row {
                SpectraValueRow(
                    label = AppLocalization.string("Proves"),
                    value = AmountPresentation.exactAmount(proof.amount, chain.gasTokenSymbol),
                    modifier = Modifier.spectraRowPadding(),
                )
            }
        }
        SpectraRowSection(
            footer = AppLocalization.string(
                "Any Monero wallet checks the proof with its transaction-key check, such as this command in monero-wallet-cli.",
            ),
        ) {
            row { ProofCopyRow(AppLocalization.string("Command"), command) }
        }
    }
}

/** A value copied whole: a key or a command cut in the middle cannot be read back or checked against what was pasted. */
@Composable
private fun ProofCopyRow(title: String, value: String) {
    CopyRow(value = value) {
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            Text(
                breakableAnywhere(value),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.semantics { contentDescription = value },
            )
        }
    }
}
