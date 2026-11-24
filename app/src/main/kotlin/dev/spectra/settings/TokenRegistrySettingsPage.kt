package dev.spectra.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLazyPage
import dev.spectra.components.SpectraSearchField
import dev.spectra.components.lazyRowGroup
import dev.spectra.components.pageItem
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import dev.spectra.models.displayName
import uniffi.spectra_core.Chain
import uniffi.spectra_core.TokenPreferenceEntry

/** Which tokens the list shows by where they came from. */
private enum class TokenSourceFilter(val key: String) {
    ALL("All"),
    BUILT_IN("Built-In"),
    CUSTOM("Custom"),
    ;

    val title: String get() = AppLocalization.string(key)
}

/** Every token the wallet knows — the catalog's and the user's — searchable and filtered. */
@Composable
internal fun TokenRegistrySettingsPage() {
    val tokens = LocalAppState.current.tokenPreferences
    val navigator = LocalNavigator.current
    var searchText by rememberSaveable { mutableStateOf("") }
    // `null` includes every chain.
    var chainFilter by rememberSaveable { mutableStateOf<Chain?>(null) }
    var sourceFilter by rememberSaveable { mutableStateOf(TokenSourceFilter.ALL) }
    val entries = tokens.entries
    val groups = remember(entries, chainFilter, sourceFilter, searchText) {
        filteredGroups(entries, chainFilter, sourceFilter, searchText)
    }
    SpectraLazyPage(
        title = AppLocalization.string("Known Tokens"),
        actions = {
            FilterMenu(
                chainFilter = chainFilter,
                sourceFilter = sourceFilter,
                onChainFilter = { chainFilter = it },
                onSourceFilter = { sourceFilter = it },
            )
            IconButton(onClick = { navigator.push(SettingsRoute.TokenForm()) }) {
                Icon(Icons.Outlined.Add, contentDescription = AppLocalization.string("New Token"))
            }
        },
    ) {
        pageItem {
            SpectraSearchField(searchText, { searchText = it }, AppLocalization.string("Search name, symbol, chain, or address"))
        }
        tokens.error?.let { error ->
            pageItem { DismissibleErrorNotice(error, onDismiss = { tokens.error = null }) }
        }
        when {
            groups.isNotEmpty() -> lazyRowGroup(groups, key = { it.key }) { group ->
                TokenRegistryGroupRow(
                    group,
                    modifier = Modifier.clickable(role = Role.Button) { navigator.push(Routes.TokenDetail(group.key)) },
                )
            }
            entries.isEmpty() -> pageItem {
                Box(Modifier.fillMaxWidth().padding(vertical = SpectraLayout.Space.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            searchText.isBlank() -> pageItem {
                SpectraEmptyStateCard(
                    title = AppLocalization.string("No matching tokens."),
                    message = AppLocalization.string("No known tokens match the selected filters."),
                    icon = Icons.Outlined.FilterList,
                )
            }
            else -> pageItem {
                UnavailableContent(
                    title = AppLocalization.string("No matches found"),
                    icon = Icons.Outlined.SearchOff,
                    message = AppLocalization.string("Try a different filter or search term."),
                )
            }
        }
    }
}

@Composable
private fun FilterMenu(
    chainFilter: Chain?,
    sourceFilter: TokenSourceFilter,
    onChainFilter: (Chain?) -> Unit,
    onSourceFilter: (TokenSourceFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = chainFilter != null || sourceFilter != TokenSourceFilter.ALL
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (isFiltered) Icons.Filled.FilterList else Icons.Outlined.FilterList,
                contentDescription = AppLocalization.string("Filters"),
                tint = if (isFiltered) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuHeader(AppLocalization.string("Network"))
            (listOf<Chain?>(null) + ChainCatalog.tokenHostingChains).forEach { chain ->
                CheckedMenuItem(chain?.displayName ?: AppLocalization.string("All"), chainFilter == chain) {
                    onChainFilter(chain)
                    expanded = false
                }
            }
            HorizontalDivider()
            MenuHeader(AppLocalization.string("Source"))
            TokenSourceFilter.entries.forEach { filter ->
                CheckedMenuItem(filter.title, sourceFilter == filter) {
                    onSourceFilter(filter)
                    expanded = false
                }
            }
            if (isFiltered) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(AppLocalization.string("Clear Filters")) },
                    onClick = {
                        onChainFilter(null)
                        onSourceFilter(TokenSourceFilter.ALL)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun MenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = secondaryText,
        modifier = Modifier.padding(horizontal = SpectraLayout.Space.l, vertical = SpectraLayout.Space.xs),
    )
}

@Composable
internal fun CheckedMenuItem(title: String, isChecked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        onClick = onClick,
        trailingIcon = if (isChecked) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}

/**
 * Core orders the list with one token's deployments together, so the groups
 * and their rows keep that order as they are.
 */
private fun filteredGroups(
    entries: List<TokenPreferenceEntry>,
    chainFilter: Chain?,
    sourceFilter: TokenSourceFilter,
    searchText: String,
): List<TokenRegistryGroup> {
    val grouped = entries.groupBy { it.token.tokenId }
    val groups = entries.map { it.token.tokenId }.distinct().mapNotNull { key ->
        val members = grouped[key] ?: return@mapNotNull null
        val representative = members.firstOrNull() ?: return@mapNotNull null
        TokenRegistryGroup(key, representative.token.name, representative.token.symbol, members)
    }
    val query = searchText.trim().lowercase()
    return groups.filter { group ->
        if (chainFilter != null && group.entries.none { it.token.chainId == chainFilter }) return@filter false
        when (sourceFilter) {
            TokenSourceFilter.ALL -> Unit
            TokenSourceFilter.BUILT_IN -> if (group.entries.none { it.isBuiltIn }) return@filter false
            TokenSourceFilter.CUSTOM -> if (group.entries.all { it.isBuiltIn }) return@filter false
        }
        if (query.isEmpty()) return@filter true
        val haystack = (
            listOf(group.symbol, group.name) + group.entries.flatMap { entry ->
                listOf(
                    entry.token.chainId.displayName,
                    entry.token.tokenStandard?.displayName ?: "",
                    entry.token.contract,
                    entry.token.coingeckoId,
                    entry.token.coinpaprikaId,
                )
            }
            ).joinToString(" ").lowercase()
        haystack.contains(query)
    }
}
