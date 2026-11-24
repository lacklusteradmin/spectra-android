package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.LocalAppState
import dev.spectra.app.authenticate
import dev.spectra.app.readOrNull
import dev.spectra.components.CopyButton
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import dev.spectra.wallets.title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.MessageScheme
import uniffi.spectra_core.SignedMessage
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.verifyMessage

/** The network's message-signing scheme, by the name its wallets give it. */
internal val MessageScheme.title: String
    get() = AppLocalization.string(
        when (this) {
            MessageScheme.SIGNED_MESSAGE -> "Signed message"
            MessageScheme.BIP322 -> "BIP-322"
            MessageScheme.PERSONAL_SIGN -> "EIP-191 personal_sign"
            MessageScheme.TRON_SIGNED_MESSAGE -> "TIP-191"
            MessageScheme.SOLANA_MESSAGE -> "Solana message"
            MessageScheme.SUI_PERSONAL_MESSAGE -> "Sui personal message"
            MessageScheme.SUBSTRATE_BYTES -> "Substrate signRaw"
            MessageScheme.STELLAR_SIGNED_MESSAGE -> "SEP-53"
            MessageScheme.CARDANO_DATA_SIGNATURE -> "CIP-8 data signature"
            MessageScheme.KASPA_PERSONAL_MESSAGE -> "Kaspa personal message"
            MessageScheme.MONERO_SIGNATURE -> "Monero message signature"
        },
    )

private enum class MessageMode { SIGN, VERIFY }

/**
 * Signing a message with a wallet's key to prove it holds its address, and
 * checking a signature against the address. Core signs and checks in the
 * network's scheme; a watched wallet can only check.
 */
@Composable
internal fun WalletMessageView(wallet: WalletState, canSign: Boolean) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(if (canSign) MessageMode.SIGN else MessageMode.VERIFY) }
    var scheme by remember { mutableStateOf<MessageScheme?>(null) }
    var message by remember { mutableStateOf("") }
    var signatureInput by remember { mutableStateOf("") }
    // Typed for one signature; never saved with the page.
    val password = remember { TextFieldState() }
    var signed by remember { mutableStateOf<SignedMessage?>(null) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val address = wallet.address ?: ""
    val needsPassword = wallet.signing.requiresPassword

    LaunchedEffect(wallet.id) {
        scheme = readOrNull { app.bridge.ready().walletMessageScheme(wallet.id) }
    }

    fun sign() {
        scope.launch {
            isWorking = true
            error = null
            try {
                val failure = app.authenticate(
                    DeviceAuthenticationAction.SEND,
                    AppLocalization.format("Authenticate to sign a message with %@", wallet.name),
                )
                if (failure != null) {
                    error = failure
                    return@launch
                }
                signed = app.bridge.ready().signWalletMessage(wallet.id, message, if (needsPassword) password.text.toString() else null)
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

    SpectraPage(title = (if (canSign) WalletAction.SIGN_MESSAGE else WalletAction.VERIFY_MESSAGE).title) {
        if (canSign) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(MessageMode.SIGN to "Sign", MessageMode.VERIFY to "Verify").forEachIndexed { index, (option, key) ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = {
                            if (mode != option) error = null
                            mode = option
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(AppLocalization.string(key)) }
                }
            }
        }
        ToolSection(title = AppLocalization.string("Address")) {
            SelectionContainer {
                Text(
                    address,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = secondaryText,
                )
            }
            scheme?.let { Text(it.title, style = MaterialTheme.typography.bodySmall, color = secondaryText) }
        }
        ToolSection(title = AppLocalization.string("Message")) {
            OutlinedTextField(
                value = message,
                onValueChange = {
                    message = it
                    signed = null
                    verdict = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
        }
        when (mode) {
            MessageMode.SIGN -> {
                if (needsPassword) {
                    ToolSection { WalletPasswordField(password) }
                }
                ToolSection(
                    footer = AppLocalization.string(
                        "Only plain text is signed. The signature proves this wallet holds the address; it authorises nothing.",
                    ),
                ) {
                    Button(
                        onClick = { sign() },
                        enabled = !isWorking && message.isNotEmpty() && !(needsPassword && password.text.isEmpty()),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                            Text(AppLocalization.string("Sign Message"))
                            if (isWorking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                signed?.let { result ->
                    ToolSection(title = AppLocalization.string("Signature")) {
                        SelectionContainer {
                            Text(result.signature, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                        CopyButton(value = result.signature, title = AppLocalization.string("Copy"))
                    }
                }
            }
            MessageMode.VERIFY -> {
                ToolSection(title = AppLocalization.string("Signature")) {
                    OutlinedTextField(
                        value = signatureInput,
                        onValueChange = {
                            signatureInput = it
                            verdict = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(AppLocalization.string("Signature")) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    )
                    FilledTonalButton(
                        onClick = { verdict = verifyMessage(wallet.chainId, address, message, signatureInput) },
                        enabled = signatureInput.isNotEmpty(),
                    ) { Text(AppLocalization.string("Verify")) }
                }
                verdict?.let { valid ->
                    ToolSection {
                        SpectraLabel(
                            text = AppLocalization.string(
                                if (valid) "This address signed this message." else "This address did not sign this message.",
                            ),
                            icon = if (valid) Icons.Outlined.Verified else Icons.Outlined.GppBad,
                            color = if (valid) SpectraColors.semantic.success else SpectraColors.semantic.failure,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        error?.let { WalletToolErrorSection(it) }
    }
}
