package dev.spectra.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout tokens. [docs/ANDROID-UI.md] is the authority for every value
 * here; this file is where the document is spelled in Kotlin, so a screen
 * never restates a number the document owns. The steps are spectra-apple's,
 * so the two apps space and round alike.
 */
object SpectraLayout {
    /** The spacing scale: every padding, gap and spacer is one of these steps. */
    object Space {
        /** Between two lines of one text pair. */
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
        val xl: Dp = 24.dp
    }

    val screenHorizontal: Dp = Space.l
    val screenTop: Dp = Space.s
    val screenBottom: Dp = Space.l
    val sectionSpacing: Dp = Space.m
    val cardPadding: Dp = Space.l
    val cardHeaderVertical: Dp = Space.m
    val rowHorizontal: Dp = Space.l
    val rowVertical: Dp = Space.s

    /** A row's minimum height: Material's touch target. */
    val rowMinHeight: Dp = 48.dp

    /**
     * Where a row divider starts: the row inset, a 36dp badge and the gap
     * after it, so the line runs under the text rather than the badge.
     */
    val rowDividerInset: Dp = rowHorizontal + 36.dp + Space.m

    /** The widest a page's column of cards grows, centred on a wide screen. */
    val pageMaxWidth: Dp = 720.dp

    /** Corner radii: a surface nested inside another takes the next step down. */
    object Radius {
        /** Cards of every kind: tab, hero, detail, list and notice cards. */
        val card: Dp = 20.dp
        /** Surfaces inside a card: inputs, address blocks, chips and tiles. */
        val inner: Dp = 14.dp
        /** Dense controls, icon backplates and single-character slots. */
        val control: Dp = 10.dp
        /** Bottom sheets and dialogs. */
        val sheet: Dp = 28.dp
    }

    /** Every divider's alpha, so a line weighs the same on every page. */
    const val DIVIDER_ALPHA: Float = 0.4f
}

/** Where a scrolling page's content sits: the screen inset every page uses. */
val SpectraScreenPadding = PaddingValues(
    start = SpectraLayout.screenHorizontal,
    end = SpectraLayout.screenHorizontal,
    top = SpectraLayout.screenTop,
    bottom = SpectraLayout.screenBottom,
)

/** A page's column: no wider than [SpectraLayout.pageMaxWidth]. */
fun Modifier.spectraPageWidth(): Modifier = fillMaxWidth().widthIn(max = SpectraLayout.pageMaxWidth)

/**
 * A surface inside a card — an input, an address block, a chip, a tile, an
 * icon backplate. Flat, one tone off the card, rather than a second card.
 */
@Composable
fun Modifier.spectraInsetFill(cornerRadius: Dp = SpectraLayout.Radius.inner): Modifier =
    clip(RoundedCornerShape(cornerRadius)).background(MaterialTheme.colorScheme.surfaceContainerHighest)

/**
 * A selectable surface inside a card: the inset fill, or the theme colour
 * when selected, its label then `onPrimary`.
 */
@Composable
fun Modifier.spectraSelectableFill(isSelected: Boolean, cornerRadius: Dp): Modifier =
    if (isSelected) {
        clip(RoundedCornerShape(cornerRadius)).background(MaterialTheme.colorScheme.primary)
    } else {
        spectraInsetFill(cornerRadius)
    }

/** The inset and minimum height of a row in a [SpectraRowGroup]. */
fun Modifier.spectraRowPadding(): Modifier = fillMaxWidth()
    .heightIn(min = SpectraLayout.rowMinHeight)
    .padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.rowVertical)

/** A divider inside a card, at the shared weight. */
@Composable
fun SpectraDivider(modifier: Modifier = Modifier, startInset: Dp = 0.dp) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = SpectraLayout.DIVIDER_ALPHA),
    )
}

/**
 * `text` with a zero-width space after every character, for display only: a
 * hash, payload or address then wraps at the edge without a hyphen that is
 * not in it. Copying must go through the original string.
 */
fun breakableAnywhere(text: String): String = text.toList().joinToString("​")

/** The secondary text colour, for labels and explanations. */
val secondaryText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Whether text is set at one of the largest sizes, where a row's figures go
 * under its name rather than squeeze it to a few letters: the iOS app's
 * accessibility sizes start at about one and a half times the default.
 */
@Composable
fun isAccessibilityTextSize(): Boolean = LocalDensity.current.fontScale >= 1.5f
