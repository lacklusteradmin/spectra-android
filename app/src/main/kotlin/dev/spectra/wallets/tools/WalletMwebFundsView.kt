package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.app.authenticate
import dev.spectra.app.refreshBalances
import dev.spectra.app.runToCompletion
import dev.spectra.components.AddressEntryRow
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.WalletPasswordField
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.coinSymbol
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import dev.spectra.wallets.PrivateFundsFlow
import dev.spectra.wallets.ScanRun
import dev.spectra.wallets.title
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.LitecoinMwebStatus
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState
import java.util.UUID

/** Moving transparent LTC into MWEB, or paying from MWEB. */
internal data class MwebTransactionRoute(val walletId: String, val flow: PrivateFundsFlow) : Route

/**
 * A Litecoin wallet's MWEB funds, as core keeps them: the scan of a Litecoin
 * node's MWEB outputs, the stealth address the wallet receives at, moving
 * its transparent LTC into MWEB, and paying from MWEB. Core builds each
 * transaction; its page signs and broadcasts it through the same stages as
 * any send.
 */
@Composable
internal fun WalletMwebFundsView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    var status by remember { mutableStateOf<LitecoinMwebStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scan = remember(wallet.id) { ScanRun() }

    suspend fun load() {
        try {
            status = app.bridge.ready().litecoinMwebStatus(wallet.id)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = userErrorMessage(failure)
        }
    }

    /**
     * Scan one core batch at a time until the scan is complete; the first
     * batch derives the MWEB keys from the seed, and only it asks for the
     * password.
     */
    suspend fun run(request: UUID) {
        try {
            scan.error = null
            val derivesKeys = status?.ready != true
            if (derivesKeys) {
                val failure = app.authenticate(DeviceAuthenticationAction.SEND, AppLocalization.string("Authorize the MWEB scan"))
                if (failure != null) {
                    scan.error = failure
                    return
                }
            }
            val password = if (derivesKeys && scan.password.text.isNotEmpty()) scan.password.text.toString() else null
            try {
                while (scan.isCurrent(request)) {
                    val next = runToCompletion { app.bridge.ready().syncLitecoinMweb(wallet.id, password) }
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

    SpectraPage(title = WalletAction.MWEB_FUNDS.title) {
        val current = status
        val failure = error
        when {
            current != null -> {
                MwebScanSection(wallet, current, scan)
                if (current.ready) {
                    PrivateBalanceSection(
                        wallet = wallet,
                        spendable = current.spendable,
                        pending = current.pending,
                        complete = current.complete,
                    ) {
                        FilledTonalButton(
                            onClick = { navigator.push(MwebTransactionRoute(wallet.id, PrivateFundsFlow.MOVE_IN)) },
                            enabled = !scan.isRunning,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(AppLocalization.string("Move LTC into MWEB")) }
                        FilledTonalButton(
                            onClick = { navigator.push(MwebTransactionRoute(wallet.id, PrivateFundsFlow.SEND)) },
                            enabled = !scan.isRunning && current.spendable != "0",
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(AppLocalization.string("Send from MWEB")) }
                    }
                }
                current.address?.let { address ->
                    PrivateAddressSection(
                        address = address,
                        title = AppLocalization.string("MWEB Address"),
                        footer = AppLocalization.string(
                            "A stealth address: each payment to it makes an output only this wallet recognizes, unlinked to its transparent address.",
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
private fun MwebScanSection(wallet: WalletState, status: LitecoinMwebStatus, scan: ScanRun) {
    ToolSection(
        footer = AppLocalization.string(
            "This device reads the unspent MWEB outputs a Litecoin node holds and finds the ones sent to this wallet; the keys never leave it. The node learns only that a light client asked.",
        ),
    ) {
        if (status.ready) {
            SpectraValueRow(label = AppLocalization.string("Scanned"), value = status.scannedHeight.toString())
        }
        if (scan.isRunning) {
            LinearProgressIndicator(
                progress = { status.progressPermille.toFloat() / 1000f },
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(onClick = { scan.cancel() }) { Text(AppLocalization.string("Cancel")) }
        } else {
            // The first batch derives the MWEB keys from the seed.
            val asksPassword = !status.ready && wallet.signing.requiresPassword
            if (asksPassword) WalletPasswordField(scan.password)
            Button(
                onClick = { scan.begin() },
                enabled = !(asksPassword && scan.password.text.isEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(AppLocalization.string(if (status.complete) "Scan Again" else "Scan for MWEB Funds"))
            }
        }
        scan.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** A peg-in's amount, or a payment's recipient and amount; then the transaction core builds, signed and broadcast through the stages. */
@Composable
internal fun MwebTransactionView(wallet: WalletState, flow: PrivateFundsFlow) {
    val app = LocalAppState.current
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val formIsComplete = (flow != PrivateFundsFlow.SEND || recipient.isNotBlank()) && amount.isNotBlank()

    suspend fun build(): SendArtifact {
        val destination = recipient.trim()
        val value = AmountPresentation.canonicalDecimalInput(amount)
        return when (flow) {
            PrivateFundsFlow.MOVE_IN -> app.bridge.ready().buildLitecoinMwebPegin(wallet.id, value)
            PrivateFundsFlow.SEND -> app.bridge.ready().buildLitecoinMwebSend(wallet.id, destination, value)
        }
    }

    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string(if (flow == PrivateFundsFlow.MOVE_IN) "Move LTC into MWEB" else "Send from MWEB"),
        authenticationReason = AppLocalization.format("Authenticate to send MWEB funds from %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The funds move once it confirms; scan again to see them."),
        formIsComplete = formIsComplete,
        build = { build() },
        fields = {
            ToolSection(
                footer = AppLocalization.string(
                    if (flow == PrivateFundsFlow.SEND) {
                        "An MWEB address is paid privately. Any other Litecoin address is paid by a peg-out, which shows the amount and the recipient on the chain."
                    } else {
                        "Transparent LTC moves into this wallet's MWEB funds. The transaction shows the transparent address it comes from."
                    },
                ),
            ) {
                if (flow == PrivateFundsFlow.SEND) {
                    AddressEntryRow(
                        title = AppLocalization.string("Destination Address"),
                        text = recipient,
                        onTextChange = { recipient = it },
                        chain = wallet.chainId,
                        contacts = app.addressBook.entries.filter { it.chainId == wallet.chainId },
                    )
                }
                AmountField(value = amount, onValueChange = { amount = it })
            }
        },
    ) { artifact ->
        when (val operation = artifact?.operation) {
            is WalletOperation.ShieldTransparent -> ToolSection {
                SpectraValueRow(
                    label = AppLocalization.string("Into MWEB"),
                    value = app.amounts.formattedNativeAmount(operation.amount, wallet.chainId),
                )
            }
            is WalletOperation.ShieldedPayment -> PrivatePaymentSummary(wallet, artifact, memo = null)
            else -> Unit
        }
    }
}

/** An amount of the wallet's coin, typed with the device's decimal separator. */
@Composable
internal fun AmountField(value: String, onValueChange: (String) -> Unit, label: String = AppLocalization.string("Amount")) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/**
 * What a private-funds page holds once its scan has started: spendable and
 * pending, then the page's own rows and actions.
 */
@Composable
internal fun PrivateBalanceSection(
    wallet: WalletState,
    spendable: String,
    pending: String,
    complete: Boolean,
    actions: @Composable () -> Unit,
) {
    val hidesBalances = LocalAppState.current.preferences.hideBalances
    val symbol = wallet.chainId.coinSymbol
    ToolSection(
        footer = if (complete) null else AppLocalization.string("The scan is not finished; funds it has not reached are not counted."),
    ) {
        SpectraValueRow(
            label = AppLocalization.string("Spendable"),
            value = AmountPresentation.exactAmount(spendable, symbol),
            isHidden = hidesBalances,
        )
        if (pending != "0") {
            SpectraValueRow(
                label = AppLocalization.string("Pending"),
                value = AmountPresentation.exactAmount(pending, symbol),
                isHidden = hidesBalances,
            )
        }
        actions()
    }
}
