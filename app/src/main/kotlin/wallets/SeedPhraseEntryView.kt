package dev.spectra.wallets

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.spectra.components.SecretWordField
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraInsetFill
import dev.spectra.models.AppLocalization
import kotlinx.coroutines.android.awaitFrame
import uniffi.spectra_core.SeedPhraseVerdict

/**
 * The seed-phrase entry grid: one field per slot, as many slots as core
 * judges the phrase at, and what core made of it underneath. The import flow
 * and the funds finder both show this, over the same [SeedPhraseEntry].
 *
 * The slot being typed in is not judged yet: "aban" is on its way to
 * "abandon", not a wrong word. Core's wordlist suggests where it may go.
 */
@Composable
internal fun SeedPhraseEntryGrid(entry: SeedPhraseEntry) {
    val context = LocalContext.current
    var focusedSlot by remember { mutableStateOf<Int?>(null) }
    var isConfirmingClear by remember { mutableStateOf(false) }
    /** The slot the keyboard moves to once it is drawn: a slot added this keystroke is not yet. */
    var focusTarget by remember { mutableStateOf<Int?>(null) }
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun requester(index: Int) = requesters.getOrPut(index) { FocusRequester() }

    LaunchedEffect(focusTarget) {
        val target = focusTarget ?: return@LaunchedEffect
        awaitFrame()
        runCatching { requester(target).requestFocus() }
        focusTarget = null
    }

    fun advance(index: Int) {
        // A space or Return in the last slot of an inferred length asks for more.
        if (index + 1 == entry.slots.size && entry.wordCountOverride == null && entry.slot(index).isNotEmpty()) {
            entry.addSlots()
        }
        val next = (index + 1).takeIf { it < entry.slots.size }
        focusedSlot = next
        // The keyboard moves now, in the event that typed the space, so the
        // next key lands in the next slot.
        if (next != null) focusTarget = next
    }

    fun typed(index: Int, newValue: String) {
        entry.update(index, newValue)
        // Several words arrive at once when typed faster than focus moves, or
        // pasted from the field's menu; `update` puts them in the slots from
        // here on, so the cursor follows the last rather than the next key
        // overwriting the second.
        val wordCount = newValue.whitespaceSeparatedWords().size
        if (wordCount == 0) return
        val last = index + wordCount - 1
        if (newValue.last().isWhitespace()) {
            advance(last)
        } else if (last != index) {
            focusedSlot = last
            focusTarget = last
        }
    }

    val verdict = entry.verdict
    val typing = focusedSlot?.let { entry.slot(it) } ?: ""
    // The word under the cursor is unfinished, not invalid.
    val invalidWords = verdict.invalidWords.toSet() - (if (typing.isEmpty()) emptySet() else setOf(typing))
    val status = seedPhraseStatus(verdict, invalidWords)

    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
        // Header: how far the phrase is, and the ways to fill or empty it at once.
        val filled = verdict.words.size
        val isComplete = verdict.isValid
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            val countColor = if (isComplete) SpectraColors.semantic.success else secondaryText
            val spoken = AppLocalization.format("%lld of %lld words", filled.toLong(), verdict.wordCount.toLong())
            Text(
                "$filled / ${verdict.wordCount}",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isComplete) countColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xs)
                    .semantics { contentDescription = spoken },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = countColor,
            )
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text != null) {
                        entry.paste(text)
                        focusedSlot = null
                    }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = SpectraLayout.Space.m,
                    vertical = SpectraLayout.Space.xs,
                ),
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                SpectraGap(SpectraLayout.Space.xs)
                Text(AppLocalization.string("Paste"))
            }
            if (filled > 0) {
                IconButton(onClick = { isConfirmingClear = true }) {
                    Icon(Icons.Outlined.Cancel, contentDescription = AppLocalization.string("Clear"), tint = secondaryText)
                }
            }
        }

        // Where the word being typed may be going. Its height is held for as
        // long as a slot is being typed in, whatever the suggestions: a bar
        // that came and went with each keystroke moved the grid under the cursor.
        focusedSlot?.let { slot ->
            val suggestions = if (typing.isEmpty()) emptyList() else entry.suggestions(typing)
            val shown = if (suggestions == listOf(typing)) emptyList() else suggestions
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shown.forEach { word ->
                    Text(
                        word,
                        modifier = Modifier
                            .spectraInsetFill()
                            .clickable(onClickLabel = AppLocalization.format("Word %lld", (slot + 1).toLong())) {
                                entry.update(slot, word)
                                advance(slot)
                            }
                            .padding(horizontal = SpectraLayout.Space.m, vertical = SpectraLayout.Space.xs),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }

        SeedPhraseColumns(minimumColumnWidth = 140.dp) { columns ->
            entry.slots.indices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    row.forEach { index ->
                        val word = entry.slot(index)
                        val isInvalid = word in invalidWords
                        // The field draws its own outline, focus and error; the cell only numbers it.
                        SeedPhraseWordCell(index = index, modifier = Modifier.weight(1f), framed = false) {
                            // The keyboard's own layout: a Japanese, Korean or Chinese
                            // wordlist cannot be typed on an ASCII one.
                            SecretWordField(
                                value = word,
                                onValueChange = { typed(index, it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(requester(index))
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            focusedSlot = index
                                        } else if (focusedSlot == index) {
                                            focusedSlot = null
                                        }
                                    }
                                    .semantics { contentDescription = AppLocalization.format("Word %lld", (index + 1).toLong()) },
                                isError = isInvalid,
                                imeAction = if (index == entry.slots.lastIndex) ImeAction.Done else ImeAction.Next,
                                onImeAction = { advance(index) },
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            SpectraLabel(
                text = status.text,
                icon = status.icon,
                color = status.color,
                modifier = Modifier.weight(1f),
            )
            if (entry.wordCountOverride == null && entry.nextSlotCount != null) {
                TextButton(onClick = { entry.addSlots() }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    SpectraGap(SpectraLayout.Space.xs)
                    Text(AppLocalization.string("More words"))
                }
            }
        }
    }

    if (isConfirmingClear) {
        AlertDialog(
            onDismissRequest = { isConfirmingClear = false },
            title = { Text(AppLocalization.string("Clear every word?")) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingClear = false
                    entry.clear()
                    focusedSlot = 0
                    focusTarget = 0
                }) { Text(AppLocalization.string("Clear"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingClear = false }) { Text(AppLocalization.string("Cancel")) }
            },
        )
    }
}

/** The line under the grid: what core read the entry as, or what is wrong with it. */
private data class SeedPhraseStatus(val text: String, val color: Color, val icon: ImageVector)

/** Never colour alone: each state has its icon. */
@Composable
private fun seedPhraseStatus(verdict: SeedPhraseVerdict, settledInvalid: Set<String>): SeedPhraseStatus {
    val languageName = verdict.language?.let { AppLocalization.string(it.name) }
    val failure = MaterialTheme.colorScheme.error
    if (verdict.words.isEmpty()) {
        return SeedPhraseStatus(AppLocalization.string("import_flow.seed_phrase_detect_hint"), secondaryText, Icons.Outlined.Edit)
    }
    if (settledInvalid.isNotEmpty()) {
        val words = verdict.invalidWords.filter { it in settledInvalid }.joinToString(", ")
        val text = if (languageName == null) {
            AppLocalization.format("import_flow.seed_phrase_unknown_words_format", words)
        } else {
            AppLocalization.format("import_flow.seed_phrase_off_list_format", languageName, words)
        }
        return SeedPhraseStatus(text, failure, Icons.Outlined.WarningAmber)
    }
    verdict.problem?.let { return SeedPhraseStatus(it.localizedMessage, failure, Icons.Outlined.WarningAmber) }
    if (verdict.isValid && languageName != null) {
        return SeedPhraseStatus(
            AppLocalization.format("import_flow.seed_phrase_valid_format", languageName, verdict.wordCount.toLong()),
            SpectraColors.semantic.success,
            Icons.Outlined.CheckCircle,
        )
    }
    return SeedPhraseStatus(
        AppLocalization.format(
            "import_flow.seed_phrase_typing_format",
            languageName ?: "—",
            verdict.words.size.toLong(),
            verdict.wordCount.toLong(),
        ),
        secondaryText,
        Icons.Outlined.MoreHoriz,
    )
}

/**
 * Three columns where a word fits whole in one, two on a narrow screen or at
 * a large font, so no word is cut short.
 */
@Composable
private fun SeedPhraseColumns(minimumColumnWidth: Dp = 120.dp, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= minimumColumnWidth * 3) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) { content(columns) }
    }
}

/** One numbered word slot, editable or shown. */
@Composable
internal fun SeedPhraseWordCell(
    index: Int,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    isInvalid: Boolean = false,
    framed: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = if (isInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val shape = RoundedCornerShape(SpectraLayout.Radius.control)
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .then(
                if (framed) {
                    Modifier
                        .spectraInsetFill(SpectraLayout.Radius.control)
                        .border(1.dp, if (isHighlighted || isInvalid) accent else Color.Transparent, shape)
                        .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xs)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        Text(
            "${index + 1}",
            modifier = Modifier.width(18.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = secondaryText,
            textAlign = TextAlign.End,
        )
        content()
    }
}

/**
 * A phrase's words as shown, numbered, each whole on one line. A word shrinks
 * to fit rather than wrap, because a wrapped word takes a hyphen that reads
 * as part of it, and rather than be cut, which would hide letters.
 */
@Composable
internal fun SeedPhraseWordGrid(words: List<String>) {
    val color = MaterialTheme.colorScheme.onSurface
    SeedPhraseColumns(minimumColumnWidth = 96.dp) { columns ->
        // Each cell is given its word, not an index into a list that may be
        // shorter by the time the cell is drawn.
        words.withIndex().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                row.forEach { (index, word) ->
                    SeedPhraseWordCell(index = index, modifier = Modifier.weight(1f)) {
                        BasicText(
                            word,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = color,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp),
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
