package dev.spectra.wallets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.spectra.app.AppState
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.breakableAnywhere
import dev.spectra.models.AppLocalization
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import uniffi.spectra_core.WalletSetupMethod

/** What the preview last derived, or why it could not. */
private sealed interface AddressPreview {
    data class Ready(val addresses: List<String>, val rejected: List<String>, val upgrades: String?) : AddressPreview
    data class Failed(val message: String) : AddressPreview
}

/**
 * The address core will store for the form as it stands, from the planning
 * the import itself runs, so the two cannot disagree. It follows the inputs
 * a moment after they settle and derives on core's worker; nothing is sent
 * anywhere and nothing is stored.
 */
@Composable
internal fun WalletAddressPreviewCard(app: AppState, draft: WalletImportDraft) {
    var preview by remember { mutableStateOf<AddressPreview?>(null) }
    val commit = draft.previewCommit
    // Restarted whenever the commit changes, so a burst of typing derives once, after it stops.
    LaunchedEffect(commit) {
        if (commit == null) {
            preview = null
            return@LaunchedEffect
        }
        delay(300)
        preview = try {
            val result = app.bridge.ready().previewWalletImport(commit)
            AddressPreview.Ready(result.addresses, result.rejectedAddresses, result.upgradesWallet)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AddressPreview.Failed(userErrorMessage(error))
        }
    }
    val shown = preview ?: return
    SpectraDetailCard {
        when (shown) {
            is AddressPreview.Ready -> {
                Text(
                    AppLocalization.string(if (shown.addresses.size == 1) "Address" else "Addresses"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                // Wrapped without a layout hyphen, which a TON address could
                // hold for real; not selectable, since a copy would carry the
                // break marks.
                shown.addresses.forEach { address ->
                    Text(
                        breakableAnywhere(address),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                SpectraHintText(previewCaption(draft))
                shown.upgrades?.let { upgrades ->
                    SpectraLabel(
                        text = AppLocalization.format("This adds keys to the watched wallet “%@”, keeping its history.", upgrades),
                        icon = Icons.Outlined.Key,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    )
                }
                if (shown.rejected.isNotEmpty()) {
                    Text(
                        AppLocalization.format(
                            "These lines are not valid addresses and will not be imported: %@",
                            shown.rejected.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is AddressPreview.Failed -> SpectraLabel(
                text = shown.message,
                icon = Icons.Outlined.WarningAmber,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

private fun previewCaption(draft: WalletImportDraft): String = when (draft.method) {
    WalletSetupMethod.CREATE_PHRASE -> AppLocalization.string("Your new wallet receives at this address.")
    WalletSetupMethod.WATCH_ADDRESSES, WalletSetupMethod.WATCH_ACCOUNT_XPUB,
    WalletSetupMethod.WATCH_VIEW_KEY, WalletSetupMethod.WATCH_MULTISIG,
    -> AppLocalization.string("Spectra will watch what is listed here.")
    else -> AppLocalization.string("Check that it matches the wallet you are restoring before you continue.")
}
