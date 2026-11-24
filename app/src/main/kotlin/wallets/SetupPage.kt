package dev.spectra.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.readOrNull
import dev.spectra.components.CoinBadge
import dev.spectra.components.FlowPageHeading
import dev.spectra.components.SecureWindow
import dev.spectra.components.SpectraBottomActionBar
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraErrorText
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLoadingRow
import dev.spectra.components.SpectraPage
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.accountKeyPrefixes
import dev.spectra.models.color
import dev.spectra.models.displayName
import dev.spectra.models.entry
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.WalletSetupMethod
import uniffi.spectra_core.isValidWatchOnlyAddress
import uniffi.spectra_core.walletSetupDescriptor

/**
 * One page of wallet setup. The network and the method were chosen before
 * the form opened; the form lives in the session's draft, and [AppState]
 * supplies app-wide state and performs the import. Each page is its own
 * route, so the system back gesture steps back one page at a time.
 */
@Composable
internal fun WalletSetupPageScreen(route: WalletSetupRoute) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val session = app.walletImport
    if (session.id != route.session) {
        // The form this page belonged to has closed; its pages go with it.
        LaunchedEffect(route) { navigator.popTo { !it.isImportFormPage } }
        SpectraPage(title = "") {}
        return
    }
    val draft = session.draft
    val setupPage = route.page
    val isEditingWallet = draft.isEditingWallet
    val pageCopy = setupPage.copy(draft.mode)
    val nextPageInFlow = draft.setupFlow.next(setupPage)
    // Whether this page's part of the form is complete enough to leave it.
    // Pages before it were complete when they were left.
    val isPageComplete = when (setupPage) {
        WalletSetupPage.SEED_PHRASE -> draft.isSecretComplete
        WalletSetupPage.BACKUP_VERIFICATION -> draft.isBackupVerificationComplete
        WalletSetupPage.WATCH_ADDRESSES -> app.canImportWallet
        WalletSetupPage.WALLET_NAME ->
            app.canImportWallet && (!draft.mode.takesWalletPassword || draft.walletPasswordValidationError == null)
    }
    val isPrimaryActionEnabled = !session.isBusy && isPageComplete && (nextPageInFlow != null || app.canImportWallet)
    // "Next" while the flow has pages left, and what the submit does on the last one.
    val primaryActionTitle = when {
        nextPageInFlow == WalletSetupPage.BACKUP_VERIFICATION ->
            AppLocalization.string("import_flow.continue_to_backup_verification")
        nextPageInFlow != null -> AppLocalization.string("import_flow.next")
        else -> when (val mode = draft.mode) {
            WalletDraftMode.Edit -> AppLocalization.string("import_flow.save_wallet")
            is WalletDraftMode.Setup -> when (mode.method) {
                WalletSetupMethod.CREATE_PHRASE -> AppLocalization.string("import_flow.create_wallet")
                WalletSetupMethod.WATCH_ADDRESSES, WalletSetupMethod.WATCH_ACCOUNT_XPUB,
                WalletSetupMethod.WATCH_VIEW_KEY, WalletSetupMethod.WATCH_MULTISIG,
                -> AppLocalization.string("import_flow.watch_addresses")
                else -> AppLocalization.string("import_flow.import_wallet")
            }
        }
    }
    // The name core gives a wallet whose name is left blank, shown where the name is typed.
    var defaultWalletName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(setupPage) {
        if (setupPage != WalletSetupPage.WALLET_NAME || isEditingWallet) return@LaunchedEffect
        defaultWalletName = readOrNull { app.bridge.ready().defaultWalletName() }
    }

    fun performPrimaryAction() {
        // Linear advance. `null` from `next` means this is the last page: submit instead of routing.
        if (nextPageInFlow != null) {
            // Backup verification checks a challenge drawn as it is entered.
            if (nextPageInFlow == WalletSetupPage.BACKUP_VERIFICATION) draft.prepareBackupVerificationChallenge()
            navigator.push(WalletSetupRoute(route.session, nextPageInFlow))
            return
        }
        val form = session.id
        // The import belongs to the app, not to this page: it completes if the page is covered.
        app.scope.launch {
            if (session.id == form) app.importWallet()
        }
    }

    // A phrase, a key or the words asked back are on screen.
    if (setupPage == WalletSetupPage.SEED_PHRASE || setupPage == WalletSetupPage.BACKUP_VERIFICATION) SecureWindow()

    SpectraPage(
        title = "",
        bottomBar = {
            SpectraBottomActionBar {
                Button(
                    onClick = ::performPrimaryAction,
                    enabled = isPrimaryActionEnabled,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(primaryActionTitle, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        },
    ) {
        SetupHeader(draft, setupPage, pageCopy)
        when (setupPage) {
            WalletSetupPage.WATCH_ADDRESSES -> WatchPageContent(app, draft)
            WalletSetupPage.SEED_PHRASE -> WalletSecretStep(route.session, draft, showsBackupVerification = false)
            WalletSetupPage.BACKUP_VERIFICATION -> WalletSecretStep(route.session, draft, showsBackupVerification = true)
            WalletSetupPage.WALLET_NAME -> WalletNamePageContent(draft, defaultWalletName)
        }
        session.error?.let { SpectraErrorText(it) }
        if (session.isBusy) {
            SpectraLoadingRow(title = AppLocalization.string("import_flow.initializing_wallet_connections"))
        }
    }
}

/** The network the wallet is added on, how far through the form this page is, and what the page asks. */
@Composable
private fun SetupHeader(draft: WalletImportDraft, setupPage: WalletSetupPage, pageCopy: WalletSetupPageCopy) {
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        val chain = draft.chain
        val entry = chain?.entry
        if (!draft.isEditingWallet && chain != null && entry != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                CoinBadge(entry.artworkName, entry.coinSymbol, entry.color.color, size = 20.dp)
                Text(
                    chain.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = secondaryText,
                )
                Spacer(Modifier.weight(1f))
                StepIndicator(draft.setupFlow, setupPage)
            }
        }
        FlowPageHeading(title = pageCopy.title, subtitle = pageCopy.subtitle)
    }
}

/** "Step 2 of 3", with a segment per page: where this page sits in the flow. */
@Composable
private fun StepIndicator(flow: SetupFlow, page: WalletSetupPage) {
    val pages = flow.pages
    val index = flow.index(page) ?: return
    if (pages.size <= 1) return
    val spoken = AppLocalization.format("Step %lld of %lld", (index + 1).toLong(), pages.size.toLong())
    Row(
        modifier = Modifier.semantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        pages.indices.forEach { position ->
            Box(
                Modifier
                    .width(18.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (position <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
            )
        }
    }
}

/**
 * A watch import's one field: addresses on the network, each judged by
 * core's watch-only rule, or the account public key that stands in for the
 * whole account.
 */
@Composable
private fun WatchPageContent(app: AppState, draft: WalletImportDraft) {
    val chain = draft.chain ?: return
    SpectraDetailCard {
        when (draft.method) {
            WalletSetupMethod.WATCH_ACCOUNT_XPUB -> {
                Text(AppLocalization.string("Account Public Key"), style = MaterialTheme.typography.titleMedium)
                PlainEntryField(
                    value = draft.accountXpubInput,
                    onValueChange = { draft.accountXpubInput = it },
                    placeholder = chain.accountKeyPrefixes.joinToString(" / ") { "$it…" },
                )
            }
            WalletSetupMethod.WATCH_MULTISIG -> {
                Text(AppLocalization.string("Multisig Policy"), style = MaterialTheme.typography.titleMedium)
                PlainEntryField(
                    value = draft.descriptorInput,
                    onValueChange = { draft.descriptorInput = it },
                    placeholder = multisigFormatTitle(chain),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 10,
                )
                SpectraHintText(
                    AppLocalization.string("Spectra watches the account and builds its transactions for its signers to sign."),
                )
            }
            WalletSetupMethod.WATCH_VIEW_KEY -> {
                Text(AppLocalization.string("Primary Address"), style = MaterialTheme.typography.titleMedium)
                PlainEntryField(
                    value = draft.watchOnlyInput,
                    onValueChange = { draft.watchOnlyInput = it },
                    placeholder = chain.displayName,
                )
                Text(AppLocalization.string("Private View Key"), style = MaterialTheme.typography.titleMedium)
                PlainEntryField(
                    value = draft.viewKeyInput,
                    onValueChange = { draft.viewKeyInput = it },
                    placeholder = AppLocalization.string("64 hex digits"),
                )
                SpectraHintText(
                    AppLocalization.string(
                        "A view key shows what the wallet receives, not what it spends: its balance does not drop when it pays from another device.",
                    ),
                )
            }
            else -> WatchedAddressSection(draft, chain)
        }
    }
    if (draft.method == WalletSetupMethod.WATCH_VIEW_KEY) RestoreHeightCard(draft)
    WalletAddressPreviewCard(app, draft)
}

private fun multisigFormatTitle(chain: Chain): String =
    walletSetupDescriptor(chain).options.firstOrNull { it.method == WalletSetupMethod.WATCH_MULTISIG }
        ?.formats?.firstOrNull()?.title ?: ""

/** Watched addresses, one per line, and what core's rule makes of them. */
@Composable
private fun WatchedAddressSection(draft: WalletImportDraft, chain: Chain) {
    val entries = draft.watchOnlyEntries
    PlainEntryField(
        value = draft.watchOnlyInput,
        onValueChange = { draft.watchOnlyInput = it },
        placeholder = AppLocalization.string("One address per line"),
        singleLine = false,
        minLines = 3,
    )
    // Nothing typed yet: the page and the field already say what goes here.
    if (entries.isEmpty()) return
    if (!entries.all { isValidWatchOnlyAddress(chain, it) }) {
        Text(
            AppLocalization.format("Every line must contain a valid %@ address.", chain.displayName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Text(
            AppLocalization.formatCount(
                "%lld valid %@ addresses ready to import.",
                entries.size.toLong(),
                entries.size.toLong(),
                chain.displayName,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SpectraColors.semantic.success,
        )
    }
}

/**
 * The name first — the page is named for it — then the password a secret
 * may be sealed under, then what the wallet will do and whom it asks, while
 * the endpoints can still change.
 */
@Composable
private fun WalletNamePageContent(draft: WalletImportDraft, defaultWalletName: String?) {
    val app = LocalAppState.current
    val nameLabel = AppLocalization.string("import_flow.wallet_name")
    SpectraDetailCard {
        OutlinedTextField(
            value = draft.walletName,
            onValueChange = { draft.walletName = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameLabel },
            placeholder = { Text(if (draft.isEditingWallet) nameLabel else defaultWalletName ?: nameLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = if (draft.walletName.isNotEmpty()) {
                {
                    IconButton(onClick = { draft.walletName = "" }) {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = AppLocalization.string("Clear wallet name"),
                            tint = secondaryText,
                        )
                    }
                }
            } else {
                null
            },
        )
    }
    if (draft.mode.takesWalletPassword) {
        SpectraDetailCard {
            Text(AppLocalization.string("import_flow.wallet_password_optional"), style = MaterialTheme.typography.titleMedium)
            Text(
                AppLocalization.string("import_flow.wallet_password_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
            )
            WalletPasswordField(
                draft.walletPassword,
                label = null,
                placeholder = AppLocalization.string("import_flow.wallet_password_field"),
            )
            WalletPasswordField(
                draft.walletPasswordConfirmation,
                label = null,
                placeholder = AppLocalization.string("import_flow.wallet_password_confirmation_field"),
            )
            val validationError = draft.walletPasswordValidationError
            if (validationError != null) {
                Text(validationError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (draft.walletPasswordInput?.trim()?.isNotEmpty() == true) {
                Text(
                    AppLocalization.string("import_flow.wallet_password_success"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SpectraColors.semantic.success,
                )
            }
        }
    }
    val chain = draft.chain
    if (!draft.isEditingWallet && chain != null) WalletSetupSummaryCard(app, chain)
}
