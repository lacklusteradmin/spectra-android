package dev.spectra.app

import dev.spectra.models.AppLocalization
import dev.spectra.models.formatRelativeTime
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.spectra_core.AppRefreshIntent
import uniffi.spectra_core.AppRefreshResult
import uniffi.spectra_core.DeviceConditions
import uniffi.spectra_core.PortfolioSnapshot
import uniffi.spectra_core.QuoteRefreshFailure
import uniffi.spectra_core.QuoteRefreshState
import uniffi.spectra_core.ResetScope
import uniffi.spectra_core.SpectraBridgeException
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.StateTransition

// Core is the canonical store for wallets, settings, contacts, tokens, alerts
// and pins; the state on AppState and its domain objects are projections of
// it, with one writer each. Every change is a StateCommand sent through
// `stateCommands`, and what core committed lands back through
// `applyCoreState` and the portfolio snapshot.

// ── State commands ───────────────────────────────────────────────────────

/** What the command queue does with each committed command: adopt core's state and the portfolio it changed. */
internal suspend fun AppState.adoptCommittedTransition(transition: StateTransition) {
    applyCoreState(transition.state)
    rebuildWalletDerivedStateFromCore()
}

/** Load core's state and mirror it. */
suspend fun AppState.loadCoreOwnedState() {
    try {
        applyCoreState(bridge.ready().committedState())
    } catch (error: SpectraBridgeException.StoreUnreadable) {
        storeStatus = StoreStatus.UNREADABLE
        appendOperationalLog("Storage", error.reason)
    } catch (error: Exception) {
        appendOperationalLog("Storage", error.toString())
    }
}

suspend fun AppState.reloadCoreProjections() {
    // Core-owned domain state first: it is the authority.
    loadCoreOwnedState()
    // Nothing else can be read from data core cannot read.
    if (storeStatus == StoreStatus.UNREADABLE) return
    diagnostics.loadFromSQLite()
    rebuildWalletDerivedStateFromCore()
    // The tabs are drawn from here, with the wallets the snapshot just brought.
    storeStatus = StoreStatus.OPEN
    refreshTransactionProjection()
}

/** Send a command whose only failure surface is [AppState.commandError]. */
fun AppState.sendStateCommand(command: StateCommand) {
    stateCommands.enqueue(command) { result ->
        result.onSuccess { commandError = null }.onFailure { reportCommandError(it) }
    }
}

/** Show a failed command on the dashboard. The message drops the detail, so the log keeps it. */
fun AppState.reportCommandError(error: Throwable) {
    commandError = userErrorMessage(error)
    appendOperationalLog("State", error.toString())
}

suspend fun AppState.removeWallet(id: String): Boolean = try {
    stateCommands.apply(StateCommand.RemoveWallet(id))
    refreshTransactionProjection()
    commandError = null
    true
} catch (error: Exception) {
    reportCommandError(error)
    false
}

// ── Derived state ────────────────────────────────────────────────────────

/**
 * Core resolves the whole portfolio — grouping, the price-request set, and
 * which coins each wallet can send or receive on — and hands it back.
 */
suspend fun AppState.rebuildWalletDerivedStateFromCore(): Boolean = try {
    applyPortfolioSnapshot(bridge.ready().portfolioSnapshot())
    true
} catch (error: Exception) {
    appendOperationalLog("Portfolio", error.toString())
    false
}

/**
 * Every wallet, quote and dashboard field adopted together. A field is
 * assigned only when it changed: mid-sweep snapshots arrive every few hundred
 * milliseconds and most leave most fields as they were.
 */
fun AppState.applyPortfolioSnapshot(snapshot: PortfolioSnapshot) {
    if (snapshot.revision <= portfolioSnapshotRevision) return
    if (!applyCoreState(snapshot.state)) return
    portfolioSnapshotRevision = snapshot.revision
    applyQuoteProjection(snapshot.quotes)
    if (portfolioValuation != snapshot.valuation) portfolioValuation = snapshot.valuation
    if (assetPrecision != snapshot.assetPrecision) assetPrecision = snapshot.assetPrecision
    val derived = snapshot.derived
    val walletById = snapshot.wallets.associateBy { it.id }
    if (wallets != snapshot.wallets) wallets = snapshot.wallets
    if (walletIdentityRevision != snapshot.walletIdentityRevision) walletIdentityRevision = snapshot.walletIdentityRevision
    val cache = WalletDerivedCache(
        walletById = walletById,
        portfolio = derived.portfolio,
        availableSendCoinsByWalletId = derived.sendCoinsByWalletId,
        availableReceiveCoinsByWalletId = derived.receiveCoinsByWalletId,
        sendEnabledWallets = derived.sendEnabledWalletIds.mapNotNull { walletById[it] },
        receiveEnabledWallets = derived.receiveEnabledWalletIds.mapNotNull { walletById[it] },
    )
    if (walletDerivedCache != cache) walletDerivedCache = cache
    if (dashboardAssetGroups != snapshot.groups) dashboardAssetGroups = snapshot.groups
    if (dashboardPinOptions != snapshot.pinOptions) dashboardPinOptions = snapshot.pinOptions
}

/** The only writer of the two quote notices, adopted with a newer, coherent snapshot. */
private fun AppState.applyQuoteProjection(quotes: QuoteRefreshState) {
    val prices = quotes.pricesError?.let {
        AppLocalization.string(
            when (it) {
                QuoteRefreshFailure.NoUsableQuote -> "No price provider returned a usable price. Showing the last known prices."
                is QuoteRefreshFailure.Unreachable -> "Couldn't reach a price provider. Showing the last known prices."
            },
        )
    }
    val rates = quotes.fiatError?.let {
        AppLocalization.string(
            when (it) {
                QuoteRefreshFailure.NoUsableQuote -> "No exchange-rate provider returned a usable rate. Showing the last known rates."
                is QuoteRefreshFailure.Unreachable -> "Couldn't reach an exchange-rate provider. Showing the last known rates."
            },
        )
    }
    if (quoteRefreshError != prices) quoteRefreshError = prices
    if (fiatRatesRefreshError != rates) fiatRatesRefreshError = rates
}

/**
 * A sweep reports each wallet as core commits it. Read the portfolio at most
 * once per interval while wallets keep landing, so balances appear as they
 * arrive rather than when the slowest chain finishes.
 */
internal fun AppState.adoptBalanceProgress() {
    if (balanceProgressJob != null) return
    balanceProgressJob = scope.launch {
        delay(300)
        balanceProgressJob = null
        rebuildWalletDerivedStateFromCore()
    }
}

/** Refresh the bounded recent and pending projection and its aggregates together. */
suspend fun AppState.refreshTransactionProjection(): Boolean = try {
    val snapshot = bridge.ready().transactionSnapshot()
    if (snapshot.revision > transactionSnapshotRevision) {
        transactionSnapshotRevision = snapshot.revision
        transactions = snapshot.recentAndPending
        transactionRevision += 1
        sendFlow.refreshProgress()
        replaceableSends = snapshot.replaceable
        pendingTransactionCount = snapshot.pendingCount
        historyPaging.adopt(snapshot.walletsWithMoreHistory.toSet())
        val firstActivity = snapshot.earliest.associate { it.walletId to it.earliestCreatedAtUnix }
        if (firstActivityByWalletId != firstActivity) firstActivityByWalletId = firstActivity
    }
    historyReadError = null
    true
} catch (_: Exception) {
    historyReadError = AppLocalization.string("Unable to read transaction history. Existing records have been kept.")
    false
}

// ── Refresh engine ───────────────────────────────────────────────────────

/** Refresh every wallet's balances now: the engine sweeps its entries together. */
suspend fun AppState.refreshBalances() {
    runCatching { bridge.refreshEngine().triggerImmediate() }
}

/** Install the observer and report the device. Core owns both refresh loops from here on. */
internal fun AppState.setupRustRefreshEngine() {
    val observer = WalletRefreshObserver()
    refreshEventsJob?.cancel()
    refreshEventsJob = scope.launch {
        observer.events.collect { event ->
            when (event) {
                WalletRefreshObserver.Event.BalanceUpdated -> adoptBalanceProgress()
                is WalletRefreshObserver.Event.RefreshComplete -> adoptRefreshResult(event.result)
                is WalletRefreshObserver.Event.TorStatusChanged -> tor.adopt(event.status)
            }
        }
    }
    // The observer is in place before the first report starts the loops.
    val previous = deviceConditionsJob
    deviceConditionsJob = scope.launch {
        previous?.join()
        runCatching { bridge.setRefreshObserver(observer) }
    }
    reportDeviceConditions()
}

/** Device-local inputs to core's refresh policy. Core owns the cadence and decisions. */
fun AppState.deviceConditions(): DeviceConditions = DeviceConditions(
    appIsActive = appIsActive,
    isNetworkReachable = deviceMonitor.isNetworkReachable,
    isConstrainedNetwork = deviceMonitor.isConstrainedNetwork,
    isExpensiveNetwork = deviceMonitor.isExpensiveNetwork,
    isLowPowerMode = deviceMonitor.isLowPowerMode,
    batteryLevel = deviceMonitor.batteryLevel,
    // Prices are on screen only on the home tab.
    wantsPriceRefresh = navigator.selectedTab == MainAppTab.HOME,
)

/** Tell core's engine what changed on the device, in the order it changed. */
fun AppState.reportDeviceConditions() {
    if (!servicesEnabled) return
    val conditions = deviceConditions()
    val previous = deviceConditionsJob
    deviceConditionsJob = scope.launch {
        previous?.join()
        runCatching { bridge.refreshEngine().setDeviceConditions(conditions) }
    }
}

// ── Lifecycle ────────────────────────────────────────────────────────────

/**
 * Core's engine starts both refresh loops in the foreground and stops them
 * in the background. Leaving locks the app when auto-lock is on.
 */
fun AppState.setAppIsActive(isActive: Boolean) {
    appIsActive = isActive
    if (!isActive && preferences.useDeviceAuthentication && preferences.useAutoLock) {
        isAppLocked = true
        appLockError = null
    }
    reportDeviceConditions()
}

suspend fun AppState.refreshForForegroundIfNeeded() {
    performCoreRefresh(AppRefreshIntent.Foreground)
}

// ── Maintenance ──────────────────────────────────────────────────────────

/** Ask core to refresh for [intent] and adopt the result. Scheduled ticks are core's own. */
suspend fun AppState.performCoreRefresh(intent: AppRefreshIntent): Boolean = try {
    adoptRefreshResult(bridge.ready().refreshApp(intent, deviceConditions()))
} catch (error: Exception) {
    appendOperationalLog("Refresh", error.toString())
    false
}

/**
 * Core evaluated alerts and movement after its own writes and logged its
 * failures; adopt what it says changed, once, then tell the user.
 */
suspend fun AppState.adoptRefreshResult(result: AppRefreshResult): Boolean {
    val portfolioRead = rebuildWalletDerivedStateFromCore()
    val historyRead = if (result.transactionsChanged) refreshTransactionProjection() else true
    result.pending?.let { notifications.deliverPendingStatusChanges(it.changes) }
    lastPendingTransactionRefreshAt = result.pendingCheckedAtUnix
    if (result.transactionsChanged) sendFlow.updateVerificationNotice()
    if (result.diagnosticsChanged) diagnostics.loadFromSQLite()
    notifications.deliverPriceAlerts(result.priceAlerts, amounts)
    result.movement?.let { notifications.deliverPortfolioMovement(it, amounts) }
    return portfolioRead && historyRead && result.failures.isEmpty() && (result.pending?.failures?.isEmpty() ?: true)
}

/** A refresh the user asked for; a second request joins the one in flight. */
suspend fun AppState.performUserInitiatedRefresh(): Boolean {
    userInitiatedRefresh?.let { return it.await() }
    val task = scope.async { performCoreRefresh(AppRefreshIntent.User) }
    userInitiatedRefresh = task
    return try {
        task.await()
    } finally {
        userInitiatedRefresh = null
    }
}

val AppState.pendingTransactionRefreshStatusText: String?
    get() = lastPendingTransactionRefreshAt?.let {
        AppLocalization.format("Last checked %@", formatRelativeTime((it * 1000).toLong()))
    }

// ── Reset ────────────────────────────────────────────────────────────────

/** `null` once the reset is done; otherwise why it was not, for the reset sheet. */
suspend fun AppState.resetSelectedData(scopes: Set<ResetScope>): String? {
    if (scopes.isEmpty()) return null
    authenticate(DeviceAuthenticationAction.RESET_DATA, AppLocalization.string("Authenticate to reset wallet data"))
        ?.let { return it }
    stateCommands.awaitPending()
    val outcome = try {
        bridge.ready().resetData(scopes.toList())
    } catch (error: Exception) {
        return userErrorMessage(error)
    }
    applyCoreState(outcome.state)
    rebuildWalletDerivedStateFromCore()
    refreshTransactionProjection()
    val plan = outcome.plan
    if (plan.resetWalletsAndSecrets) resetWalletFlows()
    if (plan.resetHistoryAndCache) {
        chainDiagnosticsState.diagnosticsRevision += 1
        lastPendingTransactionRefreshAt = null
    }
    if (plan.resetSettingsAndEndpoints) preferences.resetToDefaults()
    return null
}

/** Replace a store core could not read. `null` once the app runs on the new, empty store. */
suspend fun AppState.discardUnreadableStore(): String? {
    authenticate(DeviceAuthenticationAction.RESET_DATA, AppLocalization.string("Authenticate to reset wallet data"))
        ?.let { return it }
    try {
        bridge.discardUnreadableStore()
    } catch (error: Exception) {
        return userErrorMessage(error)
    }
    storeStatus = StoreStatus.OPENING
    resetWalletFlows()
    setupRustRefreshEngine()
    reloadCoreProjections()
    return null
}

private fun AppState.resetWalletFlows() {
    receiveFlow.reset()
    sendFlow.reset()
    walletImport.close()
    commandError = null
    navigator.resetToRoots()
}

// ── App lock ─────────────────────────────────────────────────────────────

suspend fun AppState.unlockApp() {
    val failure = authenticate(DeviceAuthenticationAction.UNLOCK, AppLocalization.string("Authenticate to unlock Spectra"))
    appLockError = failure
    if (failure == null) isAppLocked = false
}

/**
 * `null` when the action may proceed; otherwise why it may not. The caller
 * shows the reason in its own flow — an unlock failure is not a send error.
 */
suspend fun AppState.authenticate(action: DeviceAuthenticationAction, reason: String): String? {
    if (!action.requiresAuthentication(preferences.useDeviceAuthentication, preferences.authenticateSends)) return null
    return authenticateDeviceOwner(reason)
}

// ── Operational telemetry ────────────────────────────────────────────────

val AppState.networkSyncStatusText: String
    get() = AppLocalization.format(
        "Network: %@, %@, %@",
        AppLocalization.string(if (deviceMonitor.isNetworkReachable) "reachable" else "offline"),
        AppLocalization.string(if (deviceMonitor.isConstrainedNetwork) "constrained" else "unconstrained"),
        AppLocalization.string(if (deviceMonitor.isExpensiveNetwork) "expensive" else "non-expensive"),
    )

fun AppState.exportOperationalLogsText(events: List<uniffi.spectra_core.DiagnosticLog>? = null): String =
    diagnostics.exportOperationalLogsText(networkSyncStatusText, events)
