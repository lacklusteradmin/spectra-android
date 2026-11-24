package dev.spectra.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.SecureWindow
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraSelectableFill
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.formatDerivationPath
import uniffi.spectra_core.parseDerivationPath
import uniffi.spectra_core.seedPhraseLanguages

/**
 * Everything about a seed wallet that has a working default: a new phrase's
 * length, the account and path it derives, and the passphrase and HMAC
 * overrides. A page over the secret step rather than a page of the setup
 * flow, so the linear flow never routes through it and most people never
 * open it.
 */
@Composable
internal fun WalletDerivationOptionsPage(route: WalletDerivationOptionsRoute) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val session = app.walletImport
    if (session.id != route.session) {
        LaunchedEffect(route) { navigator.popTo { !it.isImportFormPage } }
        SpectraPage(title = "") {}
        return
    }
    val draft = session.draft
    // A passphrase can be shown as typed.
    SecureWindow()
    SpectraPage(
        title = AppLocalization.string("import_flow.advancedTitle"),
        actions = { TextButton(onClick = { navigator.pop() }) { Text(AppLocalization.string("Done")) } },
    ) {
        if (!draft.isCreateMode) {
            SeedPhraseReadingSection(draft.seedEntry)
        } else {
            NewPhraseLengthSection(draft)
            DerivationAccountCard(draft)
            JunctionPathCard(draft)
        }
        SpectraDetailCard {
            Text(
                AppLocalization.string("Control the derivation path this wallet uses."),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
            )
            // Starts at the chosen profile's path; an edit makes it custom, and
            // Reset returns to the profile.
            draft.profileDerivationPath?.let { profilePath ->
                SeedPathSlotEditor(
                    title = AppLocalization.string("Custom Path"),
                    path = draft.derivationPath ?: profilePath,
                    onPathChange = { draft.customDerivationPath = if (it == profilePath) "" else it },
                    defaultPath = profilePath,
                )
            }
            PowerUserOverridesSection(draft)
        }
    }
}

/**
 * How an import reads its phrase: the length and the wordlist, each inferred
 * by core unless fixed here. A length is one BIP-39 defines: no other has a
 * checksum that can hold.
 */
@Composable
private fun SeedPhraseReadingSection(entry: SeedPhraseEntry) {
    val wordlists = seedPhraseLanguages(entry.chain)
    SpectraDetailCard {
        Text(AppLocalization.string("Word Count"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        val chips = listOf<Pair<String, Int?>>(AppLocalization.string("Auto") to null) +
            entry.lengths.map { length -> length.wordCount.toInt().let { "$it" to it } }
        chips.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                row.forEach { (title, count) ->
                    SelectableChip(
                        title = title,
                        isSelected = entry.wordCountOverride == count,
                        modifier = Modifier.weight(1f),
                        minHeight = 36.dp,
                        cornerRadius = SpectraLayout.Radius.control,
                    ) { entry.wordCountOverride = count }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        SpectraHintText(AppLocalization.string("import_flow.word_count_footer"))
        // Only BIP-39's lists overlap enough to need choosing; Monero's and
        // Polyseed's are told apart by their words.
        if (wordlists.isNotEmpty()) {
            SpectraDivider()
            val detected = entry.verdict.language?.let { AppLocalization.string(it.name) }
            val autoTitle = detected?.let { AppLocalization.format("import_flow.wordlist_auto_detected_format", it) }
                ?: AppLocalization.string("Auto-detect")
            var isChoosing by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppLocalization.string("Wordlist"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Box {
                    val chosen = wordlists.firstOrNull { it.code == entry.language }
                    TextButton(onClick = { isChoosing = true }) {
                        Text(chosen?.let { AppLocalization.string(it.name) } ?: autoTitle, color = secondaryText)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = secondaryText)
                    }
                    DropdownMenu(expanded = isChoosing, onDismissRequest = { isChoosing = false }) {
                        val options = listOf<Pair<String, String?>>(autoTitle to null) +
                            wordlists.map { AppLocalization.string(it.name) to it.code }
                        options.forEach { (title, code) ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                trailingIcon = if (entry.language == code) {
                                    { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                                } else {
                                    null
                                },
                                onClick = {
                                    entry.language = code
                                    isChoosing = false
                                },
                            )
                        }
                    }
                }
            }
            SpectraHintText(AppLocalization.string("import_flow.wordlist_footer"))
        }
    }
}

/** A choice among a few, as a filled tile when chosen. */
@Composable
private fun SelectableChip(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .spectraSelectableFill(isSelected, cornerRadius)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = SpectraLayout.Space.xs),
        )
    }
}

/** The passphrase and HMAC overrides, below the per-chain paths. */
@Composable
private fun PowerUserOverridesSection(draft: WalletImportDraft) {
    val warning = SpectraColors.semantic.warning
    val shape = RoundedCornerShape(SpectraLayout.Radius.inner)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(warning.copy(alpha = 0.08f), shape)
            .border(1.dp, warning.copy(alpha = 0.35f), shape)
            .padding(SpectraLayout.Space.m),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = warning, modifier = Modifier.size(18.dp))
                Text(
                    AppLocalization.string("Power-User Overrides"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Text(
                AppLocalization.string(
                    "Secret text is used exactly as entered, including spaces. Unsupported chain overrides are refused. Leave blank to use the chain default.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = warning.copy(alpha = 0.9f),
            )
        }
        AdvancedOverrideTextField(
            title = AppLocalization.string("Passphrase"),
            detail = AppLocalization.string("BIP-39 passphrase (“25th word”). Blank = none."),
            text = draft.overridePassphrase,
            onTextChange = { draft.overridePassphrase = it },
            isSecure = true,
        )
        // A new wallet's passphrase is typed twice: a typo in it makes a
        // wallet no one can restore, and nothing else would catch it.
        if (draft.isCreateMode && draft.overridePassphrase.isNotEmpty()) {
            AdvancedOverrideTextField(
                title = AppLocalization.string("Confirm Passphrase"),
                detail = AppLocalization.string("Type the passphrase again, exactly."),
                text = draft.overridePassphraseConfirmation,
                onTextChange = { draft.overridePassphraseConfirmation = it },
                isSecure = true,
            )
            draft.passphraseConfirmationError?.let { error ->
                SpectraLabel(text = error, icon = Icons.Outlined.WarningAmber, color = MaterialTheme.colorScheme.error)
            }
        }
        AdvancedOverrideTextField(
            title = AppLocalization.string("HMAC Master Key"),
            detail = AppLocalization.string("Custom master HMAC key for supported chains. Blank uses the chain default."),
            text = draft.overrideHmacKey,
            onTextChange = { draft.overrideHmacKey = it },
        )
    }
}

@Composable
private fun AdvancedOverrideTextField(
    title: String,
    detail: String,
    text: String,
    onTextChange: (String) -> Unit,
    isSecure: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = secondaryText)
        if (isSecure) {
            // A secret field can be shown: its spaces count, and a masked field hides them.
            MaskedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = AppLocalization.string("(default)"),
                revealable = true,
                monospaced = true,
            )
        } else {
            PlainEntryField(value = text, onValueChange = onTextChange, placeholder = AppLocalization.string("(default)"))
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = secondaryText)
    }
}

/**
 * A new phrase's length, as core offers the network's created format, and a
 * fresh phrase at it. Changing either replaces the phrase on the page.
 */
@Composable
private fun NewPhraseLengthSection(draft: WalletImportDraft) {
    SpectraDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                AppLocalization.string("Phrase Length"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { draft.regenerateSeedPhrase() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap(SpectraLayout.Space.xs)
                Text(AppLocalization.string("Regenerate"))
            }
        }
        // A network whose created format has one length — Monero's 25, TON's
        // 24 — has nothing to choose.
        val lengths = draft.createdLengths
        if (lengths.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                lengths.forEach { length ->
                    val count = length.wordCount.toInt()
                    SelectableChip(
                        title = AppLocalization.formatCount("%lld words", count.toLong(), count.toLong()),
                        isSelected = draft.selectedSeedPhraseWordCount == count,
                        modifier = Modifier.weight(1f),
                        minHeight = 44.dp,
                        cornerRadius = SpectraLayout.Radius.inner,
                    ) { draft.selectedSeedPhraseWordCount = count }
                }
            }
        }
        SpectraHintText(
            AppLocalization.string("Twelve words are enough for most wallets; longer phrases are as safe and longer to write down."),
        )
    }
}

/**
 * A derivation path as its segments, each number editable in place, the
 * hardened marks kept. [onPathChange] receives the whole path again.
 */
@Composable
internal fun SeedPathSlotEditor(title: String, path: String, onPathChange: (String) -> Unit, defaultPath: String) {
    val segments = parseDerivationPath(path) ?: parseDerivationPath(defaultPath) ?: emptyList()
    fun updateSegment(index: Int, value: String) {
        val resolved = (parseDerivationPath(path) ?: parseDerivationPath(defaultPath))?.toMutableList() ?: return
        if (index !in resolved.indices) return
        val numeric = value.filter { it in '0'..'9' }.toUIntOrNull() ?: return
        resolved[index] = resolved[index].copy(value = numeric)
        onPathChange(formatDerivationPath(resolved))
    }
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            TextButton(onClick = { onPathChange(defaultPath) }) {
                Text(AppLocalization.string("Reset"), color = secondaryText)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            Text("m", style = mono.copy(fontWeight = FontWeight.SemiBold), color = secondaryText)
            segments.forEachIndexed { index, segment ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    Text("/", style = mono, color = secondaryText)
                    OutlinedTextField(
                        value = segment.value.toString(),
                        onValueChange = { updateSegment(index, it) },
                        modifier = Modifier.width(84.dp),
                        textStyle = mono,
                        placeholder = { Text("0", style = mono) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (segment.isHardened) {
                        Text("'", style = mono.copy(fontWeight = FontWeight.Bold), color = secondaryText)
                    }
                }
            }
        }
    }
}
