package dev.spectra.models

import android.content.Context
import android.os.LocaleList
import org.json.JSONObject
import java.util.Locale

/**
 * Every string a person reads, from `assets/strings/RuntimeStrings.<locale>.json`:
 * the reader's preferred languages that ship, then the source language. A key
 * in no table reads as itself, so a table holds only what differs from its
 * key — a translation, or the words for a dotted name such as
 * `settings.aboutTitle`.
 *
 * The tables are spectra-apple's, keyed by the same English sentences, with
 * Apple's format specifiers (`%@`, `%1$@`, `%lld`); [format] reads them as
 * Java's. A sentence with values is [format], never `String.format` on a
 * looked-up string, so its numbers follow the display locale.
 */
object AppLocalization {
    private class Tables(val locale: Locale, val strings: List<Map<String, String>>, val locales: LocaleList)

    private lateinit var appContext: Context
    @Volatile private var cached: Tables? = null

    /** Called once by the application, before any view reads a string. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * The language of the table read first, with the reader's region and
     * number preferences. Every date, number and currency the app formats
     * uses it, so figures follow the same settings as the words around them.
     */
    val locale: Locale get() = tables().locale

    fun string(key: String): String {
        for (table in tables().strings) {
            table[key]?.let { return it }
        }
        return key
    }

    fun format(key: String, vararg arguments: Any?): String = formatTemplate(string(key), arguments)

    /**
     * A sentence about [count] things. A table may give the singular beside
     * the key as `<key>#one`, read only from the table that has [key], so a
     * language without number agreement never borrows another's singular.
     */
    fun formatCount(key: String, count: Long, vararg arguments: Any?): String {
        var template = key
        for (table in tables().strings) {
            val value = table[key] ?: continue
            template = if (count == 1L) table["$key#one"] ?: value else value
            break
        }
        return formatTemplate(template, arguments)
    }

    private fun formatTemplate(template: String, arguments: Array<out Any?>): String {
        if (arguments.isEmpty()) return template.replace("%%", "%")
        return runCatching { String.format(locale, javaTemplate(template), *arguments) }.getOrElse { template }
    }

    /** Apple's specifiers as Java's: `%@` is `%s`, a length modifier is dropped. */
    internal fun javaTemplate(template: String): String =
        template
            .replace(Regex("%(\\d+\\$)?@"), "%$1s")
            .replace(Regex("%(\\d+\\$)?(ll|l|h|q|z|t|j)([diuxX])"), "%$1$3")
            .replace(Regex("%(\\d+\\$)?u"), "%$1d")

    /** Built once, and again after the reader's languages change. */
    private fun tables(): Tables {
        val current = LocaleList.getDefault()
        cached?.let { if (it.locales == current) return it }
        val manifest = readJson("strings/RuntimeStrings.manifest.json")
        val source = manifest?.optString("sourceLanguage") ?: "en"
        val available = manifest?.optJSONArray("availableLocales")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        } ?: listOf(source)
        val identifiers = mutableListOf<String>()
        for (index in 0 until current.size()) {
            val match = shippedLocalization(current[index], available)
            if (match != null && match !in identifiers) identifiers += match
        }
        if (source !in identifiers) identifiers += source
        val tables = Tables(
            locale = displayLocale(identifiers.first(), current[0] ?: Locale.getDefault()),
            strings = identifiers.mapNotNull { readTable("strings/RuntimeStrings.$it.json") },
            locales = current,
        )
        cached = tables
        return tables
    }

    /** [language] with the reader's region and extensions. */
    private fun displayLocale(language: String, reader: Locale): Locale {
        val tag = Locale.forLanguageTag(language)
        return Locale.Builder()
            .setLocale(reader)
            .setLanguage(tag.language)
            .setScript(tag.script)
            .build()
    }

    /**
     * The shipped table for a preferred language: an exact match, the
     * Chinese script it names or implies (`zh-TW` reads `zh-Hant`), or its
     * bare language.
     */
    private fun shippedLocalization(locale: Locale, available: List<String>): String? {
        val tag = locale.toLanguageTag()
        available.firstOrNull { it.equals(tag, ignoreCase = true) }?.let { return it }
        if (locale.language == "zh") {
            val traditional = locale.script == "Hant" ||
                (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
            val script = if (traditional) "zh-Hant" else "zh-Hans"
            return available.firstOrNull { it.equals(script, ignoreCase = true) }
        }
        return available.firstOrNull { it.equals(locale.language, ignoreCase = true) }
    }

    private fun readJson(path: String): JSONObject? = runCatching {
        appContext.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
    }.getOrNull()

    private fun readTable(path: String): Map<String, String>? {
        val json = readJson(path) ?: return null
        val table = HashMap<String, String>(json.length() * 2)
        for (key in json.keys()) table[key] = json.getString(key)
        return table
    }
}
