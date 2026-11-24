package dev.spectra.send

import dev.spectra.app.AppState
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.MainAppTab
import dev.spectra.app.Routes
import dev.spectra.app.authenticate
import dev.spectra.app.performCoreRefresh
import dev.spectra.app.readOrNull
import dev.spectra.app.refreshTransactionProjection
import dev.spectra.app.rethrowIfCancellation
import dev.spectra.models.AppLocalization
import dev.spectra.models.isEvm
import dev.spectra.models.localizedText
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import uniffi.spectra_core.AddressBookEntry
import uniffi.spectra_core.AppRefreshIntent
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.OwnedSendPreview
import uniffi.spectra_core.ReplaceableSend
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendPreview
import uniffi.spectra_core.SubmissionOutcome
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.isValidAmountInput

// What the send flow needs of the wallet projection or of several domains.
// Contract with the other features: `beginSend` and `selectedSendCoin` keep
// their signatures.

/**
 * Open the composer on [walletId], or on the first wallet that can send, and
 * on [holdingKey] when that wallet can send it. Pushes the send flow on the
 * tab the user is on; Settings has none, so it opens on Home.
 */
fun AppState.beginSend(walletId: String? = null, holdingKey: String? = null) {
    val wallets = sendEnabledWallets
    val wallet = wallets.firstOrNull { it.id == walletId } ?: wallets.firstOrNull() ?: return
    sendFlow.walletId = wallet.id
    val coins = availableSendCoins(sendFlow.walletId)
    sendFlow.holdingKey = coins.firstOrNull { it.id == holdingKey }?.id ?: coins.firstOrNull()?.id ?: ""
    sendFlow.resetComposer()
    syncSendAssetSelection()
    presentSendFlow()
}

/**
 * Push the composer on the tab the user is on — Home and History each have
 * it as a destination; Settings has none, so it opens on Home. A composer
 * still open on another tab is closed there: the flow lives on one stack.
 */
private fun AppState.presentSendFlow() {
    val tab = if (navigator.selectedTab == MainAppTab.SETTINGS) MainAppTab.HOME else navigator.selectedTab
    MainAppTab.entries.filter { it != tab }.forEach { navigator.close(it, Routes.Send) }
    sendFlow.presentingTab = tab
    sendFlow.isPresented = true
    if (navigator.selectedTab != tab) navigator.selectTab(tab)
    if (Routes.Send in navigator.currentStack) navigator.popTo { it == Routes.Send } else navigator.push(Routes.Send)
}

/**
 * The composer's page left the screen. Unless it is still on a tab's stack —
 * the user only switched tabs — the flow was closed, by Back or a reset, and
 * the composer is reset as dismissing it does.
 */
internal fun AppState.sendFlowLeftScreen() {
    if (!navigator.isOnAnyTab(Routes.Send)) sendFlow.isPresented = false
}

fun AppState.syncSendAssetSelection() {
    val availableHoldingKeys = availableSendCoins(sendFlow.walletId).map { it.id }
    if (sendFlow.holdingKey !in availableHoldingKeys) sendFlow.holdingKey = availableHoldingKeys.firstOrNull() ?: ""
    // Keep EIP-1559 fees and manual nonce when switching within the EVM
    // family; clear them when leaving it.
    if (selectedSendCoin?.chainId?.isEvm != true) {
        sendFlow.clearEvmOverrides()
    }
    sendFlow.invalidateSession()
    sendFlow.clearPreview()
    sendFlow.clearDestinationCheck()
}

/** Close the composer: its page leaves the stack and the form is reset. */
fun AppState.cancelSend() {
    MainAppTab.entries.forEach { navigator.close(it, Routes.Send) }
    sendFlow.close()
}

/** The holding the composer sends. */
val AppState.selectedSendCoin: AssetHolding?
    get() = availableSendCoins(sendFlow.walletId).firstOrNull { it.id == sendFlow.holdingKey }

val AppState.sendAmountDecimals: UInt?
    get() {
        val coin = selectedSendCoin ?: return null
        return assetPrecision?.byDeploymentId?.get(coin.id)
    }

val AppState.sendAmountIsValid: Boolean
    get() {
        val decimals = sendAmountDecimals ?: return false
        return isValidAmountInput(sendFlow.amountInput, decimals)
    }

/** The quote core made for the selected holding, if it is current. */
val AppState.sendQuote: OwnedSendPreview?
    get() {
        val coin = selectedSendCoin ?: return null
        return sendFlow.previewStore.quote(sendFlow.walletId, coin)
    }

/**
 * The quote for the amount on screen. A quote for another amount — the
 * provisional one core makes before an amount is typed, or one still in
 * flight — says nothing about this one.
 */
val AppState.sendQuoteForEnteredAmount: OwnedSendPreview?
    get() = sendQuote?.takeIf { it.amount == sendFlow.amountInput }

/**
 * Why the entered amount cannot be sent, from core's quote for it: more than
 * the balance, or too little left for the fee.
 */
val AppState.sendAmountRefusal: String?
    get() = sendQuoteForEnteredAmount?.amountRefusal?.localizedText

fun AppState.sendShortcutAmount(percentage: UInt): String? {
    if (sendFlow.isPreparingPreview) return null
    return sendQuote?.shortcuts?.get(percentage)
}

fun AppState.sendPreview(coin: AssetHolding): SendPreview? =
    sendFlow.previewStore.quote(sendFlow.walletId, coin)?.preview

val AppState.sendAddressBookEntries: List<AddressBookEntry>
    get() {
        val coin = selectedSendCoin ?: return emptyList()
        return addressBook.entries.filter { it.chainId == coin.chainId }
    }

fun AppState.selectedWalletForSend(): WalletState? = wallet(sendFlow.walletId)

fun AppState.replaceableSend(transactionId: String): ReplaceableSend? =
    replaceableSends.firstOrNull { it.transactionId == transactionId }

/**
 * Open the composer on a pending send's replacement or cancellation, filled
 * in by core. `null` once it is ready; otherwise why it is not.
 */
suspend fun AppState.openReplacementComposer(transactionId: String, cancel: Boolean): String? {
    val pending = replaceableSend(transactionId)
    if (pending == null) {
        val message = AppLocalization.string(
            "This transaction is no longer pending, so replacement and cancel are unavailable.",
        )
        sendFlow.session.error = message
        return message
    }
    presentSendFlow()
    prepareReplacementContext(pending, cancel)
    return sendFlow.session.error
}

suspend fun AppState.prepareReplacementContext(pending: ReplaceableSend, cancel: Boolean) {
    sendFlow.invalidateSession()
    val session = sendFlow.session.id
    sendFlow.isPreparingReplacement = true
    try {
        val draft = bridge.ready().replacementDraft(pending.transactionId, cancel)
        if (!sendFlow.session.isCurrent(session)) return
        sendFlow.walletId = draft.walletId
        sendFlow.holdingKey = draft.holdingKey
        sendFlow.address = draft.destination
        sendFlow.amount = draft.amount
        sendFlow.evmManualNonceEnabled = true
        sendFlow.evmManualNonce = draft.nonce.toString()
        sendFlow.useCustomEvmFees = true
        sendFlow.customEvmMaxFeeGwei = draft.maxFeeGwei
        sendFlow.customEvmPriorityFeeGwei = draft.priorityFeeGwei
    } catch (error: Exception) {
        error.rethrowIfCancellation()
        if (!sendFlow.session.isCurrent(session)) return
        sendFlow.session.error = AppLocalization.format("Unable to prepare replacement context: %@", userErrorMessage(error))
    } finally {
        if (sendFlow.session.id == session) sendFlow.isPreparingReplacement = false
    }
}

/** Ask the device owner where the settings say to, then sign the staged send. */
suspend fun AppState.confirmSigning(password: String?) {
    sendFlow.sign(password) {
        authenticate(DeviceAuthenticationAction.SEND, AppLocalization.string("Authorize transaction signing"))
    }
}

val AppState.stagedSendRequiresPassword: Boolean
    get() {
        val artifact = sendFlow.session.artifact ?: return false
        // An unknown wallet asks for a password rather than signing without one.
        return wallet(artifact.walletId)?.signing?.requiresPassword ?: true
    }

suspend fun AppState.broadcastPreparedSend() {
    val submitted = sendFlow.broadcast() ?: return
    handleBroadcastCompletion(submitted)
}

/**
 * Application completion is independent of the originating form's lifetime.
 * There is no Live Activity on Android: a send is followed by its status
 * notifications, which need the permission asked for here.
 */
suspend fun AppState.handleBroadcastCompletion(submitted: SendArtifact) {
    refreshTransactionProjection()
    // Resolve the completed operation by its own ID, never the current
    // composer or the bounded history summary.
    val transaction = readOrNull { bridge.ready().transaction(submitted.id) }
    if (transaction != null && submitted.attempts.any { it.outcome == SubmissionOutcome.ACCEPTED }) {
        notifications.requestPermissionAfterSend(committedAppSettings)
        // Broadcast acceptance alone does not establish confirmation.
        performCoreRefresh(AppRefreshIntent.AfterSend(transaction.chainId))
    }
}

val AppState.sendEnabledWallets: List<WalletState> get() = walletDerivedCache.sendEnabledWallets

val AppState.canBeginSend: Boolean get() = sendEnabledWallets.isNotEmpty()
