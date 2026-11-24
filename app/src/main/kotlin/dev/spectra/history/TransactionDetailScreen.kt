package dev.spectra.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.readOrNull
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.CopyRow
import dev.spectra.components.EndpointHolderLabel
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDisclosure
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLoadingRow
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraPill
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.SpectraValueStyle
import dev.spectra.components.groupedAddress
import dev.spectra.components.openExternalUrl
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.amountColor
import dev.spectra.models.amountSign
import dev.spectra.models.artworkName
import dev.spectra.models.chainName
import dev.spectra.models.explorerLink
import dev.spectra.models.fullTimestampText
import dev.spectra.models.historySourceName
import dev.spectra.models.localizedFailureReason
import dev.spectra.models.receiptBlockNumberText
import dev.spectra.models.storedConfirmationCountText
import dev.spectra.models.storedUsedChangeOutputText
import dev.spectra.models.subtitleText
import dev.spectra.models.titleText
import dev.spectra.send.openReplacementComposer
import dev.spectra.send.replaceableSend
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.MoneroPaymentProof
import uniffi.spectra_core.TransactionEndpoint
import uniffi.spectra_core.TransactionEndpoints
import uniffi.spectra_core.TransactionKind
import uniffi.spectra_core.TransactionRecord
import uniffi.spectra_core.TransactionStatus
import uniffi.spectra_core.displayAddress

/**
 * One stored transfer, most-read first: what moved and where it stands, the
 * actions a pending send may still take, how it got there, who was on each
 * end, and the few facts a reader checks. Everything else — gas, paths,
 * payload — is folded under Technical Details. Each fact appears once.
 */
@Composable
internal fun TransactionDetailScreen(transactionId: String) {
    val app = LocalAppState.current
    // The recent and pending projection has most records a person opens;
    // core answers for the rest, and for the newest state of every one.
    var liveTransaction by remember(transactionId) {
        mutableStateOf(app.transactions.firstOrNull { it.id == transactionId })
    }
    var hasRead by remember(transactionId) { mutableStateOf(false) }
    // Which ends to show and whether each is the wallet's own: core's answer, cached for the page.
    var endpoints by remember(transactionId) { mutableStateOf<TransactionEndpoints?>(null) }
    // A Monero send's payment proof, from core; `null` for anything else.
    var paymentProof by remember(transactionId) { mutableStateOf<MoneroPaymentProof?>(null) }

    // One effect per pair of revisions: a revision arriving mid-read cancels
    // the stale pass rather than racing it to the assignments.
    LaunchedEffect(transactionId, app.transactionRevision, app.walletIdentityRevision) {
        val projection = loadTransactionDetailProjection(
            fallback = liveTransaction,
            transaction = { app.bridge.ready().transaction(transactionId) },
            endpoints = { app.bridge.ready().transactionEndpoints(transactionId) },
        )
        liveTransaction = projection.first
        endpoints = projection.second
        hasRead = true
    }
    val transaction = liveTransaction
    // Only a Monero payment this device signed has a proof; core answers
    // `null` for every other transaction.
    val proofKind = transaction?.kind
    val proofWalletId = transaction?.walletId
    val proofHash = transaction?.transactionHash
    LaunchedEffect(proofKind, proofWalletId, proofHash) {
        if (proofKind != TransactionKind.SEND || proofWalletId == null || proofHash == null) return@LaunchedEffect
        paymentProof = readOrNull { app.bridge.ready().moneroPaymentProof(proofWalletId, proofHash) }
    }

    SpectraPage(title = AppLocalization.string("Transaction")) {
        when {
            transaction != null -> {
                TransactionHeroCard(app, transaction)
                PendingActionsCard(app, transaction)
                TransactionTimelineCard(transaction)
                AddressesCard(transaction, endpoints)
                DetailsCard(app, transaction, paymentProof)
                TechnicalDetailsCard(app, transaction, endpoints)
            }
            !hasRead -> SpectraCard {
                SpectraLoadingRow(
                    title = AppLocalization.string("Loading"),
                    modifier = Modifier.padding(SpectraLayout.cardPadding),
                )
            }
            else -> SpectraEmptyStateCard(
                title = AppLocalization.string("Transaction not found."),
                message = AppLocalization.string("This transaction is no longer stored on this device."),
                icon = Icons.Outlined.HourglassEmpty,
            )
        }
    }
}

/**
 * Read one detail projection before adopting either field: a read can finish
 * after the effect was cancelled for a newer revision, and a record core
 * cannot read keeps the one already shown.
 */
private suspend fun loadTransactionDetailProjection(
    fallback: TransactionRecord?,
    transaction: suspend () -> TransactionRecord?,
    endpoints: suspend () -> TransactionEndpoints?,
): Pair<TransactionRecord?, TransactionEndpoints?> {
    currentCoroutineContext().ensureActive()
    val record = readOrNull(transaction) ?: fallback
    currentCoroutineContext().ensureActive()
    val parties = readOrNull(endpoints)
    currentCoroutineContext().ensureActive()
    return record to parties
}

/**
 * What moved, from which wallet on which network, and where it stands. The
 * amount is signed and coloured as the history row draws it, so the
 * direction needs no row of its own.
 */
@Composable
private fun TransactionHeroCard(app: AppState, transaction: TransactionRecord) {
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                CoinBadge(
                    artworkName = transaction.artworkName,
                    fallbackText = transaction.symbol,
                    color = transaction.badgeColor,
                    size = 42.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    Text(transaction.titleText, style = MaterialTheme.typography.titleMedium)
                    Text(transaction.subtitleText, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                }
                TransactionStatusBadge(transaction.status)
            }
            BalanceText(
                transaction.amountSign + app.amounts.formattedTransactionDetailAmount(transaction),
                isHidden = app.preferences.hideBalances,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = transaction.amountColor,
            )
        }
    }
}

/**
 * What a pending transaction can still have done to it: checked again or
 * sent to the network again, each where core offers it. Second on the page
 * because it is the only thing here a reader can act on, and it cannot wait.
 * The result shows here.
 */
@Composable
private fun PendingActionsCard(app: AppState, transaction: TransactionRecord) {
    val canRecheck = transaction.actions.recheckUnavailableReason == null
    val canRebroadcast = transaction.actions.rebroadcastUnavailableReason == null
    val replaceable = app.replaceableSend(transaction.id)
    if (!canRecheck && !canRebroadcast && replaceable == null) return
    // A recheck or rebroadcast finishes even if the page is left.
    val scope = app.scope
    // What the last action on this page — a recheck, a rebroadcast — came to.
    var actionMessage by remember(transaction.id) { mutableStateOf<String?>(null) }
    var isRunningAction by remember(transaction.id) { mutableStateOf(false) }
    fun run(action: suspend () -> String?) {
        scope.launch {
            isRunningAction = true
            try {
                actionMessage = action()
            } finally {
                isRunningAction = false
            }
        }
    }
    SpectraDetailCard(title = AppLocalization.string("Pending Transaction")) {
        if (app.sendFlow.isPreparingReplacement || isRunningAction) {
            SpectraLoadingRow(title = AppLocalization.string("Working…"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                if (replaceable?.canSpeedUp == true) {
                    Button(
                        onClick = { run { app.openReplacementComposer(transaction.id, cancel = false) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = SpectraLayout.rowMinHeight),
                    ) {
                        Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        SpectraGap()
                        Text(AppLocalization.string("Speed Up This Transaction"), style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (canRecheck) {
                    PendingActionButton(AppLocalization.string("Recheck"), Icons.Outlined.Refresh) {
                        run { app.retryUTXOTransactionStatus(transaction.id) }
                    }
                }
                if (canRebroadcast) {
                    PendingActionButton(AppLocalization.string("Rebroadcast"), Icons.Outlined.CellTower) {
                        run { app.rebroadcastSignedTransaction(transaction.id) }
                    }
                }
                if (replaceable != null) {
                    OutlinedButton(
                        onClick = { run { app.openReplacementComposer(transaction.id, cancel = true) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = SpectraLayout.rowMinHeight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.HighlightOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        SpectraGap()
                        Text(AppLocalization.string("Cancel This Transaction"), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        replaceable?.let {
            Text(
                AppLocalization.string(
                    if (it.canSpeedUp) {
                        "This opens the Send composer with the same nonce and higher fee defaults so you can safely speed up or cancel the pending transaction."
                    } else {
                        "This opens the Send composer with the same nonce and higher fee defaults so you can cancel the pending transfer. A token transfer cannot be rebuilt from its record, so it cannot be sped up."
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )
        }
        actionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText) }
    }
}

@Composable
private fun PendingActionButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = SpectraLayout.rowMinHeight)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        SpectraGap()
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun AddressesCard(transaction: TransactionRecord, endpoints: TransactionEndpoints?) {
    val from = endpoints?.from
    val to = endpoints?.to
    if (from == null && to == null) return
    SpectraDetailCard(title = AppLocalization.string("Addresses")) {
        if (from != null) TransactionAddressRow(AppLocalization.string("From"), from, transaction.chainId)
        if (from != null && to != null) SpectraDivider()
        if (to != null) TransactionAddressRow(AppLocalization.string("To"), to, transaction.chainId)
    }
}

/**
 * The facts a reader checks: the network, what it cost, and the hash to look
 * it up by. Status, time, block and confirmations are the timeline's;
 * wallet, asset and amount are the hero's.
 */
@Composable
private fun DetailsCard(app: AppState, transaction: TransactionRecord, paymentProof: MoneroPaymentProof?) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val amounts = app.amounts
    // The receipt's fee when there is one, else the fee stored at
    // confirmation. When both exist the other sits under Technical Details.
    val networkFeeText = amounts.receiptNetworkFeeText(transaction) ?: amounts.confirmedNetworkFeeText(transaction)
    SpectraDetailCard(title = AppLocalization.string("Details")) {
        SpectraValueRow(label = AppLocalization.string("Network"), value = transaction.chainName, icon = Icons.Outlined.Hub)
        networkFeeText?.let { fee ->
            SpectraDivider()
            SpectraValueRow(label = AppLocalization.string("Network Fee"), value = fee, icon = Icons.Outlined.LocalGasStation)
        }
        nonEmpty(transaction.transactionHash)?.let { hash ->
            SpectraDivider()
            CopyRow(value = hash, padded = false) {
                SpectraValueRow(
                    label = AppLocalization.string("Transaction Hash"),
                    value = hash,
                    icon = Icons.Outlined.Tag,
                    style = SpectraValueStyle.Identifier,
                )
            }
        }
        if (paymentProof != null) {
            SpectraDivider()
            DetailLinkRow(AppLocalization.string("Prove Payment"), Icons.Outlined.Verified, Icons.AutoMirrored.Outlined.KeyboardArrowRight) {
                navigator.push(HistoryRoutes.PaymentProof(paymentProof, transaction.chainId))
            }
        }
        transaction.explorerLink?.let { (label, url) ->
            SpectraDivider()
            DetailLinkRow(label, Icons.Outlined.Explore, Icons.AutoMirrored.Outlined.OpenInNew) { openExternalUrl(context, url) }
        }
    }
}

@Composable
private fun DetailLinkRow(title: String, icon: ImageVector, trailing: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SpectraLayout.rowMinHeight)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(trailing, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
    }
}

private class TechnicalRow(val label: String, val value: String, val isIdentifier: Boolean)

private fun technicalRows(app: AppState, transaction: TransactionRecord, endpoints: TransactionEndpoints?): List<TechnicalRow> {
    val amounts = app.amounts
    val rows = mutableListOf<TechnicalRow>()
    fun add(label: String, value: String?, isIdentifier: Boolean = false) {
        if (!value.isNullOrEmpty()) rows += TechnicalRow(label, value, isIdentifier)
    }
    add(AppLocalization.string("History Source"), transaction.historySourceName)
    add(AppLocalization.string("Gas Used"), transaction.receiptGasUsed)
    add(AppLocalization.string("Effective Gas Price"), amounts.receiptEffectiveGasPriceText(transaction))
    if (amounts.receiptNetworkFeeText(transaction) != null) {
        add(AppLocalization.string("Confirmed Fee"), amounts.confirmedNetworkFeeText(transaction))
    }
    add(AppLocalization.string("Fee Rate"), amounts.storedFeeRateText(transaction))
    add(AppLocalization.string("Used Change Output"), transaction.storedUsedChangeOutputText)
    add(AppLocalization.string("Source Path"), transaction.sourceDerivationPath)
    add(AppLocalization.string("Change Path"), transaction.changeDerivationPath)
    // On an account chain the source is the From address above.
    if (transaction.sourceAddress != endpoints?.from?.address) {
        add(AppLocalization.string("Source Address"), transaction.sourceAddress, isIdentifier = true)
    }
    add(AppLocalization.string("Change Address"), transaction.changeAddress, isIdentifier = true)
    return rows
}

/** Folded by default: gas, paths and payload are for the reader who asks. */
@Composable
private fun TechnicalDetailsCard(app: AppState, transaction: TransactionRecord, endpoints: TransactionEndpoints?) {
    val rows = technicalRows(app, transaction, endpoints)
    if (rows.isEmpty()) return
    val title = AppLocalization.string("Technical Details")
    SpectraCard {
        SpectraDisclosure(
            title = title,
            modifier = Modifier.padding(horizontal = SpectraLayout.cardPadding, vertical = SpectraLayout.Space.xs),
            label = { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)) },
        ) {
            Column(
                modifier = Modifier.padding(bottom = SpectraLayout.cardHeaderVertical),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                rows.forEach { row ->
                    SpectraDivider()
                    if (row.isIdentifier) {
                        CopyRow(value = row.value, padded = false) {
                            SpectraValueRow(label = row.label, value = row.value, style = SpectraValueStyle.Identifier)
                        }
                    } else {
                        SpectraValueRow(label = row.label, value = row.value)
                    }
                }
            }
        }
    }
}

// ── Timeline ─────────────────────────────────────────────────────────────

private class TimelineItem(
    val id: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val tint: Color,
    val isComplete: Boolean,
    val isCurrent: Boolean,
)

@Composable
private fun timelineItems(transaction: TransactionRecord): List<TimelineItem> {
    val accent = MaterialTheme.colorScheme.primary
    val semantic = SpectraColors.semantic
    val items = mutableListOf(
        TimelineItem(
            id = "recorded",
            title = AppLocalization.string(if (transaction.submitted) "Created" else "Recorded"),
            detail = transaction.fullTimestampText,
            icon = if (transaction.submitted) Icons.AutoMirrored.Outlined.Send else Icons.Outlined.ArrowCircleDown,
            tint = accent,
            isComplete = true,
            isCurrent = false,
        ),
    )
    items += if (nonEmpty(transaction.transactionHash) != null) {
        TimelineItem(
            id = "network-hash",
            title = AppLocalization.string(if (transaction.submitted) "Broadcast" else "Detected"),
            // The hash is the Details card's; here, only that the network has it.
            detail = AppLocalization.string(
                if (transaction.submitted) "The network accepted the transaction." else "Spectra found this transaction on the network.",
            ),
            icon = Icons.Outlined.Link,
            tint = accent,
            isComplete = true,
            isCurrent = false,
        )
    } else {
        TimelineItem(
            id = "network-hash",
            title = AppLocalization.string("Awaiting Network Hash"),
            detail = AppLocalization.string("Spectra has not attached a network transaction hash yet."),
            icon = Icons.Outlined.HourglassEmpty,
            tint = semantic.warning,
            isComplete = false,
            isCurrent = false,
        )
    }
    items += when (transaction.status) {
        TransactionStatus.PENDING -> TimelineItem(
            id = "pending",
            title = AppLocalization.string("Pending Confirmation"),
            detail = AppLocalization.string("Spectra will keep refreshing this transaction."),
            icon = Icons.Outlined.Update,
            tint = semantic.warning,
            isComplete = false,
            isCurrent = true,
        )
        TransactionStatus.CONFIRMED -> TimelineItem(
            id = "confirmed",
            title = AppLocalization.string("Confirmed"),
            detail = confirmedTimelineDetail(transaction),
            icon = Icons.Outlined.Verified,
            tint = semantic.success,
            isComplete = true,
            isCurrent = false,
        )
        TransactionStatus.FAILED -> TimelineItem(
            id = "failed",
            title = AppLocalization.string("Failed"),
            detail = transaction.localizedFailureReason ?: AppLocalization.string("Network or local validation failed."),
            icon = Icons.Outlined.Dangerous,
            tint = semantic.failure,
            isComplete = false,
            isCurrent = false,
        )
    }
    return items
}

private fun confirmedTimelineDetail(transaction: TransactionRecord): String {
    val parts = listOfNotNull(
        transaction.receiptBlockNumberText?.let { AppLocalization.format("Block %@", it) },
        transaction.storedConfirmationCountText,
    )
    return if (parts.isEmpty()) AppLocalization.string("Network has confirmed this transaction.") else parts.joinToString(" · ")
}

@Composable
private fun TransactionTimelineCard(transaction: TransactionRecord) {
    val items = timelineItems(transaction)
    SpectraDetailCard(title = AppLocalization.string("Timeline")) {
        Column {
            items.forEachIndexed { index, item -> TimelineRow(item, isLast = index == items.lastIndex) }
        }
    }
}

/**
 * The connector runs the row's full height, however tall its text grows: the
 * row is as tall as its text, and the line fills what the icon leaves.
 */
@Composable
private fun TimelineRow(item: TimelineItem, isLast: Boolean) {
    val isLit = item.isComplete || item.isCurrent
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = if (isLast) 0.dp else SpectraLayout.Space.s),
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background((if (isLit) item.tint else onSurface).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = if (isLit) item.tint else secondaryText, modifier = Modifier.size(16.dp))
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SpectraLayout.Space.m)
                        .width(2.dp)
                        .background(if (item.isComplete) item.tint.copy(alpha = 0.35f) else onSurface.copy(alpha = 0.12f)),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                if (item.isCurrent) {
                    Text(
                        AppLocalization.string("Current"),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = item.tint,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(item.tint.copy(alpha = 0.14f))
                            .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xxs),
                    )
                }
            }
            SelectionContainer {
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
    }
}

// ── Addresses ────────────────────────────────────────────────────────────

/**
 * One end of the transfer: its label, who holds it — a wallet of the user's
 * or a saved contact, as core names it — and the address in groups of four
 * on one line, cut in the middle so its ends, what a reader compares
 * against, show in full. The row copies the whole address.
 */
@Composable
private fun TransactionAddressRow(label: String, endpoint: TransactionEndpoint, chain: Chain) {
    // Core's display form: an EVM address in its EIP-55 case. Copying takes
    // this, never the grouped text, which carries spaces.
    val address = remember(chain, endpoint.address) { displayAddress(chain, endpoint.address) }
    CopyRow(value = address, padded = false) {
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                val holder = endpoint.holder
                if (holder != null) {
                    EndpointHolderLabel(holder)
                } else if (endpoint.isMine) {
                    SpectraPill(AppLocalization.string("Mine"))
                }
            }
            Text(
                groupedAddress(address),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}

private fun nonEmpty(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
