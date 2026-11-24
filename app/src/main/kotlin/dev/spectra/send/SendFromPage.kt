package dev.spectra.send

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.FlowPageHeading
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.artworkName
import dev.spectra.models.chainName
import dev.spectra.models.color
import dev.spectra.models.displayName
import dev.spectra.models.family
import dev.spectra.models.familyName
import dev.spectra.models.formatRelativeTime
import dev.spectra.models.nativeChainBadge
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.CatalogColor
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendStage

private val AssetBadgeSize = 40.dp

/** The composer's first page: the sending wallet and the asset it sends. */
@Composable
internal fun SendFromPage() {
    val app = LocalAppState.current
    val presentation = SendComposerPresentation(app)
    FlowPageHeading(
        title = AppLocalization.string("Choose an asset to send"),
        subtitle = AppLocalization.string("Choose an asset from your wallet."),
    )
    // Both cards headed alike: a title over its rows.
    SpectraRowSection(title = AppLocalization.string("Sending wallet")) {
        row { SendingWalletRow(app, presentation) }
    }
    if (presentation.availableSendCoins.isNotEmpty()) {
        // One card, a row per asset, the choice marked with a trailing check —
        // as every selectable list is.
        SpectraRowGroup(
            items = presentation.availableSendCoins,
            title = AppLocalization.string("Available assets"),
            dividerInset = SpectraLayout.rowHorizontal + AssetBadgeSize + SpectraLayout.Space.m,
        ) { coin ->
            AssetRow(app, coin, isSelected = coin.id == app.sendFlow.holdingKey)
        }
    }
}

@Composable
private fun SendingWalletRow(app: AppState, presentation: SendComposerPresentation) {
    var showsWallets by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        val wallet = presentation.selectedWallet
        if (wallet != null) {
            val badge = nativeChainBadge(wallet.family)
            CoinBadge(badge?.artworkName, wallet.familyName, badge?.color ?: CatalogColor.MINT.color, size = 36.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(wallet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // The network, not the family: a Sepolia wallet read
                // "Ethereum" and looked like a mainnet one.
                Text(wallet.chainName, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (presentation.sendWallets.size > 1) {
            Box {
                TextButton(onClick = { showsWallets = true }) { Text(AppLocalization.string("Change")) }
                DropdownMenu(expanded = showsWallets, onDismissRequest = { showsWallets = false }) {
                    // Each with its network: two wallets of one name, on
                    // mainnet and on Sepolia, read the same by name alone.
                    presentation.sendWallets.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.name)
                                    Text(option.chainName, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                }
                            },
                            trailingIcon = {
                                if (option.id == app.sendFlow.walletId) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                showsWallets = false
                                if (app.sendFlow.walletId != option.id) {
                                    app.sendFlow.walletId = option.id
                                    app.syncSendAssetSelection()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One row per asset, full width. */
@Composable
private fun AssetRow(app: AppState, coin: AssetHolding, isSelected: Boolean) {
    val isAccessibilitySize = LocalDensity.current.fontScale >= ACCESSIBILITY_FONT_SCALE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected }
            .clickable(role = Role.RadioButton) {
                if (!isSelected) app.sendFlow.holdingKey = coin.id
            }
            .spectraRowPadding()
            .heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(coin.artworkName, coin.symbol, coin.color, size = AssetBadgeSize)
        val names: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(coin.symbol, style = MaterialTheme.typography.titleMedium)
                Text(coin.name, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
        val balance: @Composable () -> Unit = {
            BalanceText(
                text = app.amounts.formattedAssetAmount(coin.amount, coin.symbol, coin.id),
                isHidden = app.preferences.hideBalances,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
        }
        if (isAccessibilitySize) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                names()
                balance()
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                Box(Modifier.weight(1f)) { names() }
                balance()
            }
        }
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alpha(if (isSelected) 1f else 0f),
        )
    }
}

/**
 * A built or signed send that survived, as the first page offers it back:
 * what it moves, on which network, to whom, and which stage it is waiting in.
 */
@Composable
internal fun SavedSendList(artifacts: List<SendArtifact>, onResume: (SendArtifact) -> Unit) {
    val app = LocalAppState.current
    SpectraCard {
        artifacts.forEachIndexed { index, artifact ->
            if (index > 0) SpectraDivider(startInset = SpectraLayout.rowHorizontal + 28.dp + SpectraLayout.Space.m)
            SavedSendRow(
                artifact = artifact,
                walletName = app.wallet(artifact.walletId)?.name,
                onClick = { onResume(artifact) },
            )
        }
    }
}

@Composable
private fun SavedSendRow(artifact: SendArtifact, walletName: String?, onClick: () -> Unit) {
    val stageText = AppLocalization.string(
        when {
            artifact.stage == SendStage.PREPARED -> "Ready to sign"
            artifact.attempts.isEmpty() -> "Ready to broadcast"
            else -> "Submitted"
        },
    )
    val chain = artifact.chainId
    val badge = nativeChainBadge(chain)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(badge?.artworkName, artifact.symbol, badge?.color ?: secondaryText, size = 28.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(
                AmountPresentation.exactAmount(artifact.amount, artifact.symbol),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            )
            Text(chain.displayName, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1)
            if (walletName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = secondaryText, modifier = Modifier.size(14.dp))
                    Text(walletName, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "→ ${artifact.recipient}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(
                stageText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                formatRelativeTime((artifact.createdAt * 1000).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryText,
            )
        }
        RowChevron()
    }
}

/** The font scale from which a row stacks what it otherwise lays side by side. */
internal const val ACCESSIBILITY_FONT_SCALE = 1.5f
