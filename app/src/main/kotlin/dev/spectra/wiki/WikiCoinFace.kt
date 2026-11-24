package dev.spectra.wiki

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPill
import dev.spectra.models.ChainCatalog
import dev.spectra.models.color
import dev.spectra.models.entry
import uniffi.spectra_core.AssetWikiEntry
import uniffi.spectra_core.ChainWikiEntry

/** Artwork and identity supplied by core, shared by coin and chain wiki pages. */
data class WikiCoinFace(val name: String, val symbol: String, val artworkName: String, val color: Color)

@Composable
internal fun WikiCoinBadge(face: WikiCoinFace, size: Dp) {
    CoinBadge(artworkName = face.artworkName, fallbackText = face.symbol, color = face.color, size = size)
}

/** A coin's catalog colour, or the theme's for a coin the catalog gives none. */
val AssetWikiEntry.accentColor: Color
    @Composable get() = color?.color ?: MaterialTheme.colorScheme.primary

val AssetWikiEntry.face: WikiCoinFace
    @Composable get() = WikiCoinFace(name = name, symbol = symbol, artworkName = artworkName, color = accentColor)

/** A chain draws the coin it runs on. */
val ChainWikiEntry.face: WikiCoinFace
    @Composable get() {
        val entry = ChainCatalog.chain(id)?.entry
        return WikiCoinFace(
            name = name,
            symbol = name,
            artworkName = entry?.artworkName ?: "",
            color = entry?.color?.color ?: MaterialTheme.colorScheme.primary,
        )
    }

/** A page's tags in a row that scrolls sideways when they outrun the card. */
@Composable
internal fun WikiTagRow(titles: List<String>, color: Color) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        titles.forEach { SpectraPill(it, color = color) }
    }
}
