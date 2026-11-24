package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.isWatchOnly
import dev.spectra.wallets.title
import uniffi.spectra_core.TokenApproval
import uniffi.spectra_core.TokenApprovals
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletState

/** Revoking one ERC-20 allowance the wallet granted. */
internal data class RevokeApprovalRoute(val walletId: String, val approval: TokenApproval) : Route

/**
 * The ERC-20 allowances an EVM wallet has granted, as core reads them from
 * its indexer and confirms live, and revoking one: core builds
 * `approve(spender, 0)` as a send, and its page signs and broadcasts it
 * through the same stages.
 */
@Composable
internal fun WalletApprovalsView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val approvals = remember(wallet.id) { ToolRead<TokenApprovals>() }
    suspend fun load() = approvals.load { app.bridge.ready().walletTokenApprovals(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.TOKEN_APPROVALS.title, onRefresh = { load() }) {
        ToolReadSections(approvals) { read ->
            if (read.approvals.isEmpty()) {
                WalletToolEmptySection(AppLocalization.string("This wallet has no standing token approvals."))
            } else {
                ToolListSection(
                    items = read.approvals,
                    footer = if (read.complete) null else AppLocalization.string("The indexer's list stopped short; more approvals may stand."),
                ) { approval ->
                    ApprovalRow(wallet, approval) { navigator.push(RevokeApprovalRoute(wallet.id, approval)) }
                }
            }
            if (read.approvals.isEmpty() && !read.complete) {
                ToolSectionFooter(AppLocalization.string("The indexer's list stopped short; more approvals may stand."))
            }
        }
    }
}

@Composable
private fun ApprovalRow(wallet: WalletState, approval: TokenApproval, revoke: () -> Unit) {
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                approval.symbol.ifEmpty { AppLocalization.string("Token") },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                if (approval.unlimited) AppLocalization.string("Unlimited") else approval.allowance,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = if (approval.unlimited) SpectraColors.semantic.warning else MaterialTheme.colorScheme.onSurface,
            )
        }
        IdentifierText(approval.token)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            Text(AppLocalization.string("Spender"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            IdentifierText(approval.spender, modifier = Modifier.weight(1f))
        }
        if (!wallet.signing.isWatchOnly) {
            ToolRowButton(text = AppLocalization.string("Revoke"), onClick = revoke, destructive = true)
        }
    }
}

/** Building, signing and broadcasting one revocation. */
@Composable
internal fun RevokeApprovalView(wallet: WalletState, approval: TokenApproval) {
    val app = LocalAppState.current
    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string("Revoke Approval"),
        authenticationReason = AppLocalization.format("Authenticate to revoke an approval from %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The allowance is zero once it confirms."),
        build = { app.bridge.ready().buildApprovalRevocation(wallet.id, approval.token, approval.spender) },
    ) { _ ->
        ToolSection(
            footer = AppLocalization.string(
                "This sets the allowance to zero. The spender can no longer move this token from the wallet.",
            ),
        ) {
            SpectraValueRow(label = AppLocalization.string("Token"), value = approval.symbol.ifEmpty { approval.token })
            IdentifierValueRow(AppLocalization.string("Spender"), approval.spender)
        }
    }
}
