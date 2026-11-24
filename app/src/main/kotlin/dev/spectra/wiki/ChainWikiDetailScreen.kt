package dev.spectra.wiki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.displayName
import dev.spectra.models.id
import dev.spectra.models.title
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainWikiEntry

/**
 * A chain's own page, one level down from the coin that runs on it.
 *
 * The wiki is indexed by coin. This is what has no coin to belong to: ten
 * chains share ETH, so "Base is an optimistic rollup" cannot live on ETH's
 * page. Reached from the wiki's Chains section and a held asset's lives-on
 * rows.
 */
@Composable
internal fun ChainWikiDetailScreen(chain: Chain) {
    val entry = CoreReferenceTables.chainWiki.firstOrNull { it.id == chain.id }
    SpectraPage(title = entry?.name ?: chain.displayName) {
        if (entry == null) {
            WikiPageMissingCard()
        } else {
            ChainWikiHeroCard(entry)
            ChainWikiIdentityCard(entry)
        }
    }
}

@Composable
private fun ChainWikiHeroCard(chain: ChainWikiEntry) {
    val face = chain.face
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                WikiCoinBadge(face, size = 52.dp)
                Text(chain.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Text(chain.comment, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
            if (chain.tags.isNotEmpty()) WikiTagRow(chain.tags.map { it.title }, face.color)
        }
    }
}

@Composable
private fun ChainWikiIdentityCard(chain: ChainWikiEntry) {
    SpectraCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            SpectraValueRow(label = AppLocalization.string("Family"), value = chain.family, icon = Icons.Outlined.Link)
            SpectraDivider()
            SpectraValueRow(label = AppLocalization.string("Consensus"), value = chain.consensus, icon = Icons.Outlined.VerifiedUser)
            SpectraDivider()
            SpectraValueRow(label = AppLocalization.string("State Model"), value = chain.stateModel, icon = Icons.Outlined.Storage)
        }
    }
}
