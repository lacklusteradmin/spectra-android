package dev.spectra.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLayout
import dev.spectra.components.secondaryText
import dev.spectra.models.color
import dev.spectra.models.localizedTitle
import uniffi.spectra_core.TransactionDirection
import uniffi.spectra_core.TransactionRecord
import uniffi.spectra_core.TransactionStatus

/**
 * A transaction's status as a pill: its word and a symbol, never colour
 * alone. [compact] is the list row's, beside the title.
 */
@Composable
internal fun TransactionStatusBadge(status: TransactionStatus, modifier: Modifier = Modifier, compact: Boolean = false) {
    val color = status.color
    val title = status.localizedTitle
    val icon: ImageVector = when (status) {
        TransactionStatus.PENDING -> Icons.Outlined.Schedule
        TransactionStatus.CONFIRMED -> Icons.Outlined.CheckCircle
        TransactionStatus.FAILED -> Icons.Outlined.Dangerous
    }
    val style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(
                horizontal = SpectraLayout.Space.s,
                vertical = if (compact) SpectraLayout.Space.xxs else SpectraLayout.Space.xs,
            )
            .clearAndSetSemantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (compact) 12.dp else 14.dp))
        Text(title, style = style.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, softWrap = false)
    }
}

/** A transaction's badge colour when it has no artwork: by the way it moved funds. */
internal val TransactionRecord.badgeColor: Color
    @Composable get() = when (direction) {
        TransactionDirection.OUTGOING -> SpectraColors.semantic.failure
        TransactionDirection.INCOMING -> SpectraColors.semantic.success
        TransactionDirection.NEUTRAL -> secondaryText
    }
