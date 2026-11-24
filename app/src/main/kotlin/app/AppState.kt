package dev.spectra.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.diagnostics.WalletChainDiagnosticsState
import dev.spectra.diagnostics.WalletDiagnosticsState
import dev.spectra.history.HistoryPagingState
import dev.spectra.models.AmountPresentation
import dev.spectra.models.userErrorMessage
import dev.spectra.receive.ReceiveFlowState
import dev.spectra.send.SendFlowState
import dev.spectra.send.selectedSendCoin
import dev.spectra.settings.AddressBookState
import dev.spectra.settings.PriceAlertsState
import dev.spectra.settings.TokenPreferencesState
import dev.spectra.settings.TorState
import dev.spectra.wallets.WalletImportSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uniffi.spectra_core.AppSettingUpdate
import uniffi.spectra_core.AppSettings
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.AssetPrecisionCatalog
import uniffi.spectra_core.CommittedState
import uniffi.spectra_core.DashboardAssetGroup
import uniffi.spectra_core.DashboardPinOption
import uniffi.spectra_core.FiatCurrency
import uniffi.spectra_core.PortfolioValuation
import uniffi.spectra_core.ReplaceableSend
import uniffi.spectra_core.TransactionRecord
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.appSettingsApplying
import uniffi.spectra_core.appSettingsDefaults

/** Where launch stands with the stored data. */
enum class StoreStatus {
    /** Core has not answered yet. */
    OPENING,

    /**
     * Core has answered and the app runs on what it read. A failure other
     * than unreadable data is logged and lands here too: offering to discard
     * data over an error that may pass would destroy it.
     */
    OPEN,

    /** Core cannot read what is stored: discarding it is the only way out. */
    UNREADABLE,
}

enum class MainAppTab { HOME, HISTORY, SETTINGS }

/**
 * Bundled derived state of the wallet collection, as core resolved it,
 * assigned as one value per rebuild.
 */
data class WalletDerivedCache(
    val walletById: Map<String, WalletState>,
    val portfolio: List<AssetHolding>,
    val availableSendCoinsByWalletId: Map<String, List<AssetHolding>>,
    val availableReceiveCoinsByWalletId: Map<String, List<AssetHolding>>,
    val sendEnabledWallets: List<WalletState>,
    val receiveEnabledWallets: List<WalletState>,
) {
    companion object {
        val EMPTY = WalletDerivedCache(emptyMap(), emptyList(), emptyMap(), emptyMap(), emptyList(), emptyList())
    }
}

/**
 * The app's central store: the wallet, portfolio and transaction projections,
 * and the composition root for the domains. It lives as long as the process,
 * like the Apple app's `AppState`, and every member is read and written on
 * the main thread.
 *
 * Core is the canonical store. Each projection here has one writer — the
 * adoption of what core returned — and is `internal set` only so the
 * adopters in this package's other files can write it. A domain with state
 * of its own is a small object [AppState] owns; its screens read that object.
 * Adding state? Put it in its domain's type rather than growing this class.
 */
class AppState(
    context: Context,
    val bridge: WalletServiceBridge = WalletServiceBridge(context),
    /** Whether automatic platform work runs; off for a state built in a test. */
    val servicesEnabled: Boolean = true,
) {
    val context: Context = context.applicationContext
    val scope: CoroutineScope = MainScope()

    // ── Domains ────────────────────────────────────────────────────────

    /** Every state command goes through this one queue. */
    val stateCommands = StateCommandQueue(bridge, scope)
    val diagnostics = WalletDiagnosticsState(bridge, scope)
    val chainDiagnosticsState = WalletChainDiagnosticsState()
    val sendFlow = SendFlowState(bridge, scope)
    val receiveFlow = ReceiveFlowState()
    val walletImport = WalletImportSession()
    val addressBook = AddressBookState(stateCommands)
    val tokenPreferences = TokenPreferencesState(stateCommands, diagnostics)
    val priceAlerts = PriceAlertsState(stateCommands)
    val historyPaging = HistoryPagingState()
    val tor = TorState(bridge, scope)

    /** The six preferences this platform keeps for itself. Settings core owns are [appSettings]. */
    val preferences = AppUserPreferences(this.context)
    val notifications = PlatformNotifications(this.context, bridge, diagnostics)
    val navigator = Navigator(this)

    // ── Projections of core state ──────────────────────────────────────

    /** Recorded transactions: the bounded recent and pending projection core returns. */
    var transactions by mutableStateOf<List<TransactionRecord>>(emptyList())
        internal set
    var transactionRevision by mutableStateOf(0L)
        internal set
    var historyReadError by mutableStateOf<String?>(null)
        internal set
    var portfolioValuation by mutableStateOf<PortfolioValuation?>(null)
        internal set
    var assetPrecision by mutableStateOf<AssetPrecisionCatalog?>(null)
        internal set

    /** Core's count of transactions still pending, for the History tab's badge. */
    var pendingTransactionCount by mutableStateOf(0uL)
        internal set

    /** The pending sends core says can still be replaced on their chain. */
    var replaceableSends by mutableStateOf<List<ReplaceableSend>>(emptyList())
        internal set

    /** When each wallet's earliest stored transaction happened, in Unix seconds. */
    var firstActivityByWalletId by mutableStateOf<Map<String, Double>>(emptyMap())
        internal set

    /** Imported wallets: a projection of core's resident state, changed only through commands. */
    var wallets by mutableStateOf<List<WalletState>>(emptyList())
        internal set

    /**
     * Core's count of changes to a wallet in anything but its balances: the
     * name, addresses and settings that history rows and details are read
     * against. A screen keys its reads on it.
     */
    var walletIdentityRevision by mutableStateOf(0uL)
        internal set
    var walletDerivedCache by mutableStateOf(WalletDerivedCache.EMPTY)
        internal set
    var dashboardAssetGroups by mutableStateOf<List<DashboardAssetGroup>>(emptyList())
        internal set
    var dashboardPinOptions by mutableStateOf<List<DashboardPinOption>>(emptyList())
        internal set
    var fiatRatesRefreshError by mutableStateOf<String?>(null)
        internal set
    var quoteRefreshError by mutableStateOf<String?>(null)
        internal set

    /** Why the last state command, or a wallet deletion, did not go through. */
    var commandError by mutableStateOf<String?>(null)

    var isAppLocked by mutableStateOf(false)
        internal set
    var appLockError by mutableStateOf<String?>(null)
        internal set

    /** What core has said about the stored data; until it answers no wallet is drawn. */
    var storeStatus by mutableStateOf(StoreStatus.OPENING)
        internal set

    /** Set when core could not be given the Keystore-backed secret store. */
    var secretStoreRegistrationError by mutableStateOf<String?>(null)
        internal set

    /** When core last checked pending sends without a failure, in Unix seconds. */
    var lastPendingTransactionRefreshAt by mutableStateOf<Double?>(null)
        internal set

    // ── Settings core owns ─────────────────────────────────────────────

    /** The last committed core settings, with pending edits applied by core's rule. */
    var appSettings by mutableStateOf(appSettingsDefaults())
        private set

    /** Runtime effects use only core-committed settings. */
    internal var committedAppSettings: AppSettings = appSettingsDefaults()
        private set
    private var settingCommandsInFlight = 0
    internal var appliedCoreStateRevision = 0uL
        private set

    /** Display currency for prices and totals: core's setting. */
    val selectedFiatCurrency: FiatCurrency get() = appSettings.fiatCurrency

    val amounts: AmountPresentation
        get() = AmountPresentation(assetPrecision, portfolioValuation, selectedFiatCurrency)

    // ── Internal machinery ─────────────────────────────────────────────

    internal var portfolioSnapshotRevision = 0uL
    internal var transactionSnapshotRevision = 0uL
    internal var userInitiatedRefresh: Deferred<Boolean>? = null
    internal var balanceProgressJob: Job? = null
    internal var deviceConditionsJob: Job? = null
    internal var refreshEventsJob: Job? = null
    internal var appIsActive = true
    internal val deviceMonitor = DeviceConditionsMonitor(this.context) { reportDeviceConditions() }

    init {
        stateCommands.adopt = { adoptCommittedTransition(it) }
        sendFlow.selectedHolding = { selectedSendCoin }
        if (servicesEnabled) {
            // A launch is a return to the app too: without this, closing it from
            // the recents screen and opening it again would get past auto-lock.
            isAppLocked = preferences.useDeviceAuthentication && preferences.useAutoLock
            preferences.deviceAuthenticationDisabledHandler = {
                isAppLocked = false
                appLockError = null
            }
            deviceMonitor.start()
            scope.launch { warmUpAfterLaunch() }
        }
    }

    /**
     * Registers the secret store before any launch work that might read a seed
     * or a private key, and records the failure where both the user and a
     * diagnostics export can see it.
     */
    private fun registerSecretStoreWithBridge() {
        try {
            bridge.service()
            secretStoreRegistrationError = null
        } catch (error: Exception) {
            secretStoreRegistrationError = userErrorMessage(error)
            appendOperationalLog("Secret Store", "Secret store registration failed: $error", source = "WalletServiceBridge.service")
        }
    }

    private suspend fun warmUpAfterLaunch() {
        registerSecretStoreWithBridge()
        setupRustRefreshEngine()
        reloadCoreProjections()
        // Configuring the engine starts it; its first tick performs the launch
        // sweep, prices and exchange rates included.
    }

    // ── Settings ───────────────────────────────────────────────────────

    /**
     * Change one setting. Shown at once — [appSettingsApplying] is the
     * reducer's rule, so the value shown is the value core will store — and
     * sent to core in order. Core's committed settings replace the shown ones
     * when the last edit in flight lands, or when one fails.
     */
    fun updateSetting(update: AppSettingUpdate) {
        val before = appSettings
        val after = appSettingsApplying(before, update)
        if (after == before) return
        appSettings = after
        settingCommandsInFlight += 1
        stateCommands.enqueue(uniffi.spectra_core.StateCommand.SetAppSetting(update)) { result ->
            settingCommandsInFlight -= 1
            if (settingCommandsInFlight == 0) appSettings = committedAppSettings
            result.onFailure { error ->
                reportCommandError(error)
                runCatching { bridge.ready().committedState() }.getOrNull()?.let { applyCoreState(it) }
            }
        }
    }

    /** What a settings change sets in motion here: the notification permission a new alert needs. */
    private fun reactToSettingsChange(before: AppSettings) {
        val settings = committedAppSettings
        if ((settings.useTransactionStatusNotifications && !before.useTransactionStatusNotifications) ||
            (settings.useLargeMovementNotifications && !before.useLargeMovementNotifications)
        ) {
            notifications.requestPermission()
        }
    }

    /**
     * The only place the core-owned mirrors are written. Everything else goes
     * through a command and lands back here.
     */
    fun applyCoreState(state: CommittedState): Boolean {
        if (state.revision < appliedCoreStateRevision) return false
        appliedCoreStateRevision = state.revision
        if (state.settings != committedAppSettings) {
            val before = committedAppSettings
            committedAppSettings = state.settings
            reactToSettingsChange(before)
        }
        if (settingCommandsInFlight == 0) appSettings = state.settings
        addressBook.adopt(state.addressBook)
        tokenPreferences.adopt(state.tokenPreferences)
        priceAlerts.adopt(state.priceAlerts)
        return true
    }

    // ── Lookups the screens share ──────────────────────────────────────

    fun wallet(id: String): WalletState? = walletDerivedCache.walletById[id]

    val portfolio: List<AssetHolding> get() = walletDerivedCache.portfolio

    fun availableSendCoins(walletId: String): List<AssetHolding> =
        walletDerivedCache.availableSendCoinsByWalletId[walletId] ?: emptyList()

    fun availableReceiveCoins(walletId: String): List<AssetHolding> =
        walletDerivedCache.availableReceiveCoinsByWalletId[walletId] ?: emptyList()

    val canImportWallet: Boolean get() = walletImport.draft.canImportWallet

    /** Log a failure on this side of the boundary. Core logs the work it performs itself. */
    fun appendOperationalLog(category: String, message: String, chain: uniffi.spectra_core.Chain? = null, source: String? = null) {
        diagnostics.appendOperationalLog(category, message, chain, source)
    }

    /** Stop everything this state started. Only a test's state is ever closed. */
    fun close() {
        deviceMonitor.stop()
        scope.cancel()
    }
}
