package dev.spectra.send

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.spectra.app.AppState
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.authenticate
import dev.spectra.app.refreshTransactionProjection
import dev.spectra.components.SpectraBottomActionBar
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraErrorText
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.requiresPassword
import kotlinx.coroutines.launch
import uniffi.spectra_core.SendNextStep
import uniffi.spectra_core.WalletState

/**
 * The password, node and submission sections of a page that reviews one
 * core-built transaction itself, such as an approval revocation or an account
 * closing. Core keeps the artifact and says which step comes next; [session]
 * adopts its answers. Each section is a card in the page's column; the step's
 * button is [SendArtifactStageActionBar], in the page's bottom bar, as a
 * flow's primary action is.
 *
 * [password] is the page's, typed for the signing step only and cleared when
 * that step leaves the screen; [submittedText] is what the page says once the
 * transaction is submitted, until the chain settles it.
 */
@Composable
fun SendArtifactStageSections(
    wallet: WalletState,
    session: SendSession,
    password: TextFieldState,
    submittedText: String,
) {
    val progress = session.progress
    if (progress != null) {
        when (progress.next) {
            SendNextStep.SIGN -> {
                DisposableEffect(Unit) { onDispose { password.clearText() } }
                if (wallet.signing.requiresPassword) SpectraDetailCard { WalletPasswordField(password) }
            }
            SendNextStep.BROADCAST, SendNextStep.REBROADCAST -> {
                SpectraDetailCard { BroadcastNodePicker(session) }
                if (progress.artifact.attempts.isNotEmpty()) {
                    SpectraDetailCard {
                        SendStatusLabel(progress.status)
                        SubmissionResults(progress)
                    }
                }
            }
            SendNextStep.DONE -> SpectraDetailCard {
                SendStatusLabel(progress.status)
                if (!progress.status.isSettled) {
                    Text(submittedText, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                }
                val hash = progress.transaction?.transactionHash ?: progress.artifact.transactionHash
                if (hash != null) TransactionHashRow(hash)
            }
        }
    }
    if (session.isBusy) {
        SpectraDetailCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    session.error?.let { error -> SpectraDetailCard { SpectraErrorText(error) } }
}

/**
 * The bottom bar of a page that draws [SendArtifactStageSections]: the
 * button for core's next step — sign, broadcast or retry — then Done, which
 * [onDone] answers. Nothing until core has built the transaction.
 *
 * [authenticationReason] is why the device asks to authenticate before
 * signing; [canSign] whether the page's own confirmation lets the
 * transaction be signed. A signature or a broadcast started runs on the
 * application's scope: it is the application's to finish, not the page's.
 */
@Composable
fun SendArtifactStageActionBar(
    app: AppState,
    wallet: WalletState,
    session: SendSession,
    password: TextFieldState,
    authenticationReason: String,
    canSign: Boolean = true,
    onDone: () -> Unit,
) {
    val progress = session.progress ?: return
    val needsPassword = wallet.signing.requiresPassword
    SpectraBottomActionBar {
        when (progress.next) {
            SendNextStep.SIGN -> Button(
                onClick = {
                    val secret = if (needsPassword) password.text.toString() else null
                    app.scope.launch {
                        session.sign(
                            password = secret,
                            authenticate = { app.authenticate(DeviceAuthenticationAction.SEND, authenticationReason) },
                            sign = { id, digest, walletPassword -> app.bridge.ready().signSend(id, digest, walletPassword) },
                        )
                        password.clearText()
                    }
                },
                enabled = canSign && !session.isBusy && (!needsPassword || password.text.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(AppLocalization.string(SendNextStep.SIGN.titleKey)) }
            SendNextStep.BROADCAST, SendNextStep.REBROADCAST -> Button(
                onClick = {
                    app.scope.launch {
                        val submitted = session.broadcast { id, endpoints -> app.bridge.ready().broadcastSend(id, endpoints) }
                        if (submitted != null) app.refreshTransactionProjection()
                    }
                },
                enabled = !session.isBusy && session.selectedEndpoints.isNotEmpty() &&
                    !(progress.next == SendNextStep.REBROADCAST && !progress.canRebroadcast),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(AppLocalization.string(progress.next.titleKey)) }
            SendNextStep.DONE -> Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(AppLocalization.string("Done"))
            }
        }
    }
}
