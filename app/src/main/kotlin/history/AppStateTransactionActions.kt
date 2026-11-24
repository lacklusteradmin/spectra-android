package dev.spectra.history

import dev.spectra.app.AppState
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.authenticate
import dev.spectra.app.refreshTransactionProjection
import dev.spectra.models.AppLocalization
import dev.spectra.models.localizedTitle
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.TransactionStatus

/** Ask the network about [transactionId] again; what came of it, worded for the page. */
suspend fun AppState.retryUTXOTransactionStatus(transactionId: String): String = try {
    val change = bridge.ready().recheckTransactionStatus(transactionId)
    refreshTransactionProjection()
    notifications.deliverPendingStatusChanges(listOf(change))
    when {
        change.statusChanged -> AppLocalization.format("Status updated: %@.", change.newStatus.localizedTitle)
        change.newStatus == TransactionStatus.PENDING ->
            AppLocalization.string("No confirmation yet. Spectra will keep retrying automatically.")
        else -> AppLocalization.string("Transaction is confirmed.")
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    // Core logs the failed recheck.
    diagnostics.loadFromSQLite()
    userErrorMessage(error)
}

/**
 * Send the signed [transactionId] to its network again, after the device
 * owner allows it as they would a send; what came of it, worded for the page.
 */
suspend fun AppState.rebroadcastSignedTransaction(transactionId: String): String {
    authenticate(DeviceAuthenticationAction.SEND, AppLocalization.string("Authorize transaction rebroadcast"))
        ?.let { return it }
    return try {
        val transactionHash = bridge.ready().rebroadcastTransaction(transactionId)
        refreshTransactionProjection()
        AppLocalization.format("Transaction rebroadcasted: %@. Network confirmation is pending.", transactionHash)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        userErrorMessage(error)
    }
}
