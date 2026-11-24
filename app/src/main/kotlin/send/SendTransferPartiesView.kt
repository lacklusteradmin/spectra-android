package dev.spectra.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.rethrowIfCancellation
import dev.spectra.components.CopyButton
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLayout
import dev.spectra.components.groupedAddress
import dev.spectra.components.name
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.models.AppLocalization
import dev.spectra.settings.AddressBookState
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.EndpointHolder
import uniffi.spectra_core.PaymentMemo
import uniffi.spectra_core.displayAddress

/**
 * The two ends of a transfer. Core resolves the names; the reviewed addresses
 * remain complete and copyable. The caller owns the card, so fee rows can
 * share its surface.
 *
 * [memo] is the destination tag or memo the transaction carries to the
 * recipient; [suggestedContactName] the name a save to the address book
 * starts from, `null` to offer none.
 */
@Composable
internal fun SendTransferPartiesView(
    walletId: String,
    chain: Chain,
    sender: String?,
    recipient: String,
    memo: PaymentMemo? = null,
    suggestedContactName: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        if (sender != null) {
            SendTransferPartyView(walletId = walletId, chain = chain, address = sender, isSender = true)
            Row(
                modifier = Modifier.padding(start = SpectraLayout.Space.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
            ) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = null, tint = secondaryText, modifier = Modifier.size(16.dp))
                Text(AppLocalization.string("Transfer to"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
        SendTransferPartyView(
            walletId = walletId,
            chain = chain,
            address = recipient,
            isSender = false,
            suggestedContactName = suggestedContactName,
        )
        if (memo != null) PaymentMemoRow(memo)
    }
}

@Composable
private fun SendTransferPartyView(
    walletId: String,
    chain: Chain,
    address: String,
    isSender: Boolean,
    suggestedContactName: String? = null,
) {
    val app = LocalAppState.current
    var holder by remember { mutableStateOf<EndpointHolder?>(null) }
    var lookupCompleted by remember { mutableStateOf(false) }
    var isSavingContact by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(walletId, chain, address, app.addressBook.entries) {
        holder = null
        lookupCompleted = false
        if (walletId.isEmpty() || address.isEmpty()) return@LaunchedEffect
        try {
            holder = app.bridge.ready().addressHolder(walletId, chain, address)
            lookupCompleted = true
        } catch (error: Exception) {
            error.rethrowIfCancellation()
        }
    }
    val current = holder
    val name = current?.name ?: AppLocalization.string(if (isSender) "Sending wallet" else "Recipient")
    // Two lines for every party once core has answered, so the sender and the
    // recipient sit the same way above their addresses.
    val role: String? = when {
        isSender -> if (current == null) null else "Sending wallet"
        current is EndpointHolder.Contact -> "Saved contact"
        current is EndpointHolder.Wallet -> "Your wallet"
        lookupCompleted -> "Not in your address book"
        else -> null
    }
    val icon = when {
        isSender || current is EndpointHolder.Wallet -> Icons.Outlined.AccountBalanceWallet
        else -> Icons.Outlined.AccountCircle
    }
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
            Box(modifier = Modifier.size(36.dp).spectraInsetFill(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (role != null) {
                    Text(AppLocalization.string(role), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            Text(
                readableAddress(address, chain),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f).semantics { contentDescription = address },
            )
            CopyButton(value = displayAddress(chain, address))
        }
        if (!isSender && lookupCompleted && current == null && suggestedContactName != null) {
            FilledTonalButton(onClick = { isSavingContact = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap()
                Text(AppLocalization.string("Save Recipient To Address Book"))
            }
            if (isSavingContact) {
                SaveContactDialog(
                    addressBook = app.addressBook,
                    chain = chain,
                    address = displayAddress(chain, address),
                    initialName = suggestedContactName,
                    onDismiss = { isSavingContact = false },
                )
            }
        }
    }
}

/**
 * Naming a recipient just sent to before it is saved: a name of the user's,
 * prefilled, and core's answer shown here rather than on a page the user is
 * not on.
 */
@Composable
private fun SaveContactDialog(
    addressBook: AddressBookState,
    chain: Chain,
    address: String,
    initialName: String,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppLocalization.string("Save Contact")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(AppLocalization.string("Name")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false),
                )
                Text(address, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText)
                refusal?.let { message ->
                    Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val refused = addressBook.add(
                            name = name,
                            address = address,
                            chain = chain,
                            note = AppLocalization.string("Saved from recent send"),
                        )
                        isSaving = false
                        if (refused != null) {
                            refusal = refused
                            addressBook.error = null
                        } else {
                            onDismiss()
                        }
                    }
                },
                enabled = !isSaving && name.isNotBlank(),
            ) { Text(AppLocalization.string("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppLocalization.string("Cancel")) } },
    )
}

/**
 * An address as a person compares it, character by character: core's display
 * form (an EVM address in its EIP-55 case) in groups of four. Copying goes
 * through `displayAddress`, never this text, which carries spaces.
 */
@Composable
private fun readableAddress(address: String, chain: Chain): AnnotatedString =
    groupedAddress(displayAddress(chain, address))
