package dev.spectra.send

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraBottomActionBar
import dev.spectra.components.SpectraDisclosure
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLoadingRow
import dev.spectra.components.SpectraPage
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import kotlinx.coroutines.launch
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.SendNextStep
import uniffi.spectra_core.TransactionStatus

/** The composer's steps, in order, with the word the step bar gives each. */
private enum class SendFlowStep(val progressTitle: String) {
    FROM("Asset"),
    RECIPIENT("Recipient"),
    AMOUNT("Amount"),
    CONFIRM("Review"),
}

/**
 * The send flow: one page whose steps — the asset, the recipient, the amount
 * and the review — follow each other in place, and the staged transaction's
 * stages once it is built. Back is a step back, never the whole flow: the
 * system gesture would otherwise pop the composer and wipe what was typed.
 */
@Composable
internal fun SendScreen() {
    val app = LocalAppState.current
    val flow = app.sendFlow
    val session = flow.session
    val focusManager = LocalFocusManager.current
    val viewScope = rememberCoroutineScope()
    // A composer opened again starts on its first step.
    var currentStep by rememberSaveable(flow.presentationCount) { mutableStateOf(SendFlowStep.FROM) }
    // The artifact on screen was resumed from the list, not built from the
    // form, which it does not fill: Back returns to that list.
    var isShowingResumedSend by rememberSaveable(flow.presentationCount) { mutableStateOf(false) }
    // Never saved with the page's state: a password lives only while asked for.
    val walletPassword = remember { TextFieldState() }

    val selectedCoin = app.availableSendCoins(flow.walletId).firstOrNull { it.id == flow.holdingKey }
    val isSendBusy = session.isBusy || flow.isPreparingPreview
    // Core's next step for the staged send; none until one is built.
    val nextStep = session.progress?.next
    // A step to go back to: none on the first page, and none once the
    // transaction has gone to a node.
    val canStepBack = currentStep != SendFlowStep.FROM && session.artifact?.attempts?.isEmpty() != false

    fun go(step: SendFlowStep) {
        focusManager.clearFocus()
        currentStep = step
    }

    fun goBack() {
        // Back from a built transaction is the review it was built from: the
        // build is let go (it stays resumable), the form is not. A resumed one
        // goes back to the list it came from.
        if (currentStep == SendFlowStep.CONFIRM && session.artifact != null) {
            flow.invalidateSession()
            if (isShowingResumedSend) {
                isShowingResumedSend = false
                go(SendFlowStep.FROM)
            }
            return
        }
        val previous = SendFlowStep.entries.getOrNull(currentStep.ordinal - 1) ?: return
        go(previous)
    }

    fun startBroadcast() {
        val request = session.id
        app.scope.launch {
            if (!session.isCurrent(request)) return@launch
            app.broadcastPreparedSend()
        }
    }

    fun handlePrimaryAction() {
        when (currentStep) {
            SendFlowStep.FROM -> go(SendFlowStep.RECIPIENT)
            SendFlowStep.RECIPIENT -> go(SendFlowStep.AMOUNT)
            SendFlowStep.AMOUNT -> go(SendFlowStep.CONFIRM)
            SendFlowStep.CONFIRM -> when (nextStep) {
                null -> {
                    isShowingResumedSend = false
                    val request = session.id
                    app.scope.launch {
                        if (!session.isCurrent(request)) return@launch
                        flow.build()
                    }
                }
                SendNextStep.SIGN -> flow.isShowingHighRiskConfirmation = true
                SendNextStep.BROADCAST, SendNextStep.REBROADCAST -> startBroadcast()
                SendNextStep.DONE -> app.cancelSend()
            }
        }
    }

    BackHandler(enabled = canStepBack) { goBack() }
    LaunchedEffect(currentStep) {
        if (currentStep == SendFlowStep.FROM) flow.loadSavedArtifacts()
    }
    LaunchedEffect(flow.isShowingHighRiskConfirmation) {
        if (!flow.isShowingHighRiskConfirmation) walletPassword.clearText()
    }
    DisposableEffect(Unit) {
        onDispose {
            walletPassword.clearText()
            flow.invalidateSession()
            app.sendFlowLeftScreen()
        }
    }

    SpectraPage(
        title = AppLocalization.string(navigationTitle(app, currentStep)),
        onBack = if (canStepBack) ({ goBack() }) else null,
        actions = {
            IconButton(onClick = { app.cancelSend() }) {
                Icon(Icons.Outlined.Close, contentDescription = AppLocalization.string("Close"))
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            ) {
                SendStatusCards()
                SpectraBottomActionBar {
                    FlowBottomBar(
                        app = app,
                        currentStep = currentStep,
                        selectedCoin = selectedCoin,
                        isSendBusy = isSendBusy,
                        onRebroadcast = ::startBroadcast,
                        onPrimary = ::handlePrimaryAction,
                    )
                }
            }
        },
    ) {
        if (session.artifact == null) StepProgress(currentStep)
        if (flow.isPreparingReplacement) {
            SpectraLoadingRow(title = AppLocalization.string("Preparing replacement/cancel context..."))
        }
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                (slideInHorizontally { width -> if (forward) width else -width } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> if (forward) -width else width } + fadeOut())
            },
            label = "send-step",
        ) { step ->
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                when (step) {
                    SendFlowStep.FROM -> {
                        SendFromPage()
                        if (flow.savedArtifacts.isNotEmpty()) {
                            SpectraDisclosure(title = AppLocalization.string("Resume a transaction")) {
                                SavedSendList(flow.savedArtifacts) { artifact ->
                                    viewScope.launch {
                                        if (flow.resume(artifact.id)) {
                                            isShowingResumedSend = true
                                            go(SendFlowStep.CONFIRM)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SendFlowStep.RECIPIENT -> SendRecipientPage()
                    SendFlowStep.AMOUNT -> SendAmountPage()
                    SendFlowStep.CONFIRM -> {
                        val progress = session.progress
                        if (progress != null) SendStagesView(progress) else SendConfirmationStep()
                    }
                }
            }
        }
    }

    if (flow.isShowingHighRiskConfirmation) {
        SigningConfirmationDialog(
            app = app,
            password = walletPassword,
            onCancel = {
                walletPassword.clearText()
                flow.isShowingHighRiskConfirmation = false
            },
            onSign = {
                val password = if (app.stagedSendRequiresPassword) walletPassword.text.toString() else null
                walletPassword.clearText()
                flow.isShowingHighRiskConfirmation = false
                val request = session.id
                app.scope.launch {
                    if (!session.isCurrent(request)) return@launch
                    app.confirmSigning(password)
                }
            },
        )
    }
}

/**
 * The flow's name while it is composed — the step bar and the page's own
 * heading say which step this is — and the stage once it is built, in title
 * case as every top bar title is.
 */
private fun navigationTitle(app: AppState, currentStep: SendFlowStep): String {
    if (currentStep != SendFlowStep.CONFIRM) return "Send"
    val progress = app.sendFlow.session.progress ?: return "Review Transfer"
    return when (progress.next) {
        SendNextStep.SIGN -> "Check and Sign"
        SendNextStep.BROADCAST -> "Submit Transaction"
        SendNextStep.REBROADCAST -> "Submission Results"
        SendNextStep.DONE -> when (progress.transaction?.status) {
            TransactionStatus.CONFIRMED -> "Transfer Complete"
            TransactionStatus.FAILED -> "Transaction Failed"
            TransactionStatus.PENDING, null -> "Waiting for Confirmation"
        }
    }
}

@Composable
private fun StepProgress(currentStep: SendFlowStep) {
    val label = AppLocalization.format("Step %lld of %lld", currentStep.ordinal + 1, SendFlowStep.entries.size)
    val value = AppLocalization.string(currentStep.progressTitle)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpectraLayout.Space.s)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = value
            },
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        SendFlowStep.entries.forEach { step ->
            val color = if (step.ordinal <= currentStep.ordinal) MaterialTheme.colorScheme.primary else secondaryText
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
            ) {
                Icon(
                    when {
                        step.ordinal < currentStep.ordinal -> Icons.Filled.CheckCircle
                        step == currentStep -> Icons.Outlined.RadioButtonChecked
                        else -> Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    AppLocalization.string(step.progressTitle),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (step == currentStep) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RowScope.FlowBottomBar(
    app: AppState,
    currentStep: SendFlowStep,
    selectedCoin: AssetHolding?,
    isSendBusy: Boolean,
    onRebroadcast: () -> Unit,
    onPrimary: () -> Unit,
) {
    val session = app.sendFlow.session
    val progress = session.progress
    val nextStep = progress?.next
    if (progress != null && progress.next == SendNextStep.DONE && progress.canRebroadcast) {
        FilledTonalIconButton(
            onClick = onRebroadcast,
            enabled = !isSendBusy && session.selectedEndpoints.isNotEmpty(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = AppLocalization.string("Retry Same Transaction"))
        }
    }
    val title = when (currentStep) {
        SendFlowStep.FROM, SendFlowStep.RECIPIENT -> "Next"
        SendFlowStep.AMOUNT -> "Review"
        // Three stages, each its own action (spectra's docs/ARCHITECTURE.md):
        // what was built is inspectable before signing, and a signed send
        // waits for the user to choose which nodes receive it.
        SendFlowStep.CONFIRM -> nextStep?.titleKey ?: "Build Transaction"
    }
    val icon = when {
        currentStep != SendFlowStep.CONFIRM -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
        nextStep != null -> nextStep.icon
        else -> buildTransactionIcon
    }
    val showsProgress = currentStep == SendFlowStep.CONFIRM && isSendBusy
    Button(
        onClick = onPrimary,
        enabled = canUsePrimaryAction(app, currentStep, selectedCoin, isSendBusy),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
    ) {
        if (showsProgress) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        SpectraGap()
        Text(AppLocalization.string(title), style = MaterialTheme.typography.titleSmall)
    }
}

private fun canUsePrimaryAction(app: AppState, currentStep: SendFlowStep, selectedCoin: AssetHolding?, isSendBusy: Boolean): Boolean {
    val flow = app.sendFlow
    return when (currentStep) {
        SendFlowStep.FROM -> app.selectedWalletForSend() != null && selectedCoin != null
        SendFlowStep.RECIPIENT -> flow.currentRecipient != null
        SendFlowStep.AMOUNT -> app.sendAmountIsValid && app.sendAmountRefusal == null && flow.previewError == null
        SendFlowStep.CONFIRM -> {
            val progress = flow.session.progress
            if (progress != null) {
                if (isSendBusy) return false
                return when (progress.next) {
                    SendNextStep.BROADCAST -> flow.session.selectedEndpoints.isNotEmpty()
                    SendNextStep.REBROADCAST -> flow.session.selectedEndpoints.isNotEmpty() && progress.canRebroadcast
                    SendNextStep.SIGN, SendNextStep.DONE -> true
                }
            }
            !isSendBusy &&
                app.selectedWalletForSend() != null &&
                selectedCoin != null &&
                flow.currentRecipient != null &&
                app.sendAmountIsValid &&
                app.sendAmountRefusal == null &&
                flow.quoteIsCurrent &&
                flow.customEvmFeeValidationError == null &&
                flow.evmNonceValidationError == null
        }
    }
}

/**
 * Signing's confirmation: what is signed, every review warning, and the
 * wallet's password where it has one. Signing is destructive in the sense
 * that matters — it authorizes the transfer — so the action is drawn in the
 * error colour.
 */
@Composable
private fun SigningConfirmationDialog(
    app: AppState,
    password: TextFieldState,
    onCancel: () -> Unit,
    onSign: () -> Unit,
) {
    val requiresPassword = app.stagedSendRequiresPassword
    val artifact = app.sendFlow.session.artifact
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(AppLocalization.string("Sign this transaction?")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                if (artifact != null) {
                    Text(sendSigningConfirmationMessage(artifact, app.amounts), style = MaterialTheme.typography.bodyMedium)
                }
                if (requiresPassword) {
                    WalletPasswordField(password)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSign,
                enabled = !requiresPassword || password.text.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(AppLocalization.string("Sign Transaction")) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(AppLocalization.string("Cancel")) } },
    )
}
