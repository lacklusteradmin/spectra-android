package dev.spectra.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uniffi.spectra_core.EndpointHolder

/**
 * Who holds an address, as core names it: one of the user's wallets or a
 * saved contact. Shown beside an address wherever one is reviewed — a send's
 * recipient, a transaction's parties — so a reader sees "Alice" before
 * comparing characters.
 */
@Composable
fun EndpointHolderLabel(holder: EndpointHolder, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        Icon(holder.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            holder.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The wallet's or the contact's name. */
val EndpointHolder.name: String
    get() = when (this) {
        is EndpointHolder.Wallet -> name
        is EndpointHolder.Contact -> name
    }

/** A wallet or a person, as the holder's symbol. */
val EndpointHolder.icon: ImageVector
    get() = when (this) {
        is EndpointHolder.Wallet -> Icons.Outlined.AccountBalanceWallet
        is EndpointHolder.Contact -> Icons.Outlined.AccountCircle
    }
