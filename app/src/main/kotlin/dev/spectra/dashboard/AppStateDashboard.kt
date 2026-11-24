package dev.spectra.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.MonetizationOn
import dev.spectra.app.AppState
import dev.spectra.app.performUserInitiatedRefresh
import dev.spectra.app.sendStateCommand
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.QuotedTotal
import uniffi.spectra_core.StateCommand

/**
 * Pin or unpin one asset. Core applies it to the set the dashboard shows,
 * defaults included, and each pin option says whether it is pinned.
 */
fun AppState.setDashboardAssetPinned(isPinned: Boolean, tokenId: String) {
    sendStateCommand(StateCommand.SetDashboardAssetPinned(tokenId = tokenId, isPinned = isPinned))
}

fun AppState.resetPinnedDashboardAssets() {
    sendStateCommand(StateCommand.ResetPinnedDashboardAssets)
}

/** The portfolio's total as core valued it: the wallets counted, read and priced. */
val AppState.portfolioQuotedTotal: QuotedTotal? get() = portfolioValuation?.portfolio

fun AppState.setPortfolioInclusion(isIncluded: Boolean, walletId: String) {
    sendStateCommand(StateCommand.SetWalletPortfolioInclusion(walletId = walletId, included = isIncluded))
}

/** A pricing read that failed is asked again by a refresh. */
private val AppState.retryRefresh: AppNoticeAction
    get() = AppNoticeAction.Retry { performUserInitiatedRefresh() }

/**
 * Everything the dashboard's notices page lists: stale prices and rates and
 * each degraded chain first, then the failures. Recomputed on every read
 * from the state it describes, so a notice goes when its cause does.
 */
val AppState.appNoticeItems: List<AppNoticeItem>
    get() {
        val notices = mutableListOf<AppNoticeItem>()
        quoteRefreshError?.let { message ->
            notices += AppNoticeItem(
                title = AppLocalization.string("Pricing Notice"),
                message = message,
                severity = AppNoticeSeverity.WARNING,
                icon = Icons.Outlined.MonetizationOn,
                action = retryRefresh,
            )
        }
        fiatRatesRefreshError?.let { message ->
            notices += AppNoticeItem(
                title = AppLocalization.string("Fiat Rates Degraded Mode"),
                message = message,
                severity = AppNoticeSeverity.WARNING,
                icon = Icons.Outlined.CloudOff,
                action = retryRefresh,
            )
        }
        notices += diagnostics.chainDegradedBanners.map { banner ->
            AppNoticeItem(
                title = AppLocalization.format("%@ Degraded Mode", banner.chainName),
                message = banner.message,
                severity = AppNoticeSeverity.WARNING,
                icon = Icons.Outlined.CloudOff,
                timestampUnix = banner.lastGoodSyncAtUnix,
                action = AppNoticeAction.OpenEndpoints,
            )
        }
        walletImport.error?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            notices += AppNoticeItem(
                title = AppLocalization.string("common.walletImportErrorTitle"),
                message = message,
                severity = AppNoticeSeverity.ERROR,
                icon = Icons.Outlined.FileDownloadOff,
                action = AppNoticeAction.Dismiss { walletImport.error = null },
            )
        }
        commandError?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            notices += AppNoticeItem(
                title = AppLocalization.string("Action Failed"),
                message = message,
                severity = AppNoticeSeverity.ERROR,
                icon = Icons.Outlined.ErrorOutline,
                action = AppNoticeAction.Dismiss { commandError = null },
            )
        }
        sendFlow.session.error?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            notices += AppNoticeItem(
                title = AppLocalization.string("common.sendErrorTitle"),
                message = message,
                severity = AppNoticeSeverity.ERROR,
                icon = Icons.AutoMirrored.Outlined.Send,
                action = AppNoticeAction.Dismiss { sendFlow.session.error = null },
            )
        }
        secretStoreRegistrationError?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            notices += AppNoticeItem(
                title = AppLocalization.string("Secure Storage Unavailable"),
                message = message,
                severity = AppNoticeSeverity.ERROR,
                icon = Icons.Outlined.GppMaybe,
            )
        }
        appLockError?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            notices += AppNoticeItem(
                title = AppLocalization.string("common.securityNoticeTitle"),
                message = message,
                severity = AppNoticeSeverity.ERROR,
                icon = Icons.Outlined.GppMaybe,
                action = AppNoticeAction.Dismiss { appLockError = null },
            )
        }
        return notices
    }
