package dev.spectra.models

import android.icu.text.DateFormat
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.text.RelativeDateTimeFormatter
import uniffi.spectra_core.EvmRecipientPreflightWarning
import uniffi.spectra_core.HighRiskSendWarning
import uniffi.spectra_core.LocalizableMessage
import uniffi.spectra_core.ReviewWarning
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendDestinationActivity
import uniffi.spectra_core.SpectraBridgeException
import java.util.Date
import kotlin.math.abs

// Localized display messages. AmountPresentation owns number rendering.

/** The warning, worded. Exhaustive: a reason core adds does not compile until it has words. */
val EvmRecipientPreflightWarning.localizedText: String
    get() = when (this) {
        is EvmRecipientPreflightWarning.RecipientIsContract -> AppLocalization.format(
            "Recipient is a smart contract on %@. Confirm it can receive %@ safely.", chainId.displayName, symbol,
        )
        is EvmRecipientPreflightWarning.RecipientCodeUnknown -> AppLocalization.format(
            "Could not verify recipient contract state on %@. Review destination carefully.", chainId.displayName,
        )
        is EvmRecipientPreflightWarning.TokenContractMissing -> AppLocalization.format(
            "Token contract %@ appears missing on %@. This may be a wrong-network token selection.",
            tokenSymbol, chainId.displayName,
        )
        is EvmRecipientPreflightWarning.TokenCodeUnknown -> AppLocalization.format(
            "Could not verify %@ contract bytecode on %@.", tokenSymbol, chainId.displayName,
        )
    }

/** The reason, worded. Exhaustive for the same reason. */
val HighRiskSendWarning.localizedText: String
    get() = when (this) {
        is HighRiskSendWarning.InvalidFormat ->
            AppLocalization.format("The destination address format does not match %@.", chain.displayName)
        HighRiskSendWarning.NewAddress ->
            AppLocalization.string("This is a new destination address with no prior history in this wallet.")
        is HighRiskSendWarning.EnsResolved -> AppLocalization.format(
            "ENS name '%@' resolved to %@. Confirm this resolved address before sending.", name, address,
        )
        is HighRiskSendWarning.LargeSend -> AppLocalization.format(
            "This send is %@ of your %@ balance.", formatPercent(percent.toDouble() / 100.0), symbol,
        )
        is HighRiskSendWarning.EnsOffEthereum -> AppLocalization.format(
            "ENS names are Ethereum-specific. For %@, verify the resolved EVM address very carefully.",
            chain.displayName,
        )
        HighRiskSendWarning.ChainMismatch ->
            AppLocalization.string("Wallet-chain context mismatch detected for this send.")
    }

/**
 * What core's review says before this transaction is signed, worded: the
 * same list wherever an artifact is reviewed.
 */
val SendArtifact.reviewWarningMessages: List<String>
    get() = review.warnings.map { warning ->
        when (warning) {
            is ReviewWarning.Risk -> warning.warning.localizedText
            is ReviewWarning.Contract -> warning.warning.localizedText
            ReviewWarning.OwnAddress ->
                AppLocalization.string("This destination belongs to your wallet. Confirm intentional self-send.")
            is ReviewWarning.TokenFee -> AppLocalization.format(
                "The token takes %@ %@ of this amount on the way; the recipient receives %@ %@.",
                AmountPresentation.localizedDecimal(warning.fee), symbol,
                AmountPresentation.localizedDecimal(warning.received), symbol,
            )
            is ReviewWarning.CarriesNative -> AppLocalization.format(
                "The token goes with %@ %@, the least its output can hold; the recipient keeps it.",
                AmountPresentation.localizedDecimal(warning.amount), chainId.coinSymbol,
            )
            is ReviewWarning.RegistersRecipient -> AppLocalization.format(
                "The token has not registered the recipient. This send registers it first for %@ %@, which the token keeps for the recipient.",
                AmountPresentation.localizedDecimal(warning.deposit), chainId.coinSymbol,
            )
            is ReviewWarning.TransferHook -> AppLocalization.format(
                "This token runs program %@ on every transfer, and it can refuse this one.", warning.program,
            )
        }
    }

/** Localized warning and note for a destination verdict. */
fun chainRiskProbeMessages(chainName: String, symbol: String, activity: SendDestinationActivity): Pair<String?, String?> =
    when (activity) {
        SendDestinationActivity.UNUSED -> AppLocalization.format(
            "Warning: this %@ address has zero %@ balance and no transaction history. Double-check recipient details.",
            chainName, symbol,
        ) to null
        SendDestinationActivity.EMPTY_PREVIOUSLY_USED -> null to AppLocalization.format(
            "Note: this %@ address has transaction history but currently zero %@ balance.", chainName, symbol,
        )
        SendDestinationActivity.FUNDED -> null to null
    }

/**
 * Core's sentence in the reader's language: its English template is the key
 * into the string tables, and its values go where the template says. Text
 * with no table entry — a node's or a library's own words — reads as sent.
 */
val LocalizableMessage.localizedText: String
    get() {
        val text = if (args.isEmpty()) AppLocalization.string(template) else AppLocalization.format(template, *args.toTypedArray())
        return text.replaceFirstChar { it.uppercase(AppLocalization.locale) }
    }

/**
 * What the user reads when a call fails. UniFFI words its exceptions with
 * their type and field names, so a bridge error's `message` never belongs on
 * screen. Core's sentences for refused input and failed requests are
 * translated here; a network or decoding failure carries a transport message
 * meant for the log, so it gets a fixed sentence instead.
 */
fun userErrorMessage(error: Throwable): String = when (error) {
    is SpectraBridgeException.InvalidInput -> error.reason.localizedText
    is SpectraBridgeException.Failure -> error.reason.localizedText
    is SpectraBridgeException.Network ->
        AppLocalization.string("Couldn't reach the network. Check your connection and try again.")
    is SpectraBridgeException.Decode ->
        AppLocalization.string("Spectra received data it couldn't read. Try again later.")
    is SpectraBridgeException.StoreUnreadable -> AppLocalization.string("content.unreadable.subtitle")
    is DisplayedError -> error.message
    else -> AppLocalization.string("Something went wrong. Try again.")
}

/** A failure already worded for the reader. */
class DisplayedError(override val message: String) : Exception(message)

/** How much of a date a formatted date shows. */
enum class DateStyle { OMITTED, ABBREVIATED, LONG }

/** How much of a time a formatted date shows. */
enum class TimeStyle { OMITTED, SHORTENED, STANDARD }

/**
 * A moment in the app's language and the reader's region. The words around a
 * date are in the app's language, which need not be the system's, so the
 * date's month names and order are too.
 */
fun formatAppDate(millis: Long, date: DateStyle = DateStyle.ABBREVIATED, time: TimeStyle = TimeStyle.SHORTENED): String {
    val dateStyle = when (date) {
        DateStyle.OMITTED -> DateFormat.NONE
        DateStyle.ABBREVIATED -> DateFormat.MEDIUM
        DateStyle.LONG -> DateFormat.LONG
    }
    val timeStyle = when (time) {
        TimeStyle.OMITTED -> DateFormat.NONE
        TimeStyle.SHORTENED -> DateFormat.SHORT
        TimeStyle.STANDARD -> DateFormat.MEDIUM
    }
    return DateFormat.getDateTimeInstance(dateStyle, timeStyle, AppLocalization.locale).format(Date(millis))
}

/** A Unix-seconds moment from core, formatted as [formatAppDate] does. */
fun formatUnixDate(seconds: Double, date: DateStyle = DateStyle.ABBREVIATED, time: TimeStyle = TimeStyle.SHORTENED): String =
    formatAppDate((seconds * 1000).toLong(), date, time)

/** How long ago [millis] was, abbreviated and numeric ("5 min. ago"). */
fun formatRelativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val formatter = RelativeDateTimeFormatter.getInstance(
        android.icu.util.ULocale.forLocale(AppLocalization.locale), null, RelativeDateTimeFormatter.Style.SHORT, android.icu.text.DisplayContext.CAPITALIZATION_NONE,
    )
    val seconds = (now - millis) / 1000
    val (amount, unit) = when {
        abs(seconds) < 60 -> seconds.toDouble() to RelativeDateTimeFormatter.RelativeDateTimeUnit.SECOND
        abs(seconds) < 3600 -> (seconds / 60).toDouble() to RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE
        abs(seconds) < 86_400 -> (seconds / 3600).toDouble() to RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR
        else -> (seconds / 86_400).toDouble() to RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
    }
    return formatter.formatNumeric(-amount, unit)
}

/** A fraction as a whole percent in the display locale ("25%"). */
fun formatPercent(fraction: Double, fractionDigits: Int = 0): String =
    NumberFormat.getPercentInstance(AppLocalization.locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(fraction)

/** A plain number in the display locale, with grouping. */
fun formatNumber(value: Number): String = NumberFormat.getNumberInstance(AppLocalization.locale).format(value)

/** A duration in seconds as the display locale words it ("2 hr, 5 min"). */
fun formatDuration(seconds: Long): String {
    val format = MeasureFormat.getInstance(AppLocalization.locale, MeasureFormat.FormatWidth.SHORT)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    val parts = buildList {
        if (hours > 0) add(android.icu.util.Measure(hours, android.icu.util.MeasureUnit.HOUR))
        if (minutes > 0) add(android.icu.util.Measure(minutes, android.icu.util.MeasureUnit.MINUTE))
        if (rest > 0 || isEmpty()) add(android.icu.util.Measure(rest, android.icu.util.MeasureUnit.SECOND))
    }
    return format.formatMeasures(*parts.toTypedArray())
}
