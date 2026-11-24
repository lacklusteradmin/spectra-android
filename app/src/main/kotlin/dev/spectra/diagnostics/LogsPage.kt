package dev.spectra.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.exportOperationalLogsText
import dev.spectra.app.networkSyncStatusText
import dev.spectra.app.pendingTransactionRefreshStatusText
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLazyPage
import dev.spectra.components.SpectraSearchField
import dev.spectra.components.copyToClipboard
import dev.spectra.components.lazyRowGroup
import dev.spectra.components.pageItem
import dev.spectra.components.rememberNotice
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.DateStyle
import dev.spectra.models.TimeStyle
import dev.spectra.models.color
import dev.spectra.models.diagnosticLogLevels
import dev.spectra.models.displayName
import dev.spectra.models.formatUnixDate
import dev.spectra.settings.FormDividerInset
import dev.spectra.settings.FormPickerRow
import dev.spectra.settings.FormSection
import dev.spectra.settings.FormTextRow
import uniffi.spectra_core.CatalogColor
import uniffi.spectra_core.DiagnosticLog
import uniffi.spectra_core.DiagnosticLogLevel

/** The category filter's choice for every category; no category is spelled this way. */
private const val ALL_CATEGORIES = "__all__"

/** The operational log: what the app and core did, filtered and searched, and copied for a report. */
@Composable
internal fun LogsPage() {
    val app = LocalAppState.current
    val context = LocalContext.current
    val notice = rememberNotice()
    val diagnostics = app.diagnostics
    var searchText by rememberSaveable { mutableStateOf("") }
    // `null` shows every level.
    var selectedLevel by rememberSaveable { mutableStateOf<DiagnosticLogLevel?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf(ALL_CATEGORIES) }
    var isConfirmingClear by remember { mutableStateOf(false) }
    val logs = diagnostics.operationalLogs
    val categories = remember(diagnostics.operationalLogsRevision) {
        listOf(ALL_CATEGORIES) + logs.map { it.input.category }.toSortedSet()
    }
    // A category the log no longer has falls back to all of them.
    val category = if (selectedCategory in categories) selectedCategory else ALL_CATEGORIES
    val filteredLogs = remember(diagnostics.operationalLogsRevision, selectedLevel, category, searchText) {
        filtered(logs, selectedLevel, category, searchText)
    }

    SpectraLazyPage(
        title = AppLocalization.string("Logs"),
        actions = {
            TextButton(
                onClick = {
                    copyToClipboard(context, app.exportOperationalLogsText(filteredLogs))
                    notice(AppLocalization.formatCount("Copied %lld log entries", filteredLogs.size.toLong(), filteredLogs.size.toLong()))
                },
                enabled = filteredLogs.isNotEmpty(),
            ) { Text(AppLocalization.string("Copy")) }
            TextButton(onClick = { isConfirmingClear = true }, enabled = logs.isNotEmpty()) {
                Text(
                    AppLocalization.string("Clear"),
                    color = if (logs.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
    ) {
        pageItem { SpectraSearchField(searchText, { searchText = it }, AppLocalization.string("Search message, chain, tx hash")) }
        pageItem {
            FormSection(title = AppLocalization.string("Status")) {
                row { FormTextRow(app.pendingTransactionRefreshStatusText ?: AppLocalization.string("No refresh status yet")) }
                row { FormTextRow(app.networkSyncStatusText) }
                row { FormTextRow(summaryText(filteredLogs)) }
            }
        }
        pageItem {
            FormSection(title = AppLocalization.string("Filters")) {
                row {
                    FormPickerRow(
                        label = AppLocalization.string("Level"),
                        options = listOf<DiagnosticLogLevel?>(null) + diagnosticLogLevels,
                        selected = selectedLevel,
                        optionTitle = { it?.displayName ?: AppLocalization.string("All") },
                        onSelect = { selectedLevel = it },
                    )
                }
                row {
                    FormPickerRow(
                        label = AppLocalization.string("Category"),
                        options = categories,
                        selected = category,
                        optionTitle = { if (it == ALL_CATEGORIES) AppLocalization.string("All") else it },
                        onSelect = { selectedCategory = it },
                    )
                }
            }
        }
        if (filteredLogs.isEmpty()) {
            pageItem {
                FormSection(title = AppLocalization.string("Events")) {
                    row { FormTextRow(AppLocalization.string("No operational events yet.")) }
                }
            }
        } else {
            lazyRowGroup(
                items = filteredLogs,
                key = { it.id },
                title = AppLocalization.string("Events"),
                dividerInset = FormDividerInset,
            ) { log -> LogRow(log) }
        }
    }
    // The log is what a problem report is made from; clearing it is not undone.
    if (isConfirmingClear) {
        AlertDialog(
            onDismissRequest = { isConfirmingClear = false },
            title = { Text(AppLocalization.string("Clear every log entry?")) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingClear = false
                    diagnostics.clearOperationalLogs()
                }) { Text(AppLocalization.string("Clear"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingClear = false }) { Text(AppLocalization.string("Cancel")) } },
        )
    }
}

@Composable
private fun LogRow(log: DiagnosticLog) {
    val event = log.input
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            Icon(event.level.icon, contentDescription = event.level.displayName, tint = event.level.color, modifier = Modifier.size(18.dp))
            Text(
                formatUnixDate(log.timestampUnix, DateStyle.ABBREVIATED, TimeStyle.STANDARD),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = secondaryText,
            )
            Text(
                event.category,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = secondaryText,
                modifier = Modifier
                    .background(secondaryText.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = SpectraLayout.Space.xs, vertical = SpectraLayout.Space.xxs),
            )
        }
        Text(event.message, style = MaterialTheme.typography.bodyMedium)
        event.source?.takeIf { it.isNotEmpty() }?.let {
            Text(AppLocalization.format("source: %@", it), style = mono, color = secondaryText)
        }
        event.chainId?.displayName?.takeIf { it.isNotEmpty() }?.let {
            Text(AppLocalization.format("chain: %@", it), style = mono, color = secondaryText)
        }
        event.transactionHash?.takeIf { it.isNotEmpty() }?.let { hash ->
            SelectionContainer { Text(hash, style = mono, color = secondaryText) }
        }
    }
}

private fun filtered(logs: List<DiagnosticLog>, level: DiagnosticLogLevel?, category: String, searchText: String): List<DiagnosticLog> {
    val query = searchText.trim().lowercase()
    return logs.filter { log ->
        val event = log.input
        val levelMatches = level == null || event.level == level
        val categoryMatches = category == ALL_CATEGORIES || event.category == category
        val searchMatches = query.isEmpty() || listOf(
            event.message,
            event.category,
            event.chainId?.displayName ?: "",
            event.source ?: "",
            event.transactionHash ?: "",
        ).joinToString(" ").lowercase().contains(query)
        levelMatches && categoryMatches && searchMatches
    }
}

private fun summaryText(logs: List<DiagnosticLog>): String {
    fun count(level: DiagnosticLogLevel) = logs.count { it.input.level == level }
    return AppLocalization.format(
        "Showing %lld logs • D:%lld I:%lld W:%lld E:%lld",
        logs.size,
        count(DiagnosticLogLevel.DEBUG),
        count(DiagnosticLogLevel.INFO),
        count(DiagnosticLogLevel.WARNING),
        count(DiagnosticLogLevel.ERROR),
    )
}

private val DiagnosticLogLevel.icon: ImageVector
    get() = when (this) {
        DiagnosticLogLevel.DEBUG -> Icons.Outlined.BugReport
        DiagnosticLogLevel.INFO -> Icons.Outlined.Info
        DiagnosticLogLevel.WARNING -> Icons.Outlined.Warning
        DiagnosticLogLevel.ERROR -> Icons.Outlined.Report
    }

private val DiagnosticLogLevel.color: Color
    @Composable get() = when (this) {
        DiagnosticLogLevel.DEBUG -> SpectraColors.semantic.neutral
        DiagnosticLogLevel.INFO -> CatalogInfoBlue
        DiagnosticLogLevel.WARNING -> SpectraColors.semantic.warning
        DiagnosticLogLevel.ERROR -> SpectraColors.semantic.failure
    }

/** Info is the system blue on iOS: the catalog's blue, not the theme colour, so it reads apart from a link. */
private val CatalogInfoBlue: Color get() = CatalogColor.BLUE.color
