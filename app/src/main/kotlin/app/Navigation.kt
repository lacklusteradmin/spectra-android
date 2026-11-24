package dev.spectra.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import uniffi.spectra_core.Chain
import uniffi.spectra_core.WalletAction

/**
 * A destination on a tab's back stack. A feature defines the routes inside
 * it in its own package and registers them in its `…Entries` function; the
 * routes one feature opens in another are [Routes], so the two agree on
 * their shape.
 */
interface Route

/** The routes that cross features, and each tab's root. */
object Routes {
    // ── Tab roots ──────────────────────────────────────────────────────
    data object Home : Route
    data object History : Route
    data object Settings : Route

    // ── Home ───────────────────────────────────────────────────────────

    /** The add-wallet flow: create, import or watch. */
    data object AddWallet : Route
    data class WalletDetail(val walletId: String) : Route
    data class AssetGroupDetail(val groupId: String) : Route
    data object PinnedAssets : Route

    /** The send composer, on whatever `AppState.beginSend` set up. */
    data object Send : Route

    /** The receive flow, on whatever `AppState.beginReceive` set up. */
    data object Receive : Route

    // ── A wallet's actions ─────────────────────────────────────────────

    /** One wallet's own history. */
    data class WalletHistory(val walletId: String) : Route

    /** A wallet's staking page. */
    data class WalletStaking(val walletId: String) : Route

    /**
     * A wallet tool: multisig, block scan, coins, approvals, NFTs, shielded
     * or MWEB funds, the network account, access keys, token storage, coin
     * objects, token accounts, trust lines, and signing or verifying a
     * message.
     */
    data class WalletTool(val walletId: String, val action: WalletAction) : Route

    // ── History ────────────────────────────────────────────────────────
    data class TransactionDetail(val transactionId: String) : Route

    // ── Settings ───────────────────────────────────────────────────────
    data object Diagnostics : Route
    data object OperationalLogs : Route

    /** Tor's settings and status: from Home's Tor indicator. */
    data object TorSettings : Route

    /** The endpoint catalog's settings: from a degraded-endpoint notice. */
    data object EndpointSettings : Route

    /** A known token's detail in the token registry, by its token id: from an asset group. */
    data class TokenDetail(val tokenId: String) : Route

    /** The form that adds a custom endpoint for [chain]: from settings and from wallet setup. */
    data class AddCustomEndpoint(val chain: Chain) : Route

    // ── Wiki ───────────────────────────────────────────────────────────
    data object CryptoWiki : Route
    data class ChainWiki(val chain: Chain) : Route
}

/**
 * The tabs and their back stacks. Each tab keeps its own stack, as each iOS
 * tab keeps its own `NavigationStack`; switching tabs leaves the others
 * where they were. A flow — sending, receiving, adding a wallet — is pushed
 * on the tab it started from and hides the navigation bar.
 */
class Navigator(private val app: AppState) {
    var selectedTab by mutableStateOf(MainAppTab.HOME)
        private set

    private val stacks: Map<MainAppTab, SnapshotStateList<Route>> = mapOf(
        MainAppTab.HOME to mutableStateListOf(Routes.Home),
        MainAppTab.HISTORY to mutableStateListOf(Routes.History),
        MainAppTab.SETTINGS to mutableStateListOf(Routes.Settings),
    )

    fun stack(tab: MainAppTab): List<Route> = stacks.getValue(tab)

    /** The stack on screen. */
    val currentStack: List<Route> get() = stack(selectedTab)

    /** Whether the tab on screen shows its root, where the navigation bar shows. */
    val isAtRoot: Boolean get() = currentStack.size == 1

    /** Show [tab]; choosing the tab already shown returns it to its root. */
    fun selectTab(tab: MainAppTab) {
        if (tab == selectedTab) {
            popToRoot()
            return
        }
        selectedTab = tab
        app.reportDeviceConditions()
    }

    fun push(route: Route) {
        val stack = stacks.getValue(selectedTab)
        if (stack.lastOrNull() != route) stack.add(route)
    }

    /** Push [route] on [tab], switching to it. */
    fun push(tab: MainAppTab, route: Route) {
        if (selectedTab != tab) {
            selectedTab = tab
            app.reportDeviceConditions()
        }
        push(route)
    }

    /** Go back one page. `false` at a tab's root, where back leaves the app. */
    fun pop(): Boolean {
        val stack = stacks.getValue(selectedTab)
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Pop every page above the newest that [matches]; nothing when none does. */
    fun popTo(matches: (Route) -> Boolean) {
        val stack = stacks.getValue(selectedTab)
        val index = stack.indexOfLast(matches)
        if (index < 0) return
        while (stack.lastIndex > index) stack.removeAt(stack.lastIndex)
    }

    /** Keep the bottom [size] pages of the tab on screen, and at least its root. */
    fun truncate(size: Int) {
        val stack = stacks.getValue(selectedTab)
        while (stack.size > maxOf(size, 1)) stack.removeAt(stack.lastIndex)
    }

    /** Pop the first page of [T] and everything above it: closing a flow. */
    inline fun <reified T : Route> close() {
        val index = currentStack.indexOfFirst { it is T }
        if (index > 0) truncate(index)
    }

    /**
     * Take [route] and everything above it off [tab]'s stack, never its root,
     * without switching tabs: a flow left open on a tab the user is not
     * looking at is closed there.
     */
    fun close(tab: MainAppTab, route: Route) {
        val stack = stacks.getValue(tab)
        val index = stack.indexOf(route)
        if (index <= 0) return
        while (stack.lastIndex >= index) stack.removeAt(stack.lastIndex)
    }

    /**
     * Pop [route] only while it is the page on screen: work a page started may
     * finish after the user has already left it, and must not close another.
     */
    fun dismiss(route: Route) {
        if (currentStack.lastOrNull() == route) pop()
    }

    /** Whether [route] is on any tab's stack: covered by another page or behind another tab, but not closed. */
    fun isOnAnyTab(route: Route): Boolean = stacks.values.any { route in it }

    /** Replace the page on top with [route]: a flow's next step that Back should skip. */
    fun replaceTop(route: Route) {
        val stack = stacks.getValue(selectedTab)
        if (stack.size <= 1) stack.add(route) else stack[stack.lastIndex] = route
    }

    fun popToRoot() {
        val stack = stacks.getValue(selectedTab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Every tab back at its root: after a reset, nothing open may outlive its data. */
    fun resetToRoots() {
        stacks.values.forEach { stack -> while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    }
}

/** The app's state, for any screen. */
val LocalAppState = staticCompositionLocalOf<AppState> { error("AppState is provided by SpectraRoot") }

/** The navigator, for any screen. */
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator is provided by SpectraRoot") }
