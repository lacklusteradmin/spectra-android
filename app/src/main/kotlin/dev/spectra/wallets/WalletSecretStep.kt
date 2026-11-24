package dev.spectra.wallets

import android.content.ClipboardManager
import android.icu.text.ListFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.SecretWordField
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraStepper
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.title
import dev.spectra.models.tonWalletVersionsInOrder
import uniffi.spectra_core.WalletSetupMethod
import uniffi.spectra_core.walletSetupDescriptor
import java.util.UUID

/**
 * The wallet-setup secret step: enter or record a seed, paste a private key,
 * and verify a backup. [showsBackupVerification] draws the backup check
 * instead of the secret.
 */
@Composable
internal fun WalletSecretStep(session: UUID, draft: WalletImportDraft, showsBackupVerification: Boolean) {
    if (showsBackupVerification) {
        BackupVerificationSection(draft)
    } else {
        WalletSecretStepSection(session, draft)
    }
}

/**
 * Creating a wallet shows its phrase in one card. Importing a phrase shows
 * the entry, then the one way into everything a default already answers;
 * importing a private key shows the key.
 */
@Composable
private fun WalletSecretStepSection(session: UUID, draft: WalletImportDraft) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    when {
        draft.isCreateMode -> SpectraDetailCard {
            CreateWalletSeedPhraseSection(draft)
            SpectraDivider()
            DerivationOptionsLink(draft) { navigator.push(WalletDerivationOptionsRoute(session)) }
        }
        draft.isPrivateKeyImportMode -> {
            SpectraDetailCard { PrivateKeyImportFields(draft) }
            NamedAccountCard(draft)
            TonWalletVersionCard(draft) { FindUsedAccountsButton(draft) { navigator.push(UsedAccountsRoute(session)) } }
            WalletAddressPreviewCard(app, draft)
        }
        else -> {
            SpectraDetailCard { SeedPhraseEntryGrid(draft.seedEntry) }
            if (draft.asksRestoreHeight) RestoreHeightCard(draft)
            DerivationAccountCard(draft) { FindUsedAccountsButton(draft) { navigator.push(UsedAccountsRoute(session)) } }
            JunctionPathCard(draft)
            NamedAccountCard(draft)
            TonWalletVersionCard(draft) { FindUsedAccountsButton(draft) { navigator.push(UsedAccountsRoute(session)) } }
            WalletAddressPreviewCard(app, draft)
            AdvancedCard(draft) { navigator.push(WalletDerivationOptionsRoute(session)) }
        }
    }
}

/**
 * The new phrase: what it is for, then its words, covered until the user asks
 * to see them, so it is never on screen by surprise; leaving the page covers
 * it again. Length, account and path are under Advanced.
 */
@Composable
private fun CreateWalletSeedPhraseSection(draft: WalletImportDraft) {
    var isPhraseRevealed by remember { mutableStateOf(false) }
    SpectraLabel(
        text = AppLocalization.string("import_flow.createSeedPhraseWarning"),
        icon = Icons.Outlined.WarningAmber,
        color = SpectraColors.semantic.warning,
        style = MaterialTheme.typography.bodyMedium,
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = if (isPhraseRevealed) {
                Modifier
            } else {
                Modifier.blur(10.dp).clearAndSetSemantics {}
            },
        ) {
            SeedPhraseWordGrid(draft.seedPhraseWords)
        }
        if (!isPhraseRevealed) {
            val hint = AppLocalization.string("secret.reveal.hint")
            Button(
                onClick = { isPhraseRevealed = true },
                modifier = Modifier.semantics { onClick(label = hint, action = null) },
            ) {
                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap()
                Text(AppLocalization.string("Tap to Reveal"), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PrivateKeyImportFields(draft: WalletImportDraft) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            AppLocalization.string("import_flow.privateKeyTitle"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = secondaryText,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                    ?.toString()?.trim()
                if (!text.isNullOrEmpty()) draft.privateKeyInput = text
            },
            contentPadding = PaddingValues(horizontal = SpectraLayout.Space.m, vertical = SpectraLayout.Space.xs),
        ) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            SpectraGap(SpectraLayout.Space.xs)
            Text(AppLocalization.string("Paste"))
        }
    }
    SpectraHintText(AppLocalization.string("import_flow.privateKeyPrompt"))
    val trimmed = draft.privateKeyInput.trim()
    val isLikelyValid = trimmed.isNotEmpty() && draft.isSecretComplete
    SecretWordField(
        value = draft.privateKeyInput,
        onValueChange = { draft.privateKeyInput = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        placeholder = AppLocalization.string("import_flow.privateKeyPlaceholder"),
        isError = trimmed.isNotEmpty() && !isLikelyValid,
        singleLine = false,
        imeAction = ImeAction.Done,
    )
    // The formats the network takes, from its setup descriptor. The shape is
    // core's to judge, so this row only names it; the feedback below says
    // whether the input fits.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            privateKeyFormats(draft),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryText,
            modifier = Modifier.weight(1f),
        )
        if (draft.privateKeyInput.isNotEmpty()) {
            IconButton(onClick = { draft.privateKeyInput = "" }) {
                Icon(Icons.Outlined.Cancel, contentDescription = AppLocalization.string("Clear"), tint = secondaryText)
            }
        }
    }
    if (trimmed.isNotEmpty()) {
        if (!draft.isSecretComplete) {
            SpectraLabel(
                text = AppLocalization.string("Enter a valid private key for the selected network."),
                icon = Icons.Outlined.WarningAmber,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            )
        } else {
            SpectraLabel(
                text = AppLocalization.string("Looks like a valid private key."),
                icon = Icons.Outlined.Verified,
                color = SpectraColors.semantic.success,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

private fun privateKeyFormats(draft: WalletImportDraft): String {
    val chain = draft.chain ?: return ""
    val option = walletSetupDescriptor(chain).options.firstOrNull { it.method == WalletSetupMethod.IMPORT_PRIVATE_KEY }
    return option?.formats.orEmpty().joinToString(" · ") { it.title }
}

/**
 * Restoring, the phrase may have been used on another account or wallet
 * version; a scan finds it, on request.
 */
@Composable
private fun FindUsedAccountsButton(draft: WalletImportDraft, onClick: () -> Unit) {
    if (draft.isCreateMode || draft.isPrivateKeyImportMode || !draft.seedEntry.verdict.isValid) return
    FilledTonalButton(onClick = onClick) {
        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        SpectraGap()
        Text(AppLocalization.string("Find Used Accounts"))
    }
}

/**
 * A named account the key controls, on a network that has them. The import
 * confirms on the network that the key is one of its full-access keys before
 * the wallet is added.
 */
@Composable
private fun NamedAccountCard(draft: WalletImportDraft) {
    if (!draft.asksNamedAccount) return
    SpectraDetailCard {
        Text(
            AppLocalization.string("Named Account (Optional)"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        PlainEntryField(
            value = draft.namedAccountInput,
            onValueChange = { draft.namedAccountInput = it },
            placeholder = AppLocalization.string("Named account"),
        )
        SpectraHintText(
            AppLocalization.string(
                "If this key controls a named account, enter it. Adding the wallet checks on the network that the key is one of its full-access keys.",
            ),
        )
    }
}

/**
 * The wallet contract a restored TON key's account is under. Each version is
 * a different address, which the preview below shows.
 */
@Composable
private fun TonWalletVersionCard(draft: WalletImportDraft, accessory: @Composable () -> Unit) {
    if (!draft.asksTonWalletVersion) return
    SpectraDetailCard {
        Text(
            AppLocalization.string("Wallet Version"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        val versions = tonWalletVersionsInOrder
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            versions.forEachIndexed { index, version ->
                SegmentedButton(
                    selected = draft.tonWalletVersion == version,
                    onClick = { draft.tonWalletVersion = version },
                    shape = SegmentedButtonDefaults.itemShape(index, versions.size),
                ) { Text(version.title) }
            }
        }
        SpectraHintText(
            AppLocalization.string(
                "Each version is a different address for the same key. Wallets created before 2024 usually use v4R2.",
            ),
        )
        accessory()
    }
}

/**
 * The words asked for back, each judged once it is left: a field moved past
 * says whether it was right, so a wrong word is found by its row.
 */
@Composable
private fun BackupVerificationSection(draft: WalletImportDraft) {
    var focusedVerificationSlot by remember { mutableStateOf<Int?>(null) }
    SpectraDetailCard {
        if (draft.backupVerificationPromptLabel.isNotEmpty()) {
            Text(draft.backupVerificationPromptLabel, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
        if (draft.backupVerificationWordIndices.isEmpty()) {
            FilledTonalButton(onClick = { draft.prepareBackupVerificationChallenge() }) {
                Text(AppLocalization.string("import_flow.backupVerificationButtonTitle"))
            }
        } else {
            draft.backupVerificationWordIndices.forEachIndexed { offset, wordIndex ->
                val label = AppLocalization.format("Word #%lld", (wordIndex + 1).toLong())
                val entered = draft.backupVerificationEntries.getOrElse(offset) { "" }
                val expected = draft.seedPhraseWords.getOrNull(wordIndex)
                // Judged once left, never while typed.
                val verdict: Boolean? = if (focusedVerificationSlot == offset || entered.isEmpty()) null else entered == expected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .spectraInsetFill()
                        .padding(horizontal = SpectraLayout.Space.m, vertical = SpectraLayout.Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = secondaryText,
                    )
                    SecretWordField(
                        value = entered,
                        onValueChange = { draft.updateBackupVerificationEntry(offset, it) },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    focusedVerificationSlot = offset
                                } else if (focusedVerificationSlot == offset) {
                                    focusedVerificationSlot = null
                                }
                            }
                            .semantics { contentDescription = label },
                        isError = verdict == false,
                        imeAction = if (offset == draft.backupVerificationWordIndices.lastIndex) ImeAction.Done else ImeAction.Next,
                    )
                    if (verdict != null) {
                        Icon(
                            if (verdict) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                            contentDescription = AppLocalization.string(if (verdict) "Correct" else "Incorrect"),
                            tint = if (verdict) SpectraColors.semantic.success else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (draft.isBackupVerificationComplete) {
                SpectraLabel(
                    text = AppLocalization.string("import_flow.backupVerifiedMessage"),
                    icon = Icons.Outlined.CheckCircle,
                    color = SpectraColors.semantic.success,
                )
            } else {
                SpectraHintText(AppLocalization.string("import_flow.backupVerificationHint"))
            }
        }
    }
}

/**
 * The derivation options the Advanced page has moved off their defaults, by
 * name. The entry to it is deliberately quiet, so it says when something
 * behind it changes which addresses the seed derives.
 */
private fun customizedDerivationOptionNames(draft: WalletImportDraft): List<String> {
    val names = mutableListOf<String>()
    val shortest = draft.createdLengths.firstOrNull()
    if (draft.isCreateMode && shortest != null && draft.selectedSeedPhraseWordCount != shortest.wordCount.toInt()) {
        names += AppLocalization.string("Phrase Length")
    }
    if (draft.derivationAccount != 0u || draft.derivationProfile != draft.derivationProfiles.firstOrNull()) {
        names += AppLocalization.string("Account")
    }
    if (!draft.isCreateMode && draft.seedEntry.wordCountOverride != null) names += AppLocalization.string("Word Count")
    if (!draft.isCreateMode && draft.seedEntry.language != null) names += AppLocalization.string("Wordlist")
    if (draft.customDerivationPath.trim().isNotEmpty()) names += AppLocalization.string("Custom Path")
    if (draft.overridePassphrase.isNotEmpty()) names += AppLocalization.string("Passphrase")
    if (draft.overrideHmacKey.isNotEmpty()) names += AppLocalization.string("HMAC Master Key")
    return names
}

private fun derivationOptionsSummary(draft: WalletImportDraft): String? {
    val names = customizedDerivationOptionNames(draft)
    if (names.isEmpty()) return null
    val list = ListFormatter.getInstance(AppLocalization.locale).format(names)
    return AppLocalization.format("import_flow.advanced_customized_format", list)
}

/**
 * The import's Advanced entry, as a row in its own card. Says what is in
 * effect when nothing was changed, and what was changed otherwise.
 */
@Composable
private fun AdvancedCard(draft: WalletImportDraft, onClick: () -> Unit) {
    val summary = derivationOptionsSummary(draft)
    val verdict = draft.seedEntry.verdict
    val language = verdict.language
    val inEffect = if (verdict.isValid && language != null) {
        AppLocalization.format("import_flow.advanced_in_effect_format", verdict.wordCount.toLong(), AppLocalization.string(language.name))
    } else {
        AppLocalization.string("import_flow.advanced_seed_subtitle")
    }
    val warning = SpectraColors.semantic.warning
    SpectraCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).spectraRowPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = if (summary == null) MaterialTheme.colorScheme.primary else warning)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(
                    AppLocalization.string("import_flow.advancedTitle"),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    summary ?: inEffect,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary == null) secondaryText else warning,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
        }
    }
}

/** The quiet entry to Advanced under a created phrase. */
@Composable
private fun DerivationOptionsLink(draft: WalletImportDraft, onClick: () -> Unit) {
    val summary = derivationOptionsSummary(draft)
    val warning = SpectraColors.semantic.warning
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = SpectraLayout.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = null,
            tint = if (summary == null) secondaryText else warning,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(
                AppLocalization.string("import_flow.advancedTitle"),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                summary ?: AppLocalization.string("import_flow.advancedSubtitle"),
                style = MaterialTheme.typography.labelSmall,
                color = if (summary == null) secondaryText else warning,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
    }
}

/**
 * Which account the phrase derives: the network's profile, chosen when it has
 * several, and the account index on it — both core's, from the setup
 * descriptor. A custom path under Advanced replaces them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DerivationAccountCard(draft: WalletImportDraft, accessory: @Composable () -> Unit = {}) {
    if (draft.derivationProfiles.isEmpty()) return
    SpectraDetailCard {
        Text(AppLocalization.string("Account"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        if (draft.customDerivationPath.trim().isEmpty()) {
            if (draft.derivationProfiles.size > 1) {
                var isChoosing by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(AppLocalization.string("Address Type"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { isChoosing = true }) {
                            Text(draft.derivationProfile?.title ?: "", color = secondaryText)
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = secondaryText)
                        }
                        DropdownMenu(expanded = isChoosing, onDismissRequest = { isChoosing = false }) {
                            draft.derivationProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.title) },
                                    trailingIcon = if (draft.derivationProfile == profile) {
                                        { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        draft.derivationProfile = profile
                                        isChoosing = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // The account index, 0 to 99.
            SpectraStepper(
                text = AppLocalization.format("Account %lld", draft.derivationAccount.toLong()),
                value = draft.derivationAccount.toDouble(),
                range = 0.0..99.0,
                onValueChange = { draft.derivationAccount = it.toUInt() },
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                AppLocalization.string("The custom path under Advanced replaces the account."),
                style = MaterialTheme.typography.bodySmall,
                color = SpectraColors.semantic.warning,
            )
        }
        draft.derivationPath?.let { path ->
            SelectionContainer {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = secondaryText,
                )
            }
        }
        accessory()
    }
}

/**
 * A Substrate network's account below the phrase's root: hard and soft
 * junctions, as its wallets write them. Core refuses a path it cannot read
 * one way.
 */
@Composable
internal fun JunctionPathCard(draft: WalletImportDraft) {
    if (!draft.asksJunctionPath) return
    SpectraDetailCard {
        Text(
            AppLocalization.string("Derivation Path (Optional)"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        PlainEntryField(
            value = draft.junctionPathInput,
            onValueChange = { draft.junctionPathInput = it },
            placeholder = "//polkadot//0",
        )
        SpectraHintText(
            AppLocalization.string(
                "Hard (//) and soft (/) junctions, as Polkadot wallets write them. Leave it blank for the phrase's root account.",
            ),
        )
    }
}
