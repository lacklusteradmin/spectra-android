package dev.spectra.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraNavigationRow
import dev.spectra.components.SpectraPage
import dev.spectra.components.copyToClipboard
import dev.spectra.components.rememberNotice
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.DateStyle
import dev.spectra.models.TimeStyle
import dev.spectra.models.displayName
import dev.spectra.models.formatUnixDate
import dev.spectra.models.usesAccountUtxo
import dev.spectra.models.userErrorMessage
import dev.spectra.settings.FormActionRow
import dev.spectra.settings.FormErrorRow
import dev.spectra.settings.FormSection
import dev.spectra.settings.FormTextRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainDiagnostics
import uniffi.spectra_core.DiagnosticLog
import uniffi.spectra_core.DiagnosticLogLevel
import uniffi.spectra_core.EndpointProbe
import uniffi.spectra_core.KeypoolDiagnostic

/**
 * One network's diagnostics: its runs, what core recorded of them, its
 * operational events and its owned-address state.
 */
@Composable
internal fun ChainDiagnosticsPage(chain: Chain) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val notice = rememberNotice()
    val runs = app.chainDiagnosticsState
    val label = chain.displayName
    var refreshNotice by rememberSaveable { mutableStateOf<String?>(null) }
    // What core recorded for this family: history runs, endpoint checks and
    // the document built from them. Re-read whenever a run finishes.
    var recorded by remember { mutableStateOf<ChainDiagnostics?>(null) }
    var recordedError by remember { mutableStateOf<String?>(null) }
    var keypoolError by remember { mutableStateOf<String?>(null) }
    var keypoolDiagnostics by remember { mutableStateOf<List<KeypoolDiagnostic>>(emptyList()) }
    var operationalEvents by remember { mutableStateOf<List<DiagnosticLog>>(emptyList()) }

    LaunchedEffect(chain) {
        try {
            keypoolDiagnostics = app.chainKeypoolDiagnostics(chain)
            keypoolError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            keypoolDiagnostics = emptyList()
            keypoolError = userErrorMessage(error)
        }
    }
    LaunchedEffect(runs.diagnosticsRevision) {
        try {
            recorded = app.chainDiagnostics(chain)
            recordedError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            recordedError = userErrorMessage(error)
        }
        operationalEvents = app.operationalEvents(chain)
    }

    val isRefreshing = chain in runs.refreshing
    val isRunningHistory = chain in runs.runningHistory
    val isCheckingEndpoints = chain in runs.checkingEndpoints
    val isRunningSelfTests = chain in runs.runningSelfTests
    val isRunningRescan = chain in runs.runningRescans
    val historySources = recorded?.historySources ?: emptyList()
    val endpoints = recorded?.endpoints ?: emptyList()

    SpectraPage(title = AppLocalization.format("%@ Diagnostics", label)) {
        FormSection(title = AppLocalization.string("diagnostics.actionsSectionTitle")) {
            row {
                FormActionRow(
                    title = AppLocalization.string(if (isRefreshing) "Refreshing..." else "Refresh Balances and History"),
                    onClick = {
                        refreshNotice = null
                        app.scope.launch { refreshNotice = refreshOutcomeMessage(app.refreshChainForDiagnostics(chain)) }
                    },
                    enabled = !isRefreshing,
                )
            }
            refreshNotice?.let { text -> row { FormTextRow(text) } }
            row {
                FormActionRow(
                    title = AppLocalization.string(if (isRunningHistory) "Running History Diagnostics..." else "Run History Diagnostics"),
                    onClick = { app.scope.launch { app.runHistoryDiagnostics(chain) } },
                    enabled = !isRunningHistory,
                )
            }
            row {
                FormActionRow(
                    title = AppLocalization.string("Copy Diagnostics JSON"),
                    onClick = {
                        val document = recorded?.document
                        if (document != null) {
                            copyToClipboard(context, document)
                            notice(AppLocalization.format("%@ diagnostics JSON copied.", label))
                        } else {
                            notice(AppLocalization.format("No %@ diagnostics available to copy.", label))
                        }
                    },
                )
            }
            row {
                FormActionRow(
                    title = AppLocalization.string(if (isCheckingEndpoints) "Checking Endpoints..." else "Check Endpoints"),
                    onClick = { app.scope.launch { app.runEndpointDiagnostics(chain) } },
                    enabled = !isCheckingEndpoints,
                )
            }
            row {
                FormActionRow(
                    title = AppLocalization.string(if (isRunningSelfTests) "Running Self-Tests..." else "Run Self-Tests"),
                    onClick = { app.scope.launch { app.runSelfTests(chain) } },
                    enabled = !isRunningSelfTests,
                )
            }
            if (chain.usesAccountUtxo) {
                row {
                    FormActionRow(
                        title = AppLocalization.string(if (isRunningRescan) "Rescanning..." else "Run Rescan"),
                        onClick = { app.scope.launch { app.runUTXORescan(chain) } },
                        enabled = !isRunningRescan,
                    )
                }
            }
        }
        FormSection(title = AppLocalization.string("diagnostics.statusSectionTitle")) {
            recordedError?.let { error -> row { FormErrorRow(error) } }
            val ranAt = recorded?.historyRunAtUnix
            row {
                FormTextRow(
                    if (ranAt != null) {
                        AppLocalization.format("diagnostics.lastHistoryRunFormat", formattedTime(ranAt))
                    } else {
                        AppLocalization.string("diagnostics.historyNotRunYet")
                    },
                )
            }
            row {
                FormTextRow(AppLocalization.format("diagnostics.walletDiagnosticsCoveredFormat", (recorded?.walletCount ?: 0u).toString()))
            }
            historySources.firstOrNull()?.let { primary ->
                row {
                    FormTextRow(
                        AppLocalization.format("diagnostics.mostUsedHistorySourceFormat", primary.source, primary.walletCount.toString()),
                    )
                }
            }
            recorded?.endpointsCheckedAtUnix?.let { checkedAt ->
                row { FormTextRow(AppLocalization.format("diagnostics.lastEndpointCheckFormat", formattedTime(checkedAt))) }
            }
            if (endpoints.isNotEmpty()) {
                val reachable = endpoints.count { it.checked && it.reachable }
                row {
                    FormTextRow(AppLocalization.format("diagnostics.endpointHealthFormat", reachable.toString(), endpoints.size.toString()))
                }
            }
        }
        FormSection(title = AppLocalization.format("diagnostics.historySourcesSectionTitleFormat", label)) {
            if (historySources.isEmpty()) {
                row { FormTextRow(AppLocalization.string("diagnostics.noHistoryTelemetryYet")) }
            } else {
                historySources.forEach { item ->
                    row {
                        Row(modifier = Modifier.spectraRowPadding(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.source,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                AppLocalization.format("diagnostics.countOnly", item.walletCount.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText,
                            )
                        }
                    }
                }
            }
        }
        FormSection(title = AppLocalization.format("diagnostics.endpointReachabilitySectionTitleFormat", label)) {
            if (endpoints.isEmpty()) {
                row { FormTextRow(AppLocalization.string("diagnostics.noEndpointChecksYet")) }
            } else {
                endpoints.forEach { result -> row { EndpointProbeRow(result) } }
            }
        }
        FormSection {
            row {
                SpectraNavigationRow(
                    title = AppLocalization.string("endpoints.navigationTitle"),
                    onClick = { navigator.push(Routes.EndpointSettings) },
                )
            }
        }
        FormSection(title = AppLocalization.string("Operational Events")) {
            if (operationalEvents.isEmpty()) {
                row { FormTextRow(AppLocalization.string("No operational events recorded yet.")) }
            } else {
                operationalEvents.take(20).forEach { log -> row { OperationalEventRow(log) } }
            }
        }
        FormSection(title = AppLocalization.string("Owned Address Management")) {
            val error = keypoolError
            when {
                error != null -> row { FormErrorRow(error) }
                keypoolDiagnostics.isEmpty() ->
                    row { FormTextRow(AppLocalization.string("No owned-address management state recorded yet.")) }
                else -> keypoolDiagnostics.forEach { item -> row { KeypoolRow(item) } }
            }
        }
    }
}

private fun formattedTime(unix: Double): String = formatUnixDate(unix, DateStyle.ABBREVIATED, TimeStyle.SHORTENED)

/** An endpoint nothing knows how to probe is unchecked, not a pass. */
@Composable
private fun EndpointProbeRow(result: EndpointProbe) {
    val (icon, tint) = when {
        !result.checked -> Icons.Outlined.HourglassEmpty to secondaryText
        result.reachable -> Icons.Outlined.CheckCircle to SpectraColors.semantic.success
        else -> Icons.Outlined.Cancel to SpectraColors.semantic.failure
    }
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(result.endpoint, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(result.detail, style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

@Composable
private fun OperationalEventRow(log: DiagnosticLog) {
    val event = log.input
    val levelColor = when (event.level) {
        DiagnosticLogLevel.ERROR -> SpectraColors.semantic.failure
        DiagnosticLogLevel.WARNING -> SpectraColors.semantic.warning
        else -> secondaryText
    }
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Text(event.message, style = MaterialTheme.typography.bodyMedium)
        Text(
            event.level.displayName,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = levelColor,
        )
        event.transactionHash?.takeIf { it.isNotEmpty() }?.let { hash ->
            SelectionContainer {
                Text(hash, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText)
            }
        }
    }
}

@Composable
private fun KeypoolRow(item: KeypoolDiagnostic) {
    val caption = MaterialTheme.typography.bodySmall
    val mono = caption.copy(fontFamily = FontFamily.Monospace)
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Text(item.walletName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(AppLocalization.format("Next receive index: %lld", item.keypool.nextExternalIndex), style = caption, color = secondaryText)
        Text(AppLocalization.format("Next change index: %lld", item.keypool.nextChangeIndex), style = caption, color = secondaryText)
        item.keypool.reservedReceiveIndex?.let {
            Text(AppLocalization.format("Reserved receive index: %lld", it), style = caption, color = secondaryText)
        }
        item.reservedReceive?.derivationPath?.takeIf { it.isNotEmpty() }?.let { Text(it, style = mono, color = secondaryText) }
        item.reservedReceive?.address?.takeIf { it.isNotEmpty() }?.let { Text(it, style = mono, color = secondaryText) }
    }
}
