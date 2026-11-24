package dev.spectra.wallets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.CopyButton
import dev.spectra.components.SecureWindow
import dev.spectra.components.SpectraBottomActionBar
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.chainName
import dev.spectra.models.displayName
import dev.spectra.models.profileTitle
import kotlinx.coroutines.launch
import uniffi.spectra_core.FundsFinderCandidate
import uniffi.spectra_core.FundsFinderRequest
import uniffi.spectra_core.FundsScanRead
import uniffi.spectra_core.validateWalletPassword

/** One account the scan found used: funded, or with history. */
data class FundsFinderHit(val read: FundsScanRead) {
    val candidate: FundsFinderCandidate get() = read.candidate

    /** The balance, or `null` for an account used and since emptied. */
    val balanceDisplay: String? get() = if (read.funded) read.balance?.amountDisplay else null
}

/**
 * Funds Finder's state, held by its route: nothing else reads it, and the
 * page closing cancels the scan. The same entry the import page reads a
 * phrase with, so a phrase the import would refuse is not scanned here either.
 */
internal class FundsFinderState {
    val seedEntry = SeedPhraseEntry()
    var passphrase by mutableStateOf("")
    var hasStarted by mutableStateOf(false)
    val run = FundsScanRun()

    val canStart: Boolean get() = seedEntry.verdict.isValid && !run.isRunning
    val hits: List<FundsFinderHit> get() = run.used.map(::FundsFinderHit)
}

/** Scan a seed's derivation paths for funded addresses. */
@Composable
internal fun FundsFinderPage(route: FundsFinderRoute) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val state = route.state
    val run = state.run
    val hits = state.hits
    // Leaving the page cancels the scan; the import it opens only covers it.
    DisposableEffect(route) {
        onDispose { if (!navigator.isOnAnyTab(route)) run.cancel() }
    }
    // The phrase is on screen until the scan starts.
    if (!state.hasStarted) SecureWindow()

    SpectraPage(
        title = AppLocalization.string("Funds Finder"),
        actions = {
            if (state.hasStarted && !run.isRunning) {
                // Back to the phrase as it was: the next scan is most often the
                // same phrase with a passphrase, or one word fixed.
                TextButton(onClick = {
                    run.cancel()
                    state.hasStarted = false
                }) { Text(AppLocalization.string("New Scan")) }
            }
        },
        bottomBar = {
            if (!state.hasStarted) {
                SpectraBottomActionBar {
                    Button(
                        onClick = {
                            if (!state.canStart) return@Button
                            state.hasStarted = true
                            run.start(
                                FundsFinderRequest(
                                    seedPhrase = state.seedEntry.phrase,
                                    passphrase = state.passphrase.ifEmpty { null },
                                ),
                                chain = null,
                                bridge = app.bridge,
                                scope = app.scope,
                            )
                        },
                        enabled = state.canStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        SpectraGap()
                        Text(AppLocalization.string("Start Scan"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) {
        if (!state.hasStarted) {
            InputSection(state)
        } else {
            ScanProgressSection(run, hits)
            if (hits.isNotEmpty()) {
                HitsSection(
                    hits = hits,
                    showsImportAll = hits.size > 1 && !run.isRunning,
                    onImport = { hit ->
                        // A found account opens the single-wallet import on its
                        // network, with the phrase, passphrase, profile and
                        // account it was found at.
                        app.beginFoundAccountImport(hit.candidate, state.seedEntry.verdict.words, state.passphrase)
                    },
                    onImportAll = {
                        navigator.push(FundsFinderImportAllRoute(hits, state.seedEntry.verdict.words, state.passphrase))
                    },
                )
            }
            run.unreadChains.forEach { unread ->
                ErrorBanner(
                    AppLocalization.format("%@ addresses could not be checked: %@", unread.chain.displayName, unread.reason),
                )
            }
            run.error?.let { ErrorBanner(it) }
            // Unread addresses are not empty ones.
            if (!run.isRunning && hits.isEmpty() && run.error == null && run.unreadChains.isEmpty()) {
                SpectraEmptyStateCard(
                    title = AppLocalization.string("No funds found"),
                    message = AppLocalization.string(
                        "No balance was detected at any of the scanned derivation paths. Double-check your seed phrase and try with a BIP-39 passphrase if you set one.",
                    ),
                    icon = Icons.Outlined.Inbox,
                )
            }
        }
    }
}

@Composable
private fun InputSection(state: FundsFinderState) {
    SpectraCard {
        Row(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                Text(AppLocalization.string("Scan All Derivation Paths"), style = MaterialTheme.typography.titleMedium)
                Text(
                    AppLocalization.string(
                        "Enter your seed phrase to check every network's derivation profiles at their first three accounts, revealing which accounts hold funds or have been used.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                )
            }
        }
    }
    SpectraDetailCard {
        Text(AppLocalization.string("Seed Phrase"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        SeedPhraseEntryGrid(state.seedEntry)
    }
    SpectraDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                AppLocalization.string("BIP-39 Passphrase"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(AppLocalization.string("Optional"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
        MaskedTextField(
            value = state.passphrase,
            onValueChange = { state.passphrase = it },
            placeholder = AppLocalization.string("Leave blank if none"),
            revealable = true,
        )
        SpectraHintText(AppLocalization.string("A passphrase creates a different wallet. Leave blank unless you set one up."))
    }
    SpectraCard {
        Row(
            modifier = Modifier.padding(SpectraLayout.Space.m),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = SpectraColors.semantic.success, modifier = Modifier.size(18.dp))
            Text(AppLocalization.string("fundsFinder.privacy"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
    }
}

@Composable
private fun ScanProgressSection(run: FundsScanRun, hits: List<FundsFinderHit>) {
    SpectraDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            if (run.isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Text(AppLocalization.string("Scanning…"), style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = SpectraColors.semantic.success)
                Text(AppLocalization.string("Scan Complete"), style = MaterialTheme.typography.titleMedium)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (run.total > 0) {
                Text(
                    AppLocalization.format("%lld / %lld", run.checked.toLong(), run.total.toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryText,
                )
            }
        }
        if (run.total > 0) {
            LinearProgressIndicator(progress = { run.progress }, modifier = Modifier.fillMaxWidth())
        }
        when {
            run.isRunning -> SpectraHintText(AppLocalization.string("Checking addresses across all derivation paths…"))
            hits.isEmpty() && run.unreadChains.isEmpty() -> SpectraHintText(
                AppLocalization.formatCount("Checked %lld paths — no funds found", run.checked.toLong(), run.checked.toLong()),
            )
            hits.isNotEmpty() -> SpectraHintText(
                AppLocalization.formatCount(
                    "Found %lld paths with funds across %lld checked",
                    hits.size.toLong(),
                    hits.size.toLong(),
                    run.checked.toLong(),
                ),
            )
        }
    }
}

@Composable
private fun HitsSection(
    hits: List<FundsFinderHit>,
    showsImportAll: Boolean,
    onImport: (FundsFinderHit) -> Unit,
    onImportAll: () -> Unit,
) {
    SpectraRowGroup(
        items = hits,
        title = AppLocalization.formatCount("%lld paths with funds found", hits.size.toLong(), hits.size.toLong()),
        dividerInset = SpectraLayout.rowHorizontal,
        footer = {
            // Every found account at once, each its own wallet — the scan's
            // usual end is more than one.
            if (showsImportAll) {
                SpectraDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onImportAll).spectraRowPadding(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    SpectraGap()
                    Text(
                        AppLocalization.format("Import All %lld", hits.size.toLong()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { hit -> FundsFinderHitRow(hit) { onImport(hit) } }
}

@Composable
private fun FundsFinderHitRow(hit: FundsFinderHit, importAccount: () -> Unit) {
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(hit.candidate.chainName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                hit.candidate.profileTitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }
            Text(
                hit.balanceDisplay ?: AppLocalization.string("Used"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            hit.candidate.derivationPath,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = secondaryText,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hit.candidate.address,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            CopyButton(value = hit.candidate.address)
        }
        FilledTonalButton(onClick = importAccount) {
            Text(
                AppLocalization.string("Import This Account"),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    SpectraCard {
        Row(
            modifier = Modifier.padding(SpectraLayout.Space.m),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = SpectraColors.semantic.warning, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
    }
}

// ── Import All ───────────────────────────────────────────────────────────

/** Import All's form and answers, held by its route so a run under way finishes. */
internal class ImportAllFoundState {
    /** One password for every import, typed in [dev.spectra.components.WalletPasswordField]s; never saved with the page. */
    val password = TextFieldState()
    val confirmation = TextFieldState()
    var importingIndex by mutableStateOf<Int?>(null)

    /** Each account's answer, by its candidate, once its import has run: `null` for imported. */
    val results = mutableStateMapOf<FundsFinderCandidate, String?>()
    var isFinished by mutableStateOf(false)

    val passwordProblem: String?
        get() = validateWalletPassword(password.text.toString(), confirmation.text.toString())?.localizedMessage
}

/**
 * Importing every found account: one optional password for them all, then
 * each import in turn and what core said to it.
 */
@Composable
internal fun ImportAllFoundPage(route: FundsFinderImportAllRoute) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val state = route.state
    val hits = route.hits
    val isImporting = state.importingIndex != null
    // An import under way cannot be left half-told.
    BackHandler(enabled = isImporting) {}

    fun importAll() {
        val password = state.password.text.toString()
        val commits = hits.map { hit ->
            WalletImportDraft.importCommit(hit.candidate, route.words, route.passphrase, password)
        }
        state.importingIndex = 0
        app.scope.launch {
            val built = commits.filterNotNull()
            val refusals = app.importWallets(built) { state.importingIndex = it }.iterator()
            hits.zip(commits).forEach { (hit, commit) ->
                state.results[hit.candidate] = if (commit == null) {
                    AppLocalization.string("Not imported")
                } else {
                    if (refusals.hasNext()) refusals.next() else null
                }
            }
            state.importingIndex = null
            state.isFinished = true
        }
    }

    SpectraPage(
        title = AppLocalization.string("Import All"),
        onBack = if (isImporting) null else ({ navigator.pop() }),
        bottomBar = {
            SpectraBottomActionBar {
                if (state.isFinished) {
                    Button(onClick = { navigator.pop() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(AppLocalization.string("Done"))
                    }
                } else {
                    Button(
                        onClick = ::importAll,
                        enabled = !isImporting && state.passwordProblem == null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(AppLocalization.string("Import")) }
                }
            }
        },
    ) {
        SpectraRowSection(footer = AppLocalization.string("fundsFinder.importAll.footer")) {
            hits.forEachIndexed { index, hit ->
                row {
                    Row(
                        modifier = Modifier.spectraRowPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                            Text(hit.candidate.chainName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                hit.candidate.derivationPath,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = secondaryText,
                            )
                            val refusal = state.results[hit.candidate]
                            if (refusal != null) {
                                SpectraLabel(text = refusal, icon = Icons.Outlined.WarningAmber, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        when {
                            state.results.containsKey(hit.candidate) && state.results[hit.candidate] == null -> Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = AppLocalization.string("Imported"),
                                tint = SpectraColors.semantic.success,
                            )
                            state.results.containsKey(hit.candidate) -> Icon(
                                Icons.Outlined.Cancel,
                                contentDescription = AppLocalization.string("Not imported"),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            state.importingIndex == index ->
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
        if (!state.isFinished) {
            SpectraDetailCard(title = AppLocalization.string("import_flow.wallet_password_optional")) {
                WalletPasswordField(
                    state.password,
                    label = null,
                    placeholder = AppLocalization.string("import_flow.wallet_password_field"),
                    enabled = !isImporting,
                )
                WalletPasswordField(
                    state.confirmation,
                    label = null,
                    placeholder = AppLocalization.string("import_flow.wallet_password_confirmation_field"),
                    enabled = !isImporting,
                )
                val problem = state.passwordProblem
                if (problem != null) {
                    SpectraLabel(text = problem, icon = Icons.Outlined.WarningAmber, color = MaterialTheme.colorScheme.error)
                } else {
                    SpectraHintText(AppLocalization.string("import_flow.wallet_password_subtitle"))
                }
            }
        }
    }
}
