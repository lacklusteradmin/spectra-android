package dev.spectra.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.readOrNull
import dev.spectra.components.EndpointHolderLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.icon
import dev.spectra.components.name
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.Chain
import uniffi.spectra_core.EndpointHolder

/**
 * Who the recipient is, from a local core lookup of the address against the
 * user's wallets and contacts; a menu selection never supplies identity.
 * [compact] is the one-line form the amount page shows under its question.
 */
@Composable
internal fun SendRecipientIdentityView(chain: Chain?, address: String, compact: Boolean = false) {
    val app = LocalAppState.current
    val walletId = app.sendFlow.walletId
    var holder by remember { mutableStateOf<EndpointHolder?>(null) }
    // Keyed on everything the answer depends on, the address book included:
    // a contact saved or renamed is named here at once.
    LaunchedEffect(walletId, chain, address, app.addressBook.entries) {
        holder = null
        val trimmed = address.trim()
        if (trimmed.isEmpty() || chain == null) return@LaunchedEffect
        holder = readOrNull { app.bridge.ready().addressHolder(walletId, chain, trimmed) }
    }
    val current = holder
    if (compact) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                Icon(Icons.Outlined.NorthEast, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
                Text(AppLocalization.string("Send to"), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
            }
            if (current != null) {
                EndpointHolderLabel(current)
            } else {
                Text(address, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
        }
    } else if (current != null) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Box(modifier = Modifier.size(36.dp).spectraInsetFill(), contentAlignment = Alignment.Center) {
                Icon(current.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(current.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    AppLocalization.string(
                        when (current) {
                            is EndpointHolder.Wallet -> "Your wallet"
                            is EndpointHolder.Contact -> "Saved contact"
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }
        }
    }
}
