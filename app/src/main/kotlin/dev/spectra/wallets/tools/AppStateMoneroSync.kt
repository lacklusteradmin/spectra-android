package dev.spectra.wallets.tools

import dev.spectra.app.AppState
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.authenticate
import dev.spectra.app.refreshBalances
import dev.spectra.app.runToCompletion
import dev.spectra.models.AppLocalization
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import uniffi.spectra_core.MoneroSyncStatus

suspend fun AppState.moneroSyncStatus(walletId: String): MoneroSyncStatus? = bridge.ready().moneroSyncStatus(walletId)

/**
 * Scan a Monero wallet to the chain tip on this device, one core batch at a
 * time, reporting each batch's status. `null` once scanned; otherwise why it
 * stopped. A cancelled scan ends with the batch in flight: core persists
 * every completed batch, so it resumes where it left off. The first batch
 * starts at the restore height the wallet was imported with.
 */
suspend fun AppState.syncMoneroWallet(
    walletId: String,
    password: String?,
    progress: (MoneroSyncStatus) -> Unit,
): String? {
    authenticate(DeviceAuthenticationAction.SEND, AppLocalization.string("Authorize local wallet sync"))?.let { return it }
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val status = runToCompletion { bridge.ready().syncMoneroWallet(walletId, password) }
            // A batch that finished after the scan was cancelled is kept by
            // core, but no longer reported.
            currentCoroutineContext().ensureActive()
            progress(status)
            if (status.complete) break
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        return userErrorMessage(failure)
    }
    refreshBalances()
    return null
}
