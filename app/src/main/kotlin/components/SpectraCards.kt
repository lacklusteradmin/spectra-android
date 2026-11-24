package dev.spectra.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A card: the one surface a page's content sits on. [elevated] is for hero
 * and header cards, one tone up from an ordinary content card. A tinted
 * notice passes its own [color]. Nothing inside a card is a card: a surface
 * inside one takes [spectraInsetFill].
 */
@Composable
fun SpectraCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SpectraLayout.Radius.card),
        color = color ?: if (elevated) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(content = content)
    }
}

/**
 * A detail card: an optional title over facts, padded as a card is. Every
 * detail page, review and summary lists its facts in one of these.
 */
@Composable
fun SpectraDetailCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SpectraCard(modifier = modifier, elevated = elevated) {
        Column(
            modifier = Modifier.padding(SpectraLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/**
 * One card holding a list, one row per element, with inset dividers between
 * rows. A list is rows in one card, never a card per row.
 *
 * The header is a [title] with an optional [trailing] count, or the caller's
 * own [header]. The [footer] sits under the rows, inside the card: an empty
 * state, a loading row or an action on the whole list. Each row applies
 * [spectraRowPadding] inside its own clickable, so the padding is part of the
 * tap target.
 */
@Composable
fun <T> SpectraRowGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: String? = null,
    dividerInset: Dp = SpectraLayout.rowDividerInset,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    SpectraCard(modifier = modifier) {
        if (title != null) {
            SpectraCardHeader(title = title, trailing = trailing)
            SpectraDivider()
        } else if (header != null) {
            header()
            SpectraDivider()
        }
        if (items.isNotEmpty()) {
            Column(modifier = Modifier.padding(vertical = SpectraLayout.Space.xs)) {
                items.forEachIndexed { index, item ->
                    row(item)
                    if (index < items.lastIndex) SpectraDivider(startInset = dividerInset)
                }
            }
        }
        footer?.invoke(this)
    }
}

/**
 * The lazy form of [SpectraRowGroup], for a [SpectraLazyPage]: one card
 * drawn as a segment per row, so only the rows on screen are composed. A
 * [footnote] explains the card from under it.
 */
fun <T> LazyListScope.lazyRowGroup(
    items: List<T>,
    key: ((T) -> Any)? = null,
    title: String? = null,
    trailing: String? = null,
    dividerInset: Dp = SpectraLayout.rowDividerInset,
    footnote: String? = null,
    row: @Composable (T) -> Unit,
) {
    if (title != null) {
        item {
            CardSegment(isFirst = true, isLast = items.isEmpty()) {
                SpectraCardHeader(title = title, trailing = trailing)
                SpectraDivider()
            }
        }
    }
    itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { index, item ->
        val isFirst = index == 0
        val isLast = index == items.lastIndex
        CardSegment(isFirst = isFirst && title == null, isLast = isLast) {
            if (isFirst) Spacer(Modifier.size(SpectraLayout.Space.xs))
            row(item)
            if (isLast) Spacer(Modifier.size(SpectraLayout.Space.xs)) else SpectraDivider(startInset = dividerInset)
        }
    }
    if (footnote != null) {
        item {
            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                modifier = Modifier.spectraPageWidth().padding(horizontal = SpectraLayout.rowHorizontal)
                    .padding(top = SpectraLayout.Space.s),
            )
        }
    }
    item { Spacer(Modifier.size(SpectraLayout.sectionSpacing)) }
}

/** A slice of a lazy card: the corners of its top on the first slice, of its bottom on the last. */
@Composable
private fun CardSegment(isFirst: Boolean, isLast: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val radius = SpectraLayout.Radius.card
    Surface(
        modifier = Modifier.spectraPageWidth(),
        shape = RoundedCornerShape(
            topStart = if (isFirst) radius else 0.dp,
            topEnd = if (isFirst) radius else 0.dp,
            bottomStart = if (isLast) radius else 0.dp,
            bottomEnd = if (isLast) radius else 0.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

/** A card's title row, with an optional trailing figure such as a count. */
@Composable
fun SpectraCardHeader(title: String, trailing: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.cardHeaderVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = secondaryText,
            )
        }
        action?.invoke()
    }
}

/** Collects the fixed rows of a [SpectraRowSection]. */
class SpectraRowSectionScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    /** One row of the section. */
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * A [SpectraRowGroup] of fixed rows rather than data: each [SpectraRowSectionScope.row]
 * is a row, with the same card, header and inset dividers. The settings tab
 * is a column of these. A [footer] explains the card from under it.
 */
@Composable
fun SpectraRowSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    dividerInset: Dp = SpectraLayout.rowDividerInset,
    content: SpectraRowSectionScope.() -> Unit,
) {
    val scope = SpectraRowSectionScope().apply(content)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        SpectraCard {
            if (title != null) {
                SpectraCardHeader(title = title)
                SpectraDivider()
            }
            Column(modifier = Modifier.padding(vertical = SpectraLayout.Space.xs)) {
                scope.rows.forEachIndexed { index, row ->
                    row()
                    if (index < scope.rows.lastIndex) SpectraDivider(startInset = dividerInset)
                }
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                modifier = Modifier.padding(horizontal = SpectraLayout.rowHorizontal),
            )
        }
    }
}
