package dev.spectra.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.app.performUserInitiatedRefresh
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainBadge
import dev.spectra.models.chainName
import dev.spectra.models.color
import dev.spectra.models.family
import dev.spectra.models.familyName
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.standardLabel
import dev.spectra.receive.beginReceive
import dev.spectra.send.beginSend
import uniffi.spectra_core.DashboardAssetGroup

/**
 * One asset across every wallet: what it is worth, where and by whom it is
 * held, and how to move it. Read from core's current groups by id, so a
 * refresh lands here as it lands on Home.
 */
@Composable
internal fun AssetGroupDetailScreen(assetGroupId: String) {
    val app = LocalAppState.current
    val assetGroup = app.dashboardAssetGroups.firstOrNull { it.id == assetGroupId }
    val navigator = LocalNavigator.current
    SpectraPage(
        title = assetGroup?.symbol ?: "",
        onRefresh = { app.performUserInitiatedRefresh() },
        actions = {
            // The catalog's own page for the token, where its contract and
            // precision are listed; a chain's native coin has none.
            if (app.tokenPreferences.entries.any { it.token.tokenId == assetGroupId }) {
                IconButton(onClick = { navigator.push(Routes.TokenDetail(assetGroupId)) }) {
                    Icon(Icons.Outlined.Info, contentDescription = AppLocalization.string("Token Details"))
                }
            }
        },
    ) {
        if (assetGroup != null) {
            AssetDetailHeroCard(app, assetGroup)
            AssetMoveButtons(app, assetGroup)
            AssetChainBreakdownCard(app, assetGroup)
            AssetWalletBreakdownCard(app, assetGroup)
        } else {
            SpectraEmptyStateCard(
                title = AppLocalization.string("Asset not listed"),
                message = AppLocalization.string("This asset is no longer in your portfolio."),
                icon = Icons.Outlined.PieChart,
            )
        }
    }
}

/**
 * Send and Receive for this asset, from the wallets that can: Send opens on
 * a wallet holding it, Receive on one whose network it lives on.
 */
@Composable
private fun AssetMoveButtons(app: AppState, assetGroup: DashboardAssetGroup) {
    val holdingKeys = assetGroup.holdings.map { it.coin.id }.toSet()
    val sendTarget = app.walletDerivedCache.sendEnabledWallets.firstNotNullOfOrNull { wallet ->
        app.availableSendCoins(wallet.id).firstOrNull { it.id in holdingKeys }?.let { coin -> wallet.id to coin.id }
    }
    val chains = assetGroup.holdings.map { it.coin.chainId }.toSet()
    val receiveWalletId = app.walletDerivedCache.receiveEnabledWallets.firstOrNull { it.chainId in chains }?.id
    MoveButtons(
        canSend = sendTarget != null,
        canReceive = receiveWalletId != null,
        onSend = { sendTarget?.let { (walletId, holdingKey) -> app.beginSend(walletId = walletId, holdingKey = holdingKey) } },
        onReceive = { receiveWalletId?.let { app.beginReceive(walletId = it) } },
    )
}

@Composable
private fun AssetDetailHeroCard(app: AppState, assetGroup: DashboardAssetGroup) {
    val hidden = app.preferences.hideBalances
    val amounts = app.amounts
    SpectraCard(elevated = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            CoinBadge(
                artworkName = assetGroup.artworkName,
                fallbackText = assetGroup.symbol,
                color = assetGroup.color,
                size = 60.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                Text(assetGroup.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                BalanceText(
                    amounts.formattedFiat(assetGroup.totalValue),
                    isHidden = hidden,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                BalanceText(
                    amounts.formattedAssetAmount(assetGroup.totalAmount, assetGroup.symbol, assetGroup.identity.id),
                    isHidden = hidden,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = secondaryText,
                )
                Text(
                    AppLocalization.format(
                        "dashboard.asset.price",
                        amounts.formattedFiat(assetGroup.price),
                        assetGroup.symbol,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }
        }
    }
}

/**
 * Every place the asset is held, largest first.
 *
 * The badge is the **chain's**, which is what the row is about: USDC on
 * Ethereum and USDC on Solana must not look alike. The asset's colour stands
 * in for a chain the registry has no artwork for.
 */
@Composable
private fun AssetChainBreakdownCard(app: AppState, assetGroup: DashboardAssetGroup) {
    val amounts = app.amounts
    AssetBreakdownCard(AppLocalization.string("Chain Breakdown"), assetGroup.holdings) { holding ->
        val coin = holding.coin
        val badge = nativeChainBadge(coin.chainId)
        AssetBreakdownRow(
            badgeArtworkName = badge?.artworkName,
            badgeColor = badge?.color ?: coin.color,
            badgeMark = coin.chainName,
            title = coin.chainName,
            subtitle = coin.standardLabel,
            amountText = amounts.formattedAssetAmount(coin.amount, coin.symbol, coin.id),
            valueText = amounts.formattedFiat(holding.value),
            hidesBalance = app.preferences.hideBalances,
        )
    }
}

/**
 * Every wallet that holds the asset, largest first, each summed over the
 * networks it holds it on. The badge is the wallet's, as in the wallet list.
 */
@Composable
private fun AssetWalletBreakdownCard(app: AppState, assetGroup: DashboardAssetGroup) {
    val amounts = app.amounts
    val shares = assetGroup.wallets.mapNotNull { share -> app.wallet(share.walletId)?.let { share to it } }
    AssetBreakdownCard(AppLocalization.string("Wallet Breakdown"), shares) { (share, wallet) ->
        val badge = nativeChainBadge(wallet.family)
        AssetBreakdownRow(
            badgeArtworkName = badge?.artworkName,
            badgeColor = badge?.color ?: ChainBadge.FALLBACK.color,
            badgeMark = wallet.familyName,
            title = wallet.name,
            subtitle = wallet.chainName,
            amountText = amounts.formattedAssetAmount(share.amount, assetGroup.symbol, assetGroup.identity.id),
            valueText = amounts.formattedFiat(share.value),
            hidesBalance = app.preferences.hideBalances,
        )
    }
}

/** A titled card of breakdown rows with dividers between them. */
@Composable
private fun <T> AssetBreakdownCard(title: String, items: List<T>, row: @Composable (T) -> Unit) {
    SpectraDetailCard(title = title) {
        items.forEachIndexed { index, item ->
            row(item)
            if (index < items.lastIndex) SpectraDivider()
        }
    }
}

/** One share of the asset: where or who, then how much and what it is worth. */
@Composable
private fun AssetBreakdownRow(
    badgeArtworkName: String?,
    badgeColor: Color,
    badgeMark: String,
    title: String,
    subtitle: String,
    amountText: String,
    valueText: String,
    hidesBalance: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(artworkName = badgeArtworkName, fallbackText = badgeMark, color = badgeColor, size = 30.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = secondaryText)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            BalanceText(
                amountText,
                isHidden = hidesBalance,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            BalanceText(valueText, isHidden = hidesBalance, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1)
        }
    }
}
