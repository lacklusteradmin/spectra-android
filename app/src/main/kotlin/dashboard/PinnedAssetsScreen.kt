package dev.spectra.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraSearchField
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.AssetPresentationCatalog
import uniffi.spectra_core.DashboardPinOption

/** Which assets Home lists even with nothing held, searched and switched one by one. */
@Composable
internal fun PinnedAssetsScreen() {
    val app = LocalAppState.current
    var searchText by rememberSaveable { mutableStateOf("") }
    var isConfirmingReset by rememberSaveable { mutableStateOf(false) }
    val query = searchText.trim()
    val options = app.dashboardPinOptions.let { all ->
        if (query.isEmpty()) {
            all
        } else {
            all.filter { option ->
                option.symbol.contains(query, ignoreCase = true) ||
                    option.name.contains(query, ignoreCase = true) ||
                    option.subtitle.contains(query, ignoreCase = true)
            }
        }
    }
    SpectraPage(
        title = AppLocalization.string("Pinned Assets"),
        actions = {
            TextButton(onClick = { isConfirmingReset = true }) { Text(AppLocalization.string("Reset")) }
        },
    ) {
        SpectraSearchField(searchText, { searchText = it }, AppLocalization.string("Search assets"))
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            SpectraRowGroup(items = options, title = AppLocalization.string("Pinned Assets")) { option ->
                PinnedAssetRow(
                    option = option,
                    subtitleText = AppLocalization.format("dashboard.pinnedAsset.symbolSubtitle", option.symbol, option.subtitle),
                    onPinnedChange = { app.setDashboardAssetPinned(it, option.tokenId) },
                )
            }
            Text(
                AppLocalization.string("Pinned assets stay in the Assets list even when their balance is zero."),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                modifier = Modifier.padding(horizontal = SpectraLayout.rowHorizontal),
            )
        }
    }
    if (isConfirmingReset) {
        AlertDialog(
            onDismissRequest = { isConfirmingReset = false },
            title = { Text(AppLocalization.string("Pin only the default assets again?")) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingReset = false
                    app.resetPinnedDashboardAssets()
                }) {
                    Text(AppLocalization.string("Reset Pins"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingReset = false }) { Text(AppLocalization.string("Cancel")) }
            },
        )
    }
}

/** The catalog colour of the deployment a pin option names. */
val DashboardPinOption.color: Color get() = AssetPresentationCatalog.color(deploymentId)

@Composable
private fun PinnedAssetRow(option: DashboardPinOption, subtitleText: String, onPinnedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .toggleable(value = option.isPinned, role = Role.Switch, onValueChange = onPinnedChange)
            .spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(artworkName = option.artworkName, fallbackText = option.symbol, color = option.color, size = 34.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(option.name, style = MaterialTheme.typography.bodyLarge)
            Text(subtitleText, style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
        Switch(checked = option.isPinned, onCheckedChange = null)
    }
}
