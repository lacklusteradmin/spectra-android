package dev.spectra.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.formatUnixDate
import kotlinx.coroutines.launch

enum class AppNoticeSeverity {
    WARNING,
    ERROR,
    ;

    val tint: Color
        @Composable get() = when (this) {
            WARNING -> SpectraColors.semantic.warning
            ERROR -> SpectraColors.semantic.failure
        }

    val label: String
        get() = AppLocalization.string(
            when (this) {
                WARNING -> "Warning"
                ERROR -> "Error"
            },
        )
}

/** A notice's way forward: ask again, go where it is fixed, or put it away. */
sealed interface AppNoticeAction {
    /** Run the read that failed again. */
    class Retry(val run: suspend () -> Unit) : AppNoticeAction

    /** Where a network's endpoints are chosen. */
    data object OpenEndpoints : AppNoticeAction

    /** Seen, with nothing else to do. */
    class Dismiss(val run: () -> Unit) : AppNoticeAction
}

class AppNoticeItem(
    val title: String,
    val message: String,
    val severity: AppNoticeSeverity,
    val icon: ImageVector,
    /** When the chain last synced cleanly, in Unix seconds. */
    val timestampUnix: Double? = null,
    /** What can be done about it from the notices page, if anything. */
    val action: AppNoticeAction? = null,
) {
    /**
     * The notice's own words: `appNoticeItems` is recomputed on every read,
     * so a generated id would make every notice a new row each time.
     */
    val id: String get() = "$title\u001F$message"
}

/** Every active notice, each with its way forward. */
@Composable
internal fun AppNoticesScreen() {
    val app = LocalAppState.current
    val notices = app.appNoticeItems
    SpectraPage(title = AppLocalization.string("Notices")) {
        if (notices.isEmpty()) {
            SpectraCard {
                Column(
                    modifier = Modifier.padding(SpectraLayout.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                ) {
                    Text(AppLocalization.string("No active notices"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        AppLocalization.string("Current wallet, pricing, and chain-state warnings will appear here."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryText,
                    )
                }
            }
        } else {
            SpectraRowGroup(
                items = notices,
                title = AppLocalization.string("Active Notices"),
                dividerInset = SpectraLayout.rowHorizontal,
            ) { notice -> NoticeRow(notice) }
        }
    }
}

/**
 * A notice with its way forward: a row that opens where it is fixed, or a
 * text button under it.
 */
@Composable
private fun NoticeRow(notice: AppNoticeItem) {
    val navigator = LocalNavigator.current
    val scope = LocalAppState.current.scope
    when (val action = notice.action) {
        AppNoticeAction.OpenEndpoints -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { navigator.openEndpointSettings() }
                .spectraRowPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            DashboardNoticeCard(notice, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
        }
        is AppNoticeAction.Retry -> NoticeWithButton(notice, AppLocalization.string("Retry"), Icons.Outlined.Refresh) {
            scope.launch { action.run() }
        }
        is AppNoticeAction.Dismiss -> NoticeWithButton(notice, AppLocalization.string("Dismiss"), Icons.Outlined.Close) {
            action.run()
        }
        null -> DashboardNoticeCard(notice, Modifier.spectraRowPadding())
    }
}

@Composable
private fun NoticeWithButton(notice: AppNoticeItem, title: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier.spectraRowPadding(),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        DashboardNoticeCard(notice)
        TextButton(onClick = onClick) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            SpectraGap()
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** A notice's words: its icon and title, how bad it is, what happened and when the chain last synced. */
@Composable
internal fun DashboardNoticeCard(notice: AppNoticeItem, modifier: Modifier = Modifier) {
    val tint = notice.severity.tint
    Column(
        modifier = modifier.padding(vertical = SpectraLayout.Space.xs),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            Icon(notice.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(notice.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                notice.severity.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
            )
        }
        Text(notice.message, style = MaterialTheme.typography.bodyMedium)
        notice.timestampUnix?.let { timestamp ->
            Text(
                AppLocalization.format("Last known healthy sync: %@", formatUnixDate(timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )
        }
    }
}
