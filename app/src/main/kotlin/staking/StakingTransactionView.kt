package dev.spectra.staking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.CopyButton
import dev.spectra.components.ReviewWarnings
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.groupedAddress
import dev.spectra.components.openExternalUrl
import dev.spectra.components.secondaryText
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.explorerLink
import dev.spectra.models.coinSymbol
import dev.spectra.models.localizedFailureReason
import dev.spectra.models.requiresPassword
import dev.spectra.send.BroadcastNodePicker
import dev.spectra.send.SendStatusLabel
import dev.spectra.send.SubmissionResults
import dev.spectra.send.TransactionHashRow
import dev.spectra.send.isSettled
import dev.spectra.send.titleKey
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendNextStep
import uniffi.spectra_core.SendProgress
import uniffi.spectra_core.SendStatus
import uniffi.spectra_core.displayAddress

/**
 * Signing and submission are explicit steps over one immutable core review;
 * which of them, and whether a check or a repair is offered, is core's
 * `SendProgress`. Signing and broadcasting each ask for confirmation first:
 * [onSign] and [onBroadcast] open the page's dialogs.
 */
@Composable
internal fun ColumnScope.StakingTransactionView(
    staking: StakingState,
    progress: SendProgress,
    onSign: () -> Unit,
    onBroadcast: () -> Unit,
) {
    val app = LocalAppState.current
    val context = LocalContext.current
    val artifact = progress.artifact
    val canBroadcast = progress.next == SendNextStep.BROADCAST ||
        (progress.next == SendNextStep.REBROADCAST && progress.canRebroadcast)
    val needsReadPassword = staking.rules.positionsRequireAuthorization &&
        (app.wallet(artifact.walletId)?.signing?.requiresPassword ?: true)
    val readPasswordMissing = needsReadPassword && staking.password.text.isEmpty()

    ReviewCard(staking, artifact)
    StatusCard(progress)
    if (canBroadcast) {
        SpectraDetailCard { BroadcastNodePicker(staking.session, enabled = !staking.isBusy) }
    }
    if (artifact.review.staking != null && progress.next == SendNextStep.SIGN) {
        SpectraHintText(AppLocalization.string("staking.review_before_signing"))
        Button(
            onClick = onSign,
            enabled = !staking.isBusy && artifact.review.networkFee != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(AppLocalization.string(SendNextStep.SIGN.titleKey)) }
    } else if (canBroadcast) {
        Button(
            onClick = onBroadcast,
            enabled = !staking.isBusy && staking.session.selectedEndpoints.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(AppLocalization.string(progress.next.titleKey)) }
    }
    if (progress.canRecheck) {
        FilledTonalButton(
            onClick = { staking.begin(StakingState.Operation.Recheck) },
            enabled = !staking.isBusy && !readPasswordMissing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(AppLocalization.string("staking.check_status")) }
    }
    if (progress.canRepair) {
        SpectraHintText(AppLocalization.string("staking.repair_explanation"))
        FilledTonalButton(
            onClick = { staking.begin(StakingState.Operation.Repair) },
            enabled = !staking.isBusy && !readPasswordMissing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(AppLocalization.string("staking.repair")) }
    }
    progress.transaction?.explorerLink?.let { (_, url) ->
        FilledTonalButton(onClick = { openExternalUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
            Text(AppLocalization.string("View in block explorer"))
        }
    }
    if (staking.isBusy) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.5.dp)
    }
    staking.stepError?.let { StakingErrorText(it) }
    OutlinedButton(
        onClick = { staking.startStake() },
        enabled = !staking.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(AppLocalization.string("staking.close_review")) }
}

@Composable
private fun ReviewCard(staking: StakingState, artifact: SendArtifact) {
    val app = LocalAppState.current
    SpectraDetailCard(title = AppLocalization.string("staking.review"), elevated = true) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                artifact.staking?.let { intent ->
                    SpectraValueRow(label = AppLocalization.string("staking.action"), value = intent.action.localizedTitle)
                    SpectraValueRow(
                        label = AppLocalization.string("Amount"),
                        value = AmountPresentation.exactAmount(artifact.amount, artifact.symbol),
                    )
                    if (intent.amount == null) SpectraHintText(AppLocalization.string("staking.full_position_action"))
                    intent.validatorId?.let { validator ->
                        val name = staking.validatorName(validator)
                        if (name != null) {
                            SpectraValueRow(label = AppLocalization.string("staking.validator"), value = name)
                            Text(
                                validator,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = secondaryText,
                            )
                        } else {
                            SpectraValueRow(label = AppLocalization.string("staking.validator_identifier"), value = validator)
                        }
                    }
                    intent.positionId?.let { SpectraValueRow(label = AppLocalization.string("staking.position"), value = it) }
                    artifact.review.staking?.lockupSeconds?.let {
                        SpectraValueRow(
                            label = AppLocalization.string("staking.dissolve_delay"),
                            value = AppLocalization.format("staking.seconds", it.toLong()),
                        )
                    }
                }
                SpectraValueRow(
                    label = AppLocalization.string("Wallet"),
                    value = app.wallet(artifact.walletId)?.name ?: artifact.walletId,
                )
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    Text(AppLocalization.string("From"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                        // Grouped for comparing; copied whole in core's display form.
                        val shown = displayAddress(artifact.chainId, artifact.sender)
                        Text(
                            groupedAddress(shown),
                            modifier = Modifier.weight(1f).semantics { contentDescription = artifact.sender },
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                        CopyButton(value = shown)
                    }
                }
                CoreReferenceTables.stakingEntry(artifact.chainId)?.let {
                    SpectraValueRow(label = AppLocalization.string("Unbonding"), value = AppLocalization.string(it.unbondingPeriod))
                }
                val review = artifact.review.staking
                if (review != null) {
                    if (review.fundingAlreadyCompleted) SpectraHintText(AppLocalization.string("staking.funding_completed"))
                    // With its display-currency value and its bound, as a send's fee is shown.
                    artifact.review.networkFee?.let { fee ->
                        SpectraValueRow(
                            label = AppLocalization.string("Network Fee"),
                            value = app.amounts.reviewedNetworkFee(fee, artifact.chainId),
                        )
                    }
                    if (review.feeIsDeductedFromAmount) SpectraHintText(AppLocalization.string("staking.fee_deducted"))
                    review.refundableDeposit?.let {
                        SpectraValueRow(
                            label = AppLocalization.string("staking.refundable_deposit"),
                            value = AmountPresentation.exactAmount(it, artifact.chainId.coinSymbol),
                        )
                    }
                    if (review.rewardPayoutIsDelayed) SpectraHintText(AppLocalization.string("staking.delayed_reward_payout"))
                } else {
                    Text(
                        AppLocalization.string("staking.review_unavailable"),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpectraColors.semantic.failure,
                    )
                }
                ReviewWarnings(artifact)
                SpectraHintText(AppLocalization.string("staking.saved_review"))
            }
        }
    }
}

@Composable
private fun StatusCard(progress: SendProgress) {
    SpectraDetailCard {
        SendStatusLabel(progress.status)
        progress.transaction?.localizedFailureReason?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SpectraColors.semantic.failure)
        }
        if (progress.status == SendStatus.AWAITING_CONFIRMATION) {
            Text(AppLocalization.string("staking.submission_pending"), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
        if (progress.artifact.attempts.isNotEmpty()) {
            SpectraDivider()
            SubmissionResults(progress)
        }
        // The results name the hash until the chain settles it; a staking
        // page has no receipt to name it after.
        val hash = progress.transaction?.transactionHash ?: progress.artifact.transactionHash
        if (progress.status.isSettled && hash != null) {
            SpectraDivider()
            TransactionHashRow(hash)
        }
    }
}
