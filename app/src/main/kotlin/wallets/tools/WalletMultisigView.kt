package dev.spectra.wallets.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.app.authenticate
import dev.spectra.app.runToCompletion
import dev.spectra.components.AddressEntryRow
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.components.shareText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.DateStyle
import dev.spectra.models.TimeStyle
import dev.spectra.models.formatUnixDate
import dev.spectra.models.coinSymbol
import dev.spectra.models.localizedText
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import dev.spectra.send.SendPaymentMemoField
import dev.spectra.wallets.title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.MultisigAccount
import uniffi.spectra_core.MultisigSession
import uniffi.spectra_core.MultisigSigner
import uniffi.spectra_core.MultisigSpend
import uniffi.spectra_core.MultisigSubmission
import uniffi.spectra_core.PaymentMemo
import uniffi.spectra_core.PaymentMemoKind
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.formatNativeAmount
import uniffi.spectra_core.paymentMemoKinds

/** One multisig session of the account, opened to sign, hand on or submit. */
internal data class MultisigSessionRoute(
    val walletId: String,
    val account: MultisigAccount,
    val session: MultisigSession,
) : Route

/**
 * A multisig account as core reads its policy, and its sessions: build a
 * spend, read one another signer sent, and open one to sign it, hand it on,
 * or submit it once enough signers have signed. Core decides who may sign
 * and how a session reaches the network; this page only asks. A session
 * opens on its own page; this one reads the sessions again on return.
 */
@Composable
internal fun WalletMultisigView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<MultisigAccount?>(null) }
    var sessions by remember { mutableStateOf<List<MultisigSession>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var memoKind by remember { mutableStateOf<PaymentMemoKind?>(null) }
    var memo by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            val service = app.bridge.ready()
            account = service.multisigAccount(wallet.id)
            sessions = service.multisigSessions(wallet.id)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = userErrorMessage(failure)
        }
    }

    fun work(body: suspend () -> Unit) {
        scope.launch {
            isWorking = true
            try {
                body()
                error = null
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = userErrorMessage(failure)
            } finally {
                isWorking = false
            }
        }
    }

    fun open(session: MultisigSession) {
        val current = account ?: return
        navigator.push(MultisigSessionRoute(wallet.id, current, session))
    }

    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.MULTISIG.title, onRefresh = { load() }) {
        val currentAccount = account
        val currentSessions = sessions
        if (currentAccount != null && currentSessions != null) {
            MultisigPolicySections(currentAccount)
            ToolSection(title = AppLocalization.string("Spend")) {
                AddressEntryRow(
                    title = AppLocalization.string("Recipient"),
                    text = recipient,
                    onTextChange = { recipient = it },
                    chain = wallet.chainId,
                    contacts = app.addressBook.entries.filter { it.chainId == wallet.chainId },
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(AppLocalization.format("Amount (%@)", wallet.chainId.coinSymbol)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val kinds = paymentMemoKinds(wallet.chainId)
                if (kinds.isNotEmpty()) {
                    SendPaymentMemoField(
                        kinds = kinds,
                        kind = memoKind,
                        onKindChange = { memoKind = it },
                        text = memo,
                        onTextChange = { memo = it },
                    )
                }
                Button(
                    onClick = {
                        work {
                            val note = memo.trim()
                            val created = runToCompletion {
                                app.bridge.ready().createMultisig(
                                    wallet.id,
                                    MultisigSpend(
                                        toAddress = recipient,
                                        amount = AmountPresentation.canonicalDecimalInput(amount),
                                        feeRate = null,
                                        expiresInSecs = null,
                                        memo = if (note.isEmpty()) null else memoKind?.let { PaymentMemo(it, note) },
                                    ),
                                )
                            }
                            recipient = ""
                            amount = ""
                            memo = ""
                            open(created)
                        }
                    },
                    enabled = !isWorking && recipient.isNotEmpty() && amount.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(AppLocalization.string("Build Transaction")) }
            }
            ToolSection(
                title = AppLocalization.string("From a Signer"),
                footer = AppLocalization.string("A transaction already here joins it, its signatures with the others."),
            ) {
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(AppLocalization.string("Transaction from a signer")) },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                )
                FilledTonalButton(
                    onClick = {
                        work {
                            val read = runToCompletion { app.bridge.ready().importMultisig(wallet.id, pasted) }
                            pasted = ""
                            open(read)
                        }
                    },
                    enabled = !isWorking && pasted.isNotEmpty(),
                ) { Text(AppLocalization.string("Read Transaction")) }
            }
            if (currentSessions.isNotEmpty()) {
                ToolListSection(items = currentSessions, title = AppLocalization.string("Transactions")) { session ->
                    Column(
                        modifier = Modifier.clickable { open(session) }.spectraRowPadding(),
                        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
                    ) {
                        IdentifierText(session.transactionId, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (session.submittedTxid != null) {
                                AppLocalization.string("Submitted")
                            } else {
                                AppLocalization.format("%lld of %lld signed", session.signedWeight.toLong(), session.threshold.toLong())
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                        )
                    }
                }
            }
            error?.let { WalletToolErrorSection(it) }
        } else {
            val failure = error
            if (failure != null) WalletToolErrorSection(failure) else WalletToolLoadingSection()
        }
    }
}

/**
 * The account's address, its signers and thresholds, and what else can
 * move or block its funds.
 */
@Composable
private fun MultisigPolicySections(account: MultisigAccount) {
    ToolSection(title = AppLocalization.string("Account")) {
        SelectionContainer {
            Text(account.address, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
    account.permissions.forEach { permission ->
        ToolSection(title = AppLocalization.format("%@: %lld to sign", permission.name, permission.threshold.toLong())) {
            permission.signers.forEach { MultisigSignerRow(it) }
            if (permission.covers.isNotEmpty()) {
                Text(permission.covers.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
    }
    if (account.warnings.isNotEmpty()) {
        ToolSection {
            account.warnings.forEach { warning ->
                SpectraLabel(
                    text = warning.localizedText,
                    icon = Icons.Outlined.WarningAmber,
                    color = MaterialTheme.colorScheme.onSurface,
                    iconColor = SpectraColors.semantic.warning,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * One signer: its key or address, its weight where weights differ, and the
 * wallet on this device that holds it.
 */
@Composable
private fun MultisigSignerRow(signer: MultisigSigner) {
    val app = LocalAppState.current
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            if (signer.signed) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SpectraColors.semantic.success,
                    modifier = Modifier.size(18.dp),
                )
            }
            IdentifierText(signer.signer, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            if (signer.weight != 1uL) {
                Text(
                    AppLocalization.format("Weight %lld", signer.weight.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }
        }
        signer.walletId?.let { id -> app.wallets.firstOrNull { it.id == id }?.name }?.let { name ->
            Text(AppLocalization.format("On this device: %@", name), style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
    }
}

/**
 * One session: what it spends and pays, who has signed, and what can be
 * done with it now.
 */
@Composable
internal fun MultisigSessionView(account: MultisigAccount, initial: MultisigSession) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(initial) }
    var signerId by remember { mutableStateOf<String?>(null) }
    // Typed for one signature; never saved with the page.
    val password = remember { TextFieldState() }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    val signers = account.signerWalletIds.mapNotNull { id -> app.wallets.firstOrNull { it.id == id } }
    val signer = signers.firstOrNull { it.id == signerId } ?: signers.firstOrNull()

    fun amount(smallestUnit: String): String =
        "${formatNativeAmount(session.chain, smallestUnit)?.let { AmountPresentation.localizedDecimal(it) } ?: smallestUnit} " +
            session.chain.coinSymbol

    fun work(body: suspend () -> Unit) {
        scope.launch {
            isWorking = true
            try {
                body()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = userErrorMessage(failure)
            } finally {
                isWorking = false
            }
        }
    }

    fun sign() {
        val chosen = signer ?: return
        scope.launch {
            val failure = app.authenticate(
                DeviceAuthenticationAction.SEND,
                AppLocalization.format("Authenticate to sign from %@", chosen.name),
            )
            if (failure != null) {
                error = failure
                return@launch
            }
            work {
                val typed = password.text.toString().ifEmpty { null }
                val current = session
                session = runToCompletion {
                    app.bridge.ready().signMultisig(current.id, current.reviewDigest, chosen.id, typed)
                }
                password.clearText()
            }
        }
    }

    fun submit(executor: WalletState?) {
        scope.launch {
            if (executor != null) {
                val failure = app.authenticate(
                    DeviceAuthenticationAction.SEND,
                    AppLocalization.format("Authenticate to sign from %@", executor.name),
                )
                if (failure != null) {
                    error = failure
                    return@launch
                }
            }
            work {
                val typed = if (executor == null || password.text.isEmpty()) null else password.text.toString()
                val current = session
                session = runToCompletion { app.bridge.ready().submitMultisig(current.id, executor?.id, typed) }
                password.clearText()
            }
        }
    }

    SpectraPage(title = AppLocalization.string("Multisig Transaction")) {
        ToolSection(title = AppLocalization.string("Transaction")) {
            SelectionContainer {
                Text(session.transactionId, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
            session.sequence?.let { SpectraValueRow(label = AppLocalization.string("Sequence"), value = it) }
            session.expiresAt?.let {
                SpectraValueRow(
                    label = AppLocalization.string("Expires"),
                    value = formatUnixDate(it.toDouble(), DateStyle.LONG, TimeStyle.OMITTED),
                )
            }
            session.expiresAtHeight?.let { SpectraValueRow(label = AppLocalization.string("Expires After"), value = it.toString()) }
        }
        if (session.inputs.isNotEmpty()) {
            ToolSection(title = AppLocalization.string("Spends")) {
                session.inputs.forEach { input ->
                    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                        LabeledLine(
                            label = amount(input.value),
                            value = AppLocalization.format(
                                "%lld of %lld signed",
                                input.signatures.toLong(),
                                session.threshold.toLong(),
                            ),
                        )
                        IdentifierText(input.address)
                    }
                }
            }
        }
        ToolSection(title = AppLocalization.string("Pays")) {
            session.outputs.forEach { output ->
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    LabeledLine(
                        label = if (output.asset == null) amount(output.value) else output.value,
                        value = if (output.isChange) AppLocalization.string("Change") else null,
                    )
                    IdentifierText(output.address)
                    output.asset?.let { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText) }
                    output.memo?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
                    output.data?.let {
                        IdentifierText(it, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
            if (session.fee != "0") {
                SpectraValueRow(label = AppLocalization.string("Network Fee"), value = amount(session.fee))
            }
        }
        ToolSection(
            title = AppLocalization.format("%lld of %lld signed", session.signedWeight.toLong(), session.threshold.toLong()),
        ) {
            session.signers.forEach { MultisigSignerRow(it) }
        }
        ToolSection(footer = multisigFooter(account, signers.isEmpty() && session.submittedTxid == null)) {
            val submitted = session.submittedTxid
            if (submitted != null) {
                IdentifierValueRow(AppLocalization.string("Submitted"), submitted)
            } else {
                if (signers.size > 1 && signer != null) {
                    SignerPicker(signers = signers, selected = signer, onSelect = { signerId = it.id })
                }
                if (signer?.signing?.requiresPassword == true) {
                    WalletPasswordField(password)
                }
                if (signer != null) {
                    Button(onClick = { sign() }, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
                        Text(AppLocalization.string(if (account.submission == MultisigSubmission.BY_APPROVAL) "Approve" else "Sign"))
                    }
                }
                if (session.complete) {
                    when (account.submission) {
                        MultisigSubmission.AS_IS -> Button(
                            onClick = { submit(null) },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(AppLocalization.string("Submit")) }
                        MultisigSubmission.BY_EXECUTOR -> if (signer != null) {
                            Button(onClick = { submit(signer) }, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
                                Text(AppLocalization.format("Execute as %@", signer.name))
                            }
                        }
                        MultisigSubmission.BY_APPROVAL -> Unit
                    }
                }
            }
            FilledTonalButton(
                onClick = { shareText(context, session.data) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap()
                Text(AppLocalization.string("Share Transaction"))
            }
            if (session.submittedTxid == null) {
                OutlinedButton(
                    onClick = {
                        work {
                            val current = session
                            runToCompletion { app.bridge.ready().discardMultisig(current.id) }
                            navigator.pop()
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(AppLocalization.string("Discard")) }
            }
        }
        error?.let { WalletToolErrorSection(it) }
    }
}

/** What happens to the session next, as the account's submission rule says. */
private fun multisigFooter(account: MultisigAccount, noSignerHere: Boolean): String =
    if (noSignerHere) {
        AppLocalization.string("No wallet on this device can sign for this account: hand the transaction to a signer.")
    } else {
        AppLocalization.string(
            when (account.submission) {
                MultisigSubmission.AS_IS ->
                    "Hand the transaction to the next signer; once enough have signed, any of them can submit it."
                MultisigSubmission.BY_EXECUTOR ->
                    "Hand the transaction to the next owner; once enough have signed, one of them executes it and pays its fee."
                MultisigSubmission.BY_APPROVAL ->
                    "Each signer's approval is a transaction of its own; the one that meets the threshold executes the transfer."
            },
        )
    }

/** A line with its primary label and an optional trailing note. */
@Composable
private fun LabeledLine(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
    }
}

/** Which of this device's signers signs, where more than one could. */
@Composable
private fun SignerPicker(signers: List<WalletState>, selected: WalletState, onSelect: (WalletState) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = SpectraLayout.Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(AppLocalization.string("Signer"), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
            Spacer(Modifier.weight(1f))
            Text(selected.name, style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            signers.forEach { wallet ->
                DropdownMenuItem(
                    text = { Text(wallet.name) },
                    onClick = {
                        onSelect(wallet)
                        expanded = false
                    },
                    trailingIcon = if (wallet.id == selected.id) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
