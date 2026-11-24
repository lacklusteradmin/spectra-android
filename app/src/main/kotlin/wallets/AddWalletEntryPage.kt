package dev.spectra.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.readOrNull
import dev.spectra.components.AllChainsSelectionPage
import dev.spectra.components.ChainSelectionDescriptor
import dev.spectra.components.CoinBadge
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.isWatchOnly
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletSecretFormat
import uniffi.spectra_core.WalletSetupMethod
import uniffi.spectra_core.WalletSetupOption
import uniffi.spectra_core.walletSetupDescriptor

/**
 * Add Wallet: choose the network first. Every page after it — the ways of
 * adding a wallet, the formats they accept, the options they offer — is that
 * network's own: a wallet is on one network, and Add to Another Network
 * takes its phrase to the next. Funds Finder sits under the list, for a
 * phrase whose network is unknown.
 */
@Composable
internal fun AddWalletEntryPage() {
    val navigator = LocalNavigator.current
    val descriptors = remember { ChainSelectionDescriptor.popularOrder() }
    var searchText by rememberSaveable { mutableStateOf("") }
    // A choice opens the network's page, so each row ends in a chevron.
    AllChainsSelectionPage(
        title = AppLocalization.string("Add Wallet"),
        descriptors = descriptors,
        query = searchText,
        onQueryChange = { searchText = it },
        onPick = { chain -> navigator.push(WalletSetupMethodsRoute(chain)) },
        disclosure = true,
    ) {
        SpectraCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navigator.push(FundsFinderRoute()) }
                    .spectraRowPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp).padding(SpectraLayout.Space.xs),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    Text(AppLocalization.string("Find Lost Funds"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        AppLocalization.string("Check every network's derivation profiles for accounts a phrase has used."),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
            }
        }
    }
}

/**
 * One network's ways of adding a wallet, as core's setup descriptor lists
 * them, each with the formats it accepts there. Opened from a watched
 * wallet's page, the ways of giving that wallet its keys instead, as core
 * offers them for it.
 */
@Composable
internal fun WalletSetupMethodsPage(route: WalletSetupMethodsRoute) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val chain = route.chain
    val upgradingId = route.upgradingWalletId
    val descriptor = remember(chain) { walletSetupDescriptor(chain) }
    var upgradeMethods by remember { mutableStateOf<List<WalletSetupMethod>>(emptyList()) }
    val options: List<WalletSetupOption> =
        if (upgradingId == null) descriptor.options else descriptor.options.filter { it.method in upgradeMethods }
    val upgradedIsWatchOnly: Boolean? = upgradingId?.let { app.wallet(it)?.signing?.isWatchOnly }

    LaunchedEffect(upgradingId) {
        val id = upgradingId ?: return@LaunchedEffect
        upgradeMethods = readOrNull { app.bridge.ready().walletUpgradeMethods(id) } ?: emptyList()
    }
    // Once the wallet has its keys there is nothing left to give it.
    LaunchedEffect(upgradedIsWatchOnly) {
        if (upgradedIsWatchOnly == false) {
            val index = navigator.currentStack.indexOf(route)
            if (index > 0) navigator.truncate(index)
        }
    }

    // The header names the network; the bar would say it a second time.
    SpectraPage(title = if (upgradingId == null) "" else WalletAction.ADD_KEYS.title) {
        ChainSelectionDescriptor.of(chain)?.let { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                CoinBadge(row.artworkName, row.symbol, row.color, size = 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    Text(row.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text(row.tagLine, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                }
            }
        }
        if (upgradingId != null) {
            Text(AppLocalization.string(route.upgradeNote), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
        SpectraRowGroup(items = options) { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (upgradingId != null) {
                            app.beginWalletUpgrade(upgradingId, chain, option.method)
                        } else {
                            app.beginWalletSetup(chain, option.method)
                        }
                    }
                    .spectraRowPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                Icon(
                    option.method.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp).padding(SpectraLayout.Space.xs),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                    Text(option.method.title, style = MaterialTheme.typography.titleMedium)
                    Text(option.method.subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    Text(
                        option.formats.joinToString(" · ") { it.title },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = secondaryText.copy(alpha = 0.75f),
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = secondaryText)
            }
        }
    }
}

val WalletSetupMethod.title: String
    get() = AppLocalization.string(
        when (this) {
            WalletSetupMethod.CREATE_PHRASE -> "Create New Wallet"
            WalletSetupMethod.IMPORT_PHRASE -> "Import Seed Phrase"
            WalletSetupMethod.IMPORT_PRIVATE_KEY -> "Import Private Key"
            WalletSetupMethod.WATCH_ADDRESSES -> "Watch Addresses"
            WalletSetupMethod.WATCH_ACCOUNT_XPUB -> "Watch Account"
            WalletSetupMethod.WATCH_VIEW_KEY -> "Watch with View Key"
            WalletSetupMethod.WATCH_MULTISIG -> "Watch Multisig"
        },
    )

val WalletSetupMethod.subtitle: String
    get() = AppLocalization.string(
        when (this) {
            WalletSetupMethod.CREATE_PHRASE -> "Generate a new seed phrase and set up your wallet."
            WalletSetupMethod.IMPORT_PHRASE -> "Restore a wallet from its recovery phrase."
            WalletSetupMethod.IMPORT_PRIVATE_KEY -> "Add one account from its private key."
            WalletSetupMethod.WATCH_ADDRESSES -> "Track public addresses without adding private keys."
            WalletSetupMethod.WATCH_ACCOUNT_XPUB -> "Track a whole account from its extended public key."
            WalletSetupMethod.WATCH_VIEW_KEY -> "Scan what a wallet receives from its address and private view key."
            WalletSetupMethod.WATCH_MULTISIG -> "Watch a multisig account from the policy its address derives from."
        },
    )

val WalletSetupMethod.icon: ImageVector
    get() = when (this) {
        WalletSetupMethod.CREATE_PHRASE -> Icons.Outlined.AddCircleOutline
        WalletSetupMethod.IMPORT_PHRASE -> Icons.Outlined.Download
        WalletSetupMethod.IMPORT_PRIVATE_KEY -> Icons.Outlined.Key
        WalletSetupMethod.WATCH_ADDRESSES -> Icons.Outlined.Visibility
        WalletSetupMethod.WATCH_ACCOUNT_XPUB -> Icons.Outlined.AccountTree
        WalletSetupMethod.WATCH_VIEW_KEY -> Icons.Outlined.Preview
        WalletSetupMethod.WATCH_MULTISIG -> Icons.Outlined.Group
    }

val WalletSecretFormat.title: String
    get() = AppLocalization.string(
        when (this) {
            WalletSecretFormat.BIP39_PHRASE -> "BIP-39 phrase, 12 to 24 words"
            WalletSecretFormat.MONERO_PHRASE -> "Monero seed, 25 words"
            WalletSecretFormat.POLYSEED -> "Polyseed, 16 words"
            WalletSecretFormat.TON_MNEMONIC -> "TON mnemonic, 24 words"
            WalletSecretFormat.ALGORAND_MNEMONIC -> "Algorand mnemonic, 25 words"
            WalletSecretFormat.HEX_SECRET32 -> "32-byte key in hex"
            WalletSecretFormat.CARDANO_EXTENDED_KEY -> "64-byte extended key in hex"
            WalletSecretFormat.WIF -> "WIF"
            WalletSecretFormat.SOLANA_KEYPAIR -> "Base58 or JSON keypair"
            WalletSecretFormat.STELLAR_SECRET_SEED -> "Secret seed (S…)"
            WalletSecretFormat.SUI_PRIVATE_KEY -> "suiprivkey1…"
            WalletSecretFormat.APTOS_PRIVATE_KEY -> "AIP-80 key (ed25519-priv-0x…)"
            WalletSecretFormat.NEAR_SECRET_KEY -> "Key string (ed25519:…)"
            WalletSecretFormat.TEZOS_SECRET_KEY -> "Secret key (edsk…)"
            WalletSecretFormat.STARKNET_PRIVATE_KEY -> "Stark key in hex"
            WalletSecretFormat.HEDERA_PRIVATE_KEY -> "DER key in hex (302e… or 3030…)"
            WalletSecretFormat.ADDRESS -> "Addresses, one per line"
            WalletSecretFormat.ACCOUNT_XPUB -> "Account public key"
            WalletSecretFormat.MONERO_VIEW_KEY -> "Private view key, 64 hex digits"
            WalletSecretFormat.MULTISIG_DESCRIPTOR -> "Output descriptor, sortedmulti(…)"
            WalletSecretFormat.SUI_MULTISIG_PUBLIC_KEY -> "Threshold and weighted public keys, as Sui's SDK takes them"
            WalletSecretFormat.APTOS_MULTI_KEY -> "Signatures required and public keys, as Aptos's SDK takes them"
            WalletSecretFormat.CARDANO_NATIVE_SCRIPT -> "Native script, as cardano-cli writes it"
            WalletSecretFormat.SUBSTRATE_MULTISIG -> "Threshold and signatory addresses"
        },
    )
