package dev.spectra.receive

import dev.spectra.app.AppState
import dev.spectra.app.MainAppTab
import dev.spectra.app.Routes
import dev.spectra.app.rethrowIfCancellation
import dev.spectra.models.AppLocalization
import dev.spectra.models.isNativeCoin
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.WalletState
import java.util.UUID

// What the receive flow needs of the wallet projection. Contract with the
// other features: `beginReceive` keeps its signature.

/**
 * Open on [walletId], or with no wallet selected unless there is only one to
 * choose. Pushes the flow on the tab the user is on; Settings has none, so it
 * opens on Home.
 */
fun AppState.beginReceive(walletId: String? = null) {
    val wallets = receiveEnabledWallets
    if (wallets.isEmpty()) return
    receiveFlow.walletId = if (walletId != null && wallets.any { it.id == walletId }) {
        walletId
    } else if (wallets.size == 1) {
        wallets[0].id
    } else {
        ""
    }
    syncReceiveAssetSelection()
    presentReceiveFlow()
}

/** The flow lives on one tab's stack: one still open on another tab is closed there first. */
private fun AppState.presentReceiveFlow() {
    val tab = if (navigator.selectedTab == MainAppTab.SETTINGS) MainAppTab.HOME else navigator.selectedTab
    MainAppTab.entries.filter { it != tab }.forEach { navigator.close(it, Routes.Receive) }
    receiveFlow.isPresented = true
    if (navigator.selectedTab != tab) navigator.selectTab(tab)
    if (Routes.Receive in navigator.currentStack) navigator.popTo { it == Routes.Receive } else navigator.push(Routes.Receive)
}

/**
 * The flow's first page left the screen. Unless it is still on a tab's stack
 * — the user only switched tabs — the flow was closed.
 */
internal fun AppState.receiveFlowLeftScreen() {
    if (!navigator.isOnAnyTab(Routes.Receive)) receiveFlow.isPresented = false
}

fun AppState.syncReceiveAssetSelection() {
    receiveFlow.holdingKey = selectedReceiveCoin(receiveFlow.walletId)?.id ?: ""
    receiveFlow.clearAddress()
}

/** Read the selected wallet's address on its network; only the latest request's answer is adopted. */
suspend fun AppState.refreshReceiveAddress() {
    val requestId = UUID.randomUUID()
    receiveFlow.requestId = requestId
    receiveFlow.resolvedAddress = ""
    receiveFlow.error = null
    receiveFlow.isResolving = false
    val wallet = wallet(receiveFlow.walletId) ?: return
    val coin = selectedReceiveCoin(receiveFlow.walletId) ?: return
    receiveFlow.isResolving = true
    try {
        val address = bridge.ready().receiveAddress(wallet.id, coin.chainId, true)
        if (!currentCoroutineContext().isActive || receiveFlow.requestId != requestId ||
            receiveFlow.walletId != wallet.id || receiveFlow.holdingKey != coin.id
        ) {
            return
        }
        receiveFlow.resolvedAddress = address ?: ""
        if (address == null) {
            receiveFlow.error = AppLocalization.string("No receive address is available for this wallet and network.")
        }
    } catch (error: Exception) {
        error.rethrowIfCancellation()
        if (receiveFlow.requestId != requestId) return
        receiveFlow.error = userErrorMessage(error)
    } finally {
        if (receiveFlow.requestId == requestId) receiveFlow.isResolving = false
    }
}

/**
 * The native holding, or the first token if none is native, for the receive
 * screen's symbol and icon. This does not select an address.
 */
fun AppState.selectedReceiveCoin(walletId: String): AssetHolding? {
    val receiveCoins = availableReceiveCoins(walletId)
    return receiveCoins.firstOrNull { it.isNativeCoin } ?: receiveCoins.firstOrNull()
}

val AppState.receiveEnabledWallets: List<WalletState> get() = walletDerivedCache.receiveEnabledWallets

val AppState.canBeginReceive: Boolean get() = receiveEnabledWallets.isNotEmpty()
