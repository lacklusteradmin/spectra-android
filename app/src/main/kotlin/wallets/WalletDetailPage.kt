package dev.spectra.wallets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.app.performCoreRefresh
import dev.spectra.app.readOrNull
import dev.spectra.app.reportCommandError
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.CopyButton
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraEmptyStateContent
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraShimmer
import dev.spectra.components.groupedAddress
import dev.spectra.components.openExternalUrl
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainBadge
import dev.spectra.models.artworkName
import dev.spectra.models.chainName
import dev.spectra.models.color
import dev.spectra.models.family
import dev.spectra.models.familyName
import dev.spectra.models.faucetUrl
import dev.spectra.models.hides
import dev.spectra.models.isWatchOnly
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.shownHoldings
import dev.spectra.models.standardLabel
import dev.spectra.receive.beginReceive
import dev.spectra.send.beginSend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.AppRefreshIntent
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletActionSection
import uniffi.spectra_core.WalletActions
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.addressExplorerLink
import uniffi.spectra_core.displayAddress

/** A holding with its figures as the page shows them. */
private data class HoldingPresentation(val coin: AssetHolding, val amountText: String, val valueText: String)

/** A wallet: what it holds, its address, what it offers, and the way to manage it. */
@Composable
internal fun WalletDetailPage(walletId: String) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallet = app.wallet(walletId)
    if (wallet == null) {
        // A wallet deleted under the page has nothing to show; leaving pops the pages about it.
        LaunchedEffect(walletId) { app.dismissWalletPages(walletId) }
        SpectraPage(title = AppLocalization.string("Wallet Details")) {}
        return
    }
    /** Core's answer to what this wallet offers; read again when the wallet or the endpoints behind its notes change. */
    var actions by remember(walletId) { mutableStateOf<WalletActions?>(null) }
    /** The wallet's ENS primary name, where core finds one that resolves back. */
    var ensName by remember(walletId) { mutableStateOf<String?>(null) }
    LaunchedEffect(walletId, app.walletIdentityRevision, app.appSettings) {
        // A wallet deleted under the page has no actions.
        actions = readOrNull { app.bridge.ready().walletActions(walletId) }
    }
    LaunchedEffect(walletId) {
        ensName = readOrNull { app.bridge.ready().walletEnsName(walletId) }
    }

    /** Open what [action] does: a flow the app presents, or its own page. */
    fun perform(action: WalletAction) {
        when (action) {
            WalletAction.SEND -> app.beginSend(walletId = walletId)
            WalletAction.RECEIVE -> app.beginReceive(walletId = walletId)
            WalletAction.OPEN_IN_EXPLORER -> {
                val address = wallet.address ?: return
                addressExplorerLink(wallet.chainId, address)?.let { link -> openExternalUrl(context, link.url) }
            }
            WalletAction.GET_TEST_COINS -> wallet.chainId.faucetUrl?.let { openExternalUrl(context, it) }
            WalletAction.ADD_TO_NETWORK, WalletAction.RENAME, WalletAction.REVEAL_PHRASE,
            WalletAction.EXPORT_KEYS, WalletAction.DELETE,
            -> navigator.push(WalletManageRoute(walletId))
            WalletAction.HISTORY -> navigator.push(Routes.WalletHistory(walletId))
            WalletAction.STAKE -> navigator.push(Routes.WalletStaking(walletId))
            WalletAction.ADD_KEYS -> navigator.push(
                WalletSetupMethodsRoute(
                    chain = wallet.chainId,
                    upgradingWalletId = walletId,
                    upgradeNote = actions?.actions?.firstOrNull { it.action == WalletAction.ADD_KEYS }?.note ?: "",
                ),
            )
            WalletAction.MULTISIG, WalletAction.SCAN_BLOCKS, WalletAction.COINS, WalletAction.TOKEN_APPROVALS,
            WalletAction.NFTS, WalletAction.SHIELDED_FUNDS, WalletAction.MWEB_FUNDS, WalletAction.NETWORK_ACCOUNT,
            WalletAction.ACCESS_KEYS, WalletAction.TOKEN_STORAGE, WalletAction.COIN_OBJECTS, WalletAction.TOKEN_ACCOUNTS,
            WalletAction.TRUST_LINES, WalletAction.SIGN_MESSAGE, WalletAction.VERIFY_MESSAGE,
            -> navigator.push(Routes.WalletTool(walletId, action))
        }
    }

    /** Hide a holding from the wallet's total and the portfolio, or show it again; core keeps the choice with the wallet. */
    fun setHidden(holding: AssetHolding, hidden: Boolean) {
        scope.launch {
            try {
                app.stateCommands.apply(StateCommand.SetHoldingHidden(walletId, holding.id, hidden))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                app.reportCommandError(error)
            }
        }
    }

    SpectraPage(
        title = AppLocalization.string("Wallet Details"),
        // Waits for the read to finish, as Home's does, so the spinner means
        // something; this wallet's network is what it reads.
        onRefresh = { app.performCoreRefresh(AppRefreshIntent.Chain(wallet.chainId)) },
        actions = {
            // Name, keys, other networks and deletion: managing the wallet, not settings for experts.
            IconButton(onClick = { navigator.push(WalletManageRoute(walletId)) }) {
                Icon(Icons.Outlined.Tune, contentDescription = AppLocalization.string("Manage"))
            }
        },
    ) {
        WalletHeroCard(app, wallet, ensName)
        actions?.let { WalletEverydayActionBar(it.actions(WalletActionSection.EVERYDAY), ::perform) }
        WalletHoldingsCard(app, wallet, ::setHidden)
        wallet.address?.let { WalletAddressCard(wallet, it) }
        actions?.let { loaded ->
            val network = loaded.actions(WalletActionSection.NETWORK)
            if (network.isNotEmpty()) WalletNetworkActionsCard(network, ::perform)
            WalletNetworkNotesCard(loaded.summary)
        }
    }
}

@Composable
private fun WalletHeroCard(app: AppState, wallet: WalletState, ensName: String?) {
    val (artwork, badgeColor) = nativeChainBadge(wallet.family) ?: ChainBadge.FALLBACK
    SpectraCard(elevated = true) {
        Row(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            CoinBadge(artwork, wallet.familyName, badgeColor, size = 56.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                    Text(
                        wallet.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    if (wallet.signing.isWatchOnly) WatchOnlyBadge()
                }
                Text(
                    wallet.chainName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = badgeColor,
                )
                ensName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = secondaryText,
                        maxLines = 1,
                    )
                }
                if (wallet.balancesReadAt == null) {
                    val reading = AppLocalization.string("Reading balances…")
                    SpectraShimmer(
                        modifier = Modifier.widthIn(max = 140.dp).semantics { contentDescription = reading },
                        height = 20.dp,
                    )
                } else {
                    BalanceText(
                        text = app.amounts.formattedWalletTotal(wallet.id),
                        isHidden = app.preferences.hideBalances,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchOnlyBadge() {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        Icon(Icons.Outlined.Visibility, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Text(
            AppLocalization.string("Watching"),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
        )
    }
}

@Composable
private fun WalletHoldingsCard(app: AppState, wallet: WalletState, setHidden: (AssetHolding, Boolean) -> Unit) {
    var isShowingHiddenHoldings by rememberSaveable(wallet.id) { mutableStateOf(false) }
    // Core orders the holdings: most valuable first, unpriced last.
    fun present(holding: AssetHolding) = HoldingPresentation(
        coin = holding,
        amountText = app.amounts.formattedAssetAmount(holding.amount, holding.symbol, holding.id),
        valueText = app.amounts.formattedFiat(app.amounts.holdingValue(wallet.id, holding)),
    )
    val shown = wallet.shownHoldings.map(::present)
    // What the user hid, listed apart and counted in no total.
    val hidden = wallet.holdings.filter { wallet.hides(it) }.map(::present)
    val hidesBalances = app.preferences.hideBalances
    SpectraRowGroup(
        items = shown,
        title = AppLocalization.string("Holdings"),
        footer = {
            // Under the rows: why there are none, or the hidden holdings, folded
            // away until asked for, each with the way to show it again.
            if (shown.isEmpty() && hidden.isEmpty()) {
                SpectraEmptyStateContent(
                    title = AppLocalization.string("No assets loaded"),
                    message = AppLocalization.string("No assets loaded for this wallet yet."),
                    icon = Icons.Outlined.Inbox,
                    modifier = Modifier.padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.Space.m),
                )
            }
            if (hidden.isNotEmpty()) {
                if (shown.isNotEmpty()) SpectraDivider()
                val state = AppLocalization.string(if (isShowingHiddenHoldings) "Expanded" else "Collapsed")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isShowingHiddenHoldings = !isShowingHiddenHoldings }
                        .semantics { stateDescription = state }
                        .spectraRowPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        AppLocalization.format("Hidden Assets (%lld)", hidden.size.toLong()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (isShowingHiddenHoldings) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = secondaryText,
                    )
                }
                if (isShowingHiddenHoldings) {
                    hidden.forEach { holding ->
                        SpectraDivider(startInset = SpectraLayout.rowDividerInset)
                        Row(
                            modifier = Modifier.spectraRowPadding(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
                        ) {
                            HoldingRow(holding, hidesBalances, Modifier.weight(1f))
                            FilledTonalButton(onClick = { setHidden(holding.coin, false) }) {
                                Text(
                                    AppLocalization.string("Show"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { holding ->
        HiddenHoldingMenuRow(holding, hidesBalances) { setHidden(holding.coin, true) }
    }
}

/** A shown holding; a long press offers to take it out of the totals, as iOS's context menu does. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenHoldingMenuRow(holding: HoldingPresentation, hidesBalances: Boolean, hide: () -> Unit) {
    var isShowingMenu by remember { mutableStateOf(false) }
    Box {
        HoldingRow(
            holding,
            hidesBalances,
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { isShowingMenu = true },
                    onLongClickLabel = AppLocalization.string("Hide From Totals"),
                )
                .spectraRowPadding(),
        )
        DropdownMenu(expanded = isShowingMenu, onDismissRequest = { isShowingMenu = false }) {
            DropdownMenuItem(
                text = { Text(AppLocalization.string("Hide From Totals")) },
                leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                onClick = {
                    isShowingMenu = false
                    hide()
                },
            )
        }
    }
}

/** A holding's badge, name and balance; its row pads it. */
@Composable
private fun HoldingRow(holding: HoldingPresentation, hidesBalances: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(holding.coin.artworkName, holding.coin.symbol, holding.coin.color, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(holding.coin.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${holding.coin.symbol} • ${holding.coin.standardLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            BalanceText(
                text = holding.amountText,
                isHidden = hidesBalances,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            BalanceText(
                text = holding.valueText,
                isHidden = hidesBalances,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WalletAddressCard(wallet: WalletState, walletAddress: String) {
    val chain = wallet.chainId
    val shownAddress = displayAddress(chain, walletAddress)
    SpectraDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppLocalization.string("Wallet Address"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            CopyButton(value = shownAddress, title = AppLocalization.string("Copy"))
        }
        // Grouped and in its checksummed case, so it is compared a group at a
        // time; it wraps only between groups.
        Text(
            groupedAddress(shownAddress),
            modifier = Modifier
                .fillMaxWidth()
                .spectraInsetFill()
                .padding(horizontal = SpectraLayout.Space.m, vertical = SpectraLayout.Space.s)
                .semantics { contentDescription = walletAddress },
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        )
        // An ICP account is a hash of its principal; each names the wallet to a
        // different kind of sender.
        wallet.icpPrincipal?.let { principal ->
            Text(AppLocalization.string("Principal"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            SelectionContainer {
                Text(
                    principal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .spectraInsetFill()
                        .padding(horizontal = SpectraLayout.Space.m, vertical = SpectraLayout.Space.s),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = secondaryText,
                )
            }
            SpectraHintText(
                AppLocalization.string(
                    "Exchanges and ICP ledger transfers pay the account ID above. ICRC tokens and the NNS address the principal.",
                ),
            )
        }
    }
}
