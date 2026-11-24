package dev.spectra.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.spectra.models.AppLocalization

/** The mask a hidden balance shows. */
const val HIDDEN_BALANCE_MASK = "••••••"

/**
 * A balance — an amount held, or what it is worth — or a mask while Hide
 * Balances is on. The mask reads "Hidden" to TalkBack, not six bullets. A
 * price is public and never goes through this.
 *
 * [sentence] is the sentence the balance sits in, made from the balance or
 * from the mask; the balance alone when `null`.
 */
@Composable
fun BalanceText(
    text: String,
    isHidden: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    sentence: ((String) -> String)? = null,
) {
    fun phrase(value: String) = sentence?.invoke(value) ?: value
    if (isHidden) {
        val spoken = phrase(AppLocalization.string("Hidden"))
        Text(
            phrase(HIDDEN_BALANCE_MASK),
            modifier = modifier.semantics { contentDescription = spoken },
            style = style,
            color = color,
            maxLines = maxLines,
        )
    } else {
        Text(phrase(text), modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** How a [SpectraValueRow] draws its value. */
enum class SpectraValueStyle {
    /** Words or a figure, its digits of equal width so a column of amounts lines up. */
    Text,

    /**
     * A hash, an address or another id: monospaced, on one line, in groups of
     * four with the first and last primary, cut in the middle so both ends show.
     */
    Identifier,
}

/**
 * A label and its value: an optional accent icon, a secondary label and a
 * primary value. Side by side when both fit on one line, otherwise the value
 * goes under the label, so a long value is never squeezed.
 */
@Composable
fun SpectraValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: SpectraValueStyle = SpectraValueStyle.Text,
    isHidden: Boolean = false,
    valueColor: Color = Color.Unspecified,
) {
    val labelView: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
    }
    when (style) {
        SpectraValueStyle.Identifier -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            labelView()
            Spacer(Modifier.weight(1f))
            Text(
                groupedAddress(value),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        SpectraValueStyle.Text -> {
            val valueView: @Composable () -> Unit = {
                BalanceText(
                    text = value,
                    isHidden = isHidden,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = valueColor,
                )
            }
            // Side by side when label and value fit one line; stacked otherwise.
            Layout(
                modifier = modifier.fillMaxWidth(),
                contents = listOf(labelView, valueView),
            ) { (labelMeasurables, valueMeasurables), constraints ->
                val gap = SpectraLayout.Space.m.roundToPx()
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val labelWidth = labelMeasurables.first().maxIntrinsicWidth(Int.MAX_VALUE)
                val valueWidth = valueMeasurables.first().maxIntrinsicWidth(Int.MAX_VALUE)
                val width = constraints.maxWidth
                if (labelWidth + gap + valueWidth <= width) {
                    val labelPlaceable = labelMeasurables.first().measure(loose)
                    val valuePlaceable = valueMeasurables.first().measure(loose)
                    val height = maxOf(labelPlaceable.height, valuePlaceable.height)
                    layout(width, height) {
                        labelPlaceable.place(0, (height - labelPlaceable.height) / 2)
                        valuePlaceable.place(width - valuePlaceable.width, (height - valuePlaceable.height) / 2)
                    }
                } else {
                    val labelPlaceable = labelMeasurables.first().measure(loose)
                    val indent = if (icon != null) (20.dp + SpectraLayout.Space.s).roundToPx() else 0
                    val valuePlaceable = valueMeasurables.first().measure(loose.copy(maxWidth = (width - indent).coerceAtLeast(0)))
                    val spacing = SpectraLayout.Space.xxs.roundToPx()
                    layout(width, labelPlaceable.height + spacing + valuePlaceable.height) {
                        labelPlaceable.place(0, 0)
                        valuePlaceable.place(indent, labelPlaceable.height + spacing)
                    }
                }
            }
        }
    }
}

/**
 * The question a flow's page asks, over what answers it: Send's steps and
 * Receive's wallet list.
 */
@Composable
fun FlowPageHeading(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpectraLayout.Space.s)
            .semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
    }
}

/** A short explanation under a control or a card. */
@Composable
fun SpectraHintText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = secondaryText)
}

/** A line about something that needs attention, in the warning colour. */
@Composable
fun SpectraWarningText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = SpectraColors.semantic.warning)
}

/** A failure, in the error colour. */
@Composable
fun SpectraErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/**
 * A short line with its symbol, as iOS draws a `Label`: a note, a verdict, a
 * status, so its meaning is not carried by colour alone. The symbol sits by
 * the first line when the text wraps; [iconColor] tints it apart from the
 * text where the line asks for it.
 */
@Composable
fun SpectraLabel(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    iconColor: Color = color,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        Text(text, style = style, color = color)
    }
}

/**
 * An address or a hash in groups of four, a `0x` prefix riding on the first,
 * with the first and last groups primary and the rest secondary.
 */
@Composable
fun groupedAddress(address: String): AnnotatedString {
    val primary = LocalContentColor.current
    val secondary = secondaryText
    val hasHexPrefix = address.startsWith("0x")
    val body = if (hasHexPrefix) address.drop(2) else address
    val groups = body.chunked(4).toMutableList()
    if (groups.isEmpty()) return AnnotatedString(address)
    if (hasHexPrefix) groups[0] = "0x" + groups[0]
    return buildAnnotatedString {
        groups.forEachIndexed { index, group ->
            if (index > 0) append(" ")
            withStyle(SpanStyle(color = if (index == 0 || index == groups.lastIndex) primary else secondary)) {
                append(group)
            }
        }
    }
}

/**
 * Both ends of an address or hash, which is how one is recognised, where a
 * row has no room for the middle. Anything short enough is returned whole.
 */
fun shortAddress(value: String, keepingEachEnd: Int = 6): String {
    if (value.length <= keepingEachEnd * 2 + 1) return value
    return value.take(keepingEachEnd) + "…" + value.takeLast(keepingEachEnd)
}

/** A gap of one spacing step, for a row's leading badge. */
@Composable
fun SpectraGap(width: androidx.compose.ui.unit.Dp = SpectraLayout.Space.s) {
    Spacer(Modifier.width(width))
}
