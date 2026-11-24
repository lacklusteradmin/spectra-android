package dev.spectra.send

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.spectra.app.LocalAppState
import dev.spectra.components.AddressEntryRow
import dev.spectra.components.FlowPageHeading
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLoadingRow
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.paymentMemoKinds

/**
 * The composer's recipient: the address, entered as on every page that asks
 * for one, and what core said of it.
 */
@Composable
internal fun SendRecipientPage() {
    val app = LocalAppState.current
    val flow = app.sendFlow
    val presentation = SendComposerPresentation(app)
    SendAssetContextView(presentation)
    FlowPageHeading(
        title = AppLocalization.string("Who are you sending to?"),
        subtitle = AppLocalization.string("Enter an address or choose a saved contact."),
    )
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            SendRecipientIdentityView(
                chain = presentation.selectedCoin?.chainId,
                address = flow.currentRecipient?.address ?: flow.address,
            )
            val coin = presentation.selectedCoin
            if (coin != null) {
                // The field's label names it, as the section label over it does
                // on iOS.
                AddressEntryRow(
                    title = AppLocalization.string("Recipient address"),
                    text = flow.address,
                    onTextChange = { flow.address = it },
                    chain = coin.chainId,
                    contacts = presentation.addressBookEntries,
                    readScan = flow::applyScannedPayment,
                )
                val kinds = remember(coin.chainId) { paymentMemoKinds(coin.chainId) }
                if (kinds.isNotEmpty()) {
                    SendPaymentMemoField(
                        kinds = kinds,
                        kind = flow.memoKind,
                        onKindChange = { flow.memoKind = it },
                        text = flow.memoText,
                        onTextChange = { flow.memoText = it },
                    )
                }
            }
            RecipientStatus()
            RecipientMessages(presentation)
        }
    }
    SpectraLabel(
        text = AppLocalization.string("Check the full address. A valid address does not verify the recipient's identity."),
        icon = Icons.Outlined.Info,
        color = secondaryText,
        modifier = Modifier.padding(horizontal = SpectraLayout.Space.xs),
    )
}

/**
 * One row while anything about the recipient is being asked — the address
 * check and the preview's look at its history — then core's verdict.
 */
@Composable
private fun RecipientStatus() {
    val flow = LocalAppState.current.sendFlow
    val problem = flow.recipientProblem
    val resolution = flow.currentRecipient
    when {
        flow.isResolvingRecipient || flow.isCheckingDestination ->
            SpectraLoadingRow(title = AppLocalization.string("Checking recipient..."))
        problem != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            SpectraLabel(
                text = problem.message,
                icon = Icons.Outlined.WarningAmber,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            if (problem.canRetry) {
                TextButton(onClick = flow::retryRecipientResolution) { Text(AppLocalization.string("Retry")) }
            }
        }
        resolution != null -> SpectraLabel(
            text = if (resolution.usedEns) {
                AppLocalization.format("Resolved ENS %@ to %@.", flow.address.trim(), resolution.address)
            } else {
                AppLocalization.string("Valid address for this network")
            },
            icon = Icons.Outlined.CheckCircle,
            color = SpectraColors.semantic.success,
        )
    }
}

@Composable
private fun RecipientMessages(presentation: SendComposerPresentation) {
    val flow = LocalAppState.current.sendFlow
    scanNotice(flow, presentation)?.let { notice ->
        SpectraLabel(text = notice, icon = Icons.Outlined.QrCodeScanner, color = secondaryText)
    }
    // What the recipient's history says is about a valid address only; beside
    // a refusal or mid-check it would describe nothing.
    if (flow.currentRecipient != null && flow.recipientProblem == null && !flow.isResolvingRecipient &&
        !flow.isCheckingDestination
    ) {
        flow.destinationWarnings.forEach { warning ->
            SpectraLabel(text = warning, icon = Icons.Outlined.WarningAmber, color = SpectraColors.semantic.warning)
        }
        flow.destinationInfoMessage?.let { info ->
            SpectraLabel(text = info, icon = Icons.Outlined.Info, color = secondaryText)
        }
    }
}

/**
 * "From the code: 0.1 BTC and Destination Tag 123456." — what the scan filled
 * in besides the address, while that address stands.
 */
private fun scanNotice(flow: SendFlowState, presentation: SendComposerPresentation): String? {
    val payment = flow.scannedPayment ?: return null
    if (payment.address != flow.address) return null
    val filled = mutableListOf<String>()
    val amount = payment.amount
    val coin = presentation.selectedCoin
    if (amount != null && coin != null) {
        filled += AppLocalization.format("scan.filled.part_format", amount, coin.symbol)
    }
    payment.memo?.let { memo ->
        filled += AppLocalization.format("scan.filled.part_format", memo.kind.localizedTitle, memo.value)
    }
    if (filled.isEmpty()) return null
    return AppLocalization.format("scan.filled_format", formattedAndList(filled))
}

internal fun formattedAndList(items: List<String>): String =
    ListFormatter.getInstance(AppLocalization.locale).format(items)
