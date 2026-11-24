package dev.spectra.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPill
import dev.spectra.components.breakableAnywhere
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.AssetPresentationCatalog
import dev.spectra.models.color
import dev.spectra.models.displayName
import dev.spectra.models.entry
import dev.spectra.models.hostsTokens
import uniffi.spectra_core.Chain
import uniffi.spectra_core.TokenPreferenceEntry

/** The chain hosting this token, when its network can hold tokens. */
internal val TokenPreferenceEntry.hostingChain: Chain? get() = token.chainId.takeIf { it.hostsTokens }

internal val TokenPreferenceEntry.settingsArtworkName: String get() = AssetPresentationCatalog.artwork(token.deploymentId)

/** The letters a badge without artwork shows. */
internal val TokenPreferenceEntry.settingsFallbackMark: String get() = token.symbol.trim().take(2).uppercase()

/** The badge's colour: the hosting chain's, or the theme colour. */
internal val TokenPreferenceEntry.settingsBadgeTint: Color
    @Composable get() = hostingChain?.entry?.color?.color ?: MaterialTheme.colorScheme.primary

/** One token, as the known-token list groups core's rows: every deployment of one token id. */
internal data class TokenRegistryGroup(
    val key: String,
    val name: String,
    val symbol: String,
    val entries: List<TokenPreferenceEntry>,
) {
    val representativeEntry: TokenPreferenceEntry get() = entries[0]
}

/** One token in the known-token list: the wiki's row, so the two libraries read alike. */
@Composable
internal fun TokenRegistryGroupRow(group: TokenRegistryGroup, modifier: Modifier = Modifier) {
    val representative = group.representativeEntry
    val places = if (group.entries.size == 1) {
        AppLocalization.format("dashboard.asset.onChain", representative.token.chainId.displayName)
    } else {
        AppLocalization.format("wiki.asset.onChains", group.entries.size.toString())
    }
    Row(
        modifier = modifier.spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(
            representative.settingsArtworkName,
            representative.settingsFallbackMark,
            representative.settingsBadgeTint,
            size = 36.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${group.symbol} · $places",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!representative.isBuiltIn) TokenSourceTag(isBuiltIn = false)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Whether a token ships with the app or was added by the user. */
@Composable
internal fun TokenSourceTag(isBuiltIn: Boolean) {
    SpectraPill(AppLocalization.string(if (isBuiltIn) "Built-In" else "Custom"))
}

/** One network a token lives on: chain and standard, then precision and identifier. */
@Composable
internal fun TokenRegistryNetworkRow(entry: TokenPreferenceEntry) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SpectraLayout.Space.xs),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            Text(
                entry.token.chainId.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(
                entry.token.tokenStandard?.displayName ?: "",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xxs),
            )
        }
        Row {
            Text(AppLocalization.string("Supported Decimals"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            Spacer(Modifier.weight(1f))
            Text(entry.token.decimals.toString(), style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
        if (entry.token.contract.isNotEmpty()) {
            val label = AppLocalization.string("Token Identifier")
            Text(
                breakableAnywhere(entry.token.contract),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}
