package dev.spectra.history

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import dev.spectra.app.AppState
import dev.spectra.models.HistorySortOrder
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import uniffi.spectra_core.HistoryQuery
import uniffi.spectra_core.HistoryQueryFilter
import uniffi.spectra_core.TransactionRecord

/**
 * One history list's query and the pages of it core has answered: the
 * History tab's, or one wallet's. View state — core owns the records and
 * the cursors — kept for as long as the list's page is on its tab's stack.
 */
@Stable
internal class HistoryListState(
    private val app: AppState,
    /** The one wallet the list shows; `null` lets the filter choose. */
    val fixedWalletId: String?,
) {
    var selectedFilter by mutableStateOf(HistoryQueryFilter.ALL)
    var selectedSortOrder by mutableStateOf(HistorySortOrder.NEWEST)
    var selectedWalletId by mutableStateOf(fixedWalletId)
    var hidesSmallAmounts by mutableStateOf(false)
    var searchText by mutableStateOf("")

    var pageRecords by mutableStateOf<List<TransactionRecord>>(emptyList())
        private set

    /**
     * A page has landed for the query on screen. Until then an empty list
     * is not an answer, and says nothing about matches.
     */
    var hasLoadedPage by mutableStateOf(false)
        private set
    var hasMoreStoredHistory by mutableStateOf(false)
        private set
    var pageError by mutableStateOf<String?>(null)
        private set
    var isLoadingPage by mutableStateOf(false)
        private set
    var loadedFilterKey by mutableStateOf<String?>(null)
        private set
    var isRetrying by mutableStateOf(false)

    private var nextCursor: String? = null
    private var pageRequestId = 0L

    /**
     * How many rows the list last showed. A page that comes back from a
     * transaction's page has lost its rows, so its first load asks for as
     * many as were on screen rather than snapping back to one page.
     */
    private var shownCount = 0

    /** Rows the next reload adds beyond those on screen; cleared once a reload lands. */
    private var pendingGrowth = 0

    val filterKey: String
        get() = "${selectedWalletId.orEmpty()}|$selectedFilter|$selectedSortOrder|$hidesSmallAmounts|$searchText"

    /** What a reload answers: the filter, and the revisions of the records and wallets it reads. */
    val queryKey: String
        get() = "$filterKey|${app.transactionRevision}|${app.walletIdentityRevision}"

    /** Anything the filter menu narrows the list by. */
    val filtersAreActive: Boolean
        get() = (fixedWalletId == null && selectedWalletId != null) ||
            selectedFilter != HistoryQueryFilter.ALL ||
            hidesSmallAmounts

    val historyError: String? get() = pageError ?: app.historyReadError

    private val historyWalletIds: Set<String>
        get() = selectedWalletId?.let { setOf(it) } ?: app.wallets.map { it.id }.toSet()

    val shouldShowPagingControls: Boolean
        get() = hasMoreStoredHistory || app.historyPaging.canLoadMore(historyWalletIds) || app.historyPaging.isLoadingMore

    /** A new query keeps the old rows until its answer lands, dimmed, rather than flashing an empty list. */
    val isShowingStaleQuery: Boolean get() = isLoadingPage && loadedFilterKey != filterKey

    fun clearFilters() {
        if (fixedWalletId == null) selectedWalletId = null
        selectedFilter = HistoryQueryFilter.ALL
        hidesSmallAmounts = false
        searchText = ""
    }

    /**
     * Read the query's next page, or from its start when [reset]. A reload
     * keeps every row already on screen, so a refresh or an on-chain fetch
     * does not snap the list back to its first page; a new filter starts
     * from one page. Only the newest request's answer is adopted.
     */
    suspend fun loadPage(reset: Boolean) {
        val key = queryKey
        val filter = filterKey
        val requestId = ++pageRequestId
        isLoadingPage = true
        try {
            val service = app.bridge.ready()
            val sameQuery = loadedFilterKey == filter
            val target = if (reset) maxOf(PAGE_SIZE, if (sameQuery) shownCount + pendingGrowth else 0) else PAGE_SIZE
            val records = mutableListOf<TransactionRecord>()
            var cursor = if (reset) null else nextCursor
            var hasMore: Boolean
            do {
                val page = service.historyPage(
                    HistoryQuery(
                        walletId = selectedWalletId,
                        filter = selectedFilter,
                        search = searchText,
                        oldestFirst = selectedSortOrder == HistorySortOrder.OLDEST,
                        cursor = cursor,
                        // Core caps a page; the loop follows its cursor for the rest.
                        limit = (target - records.size).toUInt(),
                        hideSmallAmounts = hidesSmallAmounts,
                    ),
                )
                currentCoroutineContext().ensureActive()
                if (pageRequestId != requestId || queryKey != key) return
                records += page.records
                cursor = page.nextCursor
                hasMore = page.hasMore
            } while (hasMore && records.size < target)
            if (reset) {
                pageRecords = records
                pendingGrowth = 0
            } else {
                val present = pageRecords.map { it.id }.toSet()
                pageRecords = pageRecords + records.filter { it.id !in present }
            }
            shownCount = pageRecords.size
            loadedFilterKey = filter
            hasLoadedPage = true
            nextCursor = cursor
            hasMoreStoredHistory = hasMore
            pageError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (pageRequestId != requestId || queryKey != key) return
            pageError = userErrorMessage(error)
            app.appendOperationalLog("History", error.toString())
        } finally {
            if (pageRequestId == requestId) isLoadingPage = false
        }
    }

    /**
     * Show more: the next stored page while there is one, otherwise the
     * next page fetched from the chain.
     */
    suspend fun loadMore() {
        if (!hasMoreStoredHistory) {
            app.loadMoreOnChainHistory(historyWalletIds)
            // The fetch bumps the transaction revision, so the screen's query
            // effect reloads too, in either order. Both read `pendingGrowth`,
            // so whichever lands last shows the extra page.
            pendingGrowth = PAGE_SIZE
            loadPage(reset = true)
        } else {
            loadPage(reset = false)
        }
    }

    companion object {
        private const val PAGE_SIZE = 20

        /**
         * Keeps the query and how far it was read while the list's page is
         * under another on its stack; the rows themselves are read again.
         */
        fun saver(app: AppState, fixedWalletId: String?): Saver<HistoryListState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.selectedFilter.name,
                    state.selectedSortOrder.name,
                    state.selectedWalletId.orEmpty(),
                    state.hidesSmallAmounts,
                    state.searchText,
                    state.loadedFilterKey.orEmpty(),
                    state.shownCount,
                )
            },
            restore = { saved ->
                HistoryListState(app, fixedWalletId).apply {
                    selectedFilter = HistoryQueryFilter.valueOf(saved[0] as String)
                    selectedSortOrder = HistorySortOrder.valueOf(saved[1] as String)
                    selectedWalletId = (saved[2] as String).ifEmpty { null }
                    hidesSmallAmounts = saved[3] as Boolean
                    searchText = saved[4] as String
                    loadedFilterKey = (saved[5] as String).ifEmpty { null }
                    shownCount = saved[6] as Int
                }
            },
        )
    }
}
