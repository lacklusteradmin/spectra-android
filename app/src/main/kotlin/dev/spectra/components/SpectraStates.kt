package dev.spectra.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An empty state's words and icon, without a surface: for inside a card. On
 * its own between the cards of a page it is a [SpectraEmptyStateCard].
 */
@Composable
fun SpectraEmptyStateContent(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
    }
}

/** An empty state on its own card, with an optional action under it. */
@Composable
fun SpectraEmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionTitle: String? = null,
    actionIcon: ImageVector = Icons.AutoMirrored.Outlined.ArrowForward,
    action: (() -> Unit)? = null,
) {
    SpectraCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            SpectraEmptyStateContent(title = title, message = message, icon = icon)
            if (actionTitle != null && action != null) {
                Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    SpectraGap()
                    Text(actionTitle)
                }
            }
        }
    }
}

/** A loading row: a spinner beside what is being loaded. */
@Composable
fun SpectraLoadingRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
    }
}

/** A placeholder bar standing in for a line of text while it loads. */
@Composable
fun SpectraShimmer(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-phase",
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = androidx.compose.ui.geometry.Offset(phase * 400f - 200f, 0f),
                    end = androidx.compose.ui.geometry.Offset(phase * 400f + 200f, 0f),
                ),
            ),
    )
}

/**
 * The row carrying a flow's primary actions along the bottom edge, as wide
 * as the page's column and centred under it. A page places it in its
 * Scaffold's `bottomBar`, so the content scrolls above it; it rides on the
 * keyboard when one is up, so the step's action is never under it.
 */
@Composable
fun SpectraBottomActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = SpectraLayout.pageMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = SpectraLayout.Space.l, vertical = SpectraLayout.Space.s),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * A disclosure: the label with a turning chevron, and what it reveals at
 * full width under it.
 */
@Composable
fun SpectraDisclosure(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    label: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var ownExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val isExpanded = expanded ?: ownExpanded
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = !isExpanded
                    if (onExpandedChange != null) onExpandedChange(next) else ownExpanded = next
                }
                .padding(vertical = SpectraLayout.Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            if (label != null) {
                label()
            } else {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column { content() }
        }
    }
}

/** A notice in its own colour, such as a warning banner between cards. */
@Composable
fun SpectraNoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SpectraColors.semantic.warning,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    SpectraCard(modifier = modifier, color = color.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}
