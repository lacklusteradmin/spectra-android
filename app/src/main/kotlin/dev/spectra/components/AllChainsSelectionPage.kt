package dev.spectra.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import dev.spectra.models.AppLocalization
import dev.spectra.models.chainTagPickerOrder
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainTag

/**
 * Every chain in one list, ordered by popularity or name and narrowed by a
 * tag filter and a search: iOS's `AllChainsSelectionView`. Test networks join
 * the list from the app bar's menu, beside the sort: an option for the few
 * who want them, not a card every user reads past. The Chain Info sheet says
 * what the symbol on each row is.
 *
 * The caller passes [descriptors] in popular order and keeps the search
 * [query], so a page drawn in place of the one that asked keeps it; [onBack]
 * is that page's way back. [selectedChains] are checked; with [disclosure]
 * every row ends in a chevron instead, for a list whose choice opens a page.
 * [footer] sits under the list. Built lazily: the catalog runs to hundreds
 * of rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllChainsSelectionPage(
    title: String,
    descriptors: List<ChainSelectionDescriptor>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (Chain) -> Unit,
    modifier: Modifier = Modifier,
    selectedChains: Set<Chain> = emptySet(),
    disclosure: Boolean = false,
    onBack: (() -> Unit)? = rememberBackAction(),
    footer: (@Composable () -> Unit)? = null,
) {
    var order by rememberSaveable { mutableStateOf(ChainPickerOrder.POPULAR) }
    var filterTag by rememberSaveable { mutableStateOf<ChainTag?>(null) }
    var showsTestNetworks by rememberSaveable { mutableStateOf(false) }
    var isShowingMenu by remember { mutableStateOf(false) }
    var isShowingInfo by rememberSaveable { mutableStateOf(false) }
    val filter = ChainPickerFilter(filterTag)
    val trimmedQuery = query.trim()
    val rows = descriptors.picked(filter, trimmedQuery, order, showsTestNetworks)
    // "All", then every tag some row carries. Test networks are the menu's
    // switch, not a filter.
    val filters = remember(descriptors) {
        listOf(ChainPickerFilter.ALL) + chainTagPickerOrder
            .filter { tag -> tag != ChainTag.TESTNET && descriptors.any { tag in it.tags } }
            .map { ChainPickerFilter(it) }
    }
    SpectraLazyPage(
        title = title,
        modifier = modifier,
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { isShowingMenu = true }) {
                    Icon(Icons.Outlined.FilterList, contentDescription = AppLocalization.string("Sort and Filter"))
                }
                DropdownMenu(expanded = isShowingMenu, onDismissRequest = { isShowingMenu = false }) {
                    Text(
                        AppLocalization.string("Sort"),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryText,
                        modifier = Modifier.padding(horizontal = SpectraLayout.Space.l, vertical = SpectraLayout.Space.xs),
                    )
                    SortItem(AppLocalization.string("Popular"), Icons.Outlined.Whatshot, order == ChainPickerOrder.POPULAR) {
                        order = ChainPickerOrder.POPULAR
                        isShowingMenu = false
                    }
                    SortItem(AppLocalization.string("Name"), Icons.AutoMirrored.Outlined.Sort, order == ChainPickerOrder.NAME) {
                        order = ChainPickerOrder.NAME
                        isShowingMenu = false
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(AppLocalization.string("Show test networks")) },
                        onClick = { showsTestNetworks = !showsTestNetworks },
                        leadingIcon = {
                            Icon(
                                if (showsTestNetworks) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
            IconButton(onClick = { isShowingInfo = true }) {
                Icon(Icons.Outlined.Info, contentDescription = AppLocalization.string("Chain Info"))
            }
        },
    ) {
        pageItem { SpectraSearchField(query, onQueryChange, AppLocalization.string("import_flow.search_chains")) }
        pageItem {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                contentPadding = PaddingValues(horizontal = SpectraLayout.Space.xxs),
            ) {
                items(filters, key = { it.tag?.name ?: "all" }) { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filterTag = item.tag },
                        label = {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        },
                    )
                }
            }
        }
        when {
            rows.isNotEmpty() -> lazyRowGroup(rows, key = { it.chain }) { descriptor ->
                ChainSelectionRow(
                    descriptor = descriptor,
                    isSelected = descriptor.chain in selectedChains,
                    onClick = { onPick(descriptor.chain) },
                    disclosure = disclosure,
                )
            }
            trimmedQuery.isNotEmpty() -> pageItem {
                SpectraEmptyStateCard(
                    title = AppLocalization.format("No Results for “%@”", trimmedQuery),
                    message = AppLocalization.string("Check the spelling or try a new search."),
                    icon = Icons.Outlined.Search,
                )
            }
            else -> pageItem {
                SpectraEmptyStateCard(
                    title = AppLocalization.string("import_flow.no_chains_selected"),
                    message = "",
                    icon = Icons.Outlined.CheckCircle,
                )
            }
        }
        if (footer != null) pageItem { footer() }
    }
    if (isShowingInfo) {
        ModalBottomSheet(onDismissRequest = { isShowingInfo = false }) {
            ChainInfoSheet(onDone = { isShowingInfo = false })
        }
    }
}

@Composable
private fun SortItem(title: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isSelected) {
            { Icon(Icons.Outlined.Check, contentDescription = AppLocalization.string("Selected"), tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        onClick = onClick,
    )
}

/** What the symbol on each row is, and where to ask for a chain the list lacks. */
@Composable
private fun ChainInfoSheet(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpectraLayout.screenHorizontal)
            .padding(bottom = SpectraLayout.screenBottom)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppLocalization.string("Chain Info"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text(AppLocalization.string("Done")) }
        }
        ChainInfoBlock(
            Icons.Outlined.LocalGasStation,
            AppLocalization.string("Gas Token"),
            AppLocalization.string(
                "The symbol shown on the right of each chain is its gas token — the asset you need to pay transaction fees.",
            ),
        )
        ChainInfoBlock(
            Icons.Outlined.Layers,
            AppLocalization.string("L2s and Native Tokens"),
            AppLocalization.string(
                "Some L2 chains have a separate native token (e.g. ARB, OP) but use a different asset for gas fees (e.g. ETH). Spectra shows the gas token since that's what you'll need to keep funded for transactions.",
            ),
        )
        ChainInfoBlock(
            Icons.Outlined.AddCircleOutline,
            AppLocalization.string("Missing a Chain?"),
            AppLocalization.string(
                "If you'd like a chain added, go to Settings → Report a Problem and let the developer know. New chains are added regularly.",
            ),
        )
    }
}

@Composable
private fun ChainInfoBlock(icon: ImageVector, title: String, text: String) {
    SpectraCard(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
        Column(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
    }
}
