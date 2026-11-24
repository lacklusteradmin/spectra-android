package dev.spectra.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLazyPage
import dev.spectra.components.lazyRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.displayName
import uniffi.spectra_core.explorers

/**
 * The explorer each network's transactions and addresses open in.
 * Read-only: an explorer is a page Spectra links to, not a service it
 * requests.
 */
@Composable
internal fun ExplorerSettingsPage() {
    val explorers = remember { explorers() }
    SpectraLazyPage(title = AppLocalization.string("Explorers")) {
        lazyRowGroup(
            items = explorers,
            key = { it.chainId },
            dividerInset = FormDividerInset,
            footnote = AppLocalization.string("explorers.footer"),
        ) { explorer ->
            SelectionContainer {
                Column(
                    modifier = Modifier.spectraRowPadding(),
                    verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            explorer.chainId.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                        Text(explorer.name, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                    val urlStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    Text(explorer.txUrl, style = urlStyle, color = secondaryText, maxLines = 3)
                    explorer.addressUrl?.let { Text(it, style = urlStyle, color = secondaryText, maxLines = 3) }
                }
            }
        }
    }
}
