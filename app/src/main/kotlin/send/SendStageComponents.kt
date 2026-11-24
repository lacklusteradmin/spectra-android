package dev.spectra.send

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.components.CopyButton
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDisclosure
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.groupedAddress
import dev.spectra.components.secondaryText
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.displayName
import dev.spectra.models.reviewWarningMessages
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendNextStep
import uniffi.spectra_core.SendProgress
import uniffi.spectra_core.SendStatus
import uniffi.spectra_core.SubmissionOutcome

// The parts of a staged transaction's page that a send, a staking action and
// a wallet tool share: where it stands, the nodes it goes to and what they
// answered. Each page lays out its own review around them.

// ── Where a staged transaction stands ───────────────────────────────────

/** Where a staged transaction stands, as core says it: a word, a symbol and a colour, never the colour alone. */
internal val SendStatus.title: String
    get() = AppLocalization.string(
        when (this) {
            SendStatus.AWAITING_SIGNATURE -> "Built, awaiting signing"
            SendStatus.SIGNED -> "Signed, not submitted"
            SendStatus.REJECTED -> "Submission rejected"
            SendStatus.UNCERTAIN -> "Submission uncertain"
            SendStatus.AWAITING_CONFIRMATION -> "Awaiting confirmation"
            SendStatus.CONFIRMED -> "Confirmed on-chain"
            SendStatus.FAILED -> "Transaction failed"
        },
    )

internal val SendStatus.icon: ImageVector
    get() = when (this) {
        SendStatus.AWAITING_SIGNATURE -> Icons.Outlined.Description
        SendStatus.SIGNED -> Icons.Outlined.Draw
        SendStatus.AWAITING_CONFIRMATION -> Icons.Outlined.Schedule
        SendStatus.CONFIRMED -> Icons.Outlined.Check
        SendStatus.REJECTED, SendStatus.UNCERTAIN, SendStatus.FAILED -> Icons.Outlined.WarningAmber
    }

internal val SendStatus.color: Color
    @Composable get() = when (this) {
        SendStatus.CONFIRMED -> SpectraColors.semantic.success
        SendStatus.REJECTED, SendStatus.FAILED -> SpectraColors.semantic.failure
        SendStatus.AWAITING_SIGNATURE, SendStatus.SIGNED, SendStatus.UNCERTAIN, SendStatus.AWAITING_CONFIRMATION ->
            SpectraColors.semantic.warning
    }

/** The chain has answered: nothing the stages do changes it. */
internal val SendStatus.isSettled: Boolean get() = this == SendStatus.CONFIRMED || this == SendStatus.FAILED

// ── What a node said ────────────────────────────────────────────────────

/** What a node said to a submission, as every send and staking page shows it. */
internal val SubmissionOutcome.title: String
    get() = AppLocalization.string(
        when (this) {
            SubmissionOutcome.ACCEPTED -> "Node accepted"
            SubmissionOutcome.REJECTED -> "Node rejected"
            SubmissionOutcome.UNCERTAIN -> "Submission uncertain"
        },
    )

internal val SubmissionOutcome.icon: ImageVector
    get() = when (this) {
        SubmissionOutcome.ACCEPTED -> Icons.Outlined.CheckCircle
        SubmissionOutcome.REJECTED -> Icons.Outlined.Cancel
        SubmissionOutcome.UNCERTAIN -> Icons.AutoMirrored.Outlined.HelpOutline
    }

internal val SubmissionOutcome.color: Color
    @Composable get() = when (this) {
        SubmissionOutcome.ACCEPTED -> SpectraColors.semantic.success
        SubmissionOutcome.REJECTED -> SpectraColors.semantic.failure
        SubmissionOutcome.UNCERTAIN -> SpectraColors.semantic.warning
    }

// ── Core's next step ────────────────────────────────────────────────────

/**
 * The labels of core's next step for a staged transaction. Core decides the
 * step; an acceptance receipt is never evidence that a transaction confirmed.
 * The key is the English sentence, looked up where it is drawn.
 */
internal val SendNextStep.titleKey: String
    get() = when (this) {
        SendNextStep.SIGN -> "Sign Transaction"
        SendNextStep.BROADCAST -> "Broadcast Transaction"
        SendNextStep.REBROADCAST -> "Retry Same Transaction"
        SendNextStep.DONE -> "Done"
    }

internal val SendNextStep.icon: ImageVector
    get() = when (this) {
        SendNextStep.SIGN -> Icons.Outlined.Draw
        SendNextStep.BROADCAST -> Icons.Outlined.CellTower
        SendNextStep.REBROADCAST -> Icons.Outlined.Refresh
        SendNextStep.DONE -> Icons.Outlined.Check
    }

/** The icon of the build that comes before core's first step. */
internal val buildTransactionIcon: ImageVector get() = Icons.Outlined.Build

/**
 * What a signing confirmation states, for a send and a staking action alike:
 * the amount, the reviewed fee with its bound, the recipient and every review
 * warning. [action] heads a staking action's: what it does, and to which
 * validator.
 */
fun sendSigningConfirmationMessage(artifact: SendArtifact, amounts: AmountPresentation, action: String? = null): String {
    val lines = mutableListOf<String>()
    action?.let { lines += it }
    lines += "${AmountPresentation.exactAmount(artifact.amount, artifact.symbol)} · ${artifact.chainId.displayName}"
    artifact.review.networkFee?.let { fee ->
        lines += AppLocalization.format("Network Fee: %@", amounts.reviewedNetworkFee(fee, artifact.chainId))
    }
    lines += listOf(
        AppLocalization.string("Recipient"),
        artifact.recipient,
        "",
        AppLocalization.string("Signing authorizes this transaction. You will choose nodes and broadcast it separately."),
    )
    val warnings = artifact.reviewWarningMessages
    if (warnings.isNotEmpty()) lines += "\n• " + warnings.joinToString("\n• ")
    return lines.joinToString("\n")
}

// ── Shared views ────────────────────────────────────────────────────────

/** A staged transaction's status on one line. */
@Composable
fun SendStatusLabel(status: SendStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(20.dp))
        Text(
            status.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = status.color,
        )
    }
}

/**
 * The nodes a signed transaction is submitted to, chosen before each
 * submission. Only the chosen ones receive it from Spectra. [enabled] is
 * `false` while the page around it is busy with something of its own.
 */
@Composable
fun BroadcastNodePicker(session: SendSession, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
        Text(AppLocalization.string("Select submission nodes"), style = MaterialTheme.typography.titleMedium)
        SpectraHintText(AppLocalization.string("Signed and saved. Choose which nodes receive this transaction."))
        Column {
            session.endpoints.forEachIndexed { index, endpoint ->
                if (index > 0) SpectraDivider()
                val selected = endpoint in session.selectedEndpoints
                val canChange = enabled && !session.isBusy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canChange, role = Role.Switch) { session.select(endpoint, !selected) }
                        .padding(vertical = SpectraLayout.Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
                ) {
                    Text(
                        endpoint,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = selected, onCheckedChange = null, enabled = canChange)
                }
            }
        }
        if (session.selectedEndpoints.isEmpty()) {
            SpectraLabel(
                text = AppLocalization.string("Select at least one node to submit this transaction."),
                icon = Icons.Outlined.Info,
                color = SpectraColors.semantic.warning,
            )
        }
        SpectraHintText(
            AppLocalization.string(
                "Only selected nodes receive this submission from Spectra. Those nodes may propagate the transaction to other nodes.",
            ),
        )
    }
}

/**
 * What each node answered, with its details, and the hash the transaction
 * lands under until the chain has settled it.
 */
@Composable
fun SubmissionResults(progress: SendProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Text(AppLocalization.string("Submission results"), style = MaterialTheme.typography.titleMedium)
        progress.artifact.attempts.forEachIndexed { index, attempt ->
            if (index > 0) SpectraDivider()
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                SelectionContainer {
                    Text(attempt.endpoint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                ) {
                    Icon(attempt.outcome.icon, contentDescription = null, tint = attempt.outcome.color, modifier = Modifier.size(20.dp))
                    Text(
                        attempt.outcome.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = attempt.outcome.color,
                    )
                }
                SpectraDisclosure(title = AppLocalization.string("Submission details")) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                        Text(attempt.detail, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        attempt.transactionHash?.let { TransactionHashRow(it) }
                    }
                }
            }
        }
        val hash = progress.transaction?.transactionHash ?: progress.artifact.transactionHash
        if (!progress.status.isSettled && hash != null) {
            SpectraDivider()
            TransactionHashRow(hash)
        }
    }
}

/** A transaction hash, grouped for reading and copied whole from its button. */
@Composable
fun TransactionHashRow(hash: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Text(
            AppLocalization.string("Transaction Hash"),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = secondaryText,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            Text(
                groupedAddress(hash),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f).semantics { contentDescription = hash },
            )
            CopyButton(value = hash)
        }
    }
}

/** The trailing chevron of a row that opens something. */
@Composable
internal fun RowChevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
