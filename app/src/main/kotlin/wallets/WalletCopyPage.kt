package dev.spectra.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.LocalAppState
import dev.spectra.app.authenticate
import dev.spectra.components.ChainSelectionDescriptor
import dev.spectra.components.ChainSelectionRow
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraErrorText
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.breakableAnywhere
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.displayName
import dev.spectra.models.isWatchOnly
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletCopyCommit
import uniffi.spectra_core.WalletImportPreview

/**
 * Adds a wallet's key, or watched address, to another network as a wallet of
 * its own. Core lists the networks it can go to, reads the secret and seals
 * the copy; this page chooses the network, shows the address the copy will
 * hold and confirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletCopyPage(walletId: String) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val wallet = app.wallet(walletId)
    var targets by remember { mutableStateOf<List<ChainSelectionDescriptor>?>(null) }
    var chain by remember { mutableStateOf<Chain?>(null) }
    var name by remember { mutableStateOf(wallet?.name ?: "") }
    // Never saved with the page: a password lives only while the form is shown.
    val password = remember { TextFieldState() }
    var preview by remember { mutableStateOf<WalletImportPreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    /** The new wallet's name once core has stored it. */
    var added by remember { mutableStateOf<String?>(null) }
    SecretLifecycleEffect(onStop = { password.clearText() })
    DisposableEffect(Unit) { onDispose { password.clearText() } }
    LaunchedEffect(walletId) {
        if (targets != null) return@LaunchedEffect
        targets = try {
            ChainSelectionDescriptor.popularOrder(app.bridge.ready().walletCopyTargets(walletId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = userErrorMessage(failure)
            emptyList()
        }
    }
    if (wallet == null) {
        LaunchedEffect(walletId) { app.dismissWalletPages(walletId) }
        SpectraPage(title = WalletAction.ADD_TO_NETWORK.title) {}
        return
    }
    val holdsKey = !wallet.signing.isWatchOnly
    val needsPassword = wallet.signing.requiresPassword

    fun select(target: Chain) {
        if (target == chain) return
        chain = target
        preview = null
        error = null
    }

    fun commit(target: Chain) = WalletCopyCommit(
        sourceWalletId = wallet.id,
        chain = target,
        walletName = name.trim(),
        password = if (needsPassword) password.text.toString() else null,
        derivationPath = null,
        restoreHeight = null,
        tonWalletVersion = null,
    )

    // Reading the key needs the device owner; the address it gives is shown
    // before anything is stored.
    fun showPreview(target: Chain) {
        scope.launch {
            isWorking = true
            error = null
            try {
                if (holdsKey) {
                    app.authenticate(
                        DeviceAuthenticationAction.SECRET_MATERIAL,
                        AppLocalization.format("Authenticate to add %@ to another network", wallet.name),
                    )?.let { failure ->
                        error = failure
                        return@launch
                    }
                }
                val result = app.bridge.ready().previewWalletCopy(commit(target))
                if (chain == target) preview = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = userErrorMessage(failure)
            } finally {
                isWorking = false
            }
        }
    }

    fun add(target: Chain) {
        scope.launch {
            isWorking = true
            error = null
            try {
                val outcome = app.copyWallet(commit(target))
                password.clearText()
                added = outcome.wallets.firstOrNull()?.name ?: name
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = userErrorMessage(failure)
            } finally {
                isWorking = false
            }
        }
    }

    SpectraPage(title = WalletAction.ADD_TO_NETWORK.title) {
        Text(
            AppLocalization.string(
                if (holdsKey) {
                    "The new wallet seals its own copy of this wallet's key, under the same password. Deleting either leaves the other."
                } else {
                    "The new wallet watches this address on the network you choose."
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText,
        )
        val addedName = added
        val loaded = targets
        when {
            addedName != null -> SpectraCard {
                Row(
                    modifier = Modifier.padding(SpectraLayout.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpectraLabel(
                        text = AppLocalization.format("Added “%@”.", addedName),
                        icon = Icons.Outlined.CheckCircle,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            loaded != null -> SpectraRowGroup(items = loaded, title = AppLocalization.string("Network")) { descriptor ->
                ChainSelectionRow(
                    descriptor = descriptor,
                    isSelected = chain == descriptor.chain,
                    onClick = { select(descriptor.chain) },
                )
            }
            else -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        // A list core could not give is said here; a form's own failure is said in the form.
        if (chain == null) error?.let { SpectraErrorText(it) }
    }

    // The chosen network's form opens over the list, where it is seen, rather
    // than above a row the list may have scrolled far from.
    val target = chain
    if (target != null && added == null) {
        ModalBottomSheet(onDismissRequest = { chain = null }) {
            CopyForm(
                target = target,
                name = name,
                onNameChange = { name = it },
                password = password,
                needsPassword = needsPassword,
                preview = preview,
                error = error,
                isWorking = isWorking,
                onCancel = { chain = null },
                onConfirm = { if (preview == null) showPreview(target) else add(target) },
            )
        }
    }
}

@Composable
private fun CopyForm(
    target: Chain,
    name: String,
    onNameChange: (String) -> Unit,
    password: TextFieldState,
    needsPassword: Boolean,
    preview: WalletImportPreview?,
    error: String?,
    isWorking: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = SpectraLayout.screenHorizontal)
            .padding(bottom = SpectraLayout.Space.xl),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(target.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(AppLocalization.string("Cancel")) }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(AppLocalization.string("import_flow.wallet_name")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (needsPassword) {
            WalletPasswordField(password, label = null, placeholder = AppLocalization.string("Wallet Password"))
        }
        preview?.let { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                Text(
                    AppLocalization.string(if (shown.addresses.size == 1) "Address" else "Addresses"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                shown.addresses.forEach { address ->
                    Text(breakableAnywhere(address), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
                shown.upgradesWallet?.let { upgrades ->
                    SpectraLabel(
                        text = AppLocalization.format("This adds keys to the watched wallet “%@”, keeping its history.", upgrades),
                        icon = Icons.Outlined.Key,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        error?.let {
            SpectraLabel(
                text = it,
                icon = Icons.Outlined.WarningAmber,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            )
        }
        Button(
            onClick = onConfirm,
            enabled = !isWorking && !(needsPassword && password.text.isEmpty()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                SpectraGap()
            }
            Text(
                AppLocalization.string(if (preview == null) "Check Address" else "Add Wallet"),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
