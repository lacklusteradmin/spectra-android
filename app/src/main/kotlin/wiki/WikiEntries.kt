package dev.spectra.wiki

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes

/** The wiki's own pages, which no other feature opens. */
object WikiRoutes {
    /** A coin's page, by core's token id. */
    data class AssetWiki(val tokenId: String) : Route
}

/** The crypto wiki, a coin's page and a chain's page in it. */
fun EntryProviderScope<Route>.wikiEntries() {
    entry<Routes.CryptoWiki> { CryptoWikiLibraryScreen() }
    entry<Routes.ChainWiki> { ChainWikiDetailScreen(it.chain) }
    entry<WikiRoutes.AssetWiki> { AssetWikiDetailScreen(it.tokenId) }
}
