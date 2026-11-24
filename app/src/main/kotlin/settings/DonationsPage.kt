package dev.spectra.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.components.CoinBadge
import dev.spectra.components.CopyRow
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainBadge
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.title

/** The project's donation addresses. A tap on a row copies its address. */
@Composable
internal fun DonationsPage() {
    SpectraPage(title = AppLocalization.string("donations.navigationTitle")) {
        SpectraCard(elevated = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                Text(
                    AppLocalization.string("donations.navigationTitle"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(AppLocalization.string("donations.heroSubtitle"), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
            }
        }
        // One card, a row per address. The whole row copies — what the
        // footer says a tap does — and says so for a moment.
        SpectraRowGroup(
            items = CoreReferenceTables.donationDestinations,
            title = AppLocalization.string("Addresses"),
            footer = {
                Text(
                    AppLocalization.string("Tap an address to copy it."),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    modifier = Modifier
                        .padding(horizontal = SpectraLayout.rowHorizontal)
                        .padding(bottom = SpectraLayout.Space.m),
                )
            },
        ) { destination ->
            val (artwork, color) = nativeChainBadge(destination.chainId) ?: ChainBadge.FALLBACK
            CopyRow(value = destination.address) {
                CoinBadge(artwork, destination.title, color, size = 36.dp)
                SpectraGap(SpectraLayout.Space.m)
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    Text(destination.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        destination.address,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        }
    }
}
