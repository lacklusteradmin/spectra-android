package dev.spectra.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceBiometry
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraNavigationRow
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.CapabilityCoverage
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletActionOffer
import uniffi.spectra_core.WalletActionSection
import uniffi.spectra_core.WalletActions
import uniffi.spectra_core.WalletSetupSummary

// The rows and buttons a wallet's page draws from core's `WalletActions`.
// Core decides what a wallet offers and says what each action does; these
// views give each action its words and icon and nothing more.

val WalletAction.title: String
    get() = AppLocalization.string(
        when (this) {
            WalletAction.SEND -> "Send"
            WalletAction.MULTISIG -> "Multisig"
            WalletAction.RECEIVE -> "Receive"
            WalletAction.HISTORY -> "History"
            WalletAction.OPEN_IN_EXPLORER -> "Explorer"
            WalletAction.ADD_KEYS -> "Add Keys"
            WalletAction.STAKE -> "Staking"
            WalletAction.SCAN_BLOCKS -> "Sync Local Wallet"
            WalletAction.COINS -> "Addresses and Coins"
            WalletAction.TOKEN_APPROVALS -> "Token Approvals"
            WalletAction.NFTS -> "NFTs"
            WalletAction.SHIELDED_FUNDS -> "Shielded Funds"
            WalletAction.MWEB_FUNDS -> "MWEB Funds"
            WalletAction.NETWORK_ACCOUNT -> "Network Account"
            WalletAction.ACCESS_KEYS -> "Access Keys"
            WalletAction.TOKEN_STORAGE -> "Token Storage"
            WalletAction.COIN_OBJECTS -> "Coin Objects"
            WalletAction.GET_TEST_COINS -> "Get Test Coins"
            WalletAction.TOKEN_ACCOUNTS -> "Token Accounts"
            WalletAction.TRUST_LINES -> "Trust Lines"
            WalletAction.SIGN_MESSAGE -> "Sign Message"
            WalletAction.VERIFY_MESSAGE -> "Verify Message"
            WalletAction.ADD_TO_NETWORK -> "Add to Another Network"
            WalletAction.RENAME -> "Name"
            WalletAction.REVEAL_PHRASE -> "Show Seed Phrase"
            WalletAction.EXPORT_KEYS -> "Export Keys"
            WalletAction.DELETE -> "Delete Wallet"
        },
    )

/** How this device proves its owner, as an icon. */
val DeviceBiometry.icon: ImageVector
    get() = when (this) {
        DeviceBiometry.BIOMETRICS -> Icons.Outlined.Fingerprint
        DeviceBiometry.SCREEN_LOCK -> Icons.Outlined.Lock
    }

/** The action's icon; showing the phrase takes the device's own proof of its owner. */
val WalletAction.icon: ImageVector
    @Composable get() = when (this) {
        WalletAction.SEND -> Icons.Outlined.ArrowUpward
        WalletAction.MULTISIG -> Icons.Outlined.Draw
        WalletAction.RECEIVE -> Icons.Outlined.ArrowDownward
        WalletAction.HISTORY -> Icons.Outlined.History
        WalletAction.OPEN_IN_EXPLORER -> Icons.Outlined.Explore
        WalletAction.ADD_KEYS -> Icons.Outlined.Key
        WalletAction.STAKE -> Icons.Outlined.Savings
        WalletAction.SCAN_BLOCKS -> Icons.Outlined.Layers
        WalletAction.COINS -> Icons.AutoMirrored.Outlined.ListAlt
        WalletAction.TOKEN_APPROVALS -> Icons.Outlined.VerifiedUser
        WalletAction.NFTS -> Icons.Outlined.Collections
        WalletAction.SHIELDED_FUNDS -> Icons.Outlined.VisibilityOff
        WalletAction.MWEB_FUNDS -> Icons.Outlined.PrivacyTip
        WalletAction.NETWORK_ACCOUNT -> Icons.Outlined.Speed
        WalletAction.ACCESS_KEYS -> Icons.Outlined.VpnKey
        WalletAction.TOKEN_STORAGE -> Icons.Outlined.Archive
        WalletAction.COIN_OBJECTS -> Icons.Outlined.Apps
        WalletAction.GET_TEST_COINS -> Icons.Outlined.WaterDrop
        WalletAction.TOKEN_ACCOUNTS -> Icons.Outlined.Inbox
        WalletAction.TRUST_LINES -> Icons.Outlined.Link
        WalletAction.SIGN_MESSAGE -> Icons.Outlined.Draw
        WalletAction.VERIFY_MESSAGE -> Icons.Outlined.Verified
        WalletAction.ADD_TO_NETWORK -> Icons.Outlined.AddToPhotos
        WalletAction.RENAME -> Icons.Outlined.Edit
        WalletAction.REVEAL_PHRASE -> DeviceBiometry.current(LocalContext.current).icon
        WalletAction.EXPORT_KEYS -> Icons.Outlined.Key
        WalletAction.DELETE -> Icons.Outlined.Delete
    }

fun WalletActions.actions(section: WalletActionSection): List<WalletActionOffer> = actions.filter { it.section == section }

/**
 * Send, receive and history: one button each, in core's order, drawn as Home
 * draws Send and Receive, side by side and never broken mid-word.
 */
@Composable
internal fun WalletEverydayActionBar(offers: List<WalletActionOffer>, perform: (WalletAction) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        offers.forEach { offer ->
            val note = AppLocalization.string(offer.note)
            FilledTonalButton(
                onClick = { perform(offer.action) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { onClick(label = note, action = null) },
            ) {
                Icon(offer.action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap(SpectraLayout.Space.xs)
                Text(
                    offer.action.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** What the wallet's network adds, one row each with what it does. */
@Composable
internal fun WalletNetworkActionsCard(offers: List<WalletActionOffer>, perform: (WalletAction) -> Unit) {
    SpectraRowGroup(items = offers, dividerInset = SpectraLayout.rowHorizontal + 24.dp + SpectraLayout.Space.m) { offer ->
        SpectraNavigationRow(
            title = offer.action.title,
            subtitle = AppLocalization.string(offer.note),
            icon = offer.action.icon,
            onClick = { perform(offer.action) },
        )
    }
}

/**
 * What the network does not do for this wallet, or does only with the user's
 * endpoint: the limits and coverage the setup page showed. Draws nothing when
 * there is nothing to say.
 */
@Composable
internal fun WalletNetworkNotesCard(summary: WalletSetupSummary) {
    val reads = buildList {
        add(AppLocalization.string("Balance") to summary.balance)
        add(AppLocalization.string("History") to summary.history)
        summary.tokenDiscovery?.let { add(AppLocalization.string("Token Discovery") to it) }
    }
    val gaps = reads.filter { it.second != CapabilityCoverage.CONFIGURED }
    if (summary.limits.isEmpty() && gaps.isEmpty()) return
    SpectraDetailCard(title = AppLocalization.string("About This Network")) {
        gaps.forEach { (title, coverage) ->
            Row {
                Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(coverage.title, style = MaterialTheme.typography.bodyMedium, color = SpectraColors.semantic.warning)
            }
        }
        summary.limits.forEach { limit ->
            SpectraLabel(text = limit.explanation, icon = Icons.Outlined.Info, color = secondaryText)
        }
    }
}
