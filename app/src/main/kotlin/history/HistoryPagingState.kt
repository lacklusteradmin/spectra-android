package dev.spectra.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Paging through on-chain history: which wallets core says have pages left,
 * and whether a page is being fetched. Core owns the cursors and the stored
 * history; this holds what the history screen's "Load more" button reads.
 */
class HistoryPagingState {
    /** Wallets whose chain history has pages left, from core's transaction snapshot. */
    var walletsWithMoreHistory by mutableStateOf(emptySet<String>())
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    /** The only writer of [walletsWithMoreHistory], called with each transaction snapshot. */
    fun adopt(walletsWithMoreHistory: Set<String>) {
        if (this.walletsWithMoreHistory != walletsWithMoreHistory) this.walletsWithMoreHistory = walletsWithMoreHistory
    }

    fun canLoadMore(walletIds: Set<String>): Boolean =
        !isLoadingMore && walletIds.any { it in walletsWithMoreHistory }

    /** Fetch the next page for [walletIds] through [fetch], one fetch at a time. */
    suspend fun loadMore(walletIds: Set<String>, fetch: suspend (Set<String>) -> Unit) {
        if (isLoadingMore || walletIds.isEmpty()) return
        isLoadingMore = true
        try {
            fetch(walletIds)
        } finally {
            isLoadingMore = false
        }
    }
}
