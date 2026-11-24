package dev.spectra.receive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.app.readOrNull
import dev.spectra.components.CoinBadge
import dev.spectra.components.CopyButton
import dev.spectra.components.FlowPageHeading
import dev.spectra.components.QRCodeImage
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraNoticeCard
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraShimmer
import dev.spectra.components.groupedAddress
import dev.spectra.components.secondaryText
import dev.spectra.components.shareText
import dev.spectra.components.spectraInsetFill
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.chainName
import dev.spectra.models.color
import dev.spectra.models.coinSymbol
import dev.spectra.models.isNativeCoin
import dev.spectra.models.isWatchOnly
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.userErrorMessage
import dev.spectra.send.SendPaymentMemoField
import dev.spectra.wallets.WalletCardPresentation
import dev.spectra.wallets.WalletCardView
import kotlinx.coroutines.launch
import uniffi.spectra_core.AccountReserve
import uniffi.spectra_core.Chain
import uniffi.spectra_core.PaymentMemo
import uniffi.spectra_core.PaymentMemoKind
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.displayAddress
import uniffi.spectra_core.paymentMemoKinds
import uniffi.spectra_core.paymentRequestUri
import uniffi.spectra_core.paymentRequestsSupported

/** The side of the code, and of what stands in for it while the address is read. */
private val QrCodeSize = 216.dp

/**
 * Receive starts on the wallet list; choosing a wallet pushes its address, so
 * Back returns to the list. One wallet leaves nothing to choose:
 * `beginReceive` selected it, and the flow opens on its address.
 */
@Composable
internal fun ReceiveScreen() {
    val app = LocalAppState.current
    DisposableEffect(Unit) { onDispose { app.receiveFlowLeftScreen() } }
    if (app.receiveEnabledWallets.size == 1) ReceiveAddressPage() else ReceiveWalletList()
}

/**
 * The question, then every wallet that can receive as Home lists it — its
 * network, what it holds and whether Spectra only watches it — so the one to
 * receive into is told apart without opening each.
 */
@Composable
private fun ReceiveWalletList() {
    val app = LocalAppState.current
    val wallets = app.receiveEnabledWallets
    SpectraPage(title = AppLocalization.string("Receive")) {
        FlowPageHeading(
            title = AppLocalization.string("Choose a wallet to receive into"),
            subtitle = AppLocalization.string("Each wallet has its own address on its network."),
        )
        if (wallets.isEmpty()) {
            SpectraEmptyStateCard(
                title = AppLocalization.string("No receive wallets"),
                message = AppLocalization.string("Import a wallet to generate receive addresses."),
                icon = Icons.Outlined.AccountBalanceWallet,
            )
        } else {
            // A link, not a choice: every row opens its wallet, so none is marked.
            SpectraRowGroup(items = wallets) { wallet ->
                WalletCardView(
                    WalletCardPresentation.of(wallet, app),
                    Modifier
                        .clickable {
                            if (app.receiveFlow.walletId != wallet.id) {
                                app.receiveFlow.walletId = wallet.id
                                app.syncReceiveAssetSelection()
                            }
                            app.navigator.push(ReceiveAddressRoute)
                        }
                        .spectraRowPadding(),
                )
            }
        }
    }
}

/** One wallet's address on its network: the code, the address, and a request for an amount. */
@Composable
internal fun ReceiveAddressPage() {
    val app = LocalAppState.current
    val flow = app.receiveFlow
    val context = LocalContext.current
    val viewScope = rememberCoroutineScope()
    // What an account on a reserve network must first receive to exist, read
    // from the network while the wallet holds nothing.
    var reserve by remember { mutableStateOf<AccountReserve?>(null) }
    // An amount, and a memo where the network takes one, to ask the sender
    // for. Empty asks for nothing and the code is the address alone.
    var requestedAmount by rememberSaveable { mutableStateOf("") }
    var requestMemoKind by rememberSaveable { mutableStateOf<PaymentMemoKind?>(null) }
    var requestMemo by rememberSaveable { mutableStateOf("") }
    var isRequestingAmount by rememberSaveable { mutableStateOf(false) }
    var requestHoldingKey by rememberSaveable { mutableStateOf(flow.holdingKey) }

    val selectedWallet = app.receiveEnabledWallets.firstOrNull { it.id == flow.walletId }
    val selectedCoin = app.selectedReceiveCoin(flow.walletId)
    // The address as it is shown, copied, shared and encoded: core's display
    // form, an EVM address in its checksummed case.
    val address: String? = run {
        val resolved = flow.resolvedAddress.trim()
        val chain = selectedCoin?.chainId ?: selectedWallet?.chainId
        if (resolved.isEmpty() || flow.isResolving || chain == null) null else displayAddress(chain, resolved)
    }
    // Core's payment code for the amount asked, or why it cannot be one.
    // `null` while nothing is asked: the code is then the address.
    val paymentRequest: Result<String>? = run {
        val chain = selectedCoin?.chainId
        if (!isRequestingAmount || address == null || chain == null) return@run null
        val amount = AmountPresentation.canonicalDecimalInput(requestedAmount)
        val memoText = requestMemo.trim()
        if (amount.isEmpty() && memoText.isEmpty()) return@run null
        val kinds = paymentMemoKinds(chain)
        val memo = if (memoText.isEmpty() || kinds.isEmpty()) {
            null
        } else {
            PaymentMemo(requestMemoKind?.takeIf { it in kinds } ?: kinds.first(), memoText)
        }
        runCatching { paymentRequestUri(chain, address, amount.ifEmpty { null }, memo) }
    }
    val request = paymentRequest?.getOrNull()
    // What the code holds: the request when one is asked, else the address.
    val encoded = request ?: address

    // The page may leave with the flow's first page — a tab returned to its
    // root takes both — so it reports leaving too.
    DisposableEffect(Unit) { onDispose { app.receiveFlowLeftScreen() } }
    LaunchedEffect(flow.walletId, flow.holdingKey) { app.refreshReceiveAddress() }
    LaunchedEffect(flow.walletId) {
        reserve = null
        val wallet = selectedWallet ?: return@LaunchedEffect
        reserve = readOrNull { app.bridge.ready().walletAccountReserve(wallet.id) }
    }
    // A request is for one asset: another one starts it over.
    LaunchedEffect(flow.holdingKey) {
        if (requestHoldingKey != flow.holdingKey) {
            requestHoldingKey = flow.holdingKey
            requestedAmount = ""
            requestMemo = ""
            isRequestingAmount = false
        }
    }

    SpectraPage(title = AppLocalization.string("Receive")) {
        // Spectra holds no key for a watched address. Funds sent to it are
        // only the user's if another wallet holds that key.
        if (selectedWallet?.signing?.isWatchOnly == true) {
            SpectraNoticeCard(
                text = AppLocalization.string("receive.watchOnly.warning"),
                icon = Icons.Outlined.Visibility,
            )
        }
        ReceiveAddressHero(
            app = app,
            wallet = selectedWallet,
            address = address,
            encoded = encoded,
            isRequest = request != null,
            reserve = reserve,
            onRetry = { viewScope.launch { app.refreshReceiveAddress() } },
        )
        val chain = selectedCoin?.chainId
        if (chain != null && selectedCoin.isNativeCoin && paymentRequestsSupported(chain)) {
            RequestAmountCard(
                chain = chain,
                isRequesting = isRequestingAmount,
                onRequestingChange = { isRequestingAmount = it },
                amount = requestedAmount,
                onAmountChange = { requestedAmount = it },
                memoKind = requestMemoKind,
                onMemoKindChange = { requestMemoKind = it },
                memo = requestMemo,
                onMemoChange = { requestMemo = it },
                failure = paymentRequest?.exceptionOrNull(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            if (address != null) {
                CopyButton(
                    value = address,
                    title = AppLocalization.string("Copy Address"),
                    wide = true,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            } else {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    SpectraGap()
                    Text(AppLocalization.string("Copy Address"))
                }
            }
            OutlinedButton(
                onClick = { encoded?.let { shareText(context, it) } },
                enabled = address != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap()
                Text(AppLocalization.string("Share"))
            }
        }
    }
}

@Composable
private fun ReceiveAddressHero(
    app: AppState,
    wallet: WalletState?,
    address: String?,
    encoded: String?,
    isRequest: Boolean,
    reserve: AccountReserve?,
    onRetry: () -> Unit,
) {
    val coin = app.selectedReceiveCoin(app.receiveFlow.walletId)
    val error = app.receiveFlow.error
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            // The network is named here and on the wallet line under the code.
            if (coin != null) {
                SpectraLabel(
                    text = AppLocalization.format(
                        "Receive only %@ assets on this network. Check the sender's network before transferring.",
                        coin.chainName,
                    ),
                    icon = Icons.Outlined.WarningAmber,
                    color = SpectraColors.semantic.warning,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                encoded != null -> {
                    val description = AppLocalization.string(
                        if (isRequest) "QR code of the payment request" else "QR code of the address",
                    )
                    QRCodeImage(encoded, Modifier.size(QrCodeSize).clearAndSetSemantics { contentDescription = description })
                }
                error != null -> ReceiveError(error, onRetry)
                else -> ReceiveQRCodePlaceholder(QrCodeSize)
            }

            // The address takes any asset on the chain, so the line names the
            // chain and draws its mark, not the gas token's.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                if (coin != null) {
                    val badge = nativeChainBadge(coin.chainId)
                    CoinBadge(badge?.artworkName, coin.chainName, badge?.color ?: coin.color, size = 36.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    Text(wallet?.name ?: AppLocalization.string("Wallet"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        coin?.chainName ?: AppLocalization.string("Select a chain"),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                    )
                }
            }

            if (reserve != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = SpectraColors.semantic.warning, modifier = Modifier.size(18.dp))
                    Text(
                        AppLocalization.format(
                            "This account exists once it receives at least %@ %@; a smaller first payment fails.",
                            reserve.amount,
                            reserve.symbol,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpectraColors.semantic.warning,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (address != null) {
                // Grouped, so a sender compares it a group at a time.
                Text(
                    groupedAddress(address),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = address },
                )
            }
        }
    }
}

/** The address could not be read: why, and the way to ask again. */
@Composable
private fun ReceiveError(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.size(QrCodeSize),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            SpectraGap()
            Text(AppLocalization.string("Retry"))
        }
    }
}

/** What stands in for the code while the address is read. */
@Composable
private fun ReceiveQRCodePlaceholder(size: Dp) {
    val loading = AppLocalization.string("Loading receive address…")
    Box(
        modifier = Modifier
            .size(size)
            .spectraInsetFill(SpectraLayout.Radius.card)
            .clearAndSetSemantics { contentDescription = loading },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(42.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                SpectraShimmer(modifier = Modifier.width(size * 0.58f), height = 14.dp)
                SpectraShimmer(modifier = Modifier.width(size * 0.42f), height = 14.dp)
            }
        }
    }
}

/**
 * Asking for an amount: the network's coin only — a request's amount is in
 * it — and a memo where the network's payments carry one.
 */
@Composable
private fun RequestAmountCard(
    chain: Chain,
    isRequesting: Boolean,
    onRequestingChange: (Boolean) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    memoKind: PaymentMemoKind?,
    onMemoKindChange: (PaymentMemoKind) -> Unit,
    memo: String,
    onMemoChange: (String) -> Unit,
    failure: Throwable?,
) {
    SpectraCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SpectraLayout.rowMinHeight)
                    .clickable(role = Role.Switch) { onRequestingChange(!isRequesting) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            ) {
                Icon(Icons.Outlined.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    AppLocalization.string("Request an Amount"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = isRequesting, onCheckedChange = null)
            }
            AnimatedVisibility(visible = isRequesting) {
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(AppLocalization.string("Amount")) },
                        suffix = { Text(chain.coinSymbol, color = secondaryText) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    )
                    val kinds = remember(chain) { paymentMemoKinds(chain) }
                    if (kinds.isNotEmpty()) {
                        SendPaymentMemoField(
                            kinds = kinds,
                            kind = memoKind,
                            onKindChange = onMemoKindChange,
                            text = memo,
                            onTextChange = onMemoChange,
                        )
                    }
                    if (failure != null) {
                        SpectraLabel(
                            text = userErrorMessage(failure),
                            icon = Icons.Outlined.WarningAmber,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            AppLocalization.string("receive.request.hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                        )
                    }
                }
            }
        }
    }
}
