package dev.spectra.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraShimmer
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainBadge
import dev.spectra.models.chainName
import dev.spectra.models.family
import dev.spectra.models.familyName
import dev.spectra.models.isWatchOnly
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.shownHoldings
import uniffi.spectra_core.WalletState

/** A wallet as every list of wallets shows it: Home's and Receive's. */
data class WalletCardPresentation(
    val walletName: String,
    val chainTitleText: String,
    val totalValueText: String,
    val hidesBalance: Boolean,
    /** Core has not read this wallet's balances yet. */
    val isReadingBalances: Boolean,
    val assetCountText: String,
    val isWatchOnly: Boolean,
    val badgeArtworkName: String?,
    val badgeMark: String,
    val badgeColor: Color,
) {
    companion object {
        fun of(wallet: WalletState, app: AppState): WalletCardPresentation {
            val (artwork, color) = nativeChainBadge(wallet.family) ?: ChainBadge.FALLBACK
            val assetCount = wallet.shownHoldings.size
            return WalletCardPresentation(
                walletName = wallet.name,
                chainTitleText = wallet.chainName,
                totalValueText = app.amounts.formattedWalletTotal(wallet.id),
                hidesBalance = app.preferences.hideBalances,
                isReadingBalances = wallet.balancesReadAt == null,
                assetCountText = AppLocalization.formatCount("%lld assets", assetCount.toLong(), assetCount.toLong()),
                isWatchOnly = wallet.signing.isWatchOnly,
                badgeArtworkName = artwork,
                badgeMark = wallet.familyName,
                badgeColor = color,
            )
        }
    }
}

/**
 * One wallet's row: its badge, name and network, and its total — the same
 * row in Home's wallet list and Receive's. The caller makes the row
 * tappable and pads it as its list does.
 */
@Composable
fun WalletCardView(presentation: WalletCardPresentation, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(presentation.badgeArtworkName, presentation.badgeMark, presentation.badgeColor, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(presentation.walletName, style = MaterialTheme.typography.titleMedium)
            // Watching is said on the network line: a badge on the name line
            // took the width the name needed beside a balance.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                if (presentation.isWatchOnly) {
                    Icon(
                        Icons.Outlined.Visibility,
                        contentDescription = AppLocalization.string("Watching"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    presentation.chainTitleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText,
                    maxLines = 2,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            if (presentation.isReadingBalances) {
                val reading = AppLocalization.string("Reading balances…")
                SpectraShimmer(modifier = Modifier.width(70.dp).semantics { contentDescription = reading }, height = 14.dp)
            } else {
                BalanceText(
                    text = presentation.totalValueText,
                    isHidden = presentation.hidesBalance,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(presentation.assetCountText, style = MaterialTheme.typography.labelSmall, color = secondaryText)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
    }
}
