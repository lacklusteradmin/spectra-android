package dev.spectra.send

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLoadingRow
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.chainName
import dev.spectra.models.displayName
import dev.spectra.models.formatNumber
import dev.spectra.models.feeSymbol
import dev.spectra.models.hasSendPreview
import dev.spectra.models.isEvm
import dev.spectra.models.isNativeCoin
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.Chain
import uniffi.spectra_core.OwnedSendPreview
import uniffi.spectra_core.SendFeeTerms
import uniffi.spectra_core.SendPreview

/**
 * The send flow's network section: fee estimates and the per-chain options
 * that go with them. Shown inside the review's disclosure, which already
 * names it: no page header of its own.
 */
@Composable
internal fun SendNetworkStep() {
    val app = LocalAppState.current
    val selectedCoin = app.selectedSendCoin
    if (selectedCoin?.chainId?.hasSendPreview == true) {
        SpectraCard(elevated = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l)) {
                // One branch per preview shape. The chain decides which,
                // through the registry; nothing here names one.
                if (selectedCoin.chainId.isEvm) {
                    EvmNetworkContent(app, selectedCoin)
                } else {
                    SimpleFeeContent(app, selectedCoin, selectedCoin.chainId)
                }
                SendPreviewDetails(app, selectedCoin)
            }
        }
    } else {
        SpectraCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                NetworkSectionHeader(AppLocalization.string("Network"))
                Text(
                    if (selectedCoin != null) {
                        AppLocalization.format(
                            "Spectra will prepare the %@ transfer with the default %@ network policy.",
                            selectedCoin.symbol,
                            selectedCoin.chainName,
                        )
                    } else {
                        AppLocalization.string("Select an asset to load network details.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                )
            }
        }
    }
}

/** The quoted fee, with its display-currency value when core had one. */
private fun networkFeeText(app: AppState, quote: OwnedSendPreview, chain: Chain): String =
    AppLocalization.format(
        "Estimated Network Fee: %@",
        app.amounts.compactNetworkFee(quote.preview.estimatedNetworkFee, quote.networkFeeValue, chain),
    )

/**
 * A typed value that keeps its name and unit beside it once filled: a
 * placeholder alone left Max Fee and Priority Fee looking the same. The
 * placeholder is the quote's value, not the name again.
 */
@Composable
private fun LabeledField(
    title: String,
    unit: String?,
    estimate: String?,
    text: String,
    onTextChange: (String) -> Unit,
    keyboard: KeyboardType,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        placeholder = estimate?.let { { Text(it) } },
        suffix = unit?.let { { Text(it, color = secondaryText) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End, fontFeatureSettings = "tnum"),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
    )
}

/** A switch inside a card: the whole row toggles it. */
@Composable
private fun CardSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SpectraLayout.rowMinHeight)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun EvmNetworkContent(app: AppState, selectedCoin: AssetHolding) {
    val flow = app.sendFlow
    val terms = flow.quotedEvmTerms
    val quote = app.sendQuote
    val previewError = flow.previewError
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        NetworkSectionHeader(AppLocalization.format("%@ Network", selectedCoin.chainName))
        CardSwitch(AppLocalization.string("Use Custom Fees"), flow.useCustomEvmFees) { flow.useCustomEvmFees = it }
        if (flow.useCustomEvmFees) {
            LabeledField(
                title = AppLocalization.string("Max Fee"),
                unit = "gwei",
                estimate = terms?.let { AmountPresentation.decimalFieldText(it.maxFeePerGasGwei) },
                text = flow.customEvmMaxFeeGwei,
                onTextChange = { flow.customEvmMaxFeeGwei = it },
                keyboard = KeyboardType.Decimal,
            )
            LabeledField(
                title = AppLocalization.string("Priority Fee"),
                unit = "gwei",
                estimate = terms?.let { AmountPresentation.decimalFieldText(it.maxPriorityFeePerGasGwei) },
                text = flow.customEvmPriorityFeeGwei,
                onTextChange = { flow.customEvmPriorityFeeGwei = it },
                keyboard = KeyboardType.Decimal,
            )
            val feeError = flow.customEvmFeeValidationError
            if (feeError != null) {
                Text(feeError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                SpectraHintText(AppLocalization.string("Custom EIP-1559 fees are applied to this send and preview."))
            }
        }
        CardSwitch(AppLocalization.string("Manual Nonce"), flow.evmManualNonceEnabled) { flow.evmManualNonceEnabled = it }
        if (flow.evmManualNonceEnabled) {
            LabeledField(
                title = AppLocalization.string("Nonce"),
                unit = null,
                estimate = terms?.nonce?.toString(),
                text = flow.evmManualNonce,
                onTextChange = { flow.evmManualNonce = it },
                keyboard = KeyboardType.Number,
            )
            flow.evmNonceValidationError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        val quoted = (quote?.preview?.terms as? SendFeeTerms.Evm)?.terms
        when {
            flow.isPreparingPreview -> SpectraLoadingRow(title = AppLocalization.string("Loading nonce and fee estimate..."))
            quoted != null -> {
                SpectraDivider()
                SpectraValueRow(label = AppLocalization.string("Nonce"), value = quoted.nonce.toString())
                SpectraValueRow(label = AppLocalization.string("Gas Limit"), value = formatNumber(quoted.gasLimit))
                SpectraValueRow(label = AppLocalization.string("Max Fee"), value = app.amounts.compactGasPrice(quoted.maxFeePerGasGwei))
                SpectraValueRow(
                    label = AppLocalization.string("Priority Fee"),
                    value = app.amounts.compactGasPrice(quoted.maxPriorityFeePerGasGwei),
                )
            }
            previewError != null -> SpectraLabel(
                text = previewError,
                icon = Icons.Outlined.WarningAmber,
                color = MaterialTheme.colorScheme.error,
            )
            // The amount and recipient are in by this page; a missing quote is
            // one still on its way.
            else -> SpectraLoadingRow(title = AppLocalization.string("Loading nonce and fee estimate..."))
        }
    }
}

/**
 * The fee card for every chain but the EVM family, whose card carries fee and
 * nonce overrides. A UTXO preview's rate, size, inputs and change are the
 * detail rows below it.
 */
@Composable
private fun SimpleFeeContent(app: AppState, selectedCoin: AssetHolding, chain: Chain) {
    val flow = app.sendFlow
    val chainName = selectedCoin.chainName
    val quote = app.sendQuote
    val previewError = flow.previewError
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        NetworkSectionHeader(AppLocalization.format("%@ Network", chainName))
        when {
            flow.isPreparingPreview -> SpectraLoadingRow(title = AppLocalization.format("Loading %@ fee estimate...", chainName))
            quote != null -> {
                Text(
                    networkFeeText(app, quote, chain),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                if (!selectedCoin.isNativeCoin) {
                    SpectraHintText(
                        AppLocalization.format(
                            "Token transfers on %@ pay network fees in %@. Keep a %@ balance for fees.",
                            chainName,
                            chain.feeSymbol,
                            chain.feeSymbol,
                        ),
                    )
                }
            }
            previewError != null -> SpectraLabel(
                text = previewError,
                icon = Icons.Outlined.WarningAmber,
                color = MaterialTheme.colorScheme.error,
            )
            else -> SpectraLoadingRow(title = AppLocalization.format("Loading %@ fee estimate...", chainName))
        }
        SpectraHintText(AppLocalization.format("Spectra signs and broadcasts %@ transfers in-app.", chain.displayName))
    }
}

/**
 * Whether the preview says anything the fee rows and the amount page have
 * not: an EVM fee rate is already rows of its own, and the balance and
 * maximum are the amount page's.
 */
private fun SendPreview.hasDetailRows(isEvm: Boolean): Boolean {
    if (terms is SendFeeTerms.Utxo) return true
    return (!isEvm && feeRateDescription != null) || estimatedTransactionBytes != null
}

/**
 * What the fee section above has not already said. The EVM rows state the fee
 * rate, and the amount page states the balance and the maximum, so those are
 * not repeated here — the repeat of the rate rounded the priority fee the rows
 * gave as 0.001 gwei to "0.00".
 */
@Composable
private fun SendPreviewDetails(app: AppState, selectedCoin: AssetHolding) {
    val isEvm = selectedCoin.chainId.isEvm
    val preview = app.sendPreview(selectedCoin) ?: return
    if (!preview.hasDetailRows(isEvm)) return
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        SpectraDivider(modifier = Modifier.padding(vertical = SpectraLayout.Space.xs))
        val feeRate = preview.feeRateDescription
        if (feeRate != null && !isEvm) {
            SpectraValueRow(label = AppLocalization.string("Fee Rate"), value = feeRate)
        }
        preview.estimatedTransactionBytes?.let { bytes ->
            SpectraValueRow(
                label = AppLocalization.string("Estimated Size"),
                value = AppLocalization.formatCount("%lld bytes", bytes, bytes),
            )
        }
        val utxo = preview.terms as? SendFeeTerms.Utxo
        if (utxo != null) {
            SpectraValueRow(label = AppLocalization.string("Selected Inputs"), value = utxo.selectedInputCount.toString())
            SpectraValueRow(
                label = AppLocalization.string("Change Output"),
                value = AppLocalization.string(if (utxo.usesChangeOutput) "Yes" else "No"),
            )
        }
    }
}

/** Shared by the network card and the no-preview card that replaces it. */
@Composable
private fun NetworkSectionHeader(title: String) {
    SendComposerSectionLabel(title, Modifier.padding(bottom = SpectraLayout.Space.s))
}
