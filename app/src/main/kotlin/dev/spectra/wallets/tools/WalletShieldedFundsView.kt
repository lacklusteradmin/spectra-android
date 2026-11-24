package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.app.authenticate
import dev.spectra.app.refreshBalances
import dev.spectra.app.runToCompletion
import dev.spectra.components.AddressEntryRow
import dev.spectra.components.CopyButton
import dev.spectra.components.QRCodeImage
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.WalletPasswordField
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.gasTokenSymbol
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import dev.spectra.wallets.PrivateFundsFlow
import dev.spectra.wallets.ScanRun
import dev.spectra.wallets.title
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.ZcashShieldedStatus
import java.util.UUID

/** Shielding transparent funds, or paying from the shielded pool. */
internal data class ShieldedTransactionRoute(val walletId: String, val flow: PrivateFundsFlow) : Route

/**
 * A Zcash wallet's shielded funds, as core keeps them: the scan from the
 * wallet's restore height through a lightwalletd server, the unified address
 * it receives at, moving its transparent funds into the shielded pool, and
 * paying from it. Core builds each transaction; its page signs and
 * broadcasts it through the same stages as any send.
 */
@Composable
internal fun WalletShieldedFundsView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    var status by remember { mutableStateOf<ZcashShieldedStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scan = remember(wallet.id) { ScanRun() }

    suspend fun load() {
        try {
            status = app.bridge.ready().zcashShieldedStatus(wallet.id)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = userErrorMessage(failure)
        }
    }

    /**
     * Scan one core batch at a time until the scan is complete. Core keeps
     * every batch, so a cancelled scan resumes where it stopped; the first
     * batch makes the shielded account from the seed, and only it asks for
     * the password.
     */
    suspend fun run(request: UUID) {
        try {
            scan.error = null
            val makesAccount = status?.ready != true
            if (makesAccount) {
                val failure = app.authenticate(DeviceAuthenticationAction.SEND, AppLocalization.string("Authorize the shielded scan"))
                if (failure != null) {
                    scan.error = failure
                    return
                }
            }
            val password = if (makesAccount && scan.password.text.isNotEmpty()) scan.password.text.toString() else null
            try {
                while (scan.isCurrent(request)) {
                    val next = runToCompletion { app.bridge.ready().syncZcashShielded(wallet.id, password) }
                    if (!scan.isCurrent(request)) return
                    status = next
                    if (next.complete) break
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (scan.isCurrent(request)) scan.error = userErrorMessage(failure)
                return
            }
            app.refreshBalances()
        } finally {
            scan.finish(request)
        }
    }

    LaunchedEffect(wallet.id) { load() }
    LaunchedEffect(scan.requestId) { scan.requestId?.let { run(it) } }
    DisposableEffect(scan) { onDispose { scan.cancel() } }

    SpectraPage(title = WalletAction.SHIELDED_FUNDS.title) {
        val current = status
        val failure = error
        when {
            current != null -> {
                ShieldedScanSection(wallet, current, scan)
                if (current.ready) {
                    PrivateBalanceSection(
                        wallet = wallet,
                        spendable = current.spendable,
                        pending = current.pending,
                        complete = current.complete,
                    ) {
                        if (current.shieldable != "0") {
                            SpectraValueRow(
                                label = AppLocalization.string("Transparent, to Shield"),
                                value = AmountPresentation.exactAmount(current.shieldable, wallet.chainId.gasTokenSymbol),
                                isHidden = app.preferences.hideBalances,
                            )
                            FilledTonalButton(
                                onClick = { navigator.push(ShieldedTransactionRoute(wallet.id, PrivateFundsFlow.MOVE_IN)) },
                                enabled = !scan.isRunning,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(AppLocalization.string("Shield Transparent Funds")) }
                        }
                        FilledTonalButton(
                            onClick = { navigator.push(ShieldedTransactionRoute(wallet.id, PrivateFundsFlow.SEND)) },
                            enabled = !scan.isRunning && current.spendable != "0",
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(AppLocalization.string("Send Shielded Funds")) }
                    }
                }
                current.address?.let { address ->
                    PrivateAddressSection(
                        address = address,
                        title = AppLocalization.string("Shielded Address"),
                        footer = AppLocalization.string(
                            "A unified address with Orchard and Sapling receivers. Funds sent here are shielded; the wallet's transparent address stays unlinked to it.",
                        ),
                    )
                }
            }
            failure != null -> WalletToolErrorSection(failure)
            else -> WalletToolLoadingSection()
        }
    }
}

@Composable
private fun ShieldedScanSection(wallet: WalletState, status: ZcashShieldedStatus, scan: ScanRun) {
    ToolSection(
        footer = AppLocalization.string(
            "This device reads every block from the restore height on and finds what was sent to this wallet; the keys never leave it. The server sees which transactions are fetched in full, and the transparent address.",
        ),
    ) {
        SpectraValueRow(label = AppLocalization.string("Restore Height"), value = status.restoreHeight.toString())
        if (status.ready) {
            SpectraValueRow(
                label = AppLocalization.string("Scanned"),
                value = "${status.scannedHeight} / ${status.chainTipHeight}",
            )
        }
        if (scan.isRunning) {
            LinearProgressIndicator(
                progress = { status.progressPermille.toFloat() / 1000f },
                modifier = Modifier.fillMaxWidth(),
            )
            if (status.unreadTransactions > 0u) {
                SpectraHintText(
                    AppLocalization.format("Reading %@ transactions found by the scan.", status.unreadTransactions.toString()),
                )
            }
            FilledTonalButton(onClick = { scan.cancel() }) { Text(AppLocalization.string("Cancel")) }
        } else {
            // The first batch makes the shielded account from the seed.
            val asksPassword = !status.ready && wallet.signing.requiresPassword
            if (asksPassword) WalletPasswordField(scan.password)
            Button(
                onClick = { scan.begin() },
                enabled = !(asksPassword && scan.password.text.isEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(AppLocalization.string(if (status.complete) "Scan Again" else "Scan for Shielded Funds"))
            }
        }
        scan.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** The address a private-funds page receives at: its code, its text and its copy button. */
@Composable
internal fun PrivateAddressSection(address: String, title: String, footer: String) {
    ToolSection(title = title, footer = footer) {
        QRCodeImage(text = address, modifier = Modifier.widthIn(max = 220.dp).align(Alignment.CenterHorizontally))
        SelectionContainer {
            Text(address, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        CopyButton(value = address, title = AppLocalization.string("Copy Address"), wide = true)
    }
}

/**
 * Shielding, or a payment's recipient, amount and memo; then the
 * transaction core builds, signed and broadcast through the stages to the
 * network's lightwalletd servers.
 */
@Composable
internal fun ShieldedTransactionView(wallet: WalletState, flow: PrivateFundsFlow) {
    val app = LocalAppState.current
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    // Shielding asks for nothing and is built as the page opens.
    val formIsComplete = if (flow == PrivateFundsFlow.SEND) recipient.isNotBlank() && amount.isNotBlank() else null

    suspend fun build(): SendArtifact {
        val destination = recipient.trim()
        val value = AmountPresentation.canonicalDecimalInput(amount)
        val note = memo.trim()
        return when (flow) {
            PrivateFundsFlow.MOVE_IN -> app.bridge.ready().buildZcashShielding(wallet.id)
            PrivateFundsFlow.SEND -> app.bridge.ready().buildZcashShieldedSend(wallet.id, destination, value, note.ifEmpty { null })
        }
    }

    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string(if (flow == PrivateFundsFlow.MOVE_IN) "Shield Transparent Funds" else "Send Shielded Funds"),
        authenticationReason = AppLocalization.format("Authenticate to send shielded funds from %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The funds move once it confirms; scan again to see them."),
        formIsComplete = formIsComplete,
        endpoints = { app.bridge.ready().zcashShieldedBroadcastEndpoints(it) },
        build = { build() },
        fields = {
            ToolSection(
                footer = AppLocalization.string(
                    "A unified or Sapling address receives privately; a transparent one is public. A memo reaches only a shielded recipient, and only the recipient reads it.",
                ),
            ) {
                AddressEntryRow(
                    title = AppLocalization.string("Destination Address"),
                    text = recipient,
                    onTextChange = { recipient = it },
                    chain = wallet.chainId,
                    contacts = app.addressBook.entries.filter { it.chainId == wallet.chainId },
                )
                AmountField(value = amount, onValueChange = { amount = it })
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(AppLocalization.string("Memo (Optional)")) },
                    maxLines = 4,
                )
            }
        },
    ) { artifact ->
        when (val operation = artifact?.operation) {
            is WalletOperation.ShieldTransparent -> ToolSection(
                footer = AppLocalization.string(
                    "Every transparent output of this wallet moves into its own shielded pool. The transaction shows the transparent address it came from.",
                ),
            ) {
                SpectraValueRow(
                    label = AppLocalization.string("Shielded"),
                    value = app.amounts.formattedNativeAmount(operation.amount, wallet.chainId),
                )
            }
            is WalletOperation.ShieldedPayment -> PrivatePaymentSummary(wallet, artifact, memo = operation.memo)
            else -> Unit
        }
    }
}

/**
 * A payment out of a wallet's private funds, as both private-funds pages
 * review it: the recipient, the amount, and the memo it carries.
 */
@Composable
internal fun PrivatePaymentSummary(wallet: WalletState, artifact: SendArtifact, memo: String?) {
    val app = LocalAppState.current
    ToolSection {
        IdentifierValueRow(AppLocalization.string("To"), artifact.recipient)
        SpectraValueRow(
            label = AppLocalization.string("Amount"),
            value = app.amounts.formattedNativeAmount(artifact.amount, wallet.chainId),
        )
        memo?.let { SpectraValueRow(label = AppLocalization.string("Memo"), value = it) }
    }
}
