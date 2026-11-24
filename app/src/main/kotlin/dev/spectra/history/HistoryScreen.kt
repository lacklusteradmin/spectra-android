package dev.spectra.history

import android.icu.text.DateFormat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.MainAppTab
import dev.spectra.app.Routes
import dev.spectra.app.performUserInitiatedRefresh
import dev.spectra.app.refreshTransactionProjection
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraSearchField
import dev.spectra.components.SpectraShimmer
import dev.spectra.components.isAccessibilityTextSize
import dev.spectra.components.rememberNotice
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.HistorySortOrder
import dev.spectra.models.amountColor
import dev.spectra.models.amountSign
import dev.spectra.models.artworkName
import dev.spectra.models.createdAtMillis
import dev.spectra.models.hasKnownDate
import dev.spectra.models.historyFilterOrder
import dev.spectra.models.localizedTitle
import dev.spectra.models.timestampText
import dev.spectra.models.titleText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.spectra_core.TransactionRecord
import uniffi.spectra_core.TransactionStatus
import uniffi.spectra_core.historySmallAmountThreshold
import java.util.Calendar

/**
 * Every wallet's history, or one wallet's: the History tab, and the list a
 * wallet's page opens.
 */
@Composable
internal fun HistoryListScreen(walletId: String?) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val state = rememberSaveable(walletId, saver = HistoryListState.saver(app, walletId)) {
        HistoryListState(app, walletId)
    }
    val queryKey = state.queryKey
    LaunchedEffect(queryKey) {
        // Typing searches once it pauses, not on every keystroke.
        val loaded = state.loadedFilterKey
        if (loaded != null && loaded != state.filterKey && state.searchText.isNotEmpty()) delay(250)
        state.loadPage(reset = true)
    }
    SpectraPage(
        title = AppLocalization.string("History"),
        large = walletId == null,
        actions = { HistoryFilterMenu(state, app) },
        onRefresh = { app.performUserInitiatedRefresh() },
    ) {
        val notice = rememberNotice()
        // What a tap sets off in core — a fetch, a recheck, a rebroadcast —
        // runs on the app's scope and finishes even if the page is left.
        val scope = app.scope
        SpectraSearchField(state.searchText, { state.searchText = it }, AppLocalization.string("Search history"))
        state.historyError?.let { error ->
            HistoryErrorCard(error, isRetrying = state.isRetrying) {
                state.isRetrying = true
                scope.launch {
                    try {
                        app.refreshTransactionProjection()
                        state.loadPage(reset = true)
                        app.performUserInitiatedRefresh()
                    } finally {
                        state.isRetrying = false
                    }
                }
            }
        }
        if (!state.hasLoadedPage && state.historyError == null) {
            HistoryLoadingPlaceholder()
        } else if (state.pageRecords.isEmpty() && state.historyError == null) {
            HistoryEmptyStateCard(app, state) { navigator.push(MainAppTab.HOME, Routes.AddWallet) }
        }
        val sections = remember(state.pageRecords, state.selectedSortOrder) {
            groupedSections(state.pageRecords, state.selectedSortOrder)
        }
        val amounts = app.amounts
        val hidesBalance = app.preferences.hideBalances
        if (sections.isNotEmpty()) Column(
            modifier = Modifier.alpha(if (state.isShowingStaleQuery) 0.5f else 1f),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.sectionSpacing),
        ) {
            sections.forEach { section ->
                SpectraRowGroup(items = section.records, title = section.group.title) { transaction ->
                    HistoryTransactionRow(
                        transaction = transaction,
                        amountText = transaction.amountSign + amounts.formattedTransactionAmount(transaction),
                        timeText = transaction.timestampText(showsDate = section.group.showsDate),
                        hidesBalance = hidesBalance,
                        onOpen = { navigator.push(Routes.TransactionDetail(transaction.id)) },
                        onRecheck = { scope.launch { notice(app.retryUTXOTransactionStatus(transaction.id)) } },
                        onRebroadcast = { scope.launch { notice(app.rebroadcastSignedTransaction(transaction.id)) } },
                    )
                }
            }
        }
        if (state.shouldShowPagingControls) {
            val isLoadingMore = app.historyPaging.isLoadingMore
            FilledTonalButton(
                onClick = { scope.launch { state.loadMore() } },
                enabled = !isLoadingMore && !state.isLoadingPage,
                modifier = Modifier.fillMaxWidth().heightIn(min = SpectraLayout.rowMinHeight),
            ) {
                if (isLoadingMore) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    SpectraGap()
                }
                Text(AppLocalization.string(if (isLoadingMore) "Loading" else "Load more"))
            }
        }
    }
}

@Composable
private fun HistoryErrorCard(error: String, isRetrying: Boolean, onRetry: () -> Unit) {
    SpectraCard {
        Column(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                Text(AppLocalization.string("Unable to load history"), style = MaterialTheme.typography.titleMedium)
            }
            Text(error, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
            FilledTonalButton(onClick = onRetry, enabled = !isRetrying) { Text(AppLocalization.string("Retry")) }
        }
    }
}

@Composable
private fun HistoryLoadingPlaceholder() {
    val loading = AppLocalization.string("Loading")
    SpectraCard {
        Column(
            modifier = Modifier
                .padding(SpectraLayout.cardPadding)
                .clearAndSetSemantics { contentDescription = loading },
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            repeat(4) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                    Box(Modifier.size(36.dp).spectraInsetFill(cornerRadius = 18.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                        SpectraShimmer(modifier = Modifier.widthIn(max = 150.dp), height = 14.dp)
                        SpectraShimmer(modifier = Modifier.widthIn(max = 90.dp), height = 10.dp)
                    }
                    SpectraShimmer(modifier = Modifier.width(70.dp), height = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyStateCard(app: AppState, state: HistoryListState, onAddWallet: () -> Unit) {
    when {
        app.wallets.isEmpty() -> SpectraEmptyStateCard(
            title = AppLocalization.string("No activity yet"),
            message = AppLocalization.string("Add a wallet to see its activity here."),
            icon = Icons.Outlined.History,
            actionTitle = AppLocalization.string("Add Wallet"),
            actionIcon = Icons.Outlined.Add,
            action = onAddWallet,
        )
        state.filtersAreActive || state.searchText.isNotEmpty() -> SpectraEmptyStateCard(
            title = AppLocalization.string("No matches found"),
            message = AppLocalization.string("Try a different filter or search term."),
            icon = Icons.Outlined.SearchOff,
            actionTitle = AppLocalization.string("Clear Filters"),
            actionIcon = Icons.Outlined.HighlightOff,
            action = state::clearFilters,
        )
        else -> SpectraEmptyStateCard(
            title = AppLocalization.string("No activity yet"),
            message = AppLocalization.string("Send funds or receive funds to build a persistent transaction log."),
            icon = Icons.Outlined.History,
        )
    }
}

/** The wallet, type, sort and size the list is narrowed to, from the top bar. */
@Composable
private fun HistoryFilterMenu(state: HistoryListState, app: AppState) {
    var expanded by remember { mutableStateOf(false) }
    val active = state.filtersAreActive
    val filtered = AppLocalization.string("Filtered")
    val threshold = remember { AmountPresentation.localizedDecimal(historySmallAmountThreshold()) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { if (active) stateDescription = filtered },
        ) {
            Icon(
                if (active) Icons.Filled.FilterAlt else Icons.Outlined.FilterAlt,
                contentDescription = AppLocalization.string("Filter history"),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fun choose(apply: () -> Unit) {
                apply()
                expanded = false
            }
            if (state.fixedWalletId == null) {
                MenuSectionTitle(AppLocalization.string("Wallet"))
                MenuChoice(AppLocalization.string("All Wallets"), state.selectedWalletId == null) {
                    choose { state.selectedWalletId = null }
                }
                app.wallets.forEach { wallet ->
                    MenuChoice(wallet.name, state.selectedWalletId == wallet.id) { choose { state.selectedWalletId = wallet.id } }
                }
                HorizontalDivider()
            }
            MenuSectionTitle(AppLocalization.string("Type"))
            historyFilterOrder.forEach { filter ->
                MenuChoice(filter.localizedTitle, state.selectedFilter == filter) { choose { state.selectedFilter = filter } }
            }
            HorizontalDivider()
            MenuSectionTitle(AppLocalization.string("Sort"))
            HistorySortOrder.entries.forEach { order ->
                MenuChoice(order.localizedTitle, state.selectedSortOrder == order) { choose { state.selectedSortOrder = order } }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Column {
                        Text(AppLocalization.string("Hide small amounts"))
                        Text(
                            AppLocalization.format("Below %@", threshold),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                        )
                    }
                },
                trailingIcon = if (state.hidesSmallAmounts) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = { choose { state.hidesSmallAmounts = !state.hidesSmallAmounts } },
                modifier = Modifier.semantics { selected = state.hidesSmallAmounts },
            )
            if (active) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(AppLocalization.string("Clear Filters")) },
                    leadingIcon = { Icon(Icons.Outlined.HighlightOff, contentDescription = null) },
                    onClick = { choose(state::clearFilters) },
                )
            }
        }
    }
}

@Composable
private fun MenuSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = secondaryText,
        modifier = Modifier.padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.Space.xs),
    )
}

/** One choice in a menu group: the chosen one ends in a check. */
@Composable
private fun MenuChoice(title: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        trailingIcon = if (isSelected) {
            { Icon(Icons.Outlined.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier.semantics { selected = isSelected },
    )
}

// ── Rows ─────────────────────────────────────────────────────────────────

/**
 * What the transaction did, then the amount it moved: a stake reads as a
 * stake, not as a send, and a fee-only operation still names itself. A long
 * press offers the recheck and rebroadcast core allows for it.
 */
@Composable
private fun HistoryTransactionRow(
    transaction: TransactionRecord,
    amountText: String,
    timeText: String,
    hidesBalance: Boolean,
    onOpen: () -> Unit,
    onRecheck: () -> Unit,
    onRebroadcast: () -> Unit,
) {
    var showsActions by remember { mutableStateOf(false) }
    val canRecheck = transaction.actions.recheckUnavailableReason == null
    val canRebroadcast = transaction.actions.rebroadcastUnavailableReason == null
    val amountColor = transaction.amountColor
    Box {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = if (canRecheck || canRebroadcast) ({ showsActions = true }) else null,
                )
                .spectraRowPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            CoinBadge(
                artworkName = transaction.artworkName,
                fallbackText = transaction.symbol,
                color = transaction.badgeColor,
                size = 36.dp,
            )
            if (isAccessibilityTextSize()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    HistoryTitleLine(transaction)
                    HistoryAmount(amountText, hidesBalance, amountColor)
                    Text(transaction.walletName, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = secondaryText)
                }
            } else {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    HistoryTitleLine(transaction)
                    Text(transaction.walletName, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    HistoryAmount(amountText, hidesBalance, amountColor, maxLines = 1)
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = secondaryText)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
        }
        DropdownMenu(expanded = showsActions, onDismissRequest = { showsActions = false }) {
            if (canRecheck) {
                DropdownMenuItem(
                    text = { Text(AppLocalization.string("Recheck")) },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = {
                        showsActions = false
                        onRecheck()
                    },
                )
            }
            if (canRebroadcast) {
                DropdownMenuItem(
                    text = { Text(AppLocalization.string("Rebroadcast")) },
                    leadingIcon = { Icon(Icons.Outlined.CellTower, contentDescription = null) },
                    onClick = {
                        showsActions = false
                        onRebroadcast()
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryTitleLine(transaction: TransactionRecord) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Text(
            transaction.titleText,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false),
        )
        // A confirmed row says nothing more; only the exceptions do.
        if (transaction.status != TransactionStatus.CONFIRMED) {
            TransactionStatusBadge(transaction.status, compact = true)
        }
    }
}

@Composable
private fun HistoryAmount(text: String, hidesBalance: Boolean, color: Color, maxLines: Int = Int.MAX_VALUE) {
    BalanceText(
        text,
        isHidden = hidesBalance,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = maxLines,
    )
}

// ── Date groups ──────────────────────────────────────────────────────────

/**
 * Where a row sits in time, newest first: the unconfirmed with no date,
 * today, yesterday, then one group per month.
 */
private sealed class HistoryDateGroup(private val rank: Int, private val order: Int) : Comparable<HistoryDateGroup> {
    data object Unconfirmed : HistoryDateGroup(0, 0)
    data object Today : HistoryDateGroup(1, 0)
    data object Yesterday : HistoryDateGroup(2, 0)
    data class Month(val year: Int, val month: Int) : HistoryDateGroup(3, -(year * 12 + month))

    /** Newest first. */
    override fun compareTo(other: HistoryDateGroup): Int = compareValuesBy(this, other, { it.rank }, { it.order })

    val title: String
        get() = when (this) {
            Unconfirmed -> AppLocalization.string("Unconfirmed")
            Today -> AppLocalization.string("Today")
            Yesterday -> AppLocalization.string("Yesterday")
            is Month -> {
                val date = Calendar.getInstance().apply {
                    clear()
                    set(year, month, 1)
                }
                DateFormat.getInstanceForSkeleton("yMMMM", AppLocalization.locale).format(date.time)
            }
        }

    /** Inside today and yesterday the header gives the day; a month header gives only the month. */
    val showsDate: Boolean
        get() = when (this) {
            Today, Yesterday -> false
            Unconfirmed, is Month -> true
        }

    companion object {
        fun of(transaction: TransactionRecord, today: Calendar): HistoryDateGroup {
            if (!transaction.hasKnownDate && transaction.status == TransactionStatus.PENDING) return Unconfirmed
            val date = Calendar.getInstance().apply { timeInMillis = transaction.createdAtMillis }
            val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            return when {
                date.isSameDay(today) -> Today
                date.isSameDay(yesterday) -> Yesterday
                else -> Month(date.get(Calendar.YEAR), date.get(Calendar.MONTH))
            }
        }

        private fun Calendar.isSameDay(other: Calendar): Boolean =
            get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }
}

private class HistorySection(val group: HistoryDateGroup, val records: List<TransactionRecord>)

/** The page's rows by date group, in the order the list is sorted; each group keeps core's order inside it. */
private fun groupedSections(records: List<TransactionRecord>, sortOrder: HistorySortOrder): List<HistorySection> {
    val today = Calendar.getInstance()
    val grouped = records.groupBy { HistoryDateGroup.of(it, today) }
    val keys = grouped.keys.sorted()
    val order = if (sortOrder == HistorySortOrder.NEWEST) keys else keys.reversed()
    return order.mapNotNull { group -> grouped[group]?.takeIf { it.isNotEmpty() }?.let { HistorySection(group, it) } }
}
