package dev.spectra.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.spectra.R
import dev.spectra.diagnostics.WalletDiagnosticsState
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.displayName
import dev.spectra.models.formatPercent
import dev.spectra.models.sendOutcomeDetail
import uniffi.spectra_core.AppSettings
import uniffi.spectra_core.LargeMovementEvaluation
import uniffi.spectra_core.PriceAlertCondition
import uniffi.spectra_core.PriceAlertNotification
import uniffi.spectra_core.TransactionRecord
import uniffi.spectra_core.TransactionStatus
import uniffi.spectra_core.TransactionStatusChange

/**
 * What this platform has for telling the user something: a notification.
 *
 * Core decides what is worth telling the user — a price alert, a large
 * portfolio movement, a send reaching a terminal status — and logs it. No
 * view reads anything here, so nothing is kept.
 */
class PlatformNotifications(
    context: Context,
    private val bridge: WalletServiceBridge,
    private val diagnostics: WalletDiagnosticsState,
) {
    private val context = context.applicationContext

    init {
        val manager = this.context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TRANSACTIONS, AppLocalization.string("Transactions"), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_ALERTS, AppLocalization.string("Price Alerts"), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /**
     * Asks once; after the first answer the system replies without asking.
     * Nothing waits on the answer: whether a notification shows is the
     * system's to decide.
     */
    fun requestPermission() {
        if (hasPermission()) return
        val activity = CurrentActivity.get() ?: return
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
    }

    /** A send asks only when an alert that needs the permission is on. */
    fun requestPermissionAfterSend(settings: AppSettings) {
        if (settings.useTransactionStatusNotifications || settings.useLargeMovementNotifications) requestPermission()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(identifier: String, channel: String, title: String, body: String) {
        if (!hasPermission()) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(identifier, 0, notification)
        } catch (error: SecurityException) {
            diagnostics.appendOperationalLog(category = "Notifications", message = error.message ?: "SecurityException")
        }
    }

    fun deliverPriceAlerts(notifications: List<PriceAlertNotification>, amounts: AmountPresentation) {
        notifications.forEach { postPriceAlert(it, amounts) }
    }

    /** One sentence per condition, so each language words both directions. */
    private fun postPriceAlert(notification: PriceAlertNotification, amounts: AmountPresentation) {
        val template = when (notification.condition) {
            PriceAlertCondition.ABOVE -> "%@ on %@ is now %@, above your target of %@."
            PriceAlertCondition.BELOW -> "%@ on %@ is now %@, below your target of %@."
        }
        post(
            identifier = "price-alert-${notification.id}-${System.nanoTime()}",
            channel = CHANNEL_ALERTS,
            title = AppLocalization.format("%@ price alert", notification.symbol),
            body = AppLocalization.format(
                template,
                notification.assetDisplayName,
                notification.chainId.displayName,
                amounts.formattedFiat(notification.livePrice, notification.currency),
                amounts.formattedFiat(notification.targetPrice, notification.currency),
            ),
        )
    }

    fun deliverPortfolioMovement(evaluation: LargeMovementEvaluation, amounts: AmountPresentation) {
        val percent = formatPercent(evaluation.ratio)
        post(
            identifier = "portfolio-movement-${System.nanoTime()}",
            channel = CHANNEL_ALERTS,
            title = AppLocalization.string("Large portfolio movement detected"),
            body = AppLocalization.format(
                if (evaluation.directionUp) {
                    "Your portfolio rose by %@ (%@) since the last sync."
                } else {
                    "Your portfolio fell by %@ (%@) since the last sync."
                },
                amounts.formattedFiat(evaluation.absoluteDelta, evaluation.currency),
                percent,
            ),
        )
    }

    /** A notification for each status change core says is worth one. */
    suspend fun deliverPendingStatusChanges(changes: List<TransactionStatusChange>) {
        for (change in changes) {
            if (!change.statusChanged || !change.notify) continue
            val transaction = runCatching { bridge.ready().transaction(change.id) }.getOrNull() ?: continue
            postTransactionStatus(transaction, change.newStatus)
        }
    }

    private fun postTransactionStatus(transaction: TransactionRecord, newStatus: TransactionStatus) {
        val body = transaction.sendOutcomeDetail(newStatus) ?: return
        val title = if (newStatus == TransactionStatus.CONFIRMED) {
            AppLocalization.format("%@ transaction confirmed", transaction.symbol)
        } else {
            AppLocalization.format("%@ transaction failed", transaction.symbol)
        }
        post("transaction-status-${transaction.id}-$newStatus", CHANNEL_TRANSACTIONS, title, body)
    }

    private companion object {
        const val CHANNEL_TRANSACTIONS = "transactions"
        const val CHANNEL_ALERTS = "alerts"
        const val REQUEST_CODE = 41
    }
}
