package dev.spectra.models

import android.icu.text.DecimalFormat
import android.icu.text.DecimalFormatSymbols
import android.icu.text.NumberFormat
import android.icu.util.Currency
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.AssetPrecisionCatalog
import uniffi.spectra_core.Chain
import uniffi.spectra_core.FeeBound
import uniffi.spectra_core.FiatCurrency
import uniffi.spectra_core.NetworkFee
import uniffi.spectra_core.PortfolioValuation
import uniffi.spectra_core.PriceAlertRule
import uniffi.spectra_core.QuotedTotal
import uniffi.spectra_core.TransactionRecord
import uniffi.spectra_core.formatAssetAmount
import java.util.Locale

/**
 * A render-time value over core projections. No storage, services or side
 * effects, and no money arithmetic: amounts arrive as exact decimals and every
 * fiat figure arrives in the display currency, both from core.
 */
class AmountPresentation(
    val assetPrecision: AssetPrecisionCatalog?,
    val valuation: PortfolioValuation?,
    /** The currency the fiat figures are in when core has valued nothing yet. */
    val selectedFiatCurrency: FiatCurrency,
) {
    private val currency: FiatCurrency get() = valuation?.currency ?: selectedFiatCurrency

    // ── Fiat ────────────────────────────────────────────────────────────

    /** A display-currency figure, or "—" when core has none. */
    fun formattedFiat(value: Double?, currency: FiatCurrency? = null): String =
        formattedFiatIfAvailable(value, currency) ?: "—"

    fun formattedFiatIfAvailable(value: Double?, currency: FiatCurrency? = null): String? {
        if (value == null || !value.isFinite()) return null
        val resolved = currency ?: this.currency
        val formatter = AmountFormatters.fiat(resolved)
        val minimumVisible = resolved.displayRules.minimumVisible
        if (value > 0 && value < minimumVisible) return "<" + formatter.format(minimumVisible)
        return formatter.format(value)
    }

    /** The figure alone. An unpriced holding shows "—" on its own row. */
    fun formattedQuotedTotal(total: QuotedTotal?): String {
        val fiat = total?.fiatTotal ?: return "—"
        return formattedFiat(fiat)
    }

    fun formattedWalletTotal(walletId: String): String = formattedQuotedTotal(valuation?.wallets?.get(walletId))

    /** What a wallet's holding is worth, as core valued it. */
    fun holdingValue(walletId: String, coin: AssetHolding): Double? = valuation?.holdingValues?.get(walletId)?.get(coin.id)

    /** One unit of a held asset, as core priced it. */
    fun price(coin: AssetHolding): Double? = valuation?.prices?.get(coin.id)

    /** A price alert's target, as core converted it. */
    fun alertTarget(alert: PriceAlertRule): Double? = valuation?.alertTargets?.get(alert.id)

    // ── Asset amounts ───────────────────────────────────────────────────

    /** The amount alone, as a compact row shows it: core picks the places and cuts. */
    fun formattedAssetAmountValue(amount: String, deploymentId: String?): String {
        val decimals = supportedDecimalPlaces(deploymentId) ?: return "—"
        val text = formatAssetAmount(amount, decimals) ?: return "—"
        val value = localizedDecimal(text.value)
        return if (text.belowThreshold) "<$value" else value
    }

    fun formattedAssetAmount(amount: String, symbol: String, deploymentId: String?): String =
        "${formattedAssetAmountValue(amount, deploymentId)} $symbol"

    fun formattedTransactionAmount(transaction: TransactionRecord): String =
        formattedAssetAmount(transaction.amount, transaction.symbol, transaction.deploymentId)

    /** Every digit the record holds. */
    fun formattedTransactionDetailAmount(transaction: TransactionRecord): String =
        "${localizedDecimal(transaction.amount)} ${transaction.symbol}"

    // ── Network fees ────────────────────────────────────────────────────

    /** An amount of the chain's gas token — a fee, a deposit, rent — exactly as core stated it. */
    fun formattedNativeAmount(amount: String, chain: Chain): String = exactAmount(amount, chain.gasTokenSymbol)

    /**
     * A fee as a compact row shows it: core's significant-digit policy at the
     * gas token's precision, with its display-currency value when core had one.
     */
    fun compactNetworkFee(fee: String, value: Double?, chain: Chain): String {
        val decimals = chain.nativeDecimals
        val text = decimals?.let { formatAssetAmount(fee, it) }
        val native = if (text != null) {
            (if (text.belowThreshold) "<" else "") + localizedDecimal(text.value) + " " + chain.gasTokenSymbol
        } else {
            formattedNativeAmount(fee, chain)
        }
        val fiat = formattedFiatIfAvailable(value) ?: return native
        return "$native (~$fiat)"
    }

    /** A reviewed fee, marked as an estimate, exact, or the most it can cost. */
    fun reviewedNetworkFee(fee: NetworkFee, chain: Chain): String {
        val text = compactNetworkFee(fee.amount, fee.value, chain)
        return when (fee.bound) {
            FeeBound.ESTIMATE -> "≈ $text"
            FeeBound.EXACT -> text
            FeeBound.AT_MOST -> "≤ $text"
        }
    }

    /** A gas price as a fee row shows it: four significant digits. */
    fun compactGasPrice(gwei: String): String {
        val value = gwei.toDoubleOrNull() ?: return "${localizedDecimal(gwei)} gwei"
        return "${AmountFormatters.gasPrice().format(value)} gwei"
    }

    /** A gas price in gwei, exactly as core stated it. */
    fun formattedGasPrice(gwei: String): String = "${localizedDecimal(gwei)} gwei"

    // ── Transaction detail rows ─────────────────────────────────────────

    fun receiptEffectiveGasPriceText(transaction: TransactionRecord): String? =
        transaction.receiptEffectiveGasPriceGwei?.let { formattedGasPrice(it) }

    fun receiptNetworkFeeText(transaction: TransactionRecord): String? =
        transaction.receiptNetworkFee?.let { formattedNativeAmount(it, transaction.chainId) }

    fun confirmedNetworkFeeText(transaction: TransactionRecord): String? =
        transaction.confirmedNetworkFee?.let { formattedNativeAmount(it, transaction.chainId) }

    fun storedFeeRateText(transaction: TransactionRecord): String? =
        transaction.feeRateDescription?.trim()?.takeIf { it.isNotEmpty() }

    private fun supportedDecimalPlaces(deploymentId: String?): UInt? {
        val precision = assetPrecision ?: return null
        return deploymentId?.let { precision.byDeploymentId[it] } ?: precision.unknownDecimals
    }

    companion object {
        /** An exact decimal with the display locale's separator. No grouping, no digit added or dropped. */
        fun localizedDecimal(text: String): String {
            val separator = DecimalFormatSymbols.getInstance(AppLocalization.locale).decimalSeparator
            return if (separator == '.') text else text.replace('.', separator)
        }

        /**
         * What a decimal field holds, as core reads it: the keyboard types the
         * device's separator, which may be a comma; core reads only `.`.
         */
        fun canonicalDecimalInput(text: String, locale: Locale = Locale.getDefault()): String {
            val trimmed = text.trim()
            val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
            return if (separator == '.') trimmed else trimmed.replace(separator, '.')
        }

        /** The reverse: a decimal core wrote, as a decimal field holds it. */
        fun decimalFieldText(canonical: String): String {
            val separator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
            return if (separator == '.') canonical else canonical.replace('.', separator)
        }

        /** An exact amount core gave, every digit, beside its symbol. */
        fun exactAmount(amount: String, symbol: String): String = "${localizedDecimal(amount)} $symbol"
    }
}

/** Native formatters, rebuilt when the display locale changes. */
private object AmountFormatters {
    private var locale: Locale? = null
    private val fiat = HashMap<FiatCurrency, NumberFormat>()
    private var gasPrice: DecimalFormat? = null

    private fun matchDisplayLocale() {
        val current = AppLocalization.locale
        if (current != locale) {
            locale = current
            fiat.clear()
            gasPrice = null
        }
    }

    @Synchronized
    fun fiat(currency: FiatCurrency): NumberFormat {
        matchDisplayLocale()
        return fiat.getOrPut(currency) {
            val rules = currency.displayRules
            NumberFormat.getCurrencyInstance(AppLocalization.locale).apply {
                this.currency = Currency.getInstance(rules.code)
                minimumFractionDigits = rules.decimals.toInt()
                maximumFractionDigits = rules.decimals.toInt()
            }
        }
    }

    @Synchronized
    fun gasPrice(): DecimalFormat {
        matchDisplayLocale()
        return gasPrice ?: (NumberFormat.getNumberInstance(AppLocalization.locale) as DecimalFormat).apply {
            setSignificantDigitsUsed(true)
            maximumSignificantDigits = 4
        }.also { gasPrice = it }
    }
}
