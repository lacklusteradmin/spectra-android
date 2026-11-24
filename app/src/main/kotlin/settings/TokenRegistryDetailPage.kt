package dev.spectra.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.TokenPreferenceEntry

/**
 * One known token, every deployment core lists under the route's token id:
 * its price sources, every network it is on, and removal for a custom one.
 * Opened from the known-token list and from an asset group on Home.
 */
@Composable
internal fun TokenRegistryDetailPage(route: Routes.TokenDetail) {
    val tokens = LocalAppState.current.tokenPreferences
    val navigator = LocalNavigator.current
    val groupEntries = tokens.entries.filter { it.token.tokenId == route.tokenId }
    val entry = groupEntries.firstOrNull()
    var isShowingRemoveConfirmation by remember { mutableStateOf(false) }
    // Leave once the token is gone — removed here, or by a reset — but not
    // for a list that has not loaded yet.
    var hadEntries by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(entry == null) {
        if (entry != null) {
            hadEntries = true
        } else if (hadEntries) {
            navigator.dismiss(route)
        }
    }
    LaunchedEffect(Unit) { tokens.error = null }

    if (entry == null) {
        SpectraPage(title = "") {
            UnavailableContent(AppLocalization.string("Token Not Found"), Icons.AutoMirrored.Outlined.HelpOutline)
        }
        return
    }
    SpectraPage(
        title = entry.token.symbol,
        actions = {
            if (!entry.isBuiltIn) {
                TextButton(onClick = { navigator.push(SettingsRoute.TokenForm(entry)) }) {
                    Text(AppLocalization.string("Edit"))
                }
            }
        },
    ) {
        HeroCard(entry)
        tokens.error?.let { DismissibleErrorNotice(it, onDismiss = { tokens.error = null }) }
        SpectraDetailCard(title = AppLocalization.string("Price Sources")) {
            ProviderRow("CoinGecko", entry.token.coingeckoId)
            SpectraDivider()
            ProviderRow("CoinPaprika", entry.token.coinpaprikaId)
        }
        SpectraDetailCard(title = AppLocalization.string("Networks")) {
            groupEntries.forEachIndexed { index, network ->
                TokenRegistryNetworkRow(network)
                if (index < groupEntries.lastIndex) SpectraDivider()
            }
        }
        if (!entry.isBuiltIn) {
            OutlinedButton(
                onClick = { isShowingRemoveConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap()
                Text(AppLocalization.string("Remove Token"))
            }
        }
    }
    if (isShowingRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { isShowingRemoveConfirmation = false },
            title = { Text(AppLocalization.string("Remove Token")) },
            text = { Text(AppLocalization.string("This custom token will be removed and will no longer appear in your portfolio.")) },
            confirmButton = {
                TextButton(onClick = {
                    isShowingRemoveConfirmation = false
                    tokens.removeCustom(entry)
                }) { Text(AppLocalization.string("Remove"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { isShowingRemoveConfirmation = false }) { Text(AppLocalization.string("Cancel")) }
            },
        )
    }
}

@Composable
private fun HeroCard(entry: TokenPreferenceEntry) {
    SpectraCard(elevated = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            CoinBadge(entry.settingsArtworkName, entry.settingsFallbackMark, entry.settingsBadgeTint, size = 52.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(entry.token.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    entry.token.symbol,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = secondaryText,
                )
            }
            TokenSourceTag(entry.isBuiltIn)
        }
    }
}

/** A price provider's id for the token, or that none is set. */
@Composable
private fun ProviderRow(name: String, id: String) {
    SpectraValueRow(
        label = name,
        value = id.ifEmpty { AppLocalization.string("Not Configured") },
        valueColor = if (id.isEmpty()) secondaryText else MaterialTheme.colorScheme.onSurface,
    )
}
