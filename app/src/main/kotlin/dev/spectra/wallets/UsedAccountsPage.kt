package dev.spectra.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraErrorText
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.profileTitle
import dev.spectra.models.userErrorMessage
import uniffi.spectra_core.FundsFinderRequest
import uniffi.spectra_core.FundsScanEndpoint
import uniffi.spectra_core.FundsScanRead

/**
 * Find the accounts a phrase has used on the chosen network: core scans the
 * network's derivation profiles at the first accounts, reading balance and
 * history, or on TON each wallet version's account. The addresses go to a
 * provider before any wallet exists, so the page names the endpoints first
 * and scans only when asked. Choosing a used account sets the import's
 * profile and account, or its wallet version.
 */
@Composable
internal fun UsedAccountsPage(route: UsedAccountsRoute) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val session = app.walletImport
    if (session.id != route.session) {
        LaunchedEffect(route) { navigator.popTo { !it.isImportFormPage } }
        SpectraPage(title = "") {}
        return
    }
    val draft = session.draft
    /** Where the scan would send its addresses, named before it runs; `null` until core has derived them. */
    var endpoints by remember { mutableStateOf<List<FundsScanEndpoint>?>(null) }
    var candidateCount by remember { mutableStateOf(0) }
    /** Why the candidates could not be derived. */
    var preparationError by remember { mutableStateOf<String?>(null) }
    val run = remember { FundsScanRun() }
    DisposableEffect(run) { onDispose { run.cancel() } }

    // The scan the page runs: the import's phrase and passphrase, on its network.
    fun request() = FundsFinderRequest(
        seedPhrase = draft.seedPhrase,
        passphrase = draft.overridePassphrase.ifEmpty { null },
    )

    // Derive the candidates and name the endpoints; nothing is sent.
    LaunchedEffect(Unit) {
        val chain = draft.chain ?: return@LaunchedEffect
        if (endpoints != null) return@LaunchedEffect
        try {
            FundsScanRun.begin(request(), chain, app.bridge.service()).use { begun ->
                candidateCount = begun.candidates().size
                endpoints = begun.endpoints()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            preparationError = userErrorMessage(error)
        }
    }

    // Done, where Done goes: choosing an account has already set it on the import.
    SpectraPage(
        title = AppLocalization.string("Find Used Accounts"),
        actions = { TextButton(onClick = { navigator.pop() }) { Text(AppLocalization.string("Done")) } },
    ) {
        (preparationError ?: run.error)?.let { SpectraDetailCard { SpectraErrorText(it) } }
        endpoints?.let { list ->
            Text(
                AppLocalization.formatCount("Spectra will send %lld addresses to:", candidateCount.toLong(), candidateCount.toLong()),
                style = MaterialTheme.typography.titleSmall,
                color = secondaryText,
            )
            SpectraDetailCard {
                list.forEach { endpoint ->
                    SelectionContainer {
                        Text(endpoint.endpoint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
                if (list.isEmpty()) {
                    SpectraHintText(AppLocalization.string("No endpoint is configured for this network."))
                }
                Button(
                    onClick = {
                        val chain = draft.chain ?: return@Button
                        run.start(request(), chain, app.bridge, app.scope)
                    },
                    enabled = !run.isRunning && list.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (run.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        SpectraGap()
                        Text(AppLocalization.string("Scanning…"))
                    } else {
                        Text(AppLocalization.string("Scan Accounts"))
                    }
                }
            }
            SpectraHintText(AppLocalization.string("Nothing is sent until you scan."))
        }
        if (run.reads.isNotEmpty()) {
            SpectraRowGroup(items = run.reads, title = AppLocalization.string("Accounts")) { read ->
                ReadRow(read) {
                    if (read.used && draft.derive(read.candidate)) navigator.pop()
                }
            }
        }
    }
}

@Composable
private fun ReadRow(read: FundsScanRead, onChoose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = read.used, onClick = onChoose)
            .spectraRowPadding(),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                read.candidate.profileTitle ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(
                readStatus(read),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (read.used) MaterialTheme.colorScheme.primary else secondaryText,
            )
        }
        Text(
            read.candidate.address,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = secondaryText,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        read.error?.let {
            Text(userErrorMessage(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun readStatus(read: FundsScanRead): String {
    if (read.error != null) return AppLocalization.string("Not read")
    val balance = read.balance
    if (read.funded && balance != null) return balance.amountDisplay
    return AppLocalization.string(if (read.used) "Used" else "Unused")
}
