package dev.spectra.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLazyPage
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.components.pageItem
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.displayName
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.AppSettingUpdate
import uniffi.spectra_core.Chain
import uniffi.spectra_core.EndpointDirectoryEntry
import uniffi.spectra_core.endpointCapabilityId

/** Which endpoints the catalog shows by where they came from. */
private enum class EndpointSourceFilter(val key: String) {
    ALL("All"),
    BUILT_IN("Built-In"),
    CUSTOM("Custom"),
}

/**
 * Every endpoint Spectra asks, by network: the catalog's and the user's.
 * A custom endpoint can be edited or removed from its menu; a built-in one
 * is the catalog's.
 */
@Composable
internal fun EndpointCatalogSettingsPage() {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    var entries by remember { mutableStateOf<List<EndpointDirectoryEntry>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sourceFilter by rememberSaveable { mutableStateOf(EndpointSourceFilter.ALL) }
    val customEndpoints = app.appSettings.customEndpoints
    val onlyMine = app.appSettings.customEndpointsOnly
    LaunchedEffect(customEndpoints) {
        try {
            entries = app.bridge.ready().endpointDirectory()
            loadError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = userErrorMessage(error)
        }
    }

    fun visibleEntries(chain: Chain): List<EndpointDirectoryEntry> {
        val chains = CoreReferenceTables.endpointGroups(chain).map { it.chainId }.toSet() + chain
        return entries.filter { entry ->
            entry.record.chainId in chains &&
                (sourceFilter == EndpointSourceFilter.ALL || entry.isBuiltIn == (sourceFilter == EndpointSourceFilter.BUILT_IN))
        }
    }
    val sections = ChainCatalog.mainnets.mapNotNull { chain -> visibleEntries(chain).takeIf { it.isNotEmpty() }?.let { chain to it } }

    SpectraLazyPage(
        title = AppLocalization.string("endpoints.navigationTitle"),
        actions = {
            IconButton(onClick = { endpointNetworks.firstOrNull()?.let { navigator.push(Routes.AddCustomEndpoint(it)) } }) {
                Icon(Icons.Outlined.Add, contentDescription = AppLocalization.string("endpoints.addEndpointTitle"))
            }
            SourceFilterMenu(sourceFilter, onSelect = { sourceFilter = it })
        },
    ) {
        loadError?.let { error -> pageItem { FormSection { row { FormErrorRow(error) } } } }
        sections.forEach { (chain, rows) ->
            pageItem(key = chain) {
                FormSection(title = chain.displayName) {
                    val groups = CoreReferenceTables.endpointGroups(chain)
                    if (groups.size > 1) {
                        groups.forEach { group ->
                            val groupRows = rows.filter { it.record.chainId == group.chainId }
                            if (groupRows.isNotEmpty()) {
                                row {
                                    Text(
                                        group.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.spectraRowPadding(),
                                    )
                                }
                                groupRows.forEach { entry -> row { EndpointRow(entry) } }
                                if (offersOnlyMine(group.chainId, entries, onlyMine)) row { OnlyMineToggle(group.chainId) }
                            }
                        }
                    } else {
                        rows.forEach { entry -> row { EndpointRow(entry) } }
                        if (offersOnlyMine(chain, entries, onlyMine)) row { OnlyMineToggle(chain) }
                    }
                }
            }
        }
    }
}

/**
 * Only the user's endpoints on one network: offered once there is one to
 * use, since with none the network would have nothing to ask — or while it
 * is on, so it can be turned off.
 */
private fun offersOnlyMine(chain: Chain, entries: List<EndpointDirectoryEntry>, onlyMine: List<Chain>): Boolean =
    chain in onlyMine || entries.any { !it.isBuiltIn && it.record.chainId == chain }

@Composable
private fun OnlyMineToggle(chain: Chain) {
    val app = LocalAppState.current
    SpectraSwitchRow(
        title = AppLocalization.string("Use only my endpoints"),
        checked = chain in app.appSettings.customEndpointsOnly,
        onCheckedChange = { app.updateSetting(AppSettingUpdate.CustomEndpointsOnly(chain, it)) },
    )
}

/** An endpoint: its URL, its API and what it serves, and where it came from. */
@Composable
private fun EndpointRow(entry: EndpointDirectoryEntry) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    var isShowingMenu by remember { mutableStateOf(false) }
    val tags = listOf(entry.apiName) + entry.record.capabilities.map {
        AppLocalization.string("endpointCapability.${endpointCapabilityId(it)}")
    }
    Row(
        modifier = Modifier.spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        SelectionContainer(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(entry.record.endpoint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 3)
                Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = secondaryText)
                Text(
                    AppLocalization.string(if (entry.isBuiltIn) "Built-In" else "Custom"),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }
        }
        if (!entry.isBuiltIn) {
            Box {
                IconButton(onClick = { isShowingMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = AppLocalization.string("Actions"))
                }
                DropdownMenu(expanded = isShowingMenu, onDismissRequest = { isShowingMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(AppLocalization.string("Edit")) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            isShowingMenu = false
                            navigator.push(SettingsRoute.EditEndpoint(entry))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(AppLocalization.string("Remove"), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            isShowingMenu = false
                            app.updateSetting(AppSettingUpdate.RemoveCustomEndpoint(entry.record.chainId, entry.record.endpoint))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceFilterMenu(selected: EndpointSourceFilter, onSelect: (EndpointSourceFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selected != EndpointSourceFilter.ALL
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (isFiltered) Icons.Filled.FilterList else Icons.Outlined.FilterList,
                contentDescription = AppLocalization.string("Filters"),
                tint = if (isFiltered) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuHeader(AppLocalization.string("Source"))
            EndpointSourceFilter.entries.forEach { filter ->
                CheckedMenuItem(AppLocalization.string(filter.key), selected == filter) {
                    onSelect(filter)
                    expanded = false
                }
            }
        }
    }
}
