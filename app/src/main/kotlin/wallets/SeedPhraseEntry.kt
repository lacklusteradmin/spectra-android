package dev.spectra.wallets

import android.icu.text.ListFormatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.Chain
import uniffi.spectra_core.SeedPhraseCheck
import uniffi.spectra_core.SeedPhraseLength
import uniffi.spectra_core.SeedPhraseProblem
import uniffi.spectra_core.SeedPhraseVerdict
import uniffi.spectra_core.checkSeedPhrase
import uniffi.spectra_core.seedPhraseLengths
import uniffi.spectra_core.seedPhraseWordsFromText
import uniffi.spectra_core.seedWordSuggestions

/**
 * One seed-phrase entry grid and core's verdict on it.
 *
 * The import flow and the funds finder both read a phrase through this, so
 * the two cannot disagree about what a phrase is. The grid grows to the
 * length core judges the entry at and never drops a typed word: a fixed
 * length that is too short is refused by core rather than enforced here by
 * cutting.
 */
class SeedPhraseEntry {
    private var chainState by mutableStateOf<Chain?>(null)

    /**
     * The network the phrase is for, whose own formats core judges it in —
     * Monero's 25-word seed or Polyseed, TON's mnemonic, BIP-39 elsewhere.
     * `null` judges BIP-39, which is what the funds finder scans.
     */
    var chain: Chain?
        get() = chainState
        set(value) {
            if (value == chainState) return
            chainState = value
            reset()
        }

    /** The last network's lengths, so a render asks core once per network. */
    private var lengthsCache: Pair<Chain?, List<SeedPhraseLength>>? = null

    /** The lengths a phrase on [chain] may have, shortest first, from core. */
    val lengths: List<SeedPhraseLength>
        get() {
            val chain = chain
            lengthsCache?.let { (cachedChain, cached) -> if (cachedChain == chain) return cached }
            return seedPhraseLengths(chain).also { lengthsCache = chain to it }
        }

    /** The grid's length before anything is typed: the network's shortest. */
    val initialSlotCount: Int get() = lengths.firstOrNull()?.wordCount?.toInt() ?: 12

    /** The wordlist, from `seedPhraseLanguages(chain)`, or `null` for core to detect it. */
    var language by mutableStateOf<String?>(null)

    private var wordCountOverrideState by mutableStateOf<Int?>(null)

    /** One of core's lengths, or `null` for core to infer it from the words. */
    var wordCountOverride: Int?
        get() = wordCountOverrideState
        set(value) {
            wordCountOverrideState = value
            fitSlots(shrinking = true)
        }

    /** One entry per slot, normalized as typed. */
    var slots by mutableStateOf(List(12) { "" })
        private set

    /** The last grid core judged. Holds the words, so [reset] drops it. */
    private var verdictCache: Pair<SeedPhraseCheck, SeedPhraseVerdict>? = null

    /**
     * Everything core has to say about the grid, decided in one pass. One
     * render reads this several times, so core is asked once per grid.
     */
    val verdict: SeedPhraseVerdict
        get() {
            val check = SeedPhraseCheck(
                words = slots,
                language = language,
                wordCount = wordCountOverride?.toUInt(),
                chain = chain,
            )
            verdictCache?.let { (cachedCheck, cached) -> if (cachedCheck == check) return cached }
            return checkSeedPhrase(check).also { verdictCache = check to it }
        }

    /** The phrase as core reads it: the filled slots, normalized and joined. */
    val phrase: String get() = verdict.words.joinToString(" ")

    fun reset() {
        verdictCache = null
        language = null
        wordCountOverrideState = null
        slots = List(initialSlotCount) { "" }
    }

    /** Replace the grid with [words], judged at [wordCount]: a generated phrase, shown rather than typed. */
    fun load(words: List<String>, wordCount: Int) {
        language = null
        // The slots are replaced next, so the old grid is not fitted to the new length first.
        wordCountOverrideState = wordCount
        slots = words + List(maxOf(0, wordCount - words.size)) { "" }
    }

    fun slot(index: Int): String = slots.getOrElse(index) { "" }

    /**
     * Put what was typed or pasted at [index]. Several words fill the slots
     * from there on, adding slots rather than dropping words that do not fit.
     */
    fun update(index: Int, newValue: String) {
        if (index !in slots.indices) return
        val words = newValue.lowercase().whitespaceSeparatedWords()
        val updated = slots.toMutableList()
        val replacement = words.ifEmpty { listOf("") }
        val end = index + replacement.size
        while (updated.size < end) updated.add("")
        replacement.forEachIndexed { offset, word -> updated[index + offset] = word }
        if (updated == slots) return
        slots = updated
        fitSlots(shrinking = false)
    }

    /** Replace the whole entry with a pasted phrase, as core reads one kept numbered, on lines or between commas. */
    fun paste(text: String) {
        val words = seedPhraseWordsFromText(text)
        if (words.isEmpty()) return
        slots = List(wordCountOverride ?: initialSlotCount) { "" }
        update(0, words.joinToString(" "))
    }

    /** Up to four words of the entry's wordlist that [prefix] may become. */
    fun suggestions(prefix: String): List<String> = seedWordSuggestions(
        SeedPhraseCheck(words = slots, language = language, wordCount = wordCountOverride?.toUInt(), chain = chain),
        prefix,
        4u,
    )

    fun clear() {
        slots = List(wordCountOverride ?: initialSlotCount) { "" }
    }

    /** The network's next length past the grid's, or `null` at the longest. */
    val nextSlotCount: Int?
        get() = lengths.map { it.wordCount.toInt() }.firstOrNull { it > slots.size }

    /** Grow the grid to the next length, for a phrase typed past it. */
    fun addSlots() {
        val next = nextSlotCount ?: return
        slots = slots + List(next - slots.size) { "" }
    }

    /**
     * Size the grid to the length core judges it at, never below its last
     * filled slot. Growing alone keeps slots the user added; a new override
     * also drops blank slots past it.
     */
    private fun fitSlots(shrinking: Boolean) {
        val filledThrough = slots.indexOfLast { it.isNotEmpty() } + 1
        val target = maxOf(verdict.wordCount.toInt(), filledThrough)
        if (slots.size < target) {
            slots = slots + List(target - slots.size) { "" }
        } else if (shrinking && slots.size > target) {
            slots = slots.take(target)
        }
    }
}

/**
 * The words of [this], split at any Unicode whitespace: a Japanese phrase
 * typed with the ideographic space splits as one typed with a plain one.
 */
internal fun String.whitespaceSeparatedWords(): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    for (character in this) {
        if (character.isWhitespace()) {
            if (current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
        } else {
            current.append(character)
        }
    }
    if (current.isNotEmpty()) words += current.toString()
    return words
}

/** Core says what is wrong; the sentence is this app's to translate. */
val SeedPhraseProblem.localizedMessage: String
    get() = when (this) {
        is SeedPhraseProblem.NonStandardLength -> {
            val lengths = ListFormatter.getInstance(AppLocalization.locale, ListFormatter.Type.OR, ListFormatter.Width.WIDE)
                .format(allowed.map { it.toString() })
            AppLocalization.format("That is %lld words. A seed phrase has %@ words.", wordCount.toLong(), lengths)
        }
        is SeedPhraseProblem.WrongWordCount -> AppLocalization.format("Seed phrase must be %lld words.", expected.toLong())
        SeedPhraseProblem.InvalidChecksum -> AppLocalization.string("Invalid seed phrase checksum. Please verify your words.")
        SeedPhraseProblem.AmbiguousLanguage ->
            AppLocalization.string("These words read as different phrases in two languages. Type them in full.")
        SeedPhraseProblem.EncryptedPolyseed ->
            AppLocalization.string("This Polyseed is encrypted with a password, which Spectra cannot take for Monero.")
        SeedPhraseProblem.UnsupportedPolyseed -> AppLocalization.string("This Polyseed uses features Spectra does not support.")
    }
