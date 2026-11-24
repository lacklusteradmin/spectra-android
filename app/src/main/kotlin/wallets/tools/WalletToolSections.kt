package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraNoticeCard
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import uniffi.spectra_core.WalletState

// The wallet tool pages — coins, approvals, NFTs, access keys, trust lines
// and the rest — are lists over one read from core. They share the states
// around that read, drawn here once so every page says them the same way,
// and the few presentation helpers the pages and staking share.

/**
 * Core's answer was a failure: its reason, with a symbol, so the state is
 * not carried by colour alone. Pull to refresh asks again.
 */
@Composable
internal fun WalletToolErrorSection(message: String) {
    SpectraNoticeCard(text = message, color = SpectraColors.semantic.failure, icon = Icons.Outlined.WarningAmber)
}

/** The first read has not answered yet. */
@Composable
internal fun WalletToolLoadingSection() {
    SpectraCard {
        Box(Modifier.fillMaxWidth().padding(SpectraLayout.cardPadding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        }
    }
}

/** The read answered with nothing to list. */
@Composable
internal fun WalletToolEmptySection(message: String) {
    SpectraCard {
        Text(
            message,
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryText,
        )
    }
}

/**
 * A group of facts or controls in one card, with an optional title over it
 * and an explanation under it: what a system form's section is on iOS.
 */
@Composable
internal fun ToolSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        SpectraDetailCard(title = title, content = content)
        if (footer != null) ToolSectionFooter(footer)
    }
}

/** A list's rows in one card, with an optional title and an explanation under the card. */
@Composable
internal fun <T> ToolListSection(
    items: List<T>,
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    emptyText: String? = null,
    row: @Composable (T) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        SpectraRowGroup(
            items = items,
            title = title,
            // The rows lead with text, not a badge: the divider runs under it.
            dividerInset = SpectraLayout.rowHorizontal,
            footer = if (items.isEmpty() && emptyText != null) {
                {
                    Text(
                        emptyText,
                        modifier = Modifier.padding(SpectraLayout.cardPadding),
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondaryText,
                    )
                }
            } else {
                null
            },
            row = row,
        )
        if (footer != null) ToolSectionFooter(footer)
    }
}

/** The explanation under a card. */
@Composable
internal fun ToolSectionFooter(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = SpectraLayout.rowHorizontal),
        style = MaterialTheme.typography.bodySmall,
        color = secondaryText,
    )
}

/**
 * A key, a contract, an outpoint or another id in a row: monospaced, and on
 * one line cut in the middle, so both of its ends show.
 */
@Composable
internal fun IdentifierText(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = secondaryText,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    maxLines: Int = 1,
) {
    Text(
        value,
        modifier = modifier,
        style = style.copy(fontFamily = FontFamily.Monospace),
        color = color,
        maxLines = maxLines,
        overflow = if (maxLines == 1) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
    )
}

/**
 * A tool page for a wallet this device no longer has: what it drew went
 * with the wallet.
 */
@Composable
internal fun MissingWalletPage(title: String) {
    SpectraPage(title = title) {
        SpectraEmptyStateCard(
            title = AppLocalization.string("Wallet not found"),
            message = AppLocalization.string("This wallet is no longer on this device."),
        )
    }
}

/** The wallet [walletId] names, drawn by [content], or a page saying it is gone. */
@Composable
internal fun WithWallet(walletId: String, title: String, content: @Composable (WalletState) -> Unit) {
    val wallet = LocalAppState.current.wallet(walletId)
    if (wallet == null) MissingWalletPage(title) else content(wallet)
}

/** A labelled id — a key, a contract, an address — on one line cut in the middle. */
@Composable
internal fun IdentifierValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

/** An action on one row of a list: a short text button, in the error colour when it destroys. */
@Composable
internal fun ToolRowButton(text: String, onClick: () -> Unit, destructive: Boolean = false, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = SpectraLayout.Space.xs),
        colors = if (destructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        },
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/**
 * One read from core a tool page lists: its answer, or why there is none.
 * A later failure keeps the answer on screen; pull to refresh asks again.
 */
internal class ToolRead<T : Any> {
    var value by mutableStateOf<T?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var isLoading = false

    suspend fun load(read: suspend () -> T) {
        if (isLoading) return
        isLoading = true
        try {
            value = read()
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = userErrorMessage(failure)
        } finally {
            isLoading = false
        }
    }
}

/** A read's answer drawn by [content]; until there is one, its failure or that it is loading. */
@Composable
internal fun <T : Any> ToolReadSections(read: ToolRead<T>, content: @Composable (T) -> Unit) {
    val value = read.value
    val error = read.error
    when {
        value != null -> content(value)
        error != null -> WalletToolErrorSection(error)
        else -> WalletToolLoadingSection()
    }
}
