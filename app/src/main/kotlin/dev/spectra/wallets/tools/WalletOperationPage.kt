package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.ReviewWarnings
import dev.spectra.components.SpectraBottomActionBar
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.models.AppLocalization
import dev.spectra.models.reviewWarningMessages
import dev.spectra.send.SendArtifactStageActionBar
import dev.spectra.send.SendArtifactStageSections
import dev.spectra.send.SendSession
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.WalletState

/**
 * One transaction a wallet tool builds — a revocation, a closing, a merge,
 * an NFT sent, private funds moved — from what it asks for to its
 * broadcast: the form, when the operation needs input; core's build,
 * summarised as the tool describes it, with the fee and the warnings core's
 * review carries; then the stages every transaction goes through. Each tool
 * says only what is its own.
 *
 * Pushed on the wallet's tool page. Back is Cancel until the transaction is
 * out of the stages' hands; then the bottom bar's Done closes it.
 */
@Composable
internal fun WalletOperationPage(
    wallet: WalletState,
    title: String,
    /** Why the device asks to authenticate before signing. */
    authenticationReason: String,
    /** What the page says once the transaction is submitted. */
    submittedText: String,
    /**
     * Whether the form holds what the build needs; `null` for an operation
     * that asks for nothing, which is built as the page opens.
     */
    formIsComplete: Boolean? = null,
    /** Whether the page's own confirmation lets the transaction be signed. */
    canSign: Boolean = true,
    /** The nodes the signed transaction can go to, when not the network's send endpoints. */
    endpoints: (suspend (Chain) -> List<String>)? = null,
    /** Core's build of the operation. */
    build: suspend () -> SendArtifact,
    fields: @Composable ColumnScope.() -> Unit = {},
    /**
     * What the operation does: shown from the start when it asks for
     * nothing, without an artifact until core has built it, and in place of
     * the form once it has.
     */
    summary: @Composable ColumnScope.(SendArtifact?) -> Unit,
) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val session = remember { SendSession(app.bridge) }
    // Typed for the signing step only; never saved with the page.
    val password = remember { TextFieldState() }
    val currentBuild by rememberUpdatedState(build)
    val currentEndpoints by rememberUpdatedState(endpoints)

    suspend fun load() {
        session.load(
            operation = SendSession.Operation.BUILD,
            prepare = { currentBuild() },
            loadEndpoints = { chain -> currentEndpoints?.invoke(chain) ?: app.bridge.ready().sendEndpoints(chain) },
        )
    }

    LaunchedEffect(Unit) { if (formIsComplete == null) load() }

    SpectraPage(
        title = title,
        bottomBar = {
            if (session.artifact == null && formIsComplete != null) {
                SpectraBottomActionBar {
                    Button(
                        onClick = { scope.launch { load() } },
                        enabled = formIsComplete && !session.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(AppLocalization.string("Review")) }
                }
            } else {
                SendArtifactStageActionBar(
                    app = app,
                    wallet = wallet,
                    session = session,
                    password = password,
                    authenticationReason = authenticationReason,
                    canSign = canSign,
                    onDone = { navigator.pop() },
                )
            }
        },
    ) {
        val artifact = session.artifact
        if (artifact != null) {
            summary(artifact)
            artifact.review.networkFee?.let { fee ->
                ToolSection {
                    SpectraValueRow(
                        label = AppLocalization.string("Network Fee"),
                        value = app.amounts.reviewedNetworkFee(fee, artifact.chainId),
                    )
                }
            }
            if (artifact.reviewWarningMessages.isNotEmpty()) {
                ToolSection { ReviewWarnings(artifact) }
            }
        } else if (formIsComplete != null) {
            fields()
        } else {
            summary(null)
        }
        SendArtifactStageSections(wallet = wallet, session = session, password = password, submittedText = submittedText)
    }
}
