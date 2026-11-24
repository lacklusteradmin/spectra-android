package dev.spectra.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.CoinBadge
import dev.spectra.components.CopyButton
import dev.spectra.components.SpectraEmptyStateCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainBadge
import dev.spectra.models.chainName
import dev.spectra.models.nativeChainBadge
import dev.spectra.models.subtitleText
import uniffi.spectra_core.AddressBookEntry

/** Saved recipients, with adding a contact as a top bar action. */
@Composable
internal fun AddressBookPage() {
    val addressBook = LocalAppState.current.addressBook
    val navigator = LocalNavigator.current
    SpectraPage(
        title = AppLocalization.string("Address Book"),
        actions = {
            IconButton(onClick = { navigator.push(SettingsRoute.NewContact) }) {
                Icon(Icons.Outlined.Add, contentDescription = AppLocalization.string("New Contact"))
            }
        },
    ) {
        SettingsPageHeader(
            title = AppLocalization.string("Address Book"),
            subtitle = AppLocalization.string("Pick a saved recipient during a send instead of pasting an address."),
            icon = Icons.Outlined.AccountCircle,
        )
        // Core's reason for refusing a contact change, so a refused save does
        // not read as a save that did nothing.
        addressBook.error?.let { DismissibleErrorNotice(it, onDismiss = { addressBook.error = null }) }
        if (addressBook.entries.isEmpty()) {
            SpectraEmptyStateCard(
                title = AppLocalization.string("No saved addresses yet"),
                message = AppLocalization.string("Save frequent recipients here so future sends are faster."),
                icon = Icons.Outlined.PersonAddAlt,
                actionTitle = AppLocalization.string("New Contact"),
                actionIcon = Icons.Outlined.Add,
                action = { navigator.push(SettingsRoute.NewContact) },
            )
        } else {
            SpectraRowGroup(items = addressBook.entries) { entry ->
                AddressBookContactRow(entry, onOpen = { navigator.push(SettingsRoute.Contact(entry)) })
            }
        }
    }
}

/** One saved recipient. The row opens the contact; copying is its own button. */
@Composable
private fun AddressBookContactRow(entry: AddressBookEntry, onOpen: () -> Unit) {
    val (artwork, color) = nativeChainBadge(entry.chainId) ?: ChainBadge.FALLBACK
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onOpen)
                .heightIn(min = SpectraLayout.rowMinHeight)
                .padding(start = SpectraLayout.rowHorizontal, top = SpectraLayout.rowVertical, bottom = SpectraLayout.rowVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            CoinBadge(artwork, entry.chainName, color, size = 36.dp)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.subtitleText, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // One line, elided in the middle: an address is recognised by
                // both ends, and wrapped to two monospaced lines it, rather
                // than the name, was the largest thing in the row.
                Text(
                    entry.address,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        CopyButton(value = entry.address, modifier = Modifier.padding(end = SpectraLayout.Space.xs))
    }
}
