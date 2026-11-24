package dev.spectra.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.LocalAppState
import dev.spectra.app.authenticate
import dev.spectra.app.readOrNull
import dev.spectra.components.QRCodeImage
import dev.spectra.components.SecureWindow
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraErrorText
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.breakableAnywhere
import dev.spectra.components.copySecretToClipboard
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletKeyExport
import uniffi.spectra_core.WalletKeyKind

val WalletKeyKind.title: String
    get() = AppLocalization.string(
        when (this) {
            WalletKeyKind.PRIVATE_KEY -> "Private Key"
            WalletKeyKind.MONERO_SPEND_KEY -> "Spend Key"
            WalletKeyKind.MONERO_VIEW_KEY -> "View Key"
            WalletKeyKind.ACCOUNT_PUBLIC_KEY -> "Account Public Key"
        },
    )

val WalletKeyKind.subtitle: String
    get() = AppLocalization.string(
        when (this) {
            WalletKeyKind.PRIVATE_KEY -> "Signs for this wallet in any wallet that imports it."
            WalletKeyKind.MONERO_SPEND_KEY -> "With the view key and the address, restores this wallet."
            WalletKeyKind.MONERO_VIEW_KEY -> "With the address, shows this wallet's funds without spending them."
            WalletKeyKind.ACCOUNT_PUBLIC_KEY -> "Watches every address of this account without spending."
        },
    )

/** Whether the key can sign: shown only after a warning, never as a QR. */
val WalletKeyKind.spends: Boolean get() = this == WalletKeyKind.PRIVATE_KEY || this == WalletKeyKind.MONERO_SPEND_KEY

/**
 * A wallet's keys, as other wallets import them. Core lists what this wallet
 * can export and writes each in its network's encoding; every export is shown
 * after device authentication, and a copy stays on this device for a minute.
 */
@Composable
internal fun WalletKeyExportPage(walletId: String) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val wallet = app.wallet(walletId)
    var kinds by remember { mutableStateOf<List<WalletKeyKind>>(emptyList()) }
    // Never saved with the page: a password lives only while the page is shown.
    val password = remember { TextFieldState() }
    var shown by remember { mutableStateOf<WalletKeyExport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    SecretLifecycleEffect(onStop = {
        password.clearText()
        shown = null
    })
    DisposableEffect(Unit) {
        onDispose {
            password.clearText()
            shown = null
        }
    }
    LaunchedEffect(walletId) {
        kinds = readOrNull { app.bridge.ready().walletKeyExports(walletId) } ?: emptyList()
    }
    if (wallet == null) {
        LaunchedEffect(walletId) { app.dismissWalletPages(walletId) }
        SpectraPage(title = WalletAction.EXPORT_KEYS.title) {}
        return
    }
    val needsPassword = wallet.signing.requiresPassword

    fun export(kind: WalletKeyKind) {
        scope.launch {
            isWorking = true
            error = null
            try {
                app.authenticate(
                    DeviceAuthenticationAction.SECRET_MATERIAL,
                    AppLocalization.format("Authenticate to export a key of %@", wallet.name),
                )?.let { failure ->
                    error = failure
                    return@launch
                }
                shown = app.bridge.ready().exportWalletKey(walletId, kind, if (needsPassword) password.text.toString() else null)
                password.clearText()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = userErrorMessage(failure)
            } finally {
                isWorking = false
            }
        }
    }

    SpectraPage(title = WalletAction.EXPORT_KEYS.title) {
        SpectraRowSection(
            footer = AppLocalization.string(
                "Anyone with a private or spend key controls this wallet's funds; anyone with a view or account key sees all of its activity.",
            ),
        ) {
            kinds.forEach { kind ->
                row {
                    val enabled = !isWorking && !(needsPassword && password.text.isEmpty())
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled, role = Role.Button) { export(kind) }
                            .spectraRowPadding(),
                        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs),
                    ) {
                        Text(
                            kind.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else secondaryText,
                        )
                        Text(kind.subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                }
            }
        }
        if (needsPassword) {
            SpectraDetailCard {
                WalletPasswordField(password, label = null, placeholder = AppLocalization.string("Wallet Password"))
            }
        }
        error?.let { SpectraDetailCard { SpectraErrorText(it) } }
    }

    shown?.let { export ->
        SecureWindow()
        KeyExportSheet(export) { shown = null }
    }
}

@Composable
private fun KeyExportSheet(export: WalletKeyExport, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var didCopy by remember(export) { mutableStateOf(false) }
    SecretSheet(title = export.kind.title, onDismiss = onDismiss) {
        SpectraDetailCard {
            Text(export.format.title, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
            if (!export.kind.spends) {
                QRCodeImage(
                    export.value,
                    modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                )
            }
            Text(
                breakableAnywhere(export.value),
                modifier = Modifier
                    .fillMaxWidth()
                    .spectraInsetFill()
                    .padding(SpectraLayout.Space.m),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            FilledTonalButton(onClick = {
                copySecretToClipboard(context, export.value)
                didCopy = true
            }) {
                Icon(
                    if (didCopy) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                SpectraGap()
                Text(
                    AppLocalization.string(if (didCopy) "Copied" else "Copy"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            SpectraHintText(AppLocalization.string("A copied key stays on this device and leaves the clipboard after a minute."))
        }
    }
}
