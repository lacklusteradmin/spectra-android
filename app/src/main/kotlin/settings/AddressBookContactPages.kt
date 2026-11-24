package dev.spectra.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.AddressEntryRow
import dev.spectra.components.AllChainsSelectionPage
import dev.spectra.components.ChainSelectionDescriptor
import dev.spectra.components.CoinBadge
import dev.spectra.components.CopyButton
import dev.spectra.components.SpectraBottomActionBar
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.breakableAnywhere
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainBadge
import dev.spectra.models.ChainCatalog
import dev.spectra.models.addressPrefixHint
import dev.spectra.models.chainName
import dev.spectra.models.displayName
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.subtitleText
import dev.spectra.send.beginSend
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.isValidSendAddress

/**
 * Adding a recipient, on its own page behind the address book's `+`. The
 * chain picker opens in its place, so what was typed stays.
 */
@Composable
internal fun NewAddressBookContactPage(route: SettingsRoute.NewContact) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val addressBook = app.addressBook
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var chain by rememberSaveable { mutableStateOf(ChainCatalog.mainnets.firstOrNull()) }
    var address by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var isChoosingChain by rememberSaveable { mutableStateOf(false) }
    var chainSearchText by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    // Core's reason for refusing the save, shown here with what was typed.
    var refusal by rememberSaveable { mutableStateOf<String?>(null) }
    // Every mainnet, because core validates every mainnet.
    val descriptors = remember { ChainSelectionDescriptor.popularOrder(ChainCatalog.mainnets) }

    if (isChoosingChain) {
        BackHandler { isChoosingChain = false }
        // Drawn in place of the form, with its own Back, so the form keeps what was typed.
        AllChainsSelectionPage(
            title = AppLocalization.string("import_flow.all_chains_title"),
            descriptors = descriptors,
            query = chainSearchText,
            onQueryChange = { chainSearchText = it },
            onPick = { picked ->
                chain = picked
                isChoosingChain = false
            },
            selectedChains = setOfNotNull(chain),
            onBack = { isChoosingChain = false },
        )
        return
    }

    val trimmedAddress = address.trim()
    val canSave = chain?.let { canSaveAddressBookEntry(name, address, it) } ?: false

    // Closes only once core has stored the contact; a refusal keeps the
    // form, and what was typed, on screen with core's reason.
    fun save() {
        val picked = chain ?: return
        isSaving = true
        scope.launch {
            val refused = addressBook.add(name, address, picked, note)
            isSaving = false
            if (refused != null) {
                refusal = refused
                // Said here; the list need not say it again on the way back.
                addressBook.error = null
            } else {
                navigator.dismiss(route)
            }
        }
    }

    SpectraPage(
        title = AppLocalization.string("New Contact"),
        bottomBar = {
            Column {
                refusal?.let { RefusalLabel(it, Modifier.padding(horizontal = SpectraLayout.screenHorizontal)) }
                SpectraBottomActionBar {
                    Button(
                        onClick = ::save,
                        enabled = canSave && !isSaving,
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        SpectraGap()
                        Text(AppLocalization.string("Save Contact"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) {
        SettingsPageHeader(
            title = AppLocalization.string("New Contact"),
            subtitle = AppLocalization.string("Name a recipient and save an address you send to often."),
            icon = Icons.Outlined.PersonAdd,
        )
        SpectraDetailCard(title = AppLocalization.string("Contact")) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(AppLocalization.string("Name")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(AppLocalization.string("Note (Optional)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
        }
        SpectraDetailCard(title = AppLocalization.string("Saved Address")) {
            ChainRow(chain, onClick = { isChoosingChain = true })
            // The label is the chain's own format hint; the address can be
            // pasted or scanned as on the send page.
            chain?.let { selected ->
                val hint = selected.addressPrefixHint
                AddressEntryRow(
                    title = hint.ifEmpty { AppLocalization.string("Address") },
                    text = address,
                    onTextChange = { address = it },
                    chain = selected,
                )
            }
            // Validation shows only once there is input to judge, and never
            // by colour alone.
            if (trimmedAddress.isNotEmpty()) {
                val isValid = chain?.let { isValidSendAddress(it, trimmedAddress) } ?: false
                val message = chain?.let { addressBookAddressValidationMessage(address, it) }
                    ?: AppLocalization.string("Select a chain first.")
                val color = if (isValid) SpectraColors.semantic.success else SpectraColors.semantic.warning
                Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isValid) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(message, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
    }
}

/** The network the address is on: its badge and name, opening the chain picker. */
@Composable
private fun ChainRow(chain: Chain?, onClick: () -> Unit) {
    val (artwork, color) = nativeChainBadge(chain) ?: ChainBadge.FALLBACK
    val chainName = chain?.displayName ?: AppLocalization.string("Select a chain")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .spectraInsetFill()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(SpectraLayout.Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(artwork, chainName, color, size = 34.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(AppLocalization.string("Chain"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            Text(chainName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
    }
}

@Composable
private fun RefusalLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** A saved recipient, with send, rename and delete. */
@Composable
internal fun AddressBookContactPage(route: SettingsRoute.Contact) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val addressBook = app.addressBook
    // The stored contact, so a rename shows here rather than the name the
    // page was opened with.
    val contact = addressBook.entries.firstOrNull { it.id == route.entry.id } ?: route.entry
    var editedName by rememberSaveable { mutableStateOf(contact.name) }
    var isConfirmingDelete by remember { mutableStateOf(false) }
    val trimmedName = editedName.trim()
    val canRename = trimmedName.isNotEmpty() && trimmedName != contact.name
    // A wallet that can pay this contact: one on its network that signs.
    val payingWallet = app.walletDerivedCache.sendEnabledWallets.firstOrNull { it.chainId == contact.chainId }

    SpectraPage(
        title = AppLocalization.string("Contact"),
        actions = {
            TextButton(onClick = { addressBook.rename(contact.id, editedName) }, enabled = canRename) {
                Text(AppLocalization.string("Save"))
            }
        },
    ) {
        val (artwork, color) = nativeChainBadge(contact.chainId) ?: ChainBadge.FALLBACK
        SpectraCard(elevated = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                CoinBadge(artwork, contact.chainName, color, size = 56.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                    )
                    Text(contact.subtitleText, style = MaterialTheme.typography.bodyMedium, color = secondaryText, textAlign = TextAlign.Center)
                }
                // Whole and wrapped here, where there is room for it: the list
                // row shows the same address elided.
                Text(
                    breakableAnywhere(contact.address),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = secondaryText,
                    textAlign = TextAlign.Center,
                )
                CopyButton(value = contact.address, title = AppLocalization.string("Copy"), wide = true)
            }
        }
        // Opens the composer from that wallet.
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
            Button(
                onClick = {
                    payingWallet?.let {
                        app.beginSend(walletId = it.id)
                        app.sendFlow.address = contact.address
                    }
                },
                enabled = payingWallet != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap()
                Text(AppLocalization.format("Send to %@", contact.name), fontWeight = FontWeight.SemiBold)
            }
            if (payingWallet == null) {
                SpectraHintText(AppLocalization.format("No wallet on %@ can send yet.", contact.chainName))
            }
        }
        SpectraDetailCard(title = AppLocalization.string("Label")) {
            SpectraHintText(AppLocalization.string("You can update the label for this saved address. The chain, address, and note stay fixed."))
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(AppLocalization.string("Name")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        OutlinedButton(
            onClick = { isConfirmingDelete = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            SpectraGap()
            Text(AppLocalization.string("Delete Contact"), fontWeight = FontWeight.SemiBold)
        }
    }
    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(AppLocalization.string("Delete Contact")) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingDelete = false
                    addressBook.remove(contact.id)
                    navigator.dismiss(route)
                }) { Text(AppLocalization.string("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text(AppLocalization.string("Cancel")) } },
        )
    }
}

/**
 * Naming a recipient just sent to before it is saved: a name of the user's,
 * prefilled, and core's answer shown here rather than on a page the user is
 * not on. [onDismiss] closes the sheet; it is called once the contact is
 * stored, or on Cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveContactSheet(chain: Chain, address: String, name: String, onDismiss: () -> Unit) {
    val addressBook = LocalAppState.current.addressBook
    val scope = rememberCoroutineScope()
    var editedName by rememberSaveable { mutableStateOf(name) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun save() {
        isSaving = true
        scope.launch {
            val refused = addressBook.add(editedName, address, chain, AppLocalization.string("Saved from recent send"))
            isSaving = false
            if (refused != null) {
                refusal = refused
                addressBook.error = null
            } else {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpectraLayout.screenHorizontal)
                .padding(bottom = SpectraLayout.screenBottom)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Text(AppLocalization.string("Save Contact"), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(AppLocalization.string("Name")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
            )
            Text(
                breakableAnywhere(address),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = secondaryText,
            )
            refusal?.let { RefusalLabel(it) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(AppLocalization.string("Cancel")) }
                Button(onClick = ::save, enabled = !isSaving && editedName.isNotBlank()) {
                    Text(AppLocalization.string("Save"))
                }
            }
        }
    }
}
