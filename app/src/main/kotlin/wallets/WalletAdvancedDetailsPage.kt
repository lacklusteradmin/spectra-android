package dev.spectra.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceBiometry
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.readOrNull
import dev.spectra.components.SecureWindow
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraNavigationRow
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.hasSeedPhrase
import dev.spectra.models.isPrivateKey
import dev.spectra.models.isWatchOnly
import dev.spectra.models.requiresPassword
import kotlinx.coroutines.launch
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletActionOffer
import uniffi.spectra_core.WalletActionSection

/**
 * The wallet's name, recovery phrase, identifiers and deletion.
 *
 * Revealing the phrase and deleting the wallet are rare, and one is sensitive
 * and the other irreversible, so each is a row a level down, and the details
 * page is only what the wallet holds.
 */
@Composable
internal fun WalletAdvancedDetailsPage(walletId: String) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val seedReveal = remember { SeedPhraseRevealState() }
    var isShowingDeleteWalletAlert by remember { mutableStateOf(false) }
    val wallet = app.wallet(walletId)
    val resumed = isLifecycleResumed()

    DisposableEffect(seedReveal) {
        seedReveal.activate(sceneIsActive = resumed)
        onDispose { seedReveal.deactivate() }
    }
    // The device prompt may pause the activity briefly. Leaving the app
    // invalidates the request as well as its presentation.
    SecretLifecycleEffect(
        onPause = { seedReveal.setSceneIsActive(false) },
        onResume = { seedReveal.setSceneIsActive(true) },
        onStop = { seedReveal.invalidate() },
    )
    if (wallet == null) {
        LaunchedEffect(walletId) {
            isShowingDeleteWalletAlert = false
            seedReveal.invalidate()
            app.dismissWalletPages(walletId)
        }
        SpectraPage(title = AppLocalization.string("Manage Wallet")) {}
        return
    }

    /** What core offers for managing the wallet, in its order: the detail page's own read. */
    var manageOffers by remember { mutableStateOf<List<WalletActionOffer>>(emptyList()) }
    LaunchedEffect(walletId, app.walletIdentityRevision, app.appSettings) {
        readOrNull { app.bridge.ready().walletActions(walletId) }?.let { manageOffers = it.actions(WalletActionSection.MANAGE) }
    }
    fun offers(action: WalletAction) = manageOffers.any { it.action == action }
    fun note(action: WalletAction) = AppLocalization.string(manageOffers.firstOrNull { it.action == action }?.note ?: "")

    val requiresSeedPhrasePassword = wallet.signing.requiresPassword
    val biometry = DeviceBiometry.current(context)

    fun revealSeedPhrase(password: String? = null) {
        scope.launch {
            seedReveal.reveal(
                canPresent = { app.wallet(walletId) != null },
                operation = { app.revealSeedPhrase(wallet, password) },
            )
        }
    }

    SpectraPage(title = AppLocalization.string("Manage Wallet")) {
        if (offers(WalletAction.RENAME)) {
            SpectraRowSection {
                row {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { app.beginEditingWallet(wallet) }
                            .spectraRowPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                    ) {
                        Text(AppLocalization.string("Name"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            wallet.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
                    }
                }
            }
        }
        if (offers(WalletAction.REVEAL_PHRASE)) {
            SpectraRowSection(title = AppLocalization.string("Security")) {
                row {
                    val enabled = !seedReveal.isRevealing && wallet.signing.hasSeedPhrase
                    val title = when {
                        seedReveal.isRevealing -> AppLocalization.format("Checking %@…", biometry.displayName)
                        requiresSeedPhrasePassword -> AppLocalization.string("Show Seed Phrase (Password)")
                        else -> AppLocalization.string("Show Seed Phrase")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled, role = Role.Button) {
                                if (requiresSeedPhrasePassword) {
                                    seedReveal.passwordInput.clearText()
                                    seedReveal.isShowingPasswordPrompt = true
                                } else {
                                    revealSeedPhrase()
                                }
                            }
                            .spectraRowPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
                    ) {
                        val tint = if (enabled) MaterialTheme.colorScheme.primary else secondaryText
                        Icon(
                            if (requiresSeedPhrasePassword) Icons.Outlined.Shield else biometry.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
                    }
                }
            }
        }
        if (offers(WalletAction.EXPORT_KEYS)) {
            SpectraRowSection(footer = note(WalletAction.EXPORT_KEYS)) {
                row {
                    SpectraNavigationRow(
                        title = WalletAction.EXPORT_KEYS.title,
                        icon = WalletAction.EXPORT_KEYS.icon,
                        onClick = { navigator.push(WalletKeyExportRoute(walletId)) },
                    )
                }
            }
        }
        if (offers(WalletAction.ADD_TO_NETWORK)) {
            SpectraRowSection(footer = note(WalletAction.ADD_TO_NETWORK)) {
                row {
                    SpectraNavigationRow(
                        title = WalletAction.ADD_TO_NETWORK.title,
                        icon = WalletAction.ADD_TO_NETWORK.icon,
                        onClick = { navigator.push(WalletCopyRoute(walletId)) },
                    )
                }
            }
        }
        SpectraRowSection(title = AppLocalization.string("Details")) {
            row { DetailValueRow(AppLocalization.string("Wallet ID"), wallet.id) }
            wallet.derivationPathsText?.let { path ->
                row { DetailValueRow(AppLocalization.string("Derivation Path"), path) }
            }
            row { DetailValueRow(AppLocalization.string("First Activity"), app.firstActivityDateText(walletId)) }
        }
        if (offers(WalletAction.DELETE)) {
            SpectraRowSection {
                row {
                    val failure = MaterialTheme.colorScheme.error
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { isShowingDeleteWalletAlert = true }
                            .spectraRowPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = failure, modifier = Modifier.size(24.dp))
                        Text(AppLocalization.string("Delete Wallet"), style = MaterialTheme.typography.bodyLarge, color = failure)
                    }
                }
            }
        }
    }

    if (isShowingDeleteWalletAlert) {
        val message = when {
            wallet.signing.isWatchOnly ->
                AppLocalization.string("You can't recover this wallet after deletion unless you still have this address.")
            wallet.signing.isPrivateKey ->
                AppLocalization.string("Please keep this private key because you can't recover this wallet after deletion.")
            else ->
                AppLocalization.string("Please take note of your seed phrase because you can't recover this wallet after deletion.")
        }
        AlertDialog(
            onDismissRequest = { isShowingDeleteWalletAlert = false },
            title = { Text(AppLocalization.string("Delete Wallet?")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    isShowingDeleteWalletAlert = false
                    // The deletion belongs to the app: it completes whatever the page does meanwhile.
                    app.scope.launch { app.deleteWallet(wallet) }
                }) { Text(AppLocalization.string("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { isShowingDeleteWalletAlert = false }) { Text(AppLocalization.string("Cancel")) }
            },
        )
    }

    seedReveal.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { seedReveal.errorMessage = null },
            title = { Text(AppLocalization.string("Cannot Reveal Seed Phrase")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { seedReveal.errorMessage = null }) { Text(AppLocalization.string("OK")) }
            },
        )
    }

    if (seedReveal.isShowingPasswordPrompt) {
        AlertDialog(
            onDismissRequest = {
                seedReveal.isShowingPasswordPrompt = false
                seedReveal.passwordInput.clearText()
            },
            title = { Text(AppLocalization.string("Wallet Password")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                    Text(
                        AppLocalization.format("seedReveal.password_hint_format", biometry.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryText,
                    )
                    WalletPasswordField(seedReveal.passwordInput, label = null, placeholder = AppLocalization.string("Wallet Password"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val password = seedReveal.passwordInput.text.toString()
                        seedReveal.isShowingPasswordPrompt = false
                        seedReveal.passwordInput.clearText()
                        revealSeedPhrase(password)
                    },
                    enabled = seedReveal.passwordInput.text.isNotBlank(),
                ) { Text(AppLocalization.string("Reveal Seed Phrase")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    seedReveal.isShowingPasswordPrompt = false
                    seedReveal.passwordInput.clearText()
                }) { Text(AppLocalization.string("Cancel")) }
            },
        )
    }

    if (seedReveal.isShowingPhraseSheet) {
        SecureWindow()
        SecretSheet(
            title = AppLocalization.string("Seed Phrase"),
            onDismiss = {
                seedReveal.isShowingPhraseSheet = false
                seedReveal.clearPhrase()
            },
        ) {
            SpectraDetailCard {
                Text(
                    AppLocalization.string("Write this down and keep it offline. Anyone with this phrase can control your funds."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                )
                // Numbered as it was shown when created, so it is copied down
                // in order. No Copy: Add to Another Network takes a phrase to
                // another network without the clipboard.
                SeedPhraseWordGrid(seedReveal.phrase.whitespaceSeparatedWords())
            }
        }
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    SelectionContainer(modifier = Modifier.spectraRowPadding()) {
        SpectraValueRow(label = label, value = value)
    }
}
