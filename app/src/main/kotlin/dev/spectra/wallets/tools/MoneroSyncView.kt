package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.formatNumber
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import dev.spectra.wallets.title
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.MoneroSyncStatus
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletState

/** A cancellable projection of core's durable scan. No keys or scan state live here. */
@Composable
internal fun MoneroSyncView(walletId: String) {
    val app = LocalAppState.current
    val moneroSync = remember(walletId) { MoneroSyncState() }
    var statusAttempt by remember { mutableIntStateOf(0) }
    val status = moneroSync.status

    LaunchedEffect(walletId, statusAttempt) {
        moneroSync.error = null
        try {
            moneroSync.status = app.moneroSyncStatus(walletId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            moneroSync.error = userErrorMessage(failure)
        }
    }
    LaunchedEffect(moneroSync.requestId) {
        val request = moneroSync.requestId ?: return@LaunchedEffect
        moneroSync.sync(request) { password, progress -> app.syncMoneroWallet(walletId, password, progress) }
    }
    DisposableEffect(moneroSync) { onDispose { moneroSync.cancel() } }

    // Always drawn: a status that cannot be read is said here, with a way to
    // ask again, rather than leaving the page empty.
    SpectraDetailCard {
        Text(AppLocalization.string("Local Monero Wallet"), style = MaterialTheme.typography.titleMedium)
        SpectraHintText(AppLocalization.string("Scanning and signing happen on this device. Keys stay on this device."))
        if (status != null) {
            ScanProgress(status, restoreHeight = app.wallet(walletId)?.restoreHeight)
            if (!status.spendsKnown) {
                SpectraHintText(
                    AppLocalization.string(
                        "A view key shows what the wallet receives, not what it spends: its balance does not drop when it pays from another device.",
                    ),
                )
            }
            if (moneroSync.isRunning) {
                FilledTonalButton(onClick = { moneroSync.cancel() }) { Text(AppLocalization.string("Cancel")) }
            } else {
                if (app.wallet(walletId)?.signing?.requiresPassword ?: true) {
                    WalletPasswordField(moneroSync.password)
                }
                Button(onClick = { moneroSync.begin() }) { Text(AppLocalization.string("Sync Local Wallet")) }
            }
        } else if (moneroSync.error == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
        moneroSync.error?.let { error ->
            SpectraLabel(text = error, icon = Icons.Outlined.WarningAmber, color = SpectraColors.semantic.failure)
            if (status == null) {
                FilledTonalButton(onClick = { statusAttempt += 1 }) { Text(AppLocalization.string("Retry")) }
            }
        }
    }
}

/**
 * How far the scan has come, in blocks from where it started — the wallet's
 * restore height — to the chain's tip.
 */
@Composable
private fun ScanProgress(status: MoneroSyncStatus, restoreHeight: ULong?) {
    val start = minOf(restoreHeight ?: 0uL, status.scannedHeight)
    val total = if (status.targetHeight > start) (status.targetHeight - start).toDouble() else 0.0
    val done = (status.scannedHeight - start).toDouble()
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        if (total > 0) {
            LinearProgressIndicator(progress = { (minOf(done, total) / total).toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            AppLocalization.format(
                "monero.scan.blocks_format",
                formatNumber(status.scannedHeight.toLong()),
                formatNumber(status.targetHeight.toLong()),
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = secondaryText,
        )
    }
}

/**
 * A scanning wallet's page for its scan: where the scan started, set when
 * the wallet was added, and the scan itself.
 */
@Composable
internal fun WalletBlockScanView(wallet: WalletState) {
    SpectraPage(title = WalletAction.SCAN_BLOCKS.title) {
        wallet.restoreHeight?.let { height ->
            SpectraDetailCard {
                SpectraValueRow(label = AppLocalization.string("Restore Height"), value = height.toString())
            }
        }
        MoneroSyncView(walletId = wallet.id)
    }
}
