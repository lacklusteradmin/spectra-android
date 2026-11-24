package dev.spectra.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.pendingTransactionRefreshStatusText
import dev.spectra.components.CopyButton
import dev.spectra.components.ReviewWarnings
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDisclosure
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.breakableAnywhere
import dev.spectra.components.openExternalUrl
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.entry
import dev.spectra.models.explorerLink
import dev.spectra.models.gasTokenSymbol
import dev.spectra.models.localizedFailureReason
import dev.spectra.models.localizedTitle
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.reviewWarningMessages
import uniffi.spectra_core.AssetTransferTerms
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendNextStep
import uniffi.spectra_core.SendProgress
import uniffi.spectra_core.SendStage
import uniffi.spectra_core.SendStatus
import uniffi.spectra_core.TransactionStatus
import uniffi.spectra_core.preparedFields

/**
 * A projection of core's durable artifact, its history record and the step
 * core allows next. Node acceptance describes submission only; the history
 * record alone supplies confirmation.
 */
@Composable
internal fun SendStagesView(progress: SendProgress) {
    val app = LocalAppState.current
    val artifact = progress.artifact
    val hasFinalHistoryStatus = progress.status.isSettled
    // Nodes are chosen up front when submitting is the next step; otherwise a
    // rebroadcast core allows waits behind a disclosure.
    val showsImmediateNodeChoice = progress.next == SendNextStep.BROADCAST ||
        (progress.next == SendNextStep.REBROADCAST && progress.canRebroadcast)
    val showsRetryNodeChoice = progress.canRebroadcast && !showsImmediateNodeChoice

    if (!hasFinalHistoryStatus) StageProgress(artifact)
    SendAmountSummaryView(
        artworkName = nativeChainBadge(artifact.chainId)?.artworkName,
        symbol = artifact.symbol,
        chain = artifact.chainId,
        amount = artifact.amount,
        status = progress.status,
        isReceipt = progress.status == SendStatus.CONFIRMED,
    )
    artifact.review.transferTerms?.let { TransferTerms(artifact, it) }

    if (showsImmediateNodeChoice) StagesCard { BroadcastNodePicker(app.sendFlow.session) }

    StagesCard {
        SendTransferPartiesView(
            walletId = artifact.walletId,
            chain = artifact.chainId,
            sender = artifact.sender,
            recipient = artifact.recipient,
            memo = artifact.memo,
            // Offered once the send is on record, named for what it sent; the
            // dialog asks before anything is saved.
            suggestedContactName = progress.transaction?.let { AppLocalization.format("%@ Recipient", artifact.symbol) },
        )
        // What the quote the send was built from estimated, as the review put
        // it. A receipt states the fee paid instead.
        val fee = artifact.review.networkFee
        if (!hasFinalHistoryStatus && fee != null) {
            SpectraDivider()
            SendCostRows(
                fee = app.amounts.reviewedNetworkFee(fee, artifact.chainId),
                total = artifact.review.total?.let {
                    "≈ " + app.amounts.formattedAssetAmount(it, artifact.symbol, artifact.chainId.entry?.nativeDeploymentId)
                },
            )
        }
    }

    if (artifact.attempts.isEmpty()) ReviewWarnings(artifact)

    if (hasFinalHistoryStatus) {
        TransactionReceipt(progress)
        if (artifact.attempts.isNotEmpty()) {
            SpectraDisclosure(title = AppLocalization.string("View submission results")) {
                StagesCard { SubmissionResults(progress) }
            }
        }
    } else if (artifact.attempts.isNotEmpty()) {
        StagesCard { SubmissionResults(progress) }
    }

    TechnicalDetails(artifact)
    StageNotice(progress)

    if (showsRetryNodeChoice) {
        SpectraDisclosure(title = AppLocalization.string("Retry submission")) {
            StagesCard { BroadcastNodePicker(app.sendFlow.session) }
        }
    }
}

@Composable
private fun StagesCard(content: @Composable ColumnScope.() -> Unit) {
    SpectraCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            content = content,
        )
    }
}

/**
 * Where a stage stands. Each has its own symbol, as the composer's step bar
 * does, so the current stage is not told from a waiting one by colour.
 */
private enum class StageState { DONE, CURRENT, WAITING }

@Composable
private fun StageProgress(artifact: SendArtifact) {
    val signed = artifact.stage != SendStage.PREPARED
    val submitted = artifact.attempts.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = SpectraLayout.Space.s),
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        ProgressLabel(AppLocalization.string("Built"), StageState.DONE)
        ProgressLabel(
            AppLocalization.string(if (signed) "Signed" else "Awaiting signing"),
            if (signed) StageState.DONE else StageState.CURRENT,
        )
        ProgressLabel(
            AppLocalization.string(if (submitted) "Submitted" else "Awaiting submission"),
            when {
                submitted -> StageState.DONE
                signed -> StageState.CURRENT
                else -> StageState.WAITING
            },
        )
    }
}

@Composable
private fun RowScope.ProgressLabel(title: String, state: StageState) {
    // As the composer's step bar: what is done and what is current in the
    // accent, what is still to come in grey.
    val color = if (state == StageState.WAITING) secondaryText else MaterialTheme.colorScheme.primary
    val current = AppLocalization.string("Current")
    Column(
        modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) { if (state == StageState.CURRENT) stateDescription = current },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        Icon(
            when (state) {
                StageState.DONE -> Icons.Filled.CheckCircle
                StageState.CURRENT -> Icons.Outlined.RadioButtonChecked
                StageState.WAITING -> Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (state == StageState.CURRENT) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/** What the token's own rules do between the sender and the recipient. */
@Composable
private fun TransferTerms(artifact: SendArtifact, terms: AssetTransferTerms) {
    StagesCard {
        val hasFee = terms.fee != "0"
        if (hasFee) {
            SpectraValueRow(
                label = AppLocalization.string("Token Fee"),
                value = AmountPresentation.exactAmount(terms.fee, artifact.symbol),
            )
            SpectraDivider()
            SpectraValueRow(
                label = AppLocalization.string("Recipient Receives"),
                value = AmountPresentation.exactAmount(terms.received, artifact.symbol),
            )
        }
        terms.carriedNative?.let { carried ->
            if (hasFee) SpectraDivider()
            SpectraValueRow(
                label = AppLocalization.string("Sent With It"),
                value = AmountPresentation.exactAmount(carried, artifact.chainId.gasTokenSymbol),
            )
        }
        terms.recipientRegistration?.let { registration ->
            if (hasFee || terms.carriedNative != null) SpectraDivider()
            SpectraValueRow(
                label = AppLocalization.string("Recipient Registration"),
                value = AmountPresentation.exactAmount(registration, artifact.chainId.gasTokenSymbol),
            )
        }
        terms.hookProgram?.let { program ->
            if (hasFee || terms.carriedNative != null || terms.recipientRegistration != null) SpectraDivider()
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                SpectraLabel(
                    text = AppLocalization.string("This token runs a program on every transfer, which can refuse it."),
                    icon = Icons.Outlined.Settings,
                    color = SpectraColors.semantic.warning,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(program, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

@Composable
private fun TransactionReceipt(progress: SendProgress) {
    val app = LocalAppState.current
    val record = progress.transaction ?: return
    val context = LocalContext.current
    StagesCard {
        SpectraValueRow(
            label = AppLocalization.string(if (record.status == TransactionStatus.CONFIRMED) "On-chain status" else "Status"),
            value = record.status.localizedTitle,
        )
        record.localizedFailureReason?.let { reason ->
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        val fee = app.amounts.receiptNetworkFeeText(record) ?: app.amounts.confirmedNetworkFeeText(record)
        if (fee != null) {
            SpectraDivider()
            SpectraValueRow(label = AppLocalization.string("Network Fee"), value = fee)
        }
        val hash = record.transactionHash ?: progress.artifact.transactionHash
        if (hash != null) {
            SpectraDivider()
            TransactionHashRow(hash)
        }
        record.explorerLink?.let { (label, url) ->
            FilledTonalButton(onClick = { openExternalUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap()
                Text(label)
            }
        }
    }
}

@Composable
private fun TechnicalDetails(artifact: SendArtifact) {
    val warnings = artifact.reviewWarningMessages
    Column {
        SpectraDisclosure(title = AppLocalization.string("Transaction details")) {
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                PreparedFieldRows(artifact)
                TechnicalValue(AppLocalization.string("Review digest"), artifact.reviewDigest)
                if (artifact.signingPayloadHex.isNotEmpty()) {
                    TechnicalValue(AppLocalization.string("Signing payload"), artifact.signingPayloadHex)
                }
                if (artifact.attempts.isNotEmpty() && warnings.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                        SendComposerSectionLabel(AppLocalization.string("Review warnings"))
                        ReviewWarnings(artifact)
                    }
                }
            }
        }
        artifact.signedPayload?.let { payload ->
            SpectraDisclosure(title = AppLocalization.string("Signed payload")) {
                TechnicalText(payload)
            }
        }
    }
}

/**
 * The prepared transaction field by field, exactly as it will be signed:
 * core's names and base units, never re-rounded here.
 */
@Composable
private fun PreparedFieldRows(artifact: SendArtifact) {
    val fields = remember(artifact.preparedDetails) { preparedFields(artifact.preparedDetails) }
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        SendComposerSectionLabel(AppLocalization.string("Prepared transaction"))
        Column(modifier = Modifier.fillMaxWidth().spectraInsetFill().padding(horizontal = SpectraLayout.Space.m)) {
            fields.forEachIndexed { index, field ->
                if (index > 0) SpectraDivider()
                PreparedFieldRow(field.name, field.value)
            }
        }
    }
}

/**
 * A field's name and value side by side when both fit on one line, the value
 * under the name otherwise.
 */
@Composable
private fun PreparedFieldRow(name: String, value: String) {
    val monospace = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val nameView: @Composable () -> Unit = {
        if (name.isNotEmpty()) Text(name, style = monospace, color = secondaryText)
    }
    val valueView: @Composable () -> Unit = {
        Text(breakableAnywhere(value), style = monospace)
    }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpectraLayout.Space.s)
            .clearAndSetSemantics { contentDescription = if (name.isEmpty()) value else "$name, $value" },
        contents = listOf(nameView, valueView),
    ) { (nameMeasurables, valueMeasurables), constraints ->
        val gap = SpectraLayout.Space.m.roundToPx()
        val spacing = SpectraLayout.Space.xxs.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val nameWidth = nameMeasurables.sumOf { it.maxIntrinsicWidth(Int.MAX_VALUE) }
        val valueWidth = valueMeasurables.sumOf { it.maxIntrinsicWidth(Int.MAX_VALUE) }
        val namePlaceables = nameMeasurables.map { it.measure(loose) }
        val nameHeight = namePlaceables.maxOfOrNull { it.height } ?: 0
        if (nameWidth + gap + valueWidth <= width) {
            val valuePlaceables = valueMeasurables.map { it.measure(loose) }
            val valueHeight = valuePlaceables.maxOfOrNull { it.height } ?: 0
            val height = maxOf(nameHeight, valueHeight)
            layout(width, height) {
                namePlaceables.forEach { it.place(0, 0) }
                valuePlaceables.forEach { it.place(width - it.width, 0) }
            }
        } else {
            val valuePlaceables = valueMeasurables.map { it.measure(loose.copy(maxWidth = width)) }
            val valueHeight = valuePlaceables.maxOfOrNull { it.height } ?: 0
            val top = if (nameHeight > 0) nameHeight + spacing else 0
            layout(width, top + valueHeight) {
                namePlaceables.forEach { it.place(0, 0) }
                valuePlaceables.forEach { it.place(0, top) }
            }
        }
    }
}

@Composable
private fun TechnicalValue(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        SendComposerSectionLabel(title)
        TechnicalText(value)
    }
}

/**
 * A digest or payload: wrapped anywhere, so no hyphen appears in it, and
 * copied whole from its button, since the wrapped text is not the value.
 */
@Composable
private fun TechnicalText(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Text(
            breakableAnywhere(value),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f).semantics { contentDescription = value },
        )
        CopyButton(value = value)
    }
}

@Composable
private fun StageNotice(progress: SendProgress) {
    val app = LocalAppState.current
    val artifact = progress.artifact
    if (artifact.stage == SendStage.PREPARED) {
        SpectraLabel(
            text = AppLocalization.string("Review the complete addresses. Signing and broadcasting remain separate actions."),
            icon = Icons.Outlined.VerifiedUser,
            color = secondaryText,
        )
    } else if (!progress.status.isSettled && artifact.attempts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            if (progress.status == SendStatus.AWAITING_CONFIRMATION) {
                SpectraLabel(
                    text = AppLocalization.string("Node acceptance is not on-chain confirmation."),
                    icon = Icons.Outlined.Schedule,
                    color = SpectraColors.semantic.warning,
                )
            }
            SpectraLabel(
                text = AppLocalization.string(
                    "Transaction saved. You can close this screen and check its status in transaction history.",
                ),
                icon = Icons.Outlined.Info,
                color = secondaryText,
            )
            app.pendingTransactionRefreshStatusText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
    }
}
