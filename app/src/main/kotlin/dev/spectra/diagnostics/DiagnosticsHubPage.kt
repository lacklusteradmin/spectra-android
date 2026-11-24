package dev.spectra.diagnostics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.SpectraLazyPage
import dev.spectra.components.SpectraNavigationRow
import dev.spectra.components.SpectraSearchField
import dev.spectra.components.lazyRowGroup
import dev.spectra.components.pageItem
import dev.spectra.components.shareText
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import dev.spectra.models.displayName
import dev.spectra.models.searchKeywords
import dev.spectra.models.userErrorMessage
import dev.spectra.settings.FormActionRow
import dev.spectra.settings.FormDividerInset
import dev.spectra.settings.FormSection
import dev.spectra.settings.FormTextRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.spectra_core.Chain
import java.io.File

/** A network's diagnostics page, as the hub lists and searches it. */
private data class DiagnosticsDestination(val chain: Chain, val title: String, val keywords: List<String>)

/**
 * Diagnostics: checking every network's endpoints, a page per network, and
 * the bundle a problem report is made from.
 */
@Composable
internal fun DiagnosticsHubPage() {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runs = app.chainDiagnosticsState
    var searchText by rememberSaveable { mutableStateOf("") }
    var diagnosticsNotice by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the last bundle was written, for Share Last Export.
    var lastExportPath by rememberSaveable { mutableStateOf<String?>(null) }
    // The checks run on the app's scope; the page says they finished if it
    // was on screen to see them start or run.
    var sawCheckingAll by remember { mutableStateOf(runs.isCheckingAllEndpoints) }
    LaunchedEffect(runs.isCheckingAllEndpoints) {
        if (sawCheckingAll && !runs.isCheckingAllEndpoints) {
            diagnosticsNotice = AppLocalization.string("Endpoint checks completed.")
        }
        sawCheckingAll = runs.isCheckingAllEndpoints
    }
    // Every mainnet has a diagnostics page.
    val destinations = remember {
        ChainCatalog.mainnets.map { chain ->
            DiagnosticsDestination(chain, AppLocalization.format("%@ Diagnostics", chain.displayName), chain.searchKeywords)
        }
    }
    val query = searchText.trim()
    val filtered = if (query.isEmpty()) {
        destinations
    } else {
        destinations.filter { destination ->
            destination.title.contains(query, ignoreCase = true) || destination.keywords.any { it.contains(query, ignoreCase = true) }
        }
    }

    fun export() {
        scope.launch {
            diagnosticsNotice = try {
                lastExportPath = app.exportDiagnosticsBundle().path
                AppLocalization.string("Diagnostics bundle ready to share.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLocalization.format("Export failed: %@", userErrorMessage(error))
            }
        }
    }

    // The bundle goes to the share sheet as text: the app shares no files.
    fun shareLastExport() {
        val path = lastExportPath ?: return
        scope.launch {
            val file = File(path)
            val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }
            val failure = if (text == null) {
                AppLocalization.string("Something went wrong. Try again.")
            } else {
                shareText(context, text, subject = file.name)
            }
            if (failure != null) diagnosticsNotice = AppLocalization.format("Export failed: %@", failure)
        }
    }

    SpectraLazyPage(title = AppLocalization.string("diagnostics.navigationTitle")) {
        pageItem { SpectraSearchField(searchText, { searchText = it }, AppLocalization.string("diagnostics.searchPrompt")) }
        pageItem {
            FormSection(title = AppLocalization.string("diagnostics.actionsSectionTitle")) {
                row {
                    FormActionRow(
                        title = AppLocalization.string(
                            if (runs.isCheckingAllEndpoints) "Running Diagnostics..." else "Run All Endpoint Checks",
                        ),
                        onClick = { app.scope.launch { app.runAllEndpointDiagnostics() } },
                        enabled = !runs.isCheckingAllEndpoints,
                    )
                }
            }
        }
        lazyRowGroup(
            items = filtered,
            key = { it.chain },
            title = AppLocalization.string("diagnostics.chainsSectionTitle"),
            dividerInset = FormDividerInset,
        ) { destination ->
            SpectraNavigationRow(title = destination.title, onClick = { navigator.push(ChainDiagnosticsRoute(destination.chain)) })
        }
        pageItem {
            FormSection(title = AppLocalization.string("Diagnostics Bundle")) {
                row { FormActionRow(AppLocalization.string("Export Diagnostics Bundle"), onClick = ::export) }
                if (lastExportPath != null) {
                    row { FormActionRow(AppLocalization.string("Share Last Export"), onClick = ::shareLastExport, icon = Icons.Outlined.Share) }
                }
            }
        }
        diagnosticsNotice?.let { notice -> pageItem { FormSection { row { FormTextRow(notice) } } } }
    }
}
