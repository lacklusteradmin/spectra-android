package dev.spectra.wiki

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraSearchField
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.chainTagPickerOrder
import dev.spectra.models.formatNumber
import dev.spectra.models.title
import dev.spectra.models.tokenTagFilterOrder
import uniffi.spectra_core.AssetWikiEntry
import uniffi.spectra_core.ChainTag
import uniffi.spectra_core.TokenTag

// The wiki is indexed by coin. `listAssetWiki()` joins both catalogs so each
// coin has one page. Chains have their own section; where a coin is deployed
// is Known Tokens' job, so neither repeats it.

/** One filter over the whole library: what kind of coin, or what kind of chain. */
private sealed interface WikiTag {
    data class Token(val tag: TokenTag) : WikiTag
    data class Chain(val tag: ChainTag) : WikiTag

    val title: String
        get() = when (this) {
            is Token -> tag.title
            is Chain -> tag.title
        }

    /** The tag as a saved page keeps it: its kind and its name. */
    val key: String
        get() = when (this) {
            is Token -> "token:${tag.name}"
            is Chain -> "chain:${tag.name}"
        }

    companion object {
        val saver: Saver<WikiTag?, String> = Saver(
            save = { it?.key ?: "" },
            restore = { key ->
                when {
                    key.startsWith("token:") -> runCatching { Token(TokenTag.valueOf(key.removePrefix("token:"))) }.getOrNull()
                    key.startsWith("chain:") -> runCatching { Chain(ChainTag.valueOf(key.removePrefix("chain:"))) }.getOrNull()
                    else -> null
                }
            },
        )
    }
}

// ── Library ──────────────────────────────────────────────────────────────

/** Every coin and chain the wiki describes, searched and filtered by tag. */
@Composable
internal fun CryptoWikiLibraryScreen() {
    val navigator = LocalNavigator.current
    var searchText by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable(stateSaver = WikiTag.saver) { mutableStateOf<WikiTag?>(null) }
    val query = searchText.trim()
    fun matches(tags: List<WikiTag>, text: List<String>): Boolean {
        val tag = selectedTag
        if (tag != null && tag !in tags) return false
        if (query.isEmpty()) return true
        return (text + tags.map { it.title }).any { it.contains(query, ignoreCase = true) }
    }
    val coins = CoreReferenceTables.assetWiki.filter { entry ->
        matches(entry.tags.map { WikiTag.Token(it) }, listOf(entry.name, entry.symbol, entry.comment))
    }
    val chains = CoreReferenceTables.chainWiki.filter { chain ->
        matches(
            chain.tags.map { WikiTag.Chain(it) },
            listOf(chain.name, chain.comment, chain.family, chain.consensus, chain.stateModel),
        )
    }
    SpectraPage(
        title = AppLocalization.string("Crypto Wiki"),
        actions = { WikiTagMenu(selectedTag) { selectedTag = it } },
    ) {
        SpectraSearchField(searchText, { searchText = it }, AppLocalization.string("Search coins and chains"))
        if (coins.isNotEmpty()) {
            SpectraRowGroup(items = coins, title = AppLocalization.string("Coins"), trailing = formatNumber(coins.size)) { asset ->
                CryptoWikiRow(asset.face, asset.symbol) { navigator.push(WikiRoutes.AssetWiki(asset.tokenId)) }
            }
        }
        if (chains.isNotEmpty()) {
            SpectraRowGroup(items = chains, title = AppLocalization.string("Chains"), trailing = formatNumber(chains.size)) { chain ->
                CryptoWikiRow(chain.face, chain.family) {
                    ChainCatalog.chain(chain.id)?.let { navigator.push(Routes.ChainWiki(it)) }
                }
            }
        }
        if (coins.isEmpty() && chains.isEmpty()) {
            SpectraEmptyStateCard(
                title = AppLocalization.string("No matches found"),
                message = AppLocalization.string("Try a different filter or search term."),
                icon = Icons.Outlined.SearchOff,
            )
        }
    }
}

/**
 * Coin tags, then chain tags, as the sections are ordered; each in core's
 * order, and only the tags some row carries.
 */
private val availableWikiTags: List<WikiTag> by lazy {
    val coinTags = CoreReferenceTables.assetWiki.flatMap { it.tags }.toSet()
    val chainTags = CoreReferenceTables.chainWiki.flatMap { it.tags }.toSet()
    tokenTagFilterOrder.filter { it in coinTags }.map { WikiTag.Token(it) } +
        chainTagPickerOrder.filter { it in chainTags }.map { WikiTag.Chain(it) }
}

@Composable
private fun WikiTagMenu(selectedTag: WikiTag?, onSelect: (WikiTag?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (selectedTag == null) Icons.Outlined.FilterAlt else Icons.Filled.FilterAlt,
                contentDescription = AppLocalization.string("Filter by tag"),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                AppLocalization.string("Tag"),
                style = MaterialTheme.typography.labelMedium,
                color = secondaryText,
                modifier = Modifier.padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.Space.xs),
            )
            (listOf<WikiTag?>(null) + availableWikiTags).forEach { tag ->
                val isSelected = tag == selectedTag
                DropdownMenuItem(
                    text = { Text(tag?.title ?: AppLocalization.string("All")) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(tag)
                        expanded = false
                    },
                    modifier = Modifier.semantics { selected = isSelected },
                )
            }
        }
    }
}

/** A coin or a chain in the library: badge, name, one line under it. */
@Composable
private fun CryptoWikiRow(face: WikiCoinFace, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        WikiCoinBadge(face, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(face.name, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondaryText, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
    }
}

// ── Asset detail ─────────────────────────────────────────────────────────

/** A coin's page: what it is, its tags, and how much of it there can be. */
@Composable
internal fun AssetWikiDetailScreen(tokenId: String) {
    val asset = CoreReferenceTables.assetWiki.firstOrNull { it.tokenId == tokenId }
    SpectraPage(title = asset?.name ?: AppLocalization.string("Crypto Wiki")) {
        if (asset == null) {
            WikiPageMissingCard()
        } else {
            AssetWikiHeroCard(asset)
            if (asset.totalCirculationModel.isNotEmpty()) CirculationCard(asset)
        }
    }
}

@Composable
private fun AssetWikiHeroCard(asset: AssetWikiEntry) {
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                WikiCoinBadge(asset.face, size = 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    Text(asset.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        asset.symbol,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = secondaryText,
                    )
                }
            }
            Text(asset.comment, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
            if (asset.tags.isNotEmpty()) WikiTagRow(asset.tags.map { it.title }, asset.accentColor)
        }
    }
}

/** Supply caps belong to coins. */
@Composable
private fun CirculationCard(asset: AssetWikiEntry) {
    SpectraCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                Icon(
                    Icons.Outlined.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    AppLocalization.string("Circulation Model"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Text(
                asset.totalCirculationModel,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
                modifier = Modifier.padding(start = SpectraLayout.Space.xl + SpectraLayout.Space.xs),
            )
        }
    }
}

/** What a page opened for a coin or chain the wiki does not describe shows. */
@Composable
internal fun WikiPageMissingCard() {
    SpectraEmptyStateCard(
        title = AppLocalization.string("No wiki page"),
        message = AppLocalization.string("The Crypto Wiki has no page for this."),
        icon = Icons.AutoMirrored.Outlined.MenuBook,
    )
}
