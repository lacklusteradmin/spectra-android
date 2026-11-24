package dev.spectra.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.app.performUserInitiatedRefresh
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraEmptyStateContent
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLogo
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraShimmer
import dev.spectra.components.isAccessibilityTextSize
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.artworkName
import dev.spectra.models.color
import dev.spectra.models.formatNumber
import dev.spectra.models.formatPercent
import dev.spectra.receive.beginReceive
import dev.spectra.send.beginSend
import dev.spectra.wallets.WalletCardPresentation
import dev.spectra.wallets.WalletCardView
import uniffi.spectra_core.DashboardAssetGroup
import uniffi.spectra_core.TorStatus

/** Which list Home's card shows. */
enum class DashboardPage { WALLETS, ASSETS }

/** Home: the portfolio, Send and Receive, and the assets or wallets that make it up. */
@Composable
internal fun DashboardScreen() {
    val app = LocalAppState.current
    var page by rememberSaveable { mutableStateOf(DashboardPage.ASSETS) }
    SpectraPage(
        title = AppLocalization.string("Spectra"),
        large = true,
        actions = { DashboardToolbar(app) },
        onRefresh = { app.performUserInitiatedRefresh() },
    ) {
        // Before the first wallet a total, Send and Receive have nothing to
        // act on, so the page is one card that leads to adding one.
        if (app.wallets.isEmpty()) {
            DashboardWelcomeCard()
        } else {
            DashboardPortfolioHeader(app)
            DashboardActionButtons(app)
            AssetsOrWalletsCard(app, page) { page = it }
        }
    }
}

@Composable
private fun DashboardToolbar(app: AppState) {
    val navigator = LocalNavigator.current
    NoticeToolbarButton(app.appNoticeItems) { navigator.push(DashboardRoutes.Notices) }
    // Tor is off by default and lives in Settings; the toolbar reports it
    // only while it is doing something.
    TorToolbarIndicator(app.tor.status) { navigator.openTorSettings() }
    IconButton(onClick = { navigator.push(Routes.AddWallet) }) {
        Icon(Icons.Outlined.Add, contentDescription = AppLocalization.string("Add Wallet"))
    }
}

@Composable
private fun NoticeToolbarButton(notices: List<AppNoticeItem>, onClick: () -> Unit) {
    val count = notices.size
    val description = if (notices.isEmpty()) {
        AppLocalization.string("No active notices")
    } else {
        AppLocalization.formatCount("%lld active notices", count.toLong(), count)
    }
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        if (notices.isEmpty()) {
            Icon(Icons.Outlined.Inbox, contentDescription = null)
        } else {
            // The failure colour only when something failed; warnings take the warning colour.
            val tint = if (notices.any { it.severity == AppNoticeSeverity.ERROR }) {
                SpectraColors.semantic.failure
            } else {
                SpectraColors.semantic.warning
            }
            BadgedBox(
                badge = {
                    Badge(containerColor = tint, contentColor = MaterialTheme.colorScheme.surface) {
                        Text(if (count > 9) "9+" else formatNumber(count))
                    }
                },
            ) {
                Icon(Icons.Outlined.Feedback, contentDescription = null)
            }
        }
    }
}

/** Tor's glyph while it is connecting, connected or failing. There is none while it is stopped. */
@Composable
private fun TorToolbarIndicator(status: TorStatus, onClick: () -> Unit) {
    val semantic = SpectraColors.semantic
    val (color, label) = when (status) {
        TorStatus.Stopped -> return
        TorStatus.Ready -> semantic.success to AppLocalization.string("Tor on")
        is TorStatus.Bootstrapping ->
            semantic.warning to AppLocalization.format("Tor connecting, %lld%%", status.percent.toInt())
        is TorStatus.Error -> semantic.failure to AppLocalization.string("Tor error")
    }
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        BadgedBox(
            badge = {
                when (status) {
                    is TorStatus.Bootstrapping -> Badge(containerColor = color, contentColor = MaterialTheme.colorScheme.surface) {
                        Text(formatPercent(status.percent.toDouble() / 100.0))
                    }
                    is TorStatus.Error -> Badge(containerColor = color)
                    else -> Unit
                }
            },
        ) {
            Icon(
                if (status is TorStatus.Error) Icons.Outlined.PublicOff else Icons.Outlined.VpnLock,
                contentDescription = null,
                tint = color,
            )
        }
    }
}

// ── Portfolio ────────────────────────────────────────────────────────────

@Composable
private fun DashboardPortfolioHeader(app: AppState) {
    val navigator = LocalNavigator.current
    val hidden = app.preferences.hideBalances
    val total = app.portfolioQuotedTotal
    val reading = (total?.unreadWalletCount ?: 0uL) > 0uL
    SpectraCard(elevated = true) {
        // The two buttons sit in the card's corner on their own touch
        // targets; everything under them keeps the card's padding.
        Column(
            modifier = Modifier.padding(
                start = SpectraLayout.cardPadding,
                end = SpectraLayout.Space.xs,
                top = SpectraLayout.Space.xs,
                bottom = SpectraLayout.cardPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppLocalization.string("Portfolio"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { app.preferences.hideBalances = !hidden }) {
                    Icon(
                        if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = AppLocalization.string(if (hidden) "Show Balances" else "Hide Balances"),
                        tint = secondaryText,
                    )
                }
                HintedIconButton(
                    label = AppLocalization.string("Portfolio Wallets"),
                    hint = AppLocalization.string("Choose which wallets count in the total."),
                    onClick = { navigator.push(DashboardRoutes.PortfolioWallets) },
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = secondaryText)
                }
            }
            Column(
                modifier = Modifier.padding(end = SpectraLayout.cardPadding - SpectraLayout.Space.xs),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
            ) {
                if (total?.isUnread == true) {
                    val readingLabel = AppLocalization.string("Reading balances…")
                    SpectraShimmer(
                        modifier = Modifier.widthIn(max = 200.dp).semantics { contentDescription = readingLabel },
                        height = 34.dp,
                    )
                } else {
                    BalanceText(
                        text = app.amounts.formattedQuotedTotal(total),
                        isHidden = hidden,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (reading) ReadingBalancesLine(indicatorSize = 14)
                // A total that leaves wallets or unpriced assets out says so,
                // under the figure rather than in it.
                val walletCount = app.wallets.size
                val counted = app.wallets.count { it.includeInPortfolioTotal }
                if (counted < walletCount) {
                    FootnoteText(AppLocalization.formatCount("portfolio.counted_format", walletCount.toLong(), counted, walletCount))
                }
                if (total != null && !total.isUnread) {
                    if (total.unpricedCount > 0uL) {
                        val unpriced = total.unpricedCount.toLong()
                        FootnoteText(AppLocalization.formatCount("portfolio.unpriced_format", unpriced, unpriced))
                    }
                    if (total.testNetworkCount > 0uL) {
                        FootnoteText(AppLocalization.string("Test network assets are not counted."))
                    }
                }
            }
        }
    }
}

/**
 * An icon button that tells TalkBack both what it is and what it does, as
 * an iOS accessibility label and hint do.
 */
@Composable
private fun HintedIconButton(label: String, hint: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = hint, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun FootnoteText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = secondaryText)
}

@Composable
private fun ReadingBalancesLine(indicatorSize: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(indicatorSize.dp), strokeWidth = 2.dp)
        Text(AppLocalization.string("Reading balances…"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

@Composable
private fun DashboardWelcomeCard() {
    val navigator = LocalNavigator.current
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpectraLayout.cardPadding, vertical = SpectraLayout.Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.l),
        ) {
            SpectraLogo(size = 72.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
            ) {
                Text(
                    AppLocalization.string("Welcome to Spectra"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                Text(
                    AppLocalization.string("Create a new wallet or bring one you already have. Your keys stay on this device."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = { navigator.push(Routes.AddWallet) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(AppLocalization.string("Add Wallet"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DashboardActionButtons(app: AppState) {
    val canSend = app.walletDerivedCache.sendEnabledWallets.isNotEmpty()
    val canReceive = app.walletDerivedCache.receiveEnabledWallets.isNotEmpty()
    MoveButtons(
        canSend = canSend,
        canReceive = canReceive,
        onSend = { app.beginSend() },
        onReceive = { app.beginReceive() },
    )
}

/** Send and Receive side by side: Receive is the filled one, as on iOS. */
@Composable
internal fun MoveButtons(canSend: Boolean, canReceive: Boolean, onSend: () -> Unit, onReceive: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        OutlinedButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.NorthEast, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(SpectraLayout.Space.s))
            Text(AppLocalization.string("Send"), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onReceive,
            enabled = canReceive,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.SouthWest, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(SpectraLayout.Space.s))
            Text(AppLocalization.string("Receive"), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Assets and wallets ───────────────────────────────────────────────────

/**
 * The card's header is its page switch: a title per page, each with its
 * count, and the selected page's own action at the end.
 */
@Composable
private fun AssetsOrWalletsCard(app: AppState, page: DashboardPage, onSelect: (DashboardPage) -> Unit) {
    val navigator = LocalNavigator.current
    val rows = shownAssetRows(app)
    val header: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = SpectraLayout.rowHorizontal, end = SpectraLayout.Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.l),
        ) {
            DashboardPageTab(AppLocalization.string("Assets"), rows.size, page == DashboardPage.ASSETS) {
                onSelect(DashboardPage.ASSETS)
            }
            DashboardPageTab(AppLocalization.string("Wallets"), app.wallets.size, page == DashboardPage.WALLETS) {
                onSelect(DashboardPage.WALLETS)
            }
            Spacer(Modifier.weight(1f))
            // Pinning is available only on the assets page.
            if (page == DashboardPage.ASSETS) {
                IconButton(onClick = { navigator.push(Routes.PinnedAssets) }) {
                    Icon(Icons.Outlined.PushPin, contentDescription = AppLocalization.string("Pin Assets"), tint = secondaryText)
                }
            }
        }
    }
    when (page) {
        DashboardPage.ASSETS -> SpectraRowGroup(
            items = rows,
            header = header,
            footer = { AssetsFooter(app, rows) },
        ) { row ->
            DashboardAssetRow(
                row,
                Modifier
                    .clickable { navigator.push(Routes.AssetGroupDetail(row.id)) }
                    .spectraRowPadding(),
            )
        }
        DashboardPage.WALLETS -> SpectraRowGroup(items = app.wallets, header = header) { wallet ->
            WalletCardView(
                WalletCardPresentation.of(wallet, app),
                Modifier
                    .clickable { navigator.push(Routes.WalletDetail(wallet.id)) }
                    .spectraRowPadding(),
            )
        }
    }
}

@Composable
private fun DashboardPageTab(title: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = SpectraLayout.rowMinHeight)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else secondaryText,
        )
        Text(
            formatNumber(count),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isSelected) secondaryText else MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * What sits under the asset rows: placeholders while the only wallets are
 * still reading, a note while some are, the folded small balances, or why
 * there is nothing to list.
 */
@Composable
private fun ColumnScope.AssetsFooter(app: AppState, shown: List<DashboardAssetRowPresentation>) {
    val reading = (app.portfolioQuotedTotal?.unreadWalletCount ?: 0uL) > 0uL
    val smallCount = app.dashboardAssetGroups.count { it.isSmall }
    when {
        shown.isEmpty() && reading -> {
            val readingLabel = AppLocalization.string("Reading balances…")
            Column(
                modifier = Modifier
                    .padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.Space.m)
                    .clearAndSetSemantics { contentDescription = readingLabel },
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                repeat(3) { PlaceholderRow() }
            }
        }
        shown.isEmpty() && smallCount == 0 -> SpectraEmptyStateContent(
            title = AppLocalization.string("No balances yet"),
            message = AppLocalization.string("Pull down to read balances again."),
            icon = Icons.Outlined.PieChart,
            modifier = Modifier.padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.Space.m),
        )
        reading -> {
            SpectraDivider()
            ReadingBalancesLine(indicatorSize = 18, modifier = Modifier.spectraRowPadding())
        }
    }
    if (smallCount > 0) {
        val hidesSmall = app.preferences.hideSmallBalances
        SpectraDivider()
        Row(
            modifier = Modifier
                .clickable(role = Role.Button) { app.preferences.hideSmallBalances = !hidesSmall }
                .spectraRowPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            Text(
                if (hidesSmall) {
                    AppLocalization.formatCount("Show %lld small balances", smallCount.toLong(), smallCount)
                } else {
                    AppLocalization.string("Hide small balances")
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (hidesSmall) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = null,
                tint = secondaryText,
            )
        }
    }
}

/** A row's shape while its balances are read: a badge, two lines and a figure. */
@Composable
internal fun PlaceholderRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Box(Modifier.size(36.dp).spectraInsetFill(cornerRadius = 18.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
            SpectraShimmer(modifier = Modifier.widthIn(max = 140.dp), height = 14.dp)
            SpectraShimmer(modifier = Modifier.widthIn(max = 90.dp), height = 10.dp)
        }
        SpectraShimmer(modifier = Modifier.width(70.dp), height = 14.dp)
    }
}

/** The rows Home lists: core's groups, less the small ones while they are folded away. */
@Composable
private fun shownAssetRows(app: AppState): List<DashboardAssetRowPresentation> {
    val hidesSmall = app.preferences.hideSmallBalances
    val amounts = app.amounts
    val hidesBalance = app.preferences.hideBalances
    return app.dashboardAssetGroups
        .filter { !(hidesSmall && it.isSmall) }
        .map { DashboardAssetRowPresentation(it, amounts, hidesBalance) }
}

/** Core supplies an asset's display identity from its largest holding. */
val DashboardAssetGroup.name: String get() = identity.name
val DashboardAssetGroup.symbol: String get() = identity.symbol
val DashboardAssetGroup.artworkName: String get() = identity.artworkName
val DashboardAssetGroup.color: Color get() = identity.color

data class DashboardAssetRowPresentation(
    val assetGroup: DashboardAssetGroup,
    val amountText: String,
    val totalValueText: String,
    /**
     * A price is public: it shows with the balances hidden. `null` for an
     * asset with no price, whose "—" value already says so once.
     */
    val priceText: String?,
    val hidesBalance: Boolean,
) {
    val id: String get() = assetGroup.id

    constructor(assetGroup: DashboardAssetGroup, amounts: AmountPresentation, hidesBalance: Boolean) : this(
        assetGroup = assetGroup,
        amountText = amounts.formattedAssetAmount(assetGroup.totalAmount, assetGroup.symbol, assetGroup.identity.id),
        totalValueText = amounts.formattedFiat(assetGroup.totalValue),
        priceText = amounts.formattedFiatIfAvailable(assetGroup.price),
        hidesBalance = hidesBalance,
    )
}

@Composable
private fun DashboardAssetRow(presentation: DashboardAssetRowPresentation, modifier: Modifier = Modifier) {
    val group = presentation.assetGroup
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(artworkName = group.artworkName, fallbackText = group.symbol, color = group.color, size = 36.dp)
        // Side by side while it fits; at the largest text sizes the figures
        // go under the name rather than squeeze it to a few letters.
        if (isAccessibilityTextSize()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                AssetNameLine(group)
                BalanceText(
                    presentation.amountText,
                    isHidden = presentation.hidesBalance,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
                BalanceText(
                    presentation.totalValueText,
                    isHidden = presentation.hidesBalance,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                presentation.priceText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                AssetNameLine(group)
                BalanceText(
                    presentation.amountText,
                    isHidden = presentation.hidesBalance,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                BalanceText(
                    presentation.totalValueText,
                    isHidden = presentation.hidesBalance,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                presentation.priceText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1)
                }
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
    }
}

/** The asset's whole name, on two lines when one is not enough. */
@Composable
private fun AssetNameLine(group: DashboardAssetGroup) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        if (group.isPinned) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = AppLocalization.string("Pinned"),
                tint = secondaryText,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
    }
}
