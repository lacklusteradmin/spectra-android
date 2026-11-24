package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.isWatchOnly
import dev.spectra.wallets.title
import uniffi.spectra_core.NearAccessKey
import uniffi.spectra_core.NearAccessKeys
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletState

/** Deleting one function-call key of the wallet's NEAR account. */
internal data class DeleteAccessKeyRoute(val walletId: String, val key: NearAccessKey) : Route

/**
 * A NEAR account's access keys, as core reads them from a node, with the
 * one Spectra signs with marked; and deleting a function-call key a dapp was
 * given: core builds the deletion as a send, and its page signs and
 * broadcasts it through the same stages.
 */
@Composable
internal fun WalletAccessKeysView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val keys = remember(wallet.id) { ToolRead<NearAccessKeys>() }
    suspend fun load() = keys.load { app.bridge.ready().walletAccessKeys(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.ACCESS_KEYS.title, onRefresh = { load() }) {
        ToolReadSections(keys) { account ->
            val full = account.keys.filter { it.fullAccess }
            val calls = account.keys.filter { !it.fullAccess }
            ToolListSection(
                items = full,
                title = AppLocalization.string("Full Access"),
                footer = AppLocalization.string(
                    "A full-access key can do anything with the account. Spectra lists these keys and does not delete them.",
                ),
            ) { AccessKeyRow(wallet, it) { navigator.push(DeleteAccessKeyRoute(wallet.id, it)) } }
            ToolListSection(
                items = calls,
                title = AppLocalization.string("Function Call"),
                footer = AppLocalization.string(
                    "A dapp adds a function-call key when you sign in to it. The key can call only its contract and spend gas only from its allowance. Delete the ones you no longer use.",
                ),
                emptyText = AppLocalization.string("No function-call keys."),
            ) { AccessKeyRow(wallet, it) { navigator.push(DeleteAccessKeyRoute(wallet.id, it)) } }
        }
    }
}

@Composable
private fun AccessKeyRow(wallet: WalletState, key: NearAccessKey, delete: () -> Unit) {
    val app = LocalAppState.current
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        IdentifierText(key.publicKey, color = MaterialTheme.colorScheme.onSurface)
        if (key.signs) {
            SpectraLabel(
                text = AppLocalization.string("Spectra signs with this key"),
                icon = Icons.Outlined.Verified,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val receiver = key.receiver
        if (receiver != null) {
            IdentifierValueRow(AppLocalization.string("Contract"), receiver)
            SpectraValueRow(
                label = AppLocalization.string("Methods"),
                value = if (key.methodNames.isEmpty()) AppLocalization.string("Any") else key.methodNames.joinToString(", "),
            )
            SpectraValueRow(
                label = AppLocalization.string("Allowance"),
                value = key.allowance?.let { app.amounts.formattedNativeAmount(it, wallet.chainId) }
                    ?: AppLocalization.string("Unlimited"),
            )
            if (!wallet.signing.isWatchOnly) {
                ToolRowButton(text = AppLocalization.string("Delete Key"), onClick = delete, destructive = true)
            }
        }
    }
}

/** Building, signing and broadcasting one key deletion. */
@Composable
internal fun DeleteAccessKeyView(wallet: WalletState, key: NearAccessKey) {
    val app = LocalAppState.current
    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string("Delete Key"),
        authenticationReason = AppLocalization.format("Authenticate to delete a key from %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The key is gone once it confirms."),
        build = { app.bridge.ready().buildAccessKeyDeletion(wallet.id, key.publicKey) },
    ) { _ ->
        ToolSection(
            footer = AppLocalization.string(
                "The contract can no longer act for this account with this key. The dapp asks you to sign in again to get a new one.",
            ),
        ) {
            IdentifierValueRow(AppLocalization.string("Key"), key.publicKey)
            key.receiver?.let { IdentifierValueRow(AppLocalization.string("Contract"), it) }
        }
    }
}
