package dev.spectra.models

import androidx.compose.ui.graphics.Color
import uniffi.spectra_core.CatalogColor
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainEntry
import uniffi.spectra_core.ChainTag
import uniffi.spectra_core.DonationDestination
import uniffi.spectra_core.FiatAmountRules
import uniffi.spectra_core.FiatCurrency
import uniffi.spectra_core.TokenStandard
import uniffi.spectra_core.TokenTag
import uniffi.spectra_core.fiatCurrencyCatalog
import uniffi.spectra_core.listAllChains
import uniffi.spectra_core.listChainTags
import uniffi.spectra_core.listTokenTags
import uniffi.spectra_core.tokenStandardName
import java.util.Currency

/**
 * `Chain` is generated from core's `registry::Chain`, in `core/data/chains.toml`
 * order. Its identity, name, symbol and capabilities come from that catalog,
 * which crosses the FFI once, as `listAllChains()`.
 */
object ChainCatalog {
    val entries: List<ChainEntry> by lazy { listAllChains() }
    private val entryByChain: Map<Chain, ChainEntry> by lazy { entries.associateBy { it.chain } }
    private val chainById: Map<String, Chain> by lazy { entries.associate { it.id to it.chain } }

    /** Every chain, in catalog order. */
    val all: List<Chain> by lazy { entries.map { it.chain } }

    /** The chains that are not testnets, in catalog order. */
    val mainnets: List<Chain> by lazy { entries.filter { !it.isTestnet }.map { it.chain } }

    /** Chains that can hold tracked tokens, in catalog order. */
    val tokenHostingChains: List<Chain> by lazy { all.filter { it.hostsTokens } }

    fun entry(chain: Chain): ChainEntry? = entryByChain[chain]

    fun chain(id: String): Chain? = chainById[id]
}

/**
 * This chain's catalog row. `null` only if the enum and the catalog have
 * drifted, which core's `chain_order_matches_the_catalog` fails on.
 */
val Chain.entry: ChainEntry? get() = ChainCatalog.entry(this)

/** The catalog's stable id — `"bitcoin"`, `"bitcoin-cash"`, `"bnb"`. */
val Chain.id: String get() = entry?.id ?: ""

/** The catalog's name — `"Bitcoin Cash"`, `"XRP Ledger"`. One spelling per chain. */
val Chain.displayName: String get() = entry?.name ?: ""

/** A wallet on this chain is an account of many addresses, which a rescan can walk. */
val Chain.usesAccountUtxo: Boolean get() = entry?.usesAccountUtxo ?: false

/** The prefixes a watched account public key starts with on this network. */
val Chain.accountKeyPrefixes: List<String> get() = entry?.accountKeyPrefixes ?: emptyList()

/** The send screen has a network card to show for this chain. */
val Chain.hasSendPreview: Boolean get() = entry?.hasSendPreview ?: false

/** The chain can hold tracked tokens. */
val Chain.hostsTokens: Boolean get() = entry?.hostsTokens ?: false

/** The mainnet this chain belongs to, or itself. */
val Chain.mainnetCounterpart: Chain get() = entry?.mainnet ?: this

/** A test network's faucet page, where its coins are free. */
val Chain.faucetUrl: String? get() = entry?.faucetUrl

/** The network's native token symbol, derived by core. */
val Chain.gasTokenSymbol: String get() = entry?.gasTokenSymbol ?: ""

/**
 * The chain's native asset decimals, from the catalog. `null` only if the
 * enum and the catalog have drifted: a guessed precision would misstate
 * every fee on the chain.
 */
val Chain.nativeDecimals: UInt? get() = entry?.nativeDecimals

/** A terse example of what an address on this chain looks like. */
val Chain.addressPrefixHint: String get() = entry?.addressPrefixHint ?: ""

val Chain.isEvm: Boolean get() = entry?.isEvm ?: false

val Chain.isTestnet: Boolean get() = entry?.isTestnet ?: false

val Chain.searchKeywords: List<String> get() = entry?.searchKeywords ?: emptyList()

/** The chain's catalog colour. */
val Chain.color: Color get() = entry?.color?.color ?: Color.Gray

/** The chain's artwork in the crypto icon library. */
val Chain.artworkName: String get() = entry?.artworkName ?: ""

/** A chain's network artwork and catalog colour, as a badge draws them. */
data class ChainBadge(val artworkName: String?, val color: Color) {
    companion object {
        /** No chain, or one the catalog does not list: no artwork, in mint. */
        val FALLBACK = ChainBadge(null, CatalogColor.MINT.color)
    }
}

/**
 * [chain]'s badge — a holding's network, a wallet's family — or `null` for no
 * chain or one the catalog does not list.
 */
fun nativeChainBadge(chain: Chain?): ChainBadge? {
    val entry = chain?.entry ?: return null
    return ChainBadge(entry.artworkName, entry.color.color)
}

/** The catalog palette, as the app draws it. */
val CatalogColor.color: Color
    get() = when (this) {
        CatalogColor.BLUE -> Color(0xFF0A84FF)
        CatalogColor.CYAN -> Color(0xFF32ADE6)
        CatalogColor.GRAY -> Color(0xFF8E8E93)
        CatalogColor.GREEN -> Color(0xFF34C759)
        CatalogColor.INDIGO -> Color(0xFF5856D6)
        CatalogColor.MINT -> Color(0xFF00C7BE)
        CatalogColor.ORANGE -> Color(0xFFFF9500) // design-tokens: artwork
        CatalogColor.PINK -> Color(0xFFFF2D55)
        CatalogColor.PURPLE -> Color(0xFFAF52DE)
        CatalogColor.RED -> Color(0xFFFF3B30)
        CatalogColor.TEAL -> Color(0xFF30B0C7)
        CatalogColor.YELLOW -> Color(0xFFFFCC00)
    }

/** Every chain tag, in the order the picker offers them. */
val chainTagPickerOrder: List<ChainTag> by lazy { listChainTags() }

val ChainTag.title: String
    get() = AppLocalization.string(
        when (this) {
            ChainTag.LAYER1 -> "Layer 1"
            ChainTag.LAYER2 -> "Layer 2"
            ChainTag.EVM -> "EVM"
            ChainTag.UTXO -> "UTXO"
            ChainTag.EUTXO -> "eUTXO"
            ChainTag.MOVE -> "Move"
            ChainTag.SUBSTRATE -> "Substrate"
            ChainTag.POW -> "PoW"
            ChainTag.PRIVACY -> "Privacy"
            ChainTag.PAYMENTS -> "Payments"
            ChainTag.TESTNET -> "Testnet"
        },
    )

/** Every token tag, in the order a filter offers them. */
val tokenTagFilterOrder: List<TokenTag> by lazy { listTokenTags() }

val TokenTag.title: String
    get() = AppLocalization.string(
        when (this) {
            TokenTag.STABLECOIN -> "Stablecoin"
            TokenTag.MEME -> "Meme"
        },
    )

/** The token protocol's name, as core spells it. */
val TokenStandard.displayName: String get() = tokenStandardName(this)

/** Core's currencies, with the order and rules a picker and a formatter need. */
object FiatCurrencies {
    private val catalog: List<FiatAmountRules> by lazy { fiatCurrencyCatalog() }
    private val rulesByCurrency: Map<FiatCurrency, FiatAmountRules> by lazy { catalog.associateBy { it.currency } }

    val all: List<FiatCurrency> by lazy { catalog.map { it.currency } }

    fun rules(currency: FiatCurrency): FiatAmountRules = rulesByCurrency.getValue(currency)
}

val FiatCurrency.displayRules: FiatAmountRules get() = FiatCurrencies.rules(this)

/** The ISO 4217 code. */
val FiatCurrency.code: String get() = displayRules.code

/** The currency's name and code, in the display language. */
val FiatCurrency.displayName: String
    get() {
        val name = runCatching { Currency.getInstance(code).getDisplayName(AppLocalization.locale) }.getOrDefault(code)
        return "$name ($code)"
    }

/** A donation address's title is its chain's name. */
val DonationDestination.title: String get() = chainId.displayName
