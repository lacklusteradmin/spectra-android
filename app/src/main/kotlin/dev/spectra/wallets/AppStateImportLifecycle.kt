package dev.spectra.wallets

import androidx.compose.runtime.snapshotFlow
import dev.spectra.app.AppState
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.MainAppTab
import dev.spectra.app.Routes
import dev.spectra.app.authenticate
import dev.spectra.app.rebuildWalletDerivedStateFromCore
import dev.spectra.app.removeWallet
import dev.spectra.models.AppLocalization
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.FundsFinderCandidate
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.WalletCopyCommit
import uniffi.spectra_core.WalletImportCommit
import uniffi.spectra_core.WalletImportOutcome
import uniffi.spectra_core.WalletSetupMethod
import uniffi.spectra_core.WalletState
import dev.spectra.send.cancelSend

/**
 * Open the form for adding a wallet on [chain] by [method], one the network's
 * setup descriptor offers.
 */
fun AppState.beginWalletSetup(chain: Chain, method: WalletSetupMethod) {
    walletImport.begin { it.configure(chain, method) }
    presentWalletImport()
}

/** Open the form that gives the watched wallet [walletId] its keys, by one of the methods core offers it. */
fun AppState.beginWalletUpgrade(walletId: String, chain: Chain, method: WalletSetupMethod) {
    walletImport.begin { it.configure(chain, method, upgrading = walletId) }
    presentWalletImport()
}

/**
 * Open the single-wallet import of an account a funds scan found, on its
 * network, with the phrase, passphrase, profile and account it was found at.
 */
internal fun AppState.beginFoundAccountImport(candidate: FundsFinderCandidate, words: List<String>, passphrase: String) {
    walletImport.begin { it.configure(candidate, words, passphrase) }
    presentWalletImport()
}

fun AppState.cancelWalletImport() {
    walletImport.close()
}

fun AppState.beginEditingWallet(wallet: WalletState) {
    walletImport.begin(editing = wallet) { it.configureForEditing(wallet) }
    presentWalletImport()
}

/**
 * Push the open form's first page, and close the form once none of its pages
 * is on any stack — left by Back, a tab chosen again or a reset — so its
 * draft, with its secrets, goes with them, as iOS closes the form when its
 * navigation stack pops it.
 */
private fun AppState.presentWalletImport() {
    val session = walletImport.id
    val first = walletImport.draft.setupFlow.pages.firstOrNull() ?: WalletSetupPage.WALLET_NAME
    navigator.push(WalletSetupRoute(session, first))
    scope.launch {
        snapshotFlow {
            MainAppTab.entries.any { tab ->
                navigator.stack(tab).any { it is WalletSetupRoute && it.session == session }
            }
        }.first { onStack -> !onStack }
        if (walletImport.id == session) walletImport.close()
    }
}

/**
 * The form's pages leave once it is done: the whole add-wallet flow when it
 * was opened from Add Wallet, otherwise the form alone, back to the page that
 * opened it.
 */
private fun AppState.finishWalletImportFlow() {
    navigator.close<Routes.AddWallet>()
    navigator.popTo { !it.isImportFormPage }
}

/** Pop every page about [walletId] from the stack on screen: the wallet is gone. */
internal fun AppState.dismissWalletPages(walletId: String) {
    val index = navigator.currentStack.indexOfFirst { it.belongsToWallet(walletId) }
    if (index > 0) navigator.truncate(index)
}

/**
 * Deletes a wallet the user has already confirmed. Nothing is held between
 * the confirmation and the authentication, so a refused authentication
 * leaves no half-started deletion behind.
 */
suspend fun AppState.deleteWallet(wallet: WalletState) {
    authenticate(DeviceAuthenticationAction.DELETE_WALLET, AppLocalization.string("Authenticate to delete wallet"))?.let {
        commandError = it
        return
    }
    val deletedWalletId = wallet.id
    // Core forgets the wallet's secrets, owned addresses, history pagination
    // and diagnostics rows in the same removal.
    if (!removeWallet(deletedWalletId)) return
    chainDiagnosticsState.diagnosticsRevision += 1
    diagnostics.loadFromSQLite()
    if (sendFlow.walletId == deletedWalletId) cancelSend()
    if (receiveFlow.walletId == deletedWalletId) receiveFlow.reset()
    if (walletImport.editingWalletId == deletedWalletId) walletImport.close()
    dismissWalletPages(deletedWalletId)
    if (navigator.selectedTab != MainAppTab.HOME) navigator.selectTab(MainAppTab.HOME)
    if (wallets.isEmpty()) cancelWalletImport()
}

suspend fun AppState.importWallet() {
    if (!canImportWallet || walletImport.isBusy) return
    val draft = walletImport.draft
    val name = draft.walletName.trim()
    walletImport.editingWalletId?.let { walletId ->
        renameWallet(walletId, name)
        return
    }
    // Snapshot all user input before suspension. The draft may be replaced
    // while core commits, but that must not alter this operation's inputs.
    val commit = draft.importCommit(name) ?: return
    val completed = walletImport.submit {
        val outcome = bridge.ready().importWallets(commit)
        // Core's refresh engine reads the new wallets' balances and history
        // itself, and reports them through its observer.
        rebuildWalletDerivedStateFromCore()
        val upgraded = outcome.wallets.firstOrNull()?.takeIf { outcome.upgraded }
        when {
            upgraded != null -> AppLocalization.format("Added keys to the watched wallet “%@”.", upgraded.name)
            outcome.rejectedAddresses.isEmpty() -> null
            else -> AppLocalization.format(
                "These addresses were not valid and were not imported: %@",
                outcome.rejectedAddresses.joinToString(", "),
            )
        }
    }
    if (completed) finishWalletImportFlow()
}

/**
 * Import each commit in turn — the Funds Finder's Import All, one wallet per
 * found account. A refusal stops none of the others; each answer comes back
 * in order, `null` for an import core made. The derived state is rebuilt
 * once, at the end.
 */
suspend fun AppState.importWallets(commits: List<WalletImportCommit>, progress: (Int) -> Unit): List<String?> {
    val refusals = mutableListOf<String?>()
    commits.forEachIndexed { index, commit ->
        progress(index)
        try {
            bridge.ready().importWallets(commit)
            refusals += null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            refusals += userErrorMessage(error)
        }
    }
    rebuildWalletDerivedStateFromCore()
    return refusals
}

/**
 * Add a wallet's key, or watched address, to another network as a wallet of
 * its own. Core reads the secret and seals the copy; the app adopts the new
 * wallet as it does an import's.
 */
suspend fun AppState.copyWallet(commit: WalletCopyCommit): WalletImportOutcome {
    val outcome = bridge.ready().copyWalletToNetwork(commit)
    rebuildWalletDerivedStateFromCore()
    return outcome
}

suspend fun AppState.renameWallet(id: String, newName: String) {
    val completed = walletImport.submit {
        stateCommands.apply(StateCommand.RenameWallet(id, newName))
        null
    }
    if (completed) finishWalletImportFlow()
}
